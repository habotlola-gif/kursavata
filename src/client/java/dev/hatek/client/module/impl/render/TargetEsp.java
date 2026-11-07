package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.ColorSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import dev.hatek.client.render.LightningEsp;
import dev.hatek.client.render.TargetTracker;
import dev.hatek.client.render.WorldProjection;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Effect around the current target. Mode Lightning: flashing zigzag arcs. */
public final class TargetEsp extends Module {
    private static TargetEsp instance;
    private static final long START = System.nanoTime();

    private final ModeSetting mode = new ModeSetting("Mode", 0, "Lightning");
    private final ColorSetting color = new ColorSetting("Color", 0xFFFF4D8D);
    private final SliderSetting size = new SliderSetting("Size (%)", 100.0, 50.0, 200.0, 5.0);
    private final SliderSetting thickness = new SliderSetting("Thickness (%)", 100.0, 50.0, 200.0, 5.0);
    private final SliderSetting glow = new SliderSetting("Glow (%)", 100.0, 0.0, 100.0, 5.0);
    private final SliderSetting speed = new SliderSetting("Speed", 1.0, 0.3, 3.0, 0.1);

    private float alpha;
    private LivingEntity shown;
    private long lastFrame = System.nanoTime();

    public TargetEsp() {
        super("TargetESP", "Effect around the target you are fighting", Category.RENDER);
        instance = this;
        with(this.mode, this.color, this.size, this.thickness, this.glow, this.speed);
    }

    public static TargetEsp instance() {
        return instance;
    }

    /** HUD element: called every frame after the world is drawn. */
    public void render(GuiGraphicsExtractor gg, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || mc.player == null || !WorldProjection.isValid()) {
            this.alpha = 0.0f;
            this.shown = null;
            return;
        }
        long now = System.nanoTime();
        float seconds = Math.min((now - this.lastFrame) / 1.0e9f, 0.1f);
        this.lastFrame = now;
        LivingEntity target = TargetTracker.current();
        if (target != null) {
            this.shown = target;
        }
        this.alpha += ((target != null ? 1.0f : 0.0f) - this.alpha) * (1.0f - (float) Math.exp(-seconds * 10.0f));
        if (this.alpha < 0.01f || this.shown == null || !this.shown.isAlive()) {
            if (this.alpha < 0.01f) {
                this.shown = null;
            }
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(true);
        Vec3 base = this.shown.getPosition(partial);
        double time = (now - START) / 1.0e9 * this.speed.value();

        Render2D.begin(gg);
        try {
            Render2D.pushAlpha(this.alpha);
            LightningEsp.render(gg, this.shown, base, time, this.color.value(), partial,
                    (float) this.size.value() / 100.0f, (float) this.thickness.value() / 100.0f,
                    (float) this.glow.value() / 100.0f);
            Render2D.popAlpha();
        } finally {
            Render2D.end(gg);
        }
    }
}
