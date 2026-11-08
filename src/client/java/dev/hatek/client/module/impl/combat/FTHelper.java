package dev.hatek.client.module.impl.combat;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.KeySetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * FTHelper: one key per FunTime item. A key press uses that item at once and puts the previous slot back; the items
 * are taken from the hands, the hotbar or the inventory by ClickAction's engine.
 */
public final class FTHelper extends Module {
    private static final Item[] ITEMS = {Items.SNOWBALL, Items.PHANTOM_MEMBRANE, Items.NETHERITE_SCRAP, Items.DRIED_KELP,
            Items.SUGAR, Items.FIRE_CHARGE, Items.ENDER_EYE};
    private static final String[] NAMES = {"Снежок", "God's Aura", "Trap", "Plast", "Clear Dust", "Fire Tornado",
            "Disorientation"};

    private final KeySetting[] keys = new KeySetting[ITEMS.length];
    private final boolean[] was = new boolean[ITEMS.length];
    private int pendingUse = -1;

    public FTHelper() {
        super("FTHelper", "One key per FunTime item: snowball, God's Aura, Trap, Plast, Clear Dust, Fire Tornado, Disorientation", Category.COMBAT);
        for (int i = 0; i < this.keys.length; i++) {
            this.keys[i] = new KeySetting(NAMES[i] + " (клавиша)", -1);
        }
        with(this.keys);
    }

    @Override
    protected void onDisable() {
        this.pendingUse = -1;
        java.util.Arrays.fill(this.was, false);
    }

    @Override
    public void onClientTick() {
        for (int i = 0; i < this.keys.length; i++) {
            boolean down = this.keys[i].isDown();
            if (down && !this.was[i]) {
                this.pendingUse = i;
            }
            this.was[i] = down;
        }
        Minecraft mc = Minecraft.getInstance();
        if (this.pendingUse >= 0 && mc.player != null && mc.gui.screen() == null
                && ClickAction.request(ITEMS[this.pendingUse], NAMES[this.pendingUse])) {
            this.pendingUse = -1;
        }
    }
}
