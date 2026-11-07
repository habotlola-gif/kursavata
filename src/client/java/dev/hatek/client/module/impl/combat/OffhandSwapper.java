package dev.hatek.client.module.impl.combat;

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

import java.util.function.Predicate;

/**
 * Moves an item into the offhand. Shared by AutoSwap and AutoTotem and running regardless of which of them is on.
 * A hotbar item goes through hotbar packets (like pressing F), a bag item through one SWAP click while the player
 * stands still for two ticks. If the server does not confirm the result the other method is tried once.
 */
public final class OffhandSwapper {
    private static final OffhandSwapper I = new OffhandSwapper();

    private Predicate<ItemStack> wanted;
    private String label = "";
    private boolean quiet;
    private boolean forceClick;
    private boolean fallbackTried;
    private int stage;             // 0 idle, 1 preparing, 2 waiting for the server to confirm
    private int source = -1;
    private int pauseTicks;
    private int tickNumber;
    private int readyAtTick;
    private boolean pauseArmed;
    private boolean pauseThroughTick;
    private ItemStack expectedOffhand = ItemStack.EMPTY;
    private ItemStack expectedSource = ItemStack.EMPTY;

    private OffhandSwapper() {
    }

    public static boolean busy() {
        return I.stage != 0;
    }

    /** True while the player must stand still (inventory click path). */
    public static boolean pauseMovement() {
        return I.pauseArmed || I.pauseThroughTick;
    }

    /** Start of LocalPlayer.tick. */
    public static void beforePlayerTick(LocalPlayer player) {
        if (pauseMovement()) {
            player.setSprinting(false);
        }
    }

    /** End of LocalPlayer.tick. */
    public static void afterPlayerTick() {
        if (I.pauseArmed && I.stage == 1) {
            ++I.pauseTicks;
        }
        I.pauseThroughTick = false;
    }

    public static void cancel() {
        I.reset();
    }

    /**
     * Asks for the first inventory item accepted by {@code wanted} to be put into the offhand. Returns false if a swap
     * is already running, a screen is open or nothing suitable was found.
     */
    public static boolean request(Predicate<ItemStack> wanted, String label, boolean quiet) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || I.stage != 0 || client.gui.screen() != null) {
            return false;
        }
        int slot = I.findSlot(player, wanted);
        if (slot < 0) {
            if (!quiet) {
                message("§cНет подходящего предмета в инвентаре (" + label + ")");
            }
            return false;
        }
        if (!player.inventoryMenu.getCarried().isEmpty()) {
            if (!quiet) {
                message("§cОсвободи курсор");
            }
            return false;
        }
        I.wanted = wanted;
        I.label = label;
        I.quiet = quiet;
        I.source = slot;
        I.pauseTicks = 0;
        I.pauseArmed = true;
        I.stage = 1;
        return true;
    }

    /** Once per client tick. */
    public static void tick() {
        I.run();
    }

    private void run() {
        Minecraft client = Minecraft.getInstance();
        ++this.tickNumber;
        if (this.stage == 0) {
            return;
        }
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null || !player.isAlive() || player.isSpectator()
                || client.gui.screen() != null || player.isPassenger()) {
            reset();
            return;
        }
        if (this.stage == 2) {
            if (this.tickNumber >= this.readyAtTick) {
                boolean confirmed = ItemStack.matches(player.getOffhandItem(), this.expectedOffhand)
                        && ItemStack.matches(player.inventoryMenu.getSlot(this.source).getItem(), this.expectedSource)
                        && player.inventoryMenu.getCarried().isEmpty();
                if (!confirmed) {
                    if (!this.fallbackTried) {
                        this.fallbackTried = true;
                        this.forceClick = true;
                        this.stage = 1;
                        this.pauseTicks = 0;
                        this.pauseArmed = true;
                        return;
                    }
                    if (!this.quiet) {
                        message("§cСервер не подтвердил обмен (слот " + this.source + "), оба способа не сработали");
                    }
                }
                reset();
            }
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
        if (!this.wanted.test(player.inventoryMenu.getSlot(this.source).getItem())) {
            this.source = findSlot(player, this.wanted);
            viaHotbar = !this.forceClick && this.source >= 36 && this.source <= 44;
        }
        if (this.source < 0) {
            if (!this.quiet) {
                message("§cПредмет пропал из инвентаря (" + this.label + ")");
            }
            reset();
            return;
        }
        if (!player.inventoryMenu.getCarried().isEmpty()) {
            if (!this.quiet) {
                message("§cКурсор занят предметом, обмен отменён");
            }
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
        if (!this.quiet) {
            message("§aМеняю: " + this.expectedOffhand.getHoverName().getString() + (viaHotbar ? " (хотбар)" : " (клик)"));
        }
        this.readyAtTick = this.tickNumber + Math.max(2, Math.min(30, (latencyMs(client, player) + 49) / 50 + 2));
        this.stage = 2;
        this.pauseArmed = false;
        this.pauseThroughTick = !viaHotbar;
    }

    private int findSlot(LocalPlayer player, Predicate<ItemStack> matcher) {
        ItemStack off = player.getOffhandItem();
        boolean offMatches = matcher.test(off);
        for (int slot = 9; slot <= 44; ++slot) {
            ItemStack stack = player.inventoryMenu.getSlot(slot).getItem();
            if (!matcher.test(stack)) {
                continue;
            }
            // the offhand already holds the same item: look for another one
            if (offMatches && ItemStack.isSameItemSameComponents(stack, off)) {
                continue;
            }
            return slot;
        }
        return -1;
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
        this.forceClick = false;
        this.fallbackTried = false;
    }

    static void message(String text) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(text));
        }
    }
}
