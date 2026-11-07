package dev.hatek.client.module.impl.combat;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps a totem of undying (or a "Талисман/Тотем" item) in the offhand. Mode "Always": whenever the offhand has no
 * totem. Mode "Low health": only when your health (with absorption) drops to the chosen number of hearts or lower.
 */
public final class AutoTotem extends Module {
    private static AutoTotem instance;

    private final ModeSetting mode = new ModeSetting("Режим", 0, "Всегда", "При низком здоровье");
    private final SliderSetting hearts = new SliderSetting("Здоровье (сердец)", 5.0, 1.0, 10.0, 0.5);
    private final SliderSetting delay = new SliderSetting("Пауза между попытками (тиков)", 4.0, 0.0, 20.0, 1.0);

    private int cooldown;

    public AutoTotem() {
        super("AutoTotem", "Puts a totem into the offhand when it is missing", Category.COMBAT);
        instance = this;
        with(this.mode, this.hearts, this.delay);
    }

    public static AutoTotem instance() {
        return instance;
    }

    @Override
    public void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        if (player == null || !player.isAlive() || player.isSpectator() || this.cooldown > 0 || OffhandSwapper.busy()) {
            return;
        }
        ItemStack offhand = player.getOffhandItem();
        if (AutoSwap.matches(offhand, AutoSwap.TALISMAN)) {
            return;
        }
        if (this.mode.is("При низком здоровье")) {
            float health = player.getHealth() + player.getAbsorptionAmount();
            if (health > this.hearts.value() * 2.0) {
                return;
            }
        }
        this.cooldown = (int) Math.round(this.delay.value());
        OffhandSwapper.request(stack -> AutoSwap.matches(stack, AutoSwap.TALISMAN), AutoSwap.TALISMAN, true);
    }
}
