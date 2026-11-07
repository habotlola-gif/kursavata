package dev.hatek.client.module.impl.combat;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.KeySetting;
import dev.hatek.client.module.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/**
 * One key swaps the offhand back and forth between two kinds of items: "Талисман/Тотем" and "Шар/Сфера"
 * (found by the words in the item name). The offhand holds a talisman: the key brings a sphere. It holds a sphere:
 * the key brings a talisman. It holds something else: the key brings the first kind (Item A).
 */
public final class AutoSwap extends Module {
    public static final String TALISMAN = "Талисман/Тотем";
    public static final String SPHERE = "Шар/Сфера";

    private static AutoSwap instance;

    private final KeySetting key = new KeySetting("Клавиша", -1);
    private final ModeSetting itemA = new ModeSetting("Предмет A", 0, TALISMAN, SPHERE);
    private final ModeSetting itemB = new ModeSetting("Предмет B", 1, TALISMAN, SPHERE);

    private boolean keyWas;

    public AutoSwap() {
        super("AutoSwap", "One key: swap the offhand between a talisman and a sphere", Category.COMBAT);
        instance = this;
        with(this.key, this.itemA, this.itemB);
    }

    public static AutoSwap instance() {
        return instance;
    }

    @Override
    public void onClientTick() {
        boolean down = this.key.isDown();
        if (down && !this.keyWas) {
            swap();
        }
        this.keyWas = down;
    }

    @Override
    protected void onDisable() {
        this.keyWas = false;
    }

    private void swap() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || OffhandSwapper.busy()) {
            return;
        }
        String a = this.itemA.value();
        String b = this.itemB.value();
        ItemStack offhand = mc.player.getOffhandItem();
        String want = matches(offhand, a) ? b : matches(offhand, b) ? a : a;
        OffhandSwapper.request(stack -> matches(stack, want), want, false);
    }

    /** Kinds by the words in the item name: ANY item with such a name matches. */
    public static boolean matches(ItemStack stack, String kind) {
        if (stack.isEmpty()) {
            return false;
        }
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        if (TALISMAN.equals(kind)) {
            return stack.is(Items.TOTEM_OF_UNDYING) || name.contains("талисман") || name.contains("тотем");
        }
        return name.contains("сфера") || name.contains("шар");
    }
}
