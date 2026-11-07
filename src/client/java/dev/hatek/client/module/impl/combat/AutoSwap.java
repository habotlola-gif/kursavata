package dev.hatek.client.module.impl.combat;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.KeySetting;
import dev.hatek.client.module.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/**
 * Cycles items into the offhand on a key. Two keys, each for one item category: "Talisman/Totem" or "Sphere/Orb"
 * (matched by keywords in the item name). If the offhand already holds an item of that category, the next
 * item of the same category is taken (sphere A -> sphere B); if it holds something else, the first match is taken.
 * Hotbar items go through hotbar packets (like pressing F), bag items through one SWAP click while standing still.
 */
public final class AutoSwap extends Module {
    private static final int TYPE_TOTEM = 0;
    private static final int TYPE_SPHERE = 1;
    private static final String[] TYPE_NAMES = {"Talisman/Totem", "Sphere/Orb"};

    private static AutoSwap instance;

    private final KeySetting key1 = new KeySetting("Key 1", -1);
    private final ModeSetting item1 = new ModeSetting("Item 1", 1, TYPE_NAMES);
    private final KeySetting key2 = new KeySetting("Key 2", -1);
    private final ModeSetting item2 = new ModeSetting("Item 2", 0, TYPE_NAMES);

    private boolean key1Was;
    private boolean key2Was;
    private int pending;            // 0 none, 1 key 1, 2 key 2
    private int targetType;
    private boolean forceClick;     // fallback: inventory click instead of hotbar packets
    private boolean fallbackTried;
    private int stage;              // 0 idle, 1 preparing, 2 waiting for the server to confirm
    private int source = -1;
    private int pauseTicks;
    private int tickNumber;
    private int readyAtTick;
    private boolean pauseArmed;
    private boolean pauseThroughTick;
    private ItemStack expectedOffhand = ItemStack.EMPTY;
    private ItemStack expectedSource = ItemStack.EMPTY;

    public AutoSwap() {
        super("AutoSwap", "Cycles talismans/spheres into the offhand on a key", Category.COMBAT);
        instance = this;
        with(this.key1, this.item1, this.key2, this.item2);
    }

    public static AutoSwap instance() {
        return instance;
    }

    /** True while the player must stand still (inventory click path). */
    public static boolean pauseMovement() {
        return instance != null && (instance.pauseArmed || instance.pauseThroughTick);
    }

    @Override
    protected void onDisable() {
        reset();
    }

    /** Start of LocalPlayer.tick. */
    public void beforeTick(LocalPlayer player) {
        if (pauseMovement()) {
            player.setSprinting(false);
        }
    }

    /** End of LocalPlayer.tick. */
    public void afterTick() {
        if (this.pauseArmed && this.stage == 1) {
            ++this.pauseTicks;
        }
        this.pauseThroughTick = false;
    }

    @Override
    public void onClientTick() {
        Minecraft client = Minecraft.getInstance();
        ++this.tickNumber;

        boolean down1 = this.key1.isDown();
        boolean down2 = this.key2.isDown();
        if (this.stage == 0) {
            if (down1 && !this.key1Was) {
                this.pending = 1;
            } else if (down2 && !this.key2Was) {
                this.pending = 2;
            }
        }
        this.key1Was = down1;
        this.key2Was = down2;

        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null || !player.isAlive() || player.isSpectator()) {
            reset();
            return;
        }
        if (client.gui.screen() != null && this.stage == 1) {
            reset();
            return;
        }
        if (this.stage == 0) {
            if (this.pending != 0 && client.gui.screen() == null) {
                int which = this.pending;
                this.pending = 0;
                trigger(which);
            }
            return;
        }
        if (this.stage == 2) {
            if (this.tickNumber >= this.readyAtTick) {
                boolean confirmed = ItemStack.matches(player.getOffhandItem(), this.expectedOffhand)
                        && ItemStack.matches(player.inventoryMenu.getSlot(this.source).getItem(), this.expectedSource)
                        && player.inventoryMenu.getCarried().isEmpty();
                if (!confirmed) {
                    if (!this.fallbackTried) {
                        // first method failed: try the other one once (SWAP click on the slot)
                        this.fallbackTried = true;
                        this.forceClick = true;
                        this.stage = 1;
                        this.pauseTicks = 0;
                        this.pauseArmed = true;
                        return;
                    }
                    message("§cAutoSwap: server did not confirm the swap (slot " + this.source + "), both methods failed");
                }
                reset();
            }
            return;
        }
        if (client.gui.screen() != null || player.isPassenger()) {
            reset();
            return;
        }
        boolean viaHotbar = !this.forceClick && this.source >= 36 && this.source <= 44;
        if (player.isUsingItem() || (!viaHotbar && (client.options.keyAttack.isDown() || client.options.keyUse.isDown()
                || client.options.keySwapOffhand.isDown() || client.options.keyDrop.isDown()))) {
            this.pauseArmed = false;
            this.pauseTicks = 0;
            return;
        }
        // for a bag click stand still for 2 ticks (inventory checks), the hotbar path needs no pause
        this.pauseArmed = !viaHotbar;
        if (!viaHotbar && this.pauseTicks < 2) {
            return;
        }
        int type = this.targetType;
        if (!matches(player.inventoryMenu.getSlot(this.source).getItem(), type)) {
            this.source = findSlot(player, type);
            viaHotbar = !this.forceClick && this.source >= 36 && this.source <= 44;
        }
        if (this.source < 0) {
            message("§cAutoSwap: the item disappeared from the inventory (" + TYPE_NAMES[type] + ")");
            reset();
            return;
        }
        if (!player.inventoryMenu.getCarried().isEmpty()) {
            message("§cAutoSwap: the cursor holds an item, swap cancelled");
            reset();
            return;
        }
        this.expectedOffhand = player.inventoryMenu.getSlot(this.source).getItem().copy();
        this.expectedSource = player.getOffhandItem().copy();
        ClientPacketListener net = client.getConnection();
        if (net == null) {
            reset();
            return;
        }
        if (viaHotbar) {
            int original = player.getInventory().getSelectedSlot();
            net.send(new ServerboundSetCarriedItemPacket(this.source - 36));
            net.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ZERO, Direction.DOWN));
            net.send(new ServerboundSetCarriedItemPacket(original));
        } else {
            client.gameMode.handleContainerInput(player.inventoryMenu.containerId, this.source, 40,
                    ContainerInput.SWAP, (Player) player);
        }
        message("§aAutoSwap: swapping " + this.expectedOffhand.getHoverName().getString() + (viaHotbar ? " (hotbar)" : " (click)"));
        this.readyAtTick = this.tickNumber + Math.max(2, Math.min(30, (latencyMs(client, player) + 49) / 50 + 2));
        this.stage = 2;
        this.pauseArmed = false;
        this.pauseThroughTick = !viaHotbar;
    }

    private void trigger(int which) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || this.stage != 0) {
            return;
        }
        int type = (which == 2 ? this.item2 : this.item1).options().indexOf((which == 2 ? this.item2 : this.item1).value());
        int slot = findSlot(player, type);
        if (slot < 0) {
            message("§cAutoSwap: no suitable item in the inventory (" + TYPE_NAMES[type] + ")");
            return;
        }
        if (!player.inventoryMenu.getCarried().isEmpty()) {
            message("§cAutoSwap: free the cursor first");
            return;
        }
        this.source = slot;
        this.targetType = type;
        this.pauseTicks = 0;
        this.pauseArmed = true;
        this.stage = 1;
    }

    private int findSlot(LocalPlayer player, int type) {
        ItemStack off = player.getOffhandItem();
        boolean offIsType = matches(off, type);
        for (int slot = 9; slot <= 44; ++slot) {
            ItemStack stack = player.inventoryMenu.getSlot(slot).getItem();
            if (!matches(stack, type)) {
                continue;
            }
            // the offhand already holds the same item: look for another one
            if (offIsType && ItemStack.isSameItemSameComponents(stack, off)) {
                continue;
            }
            return slot;
        }
        return -1;
    }

    /** Categories by keywords in the item name: ANY item with such a name matches, not only heads/totems. */
    private static boolean matches(ItemStack stack, int type) {
        if (stack.isEmpty()) {
            return false;
        }
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        return switch (type) {
            case TYPE_TOTEM -> stack.is(Items.TOTEM_OF_UNDYING) || name.contains("талисман")
                    || name.contains("тотем") || name.contains("totem");
            case TYPE_SPHERE -> name.contains("сфера") || name.contains("шар") || name.contains("sphere") || name.contains("orb");
            default -> false;
        };
    }

    private static int latencyMs(Minecraft client, LocalPlayer player) {
        ClientPacketListener connection = client.getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    private void reset() {
        this.stage = 0;
        this.source = -1;
        this.pauseTicks = 0;
        this.pauseArmed = false;
        this.pauseThroughTick = false;
        this.expectedOffhand = ItemStack.EMPTY;
        this.expectedSource = ItemStack.EMPTY;
        this.pending = 0;
        this.forceClick = false;
        this.fallbackTried = false;
    }

    private static void message(String text) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(text));
        }
    }
}
