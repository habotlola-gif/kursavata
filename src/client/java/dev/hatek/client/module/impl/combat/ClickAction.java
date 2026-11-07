package dev.hatek.client.module.impl.combat;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.KeySetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * ClickAction: one key throws an ender pearl, another a wind charge. The item is taken from the offhand or the hotbar,
 * used at once and the previous slot comes back. Aim yourself (a wind charge at your feet pushes you up).
 */
public final class ClickAction extends Module {
    private final KeySetting pearlKey = new KeySetting("Жемчуг эндера (клавиша)", -1);
    private final KeySetting windKey = new KeySetting("Заряд ветра (клавиша)", -1);
    private final BoolSetting swapBack = new BoolSetting("Вернуть прошлый слот", true);
    private final BoolSetting fromInventory = new BoolSetting("Брать и из инвентаря (не только хотбар)", true);
    private final BoolSetting notify = new BoolSetting("Сообщать, если предмета нет", true);

    private boolean pearlWas;
    private boolean windWas;

    public ClickAction() {
        super("ClickAction", "Keys that throw an ender pearl or a wind charge at once", Category.COMBAT);
        with(this.pearlKey, this.windKey, this.swapBack, this.fromInventory, this.notify);
    }

    @Override
    protected void onDisable() {
        this.pearlWas = false;
        this.windWas = false;
    }

    @Override
    public void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        boolean pearl = this.pearlKey.isDown();
        boolean wind = this.windKey.isDown();
        boolean pearlPressed = pearl && !this.pearlWas;
        boolean windPressed = wind && !this.windWas;
        this.pearlWas = pearl;
        this.windWas = wind;
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || mc.gui.screen() != null) {
            return;
        }
        if (pearlPressed) {
            throwItem(player, mc, Items.ENDER_PEARL, "жемчуга эндера");
        }
        if (windPressed) {
            throwItem(player, mc, Items.WIND_CHARGE, "заряда ветра");
        }
    }

    private void throwItem(LocalPlayer player, Minecraft mc, Item item, String label) {
        if (player.getOffhandItem().is(item)) {
            mc.gameMode.useItem(player, InteractionHand.OFF_HAND);
            player.swing(InteractionHand.OFF_HAND);
            return;
        }
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(item)) {
                slot = i;
                break;
            }
        }
        if (slot < 0 && this.fromInventory.value()) {
            for (int i = 9; i < 36; i++) {
                if (player.getInventory().getItem(i).is(item)) {
                    throwFromInventory(player, mc, i);
                    return;
                }
            }
        }
        if (slot < 0) {
            if (this.notify.value()) {
                player.sendSystemMessage(Component.literal("§bClickAction: §fнет " + label + "."));
            }
            return;
        }
        int previous = player.getInventory().getSelectedSlot();
        player.getInventory().setSelectedSlot(slot);
        mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        player.swing(InteractionHand.MAIN_HAND);
        if (this.swapBack.value() && previous != slot) {
            player.getInventory().setSelectedSlot(previous);
        }
    }

    /** The item sits in the main inventory: swap it into the selected hotbar slot, use it, swap it back. */
    private void throwFromInventory(LocalPlayer player, Minecraft mc, int inventorySlot) {
        int selected = player.getInventory().getSelectedSlot();
        int container = player.inventoryMenu.containerId;
        mc.gameMode.handleContainerInput(container, inventorySlot, selected, ContainerInput.SWAP, (Player) player);
        mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        player.swing(InteractionHand.MAIN_HAND);
        mc.gameMode.handleContainerInput(container, inventorySlot, selected, ContainerInput.SWAP, (Player) player);
    }
}
