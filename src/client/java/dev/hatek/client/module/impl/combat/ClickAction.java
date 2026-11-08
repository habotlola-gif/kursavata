package dev.hatek.client.module.impl.combat;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.KeySetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
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
    private final BoolSetting debug = new BoolSetting("Писать в чат, что происходит (отладка)", true);
    private final BoolSetting notify = new BoolSetting("Сообщать, если предмета нет", true);

    private boolean pearlWas;
    private boolean windWas;

    public ClickAction() {
        super("ClickAction", "Keys that throw an ender pearl or a wind charge at once", Category.COMBAT);
        with(this.pearlKey, this.windKey, this.swapBack, this.fromInventory, this.notify, this.debug);
    }

    @Override
    protected void onDisable() {
        this.pearlWas = false;
        this.windWas = false;
    }

    @Override
    public void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        boolean pearl = this.pearlKey.isDown(true);
        boolean wind = this.windKey.isDown(true);
        boolean pearlPressed = pearl && !this.pearlWas;
        boolean windPressed = wind && !this.windWas;
        this.pearlWas = pearl;
        this.windWas = wind;
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null
                || (mc.gui.screen() != null
                && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>))) {
            return;
        }
        if ((pearlPressed || windPressed) && this.debug.value()) {
            say(player, "нажата клавиша " + (pearlPressed ? "жемчуга" : "заряда ветра") + ", модуль работает");
        }
        if (pearlPressed) {
            throwItem(player, mc, Items.ENDER_PEARL, "жемчуга эндера");
        }
        if (windPressed) {
            throwItem(player, mc, Items.WIND_CHARGE, "заряда ветра");
        }
    }

    private void say(LocalPlayer player, String text) {
        player.sendSystemMessage(Component.literal("§bClickAction: §f" + text));
    }

    private void report(LocalPlayer player, String where, InteractionResult result, Item item) {
        if (!this.debug.value()) {
            return;
        }
        String cooldown = player.getCooldowns().isOnCooldown(new ItemStack(item)) ? ", предмет на кулдауне" : "";
        say(player, where + " -> " + result.getClass().getSimpleName() + cooldown);
    }

    private void throwItem(LocalPlayer player, Minecraft mc, Item item, String label) {
        if (player.getOffhandItem().is(item)) {
            InteractionResult result = mc.gameMode.useItem(player, InteractionHand.OFF_HAND);
            player.swing(InteractionHand.OFF_HAND);
            report(player, "левая рука", result, item);
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
                    throwFromInventory(player, mc, i, item);
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
        InteractionResult result = mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        player.swing(InteractionHand.MAIN_HAND);
        report(player, "хотбар, слот " + (slot + 1), result, item);
        if (this.swapBack.value() && previous != slot) {
            player.getInventory().setSelectedSlot(previous);
        }
    }

    /** The item sits in the main inventory: swap it into the selected hotbar slot, use it, swap it back. */
    private void throwFromInventory(LocalPlayer player, Minecraft mc, int inventorySlot, Item item) {
        int selected = player.getInventory().getSelectedSlot();
        int container = player.inventoryMenu.containerId;
        mc.gameMode.handleContainerInput(container, inventorySlot, selected, ContainerInput.SWAP, (Player) player);
        InteractionResult result = mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        player.swing(InteractionHand.MAIN_HAND);
        report(player, "инвентарь, слот " + inventorySlot, result, item);
        mc.gameMode.handleContainerInput(container, inventorySlot, selected, ContainerInput.SWAP, (Player) player);
    }
}
