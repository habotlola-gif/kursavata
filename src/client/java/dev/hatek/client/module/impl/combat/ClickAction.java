package dev.hatek.client.module.impl.combat;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.KeySetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * ClickAction: one key uses an ender pearl, another a wind charge (FTHelper uses the same engine for its items).
 * The engine works in separate ticks like a careful player: the slot is selected in one tick, the item is used in the
 * next one and the old slot comes back after that. An item from the main inventory is swapped into the selected
 * slot, used once the server has surely seen the swap and swapped back; movement stops for those ticks so the
 * inventory clicks look natural to the server.
 */
public final class ClickAction extends Module {
    private static ClickAction instance;

    private final KeySetting pearlKey = new KeySetting("Жемчуг эндера (клавиша)", -1);
    private final KeySetting windKey = new KeySetting("Заряд ветра (клавиша)", -1);
    private final BoolSetting notify = new BoolSetting("Сообщать, если предмета нет", true);
    private final BoolSetting debug = new BoolSetting("Писать в чат, что происходит (отладка)", false);

    private boolean pearlWas;
    private boolean windWas;

    // ------------------------------------------------------------------------------------------------ engine state
    private static Item pending;
    private static String pendingLabel = "";
    private static Item item;
    private static int stage;          // 0 idle, 1 start, 2 use, 3 restore slot, 4 swap in, 5 use swapped, 7/8 swap back
    private static int previousSlot = -1;
    private static int itemSlot = -1;
    private static int backpackSlot = -1;
    private static int pauseTicks;
    private static int tickNumber;
    private static int readyAtTick;
    private static boolean pauseThroughTick;
    private static boolean pauseArmed;
    private static boolean borrowed;
    private static ItemStack borrowedStack = ItemStack.EMPTY;
    private static ItemStack displacedStack = ItemStack.EMPTY;
    private static InteractionHand hand = InteractionHand.MAIN_HAND;

    public ClickAction() {
        super("ClickAction", "Keys that use an ender pearl or a wind charge, also from the inventory", Category.COMBAT);
        instance = this;
        with(this.pearlKey, this.windKey, this.notify, this.debug);
    }

    public static ClickAction instance() {
        return instance;
    }

    // ------------------------------------------------------------------------------------------------ hooks

    public static boolean busy() {
        return stage != 0;
    }

    /** True while the player must stand still (inventory click path). */
    public static boolean pauseMovement() {
        return pauseArmed || pauseThroughTick;
    }

    /** Start of LocalPlayer.tick. */
    public static void beforePlayerTick(LocalPlayer player) {
        if (pauseMovement()) {
            player.setSprinting(false);
        }
    }

    /** End of LocalPlayer.tick. */
    public static void afterPlayerTick() {
        if (pauseArmed && (stage == 4 || stage == 8)) {
            ++pauseTicks;
        }
        pauseThroughTick = false;
    }

    /** Asks the engine to use this item; false if it is busy. Used by FTHelper. */
    public static boolean request(Item requested, String label) {
        if (requested == null || stage != 0 || pending != null) {
            return false;
        }
        pending = requested;
        pendingLabel = label == null ? "" : label;
        return true;
    }

    // ------------------------------------------------------------------------------------------------ keys

    @Override
    protected void onDisable() {
        this.pearlWas = false;
        this.windWas = false;
    }

    @Override
    public void onClientTick() {
        boolean pearl = this.pearlKey.isDown();
        boolean wind = this.windKey.isDown();
        boolean pearlPressed = pearl && !this.pearlWas;
        boolean windPressed = wind && !this.windWas;
        this.pearlWas = pearl;
        this.windWas = wind;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (pearlPressed || windPressed) {
            if (this.debug.value()) {
                message("нажата клавиша " + (pearlPressed ? "жемчуга" : "заряда ветра") + ", модуль работает");
            }
            request(pearlPressed ? Items.ENDER_PEARL : Items.WIND_CHARGE, pearlPressed ? "жемчуга эндера" : "заряда ветра");
        }
    }

    // ------------------------------------------------------------------------------------------------ engine

    /** Runs every client tick, whether the module is on or not. */
    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        ++tickNumber;
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null || !player.isAlive() || player.isSpectator()) {
            reset();
            return;
        }
        if (stage == 0) {
            if (pending == null || client.gui.screen() != null) {
                return;
            }
            if (OffhandSwapper.busy()) {
                return;
            }
            item = pending;
            pending = null;
            stage = 1;
        }
        if (client.gui.screen() != null) {
            requestCleanup();
            return;
        }
        if (OffhandSwapper.busy() || client.options.keyAttack.isDown() || client.options.keyUse.isDown()
                || player.isUsingItem()) {
            if (stage == 4 || stage == 8) {
                pauseArmed = false;
                pauseTicks = 0;
            }
            return;
        }
        if (stage == 4 || stage == 8) {
            pauseArmed = true;
        }
        switch (stage) {
            case 1 -> {
                if (player.getMainHandItem().is(item)) {
                    hand = InteractionHand.MAIN_HAND;
                    stage = 2;
                    break;
                }
                if (player.getOffhandItem().is(item)) {
                    hand = InteractionHand.OFF_HAND;
                    stage = 2;
                    break;
                }
                itemSlot = findHotbarSlot(player, item);
                if (itemSlot < 0) {
                    backpackSlot = findBackpackSlot(player, item);
                    if (backpackSlot < 0) {
                        if (instance == null || instance.notify.value()) {
                            message("нет " + (pendingLabel.isEmpty() ? "предмета" : pendingLabel) + " (ни в руках, ни в хотбаре, ни в инвентаре).");
                        }
                        reset();
                        return;
                    }
                    if (player.isPassenger() || !player.containerMenu.getCarried().isEmpty()) {
                        message("нельзя взять предмет из рюкзака сейчас.");
                        reset();
                        return;
                    }
                    previousSlot = player.getInventory().getSelectedSlot();
                    pauseTicks = 0;
                    stage = 4;
                    pauseArmed = true;
                    return;
                }
                previousSlot = player.getInventory().getSelectedSlot();
                player.getInventory().setSelectedSlot(itemSlot);
                stage = 2;
            }
            case 2 -> {
                if (player.getItemInHand(hand).is(item)) {
                    client.gameMode.useItem((Player) player, hand);
                    player.swing(hand);
                    debug("использовано (" + (previousSlot >= 0 ? "хотбар, слот " + (itemSlot + 1) : "из руки") + ")");
                }
                stage = previousSlot >= 0 ? 3 : 0;
                if (stage == 0) {
                    reset();
                }
            }
            case 3 -> {
                if (previousSlot >= 0) {
                    player.getInventory().setSelectedSlot(previousSlot);
                }
                reset();
            }
            case 4 -> {
                if (pauseTicks < 2) {
                    return;
                }
                if (!player.inventoryMenu.getSlot(backpackSlot).getItem().is(item)
                        || player.getInventory().getSelectedSlot() != previousSlot) {
                    reset();
                    return;
                }
                borrowedStack = player.inventoryMenu.getSlot(backpackSlot).getItem().copy();
                displacedStack = player.getInventory().getItem(previousSlot).copy();
                swapBackpack(client, player);
                borrowed = true;
                pauseThroughTick = true;
                pauseArmed = false;
                readyAtTick = tickNumber + Math.max(2, Math.min(30, (latencyMs(client, player) + 49) / 50 + 1));
                stage = 5;
            }
            case 5 -> {
                if (tickNumber < readyAtTick) {
                    return;
                }
                if (player.getInventory().getSelectedSlot() == previousSlot
                        && ItemStack.matches(player.inventoryMenu.getSlot(backpackSlot).getItem(), displacedStack)
                        && ItemStack.matches(player.getMainHandItem(), borrowedStack)) {
                    client.gameMode.useItem((Player) player, InteractionHand.MAIN_HAND);
                    player.swing(InteractionHand.MAIN_HAND);
                    debug("использовано (инвентарь, слот " + backpackSlot + ")");
                }
                stage = 7;
            }
            case 7 -> {
                pauseTicks = 0;
                stage = 8;
                pauseArmed = true;
            }
            case 8 -> {
                if (pauseTicks < 2) {
                    return;
                }
                ItemStack working = player.getInventory().getItem(previousSlot);
                if (borrowed
                        && ItemStack.matches(player.inventoryMenu.getSlot(backpackSlot).getItem(), displacedStack)
                        && (working.isEmpty() || ItemStack.isSameItemSameComponents(working, borrowedStack))) {
                    swapBackpack(client, player);
                    pauseThroughTick = true;
                }
                reset();
            }
            default -> reset();
        }
    }

    private static void requestCleanup() {
        pauseArmed = false;
        pauseTicks = 0;
        if (borrowed) {
            stage = 7;
        } else if (previousSlot >= 0 && stage <= 3) {
            stage = 3;
        } else {
            reset();
        }
    }

    private static int findHotbarSlot(LocalPlayer player, Item requested) {
        for (int slot = 0; slot < 9; ++slot) {
            if (player.getInventory().getItem(slot).is(requested)) {
                return slot;
            }
        }
        return -1;
    }

    private static int findBackpackSlot(LocalPlayer player, Item requested) {
        for (int slot = 9; slot < 36; ++slot) {
            if (player.inventoryMenu.getSlot(slot).getItem().is(requested)) {
                return slot;
            }
        }
        return -1;
    }

    private static int latencyMs(Minecraft client, LocalPlayer player) {
        ClientPacketListener connection = client.getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    private static void swapBackpack(Minecraft client, LocalPlayer player) {
        int container = player.inventoryMenu.containerId;
        client.gameMode.handleContainerInput(container, backpackSlot, previousSlot, ContainerInput.SWAP, (Player) player);
        if (client.getConnection() != null) {
            client.getConnection().send(new ServerboundContainerClosePacket(container));
        }
    }

    private static void reset() {
        stage = 0;
        item = null;
        previousSlot = -1;
        itemSlot = -1;
        backpackSlot = -1;
        borrowed = false;
        borrowedStack = ItemStack.EMPTY;
        displacedStack = ItemStack.EMPTY;
        pauseArmed = false;
        pauseThroughTick = false;
        pending = null;
    }

    private static void debug(String text) {
        if (instance != null && instance.debug.value()) {
            message(text);
        }
    }

    private static void message(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§bClickAction: §f" + text));
        }
    }
}
