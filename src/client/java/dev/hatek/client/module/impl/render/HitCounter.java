package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ColorSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.entity.LivingEntity;

/**
 * Hit counter: "Hits: N" in the plain Minecraft font in the bottom-left corner. Every attack on a living entity
 * (the aura's and your own) counts; the number can reset on its own after a pause in the fight.
 */
public final class HitCounter extends Module {
    private static HitCounter instance;

    private final ColorSetting color = new ColorSetting("Color", 0xFFFFFFFF);
    private final BoolSetting shadow = new BoolSetting("Shadow", true);
    private final SliderSetting resetAfter = new SliderSetting("Reset after a pause (s, 0 = never)", 0.0, 0.0, 30.0, 1.0);

    private int hits;
    private long lastHitAt;

    public HitCounter() {
        super("HitCounter", "Counts your hits, bottom-left, in the normal Minecraft font", Category.RENDER);
        instance = this;
        with(this.color, this.shadow, this.resetAfter);
    }

    public static HitCounter instance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        this.hits = 0;
    }

    /** Called for every attack the client sends (MultiPlayerGameModeMixin). */
    public static void onAttack(net.minecraft.world.entity.Entity target) {
        HitCounter self = instance;
        if (self == null || !self.isEnabled() || !(target instanceof LivingEntity)) {
            return;
        }
        long now = System.currentTimeMillis();
        double reset = self.resetAfter.value();
        if (reset > 0.0 && now - self.lastHitAt > reset * 1000.0) {
            self.hits = 0;
        }
        self.hits++;
        self.lastHitAt = now;
    }

    /** HUD element: called every frame. */
    public void render(GuiGraphicsExtractor gg, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || mc.player == null) {
            return;
        }
        double reset = this.resetAfter.value();
        if (reset > 0.0 && this.hits > 0 && System.currentTimeMillis() - this.lastHitAt > reset * 1000.0) {
            this.hits = 0;
        }
        // above the chat input line while the chat is open
        int y = gg.guiHeight() - (mc.gui.screen() instanceof ChatScreen ? 26 : 12);
        gg.text(mc.font, "Hits: " + this.hits, 4, y, this.color.value(), this.shadow.value());
    }
}
