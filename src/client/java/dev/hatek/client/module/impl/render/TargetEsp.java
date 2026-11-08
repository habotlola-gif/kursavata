package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ColorSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import dev.hatek.client.render.ClassicEsp;
import dev.hatek.client.render.CrystalsEsp;
import dev.hatek.client.render.LightningEsp;
import dev.hatek.client.render.SoulsEsp;
import dev.hatek.client.render.TargetTracker;
import dev.hatek.client.render.WorldProjection;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Effect around the current target. Lightning: flashing zigzag arcs. Crystals: faceted gems orbiting in tiers. Souls: glowing comets circling the target. */
public final class TargetEsp extends Module {
    private static TargetEsp instance;
    private static final long START = System.nanoTime();

    private final ModeSetting mode = new ModeSetting("Mode", 0, "Lightning", "Crystals", "Souls", ClassicEsp.MARKER, ClassicEsp.RING,
            ClassicEsp.GHOSTS, ClassicEsp.CONSTELLATION, ClassicEsp.BLOOM, ClassicEsp.SWORDS, ClassicEsp.TEST);
    private final ColorSetting color = new ColorSetting("Color", 0xFFFF4D8D);
    private final SliderSetting size = new SliderSetting("Size (%)", 100.0, 50.0, 200.0, 5.0);
    private final SliderSetting thickness = new SliderSetting("Thickness (%)", 100.0, 50.0, 200.0, 5.0);
    private final SliderSetting glow = new SliderSetting("Glow (%)", 100.0, 0.0, 100.0, 5.0);
    private final SliderSetting souls = new SliderSetting("Souls", 4.0, 1.0, 6.0, 1.0);
    private final SliderSetting trail = new SliderSetting("Trail length (%)", 100.0, 30.0, 200.0, 5.0);
    private final SliderSetting speed = new SliderSetting("Speed", 1.0, 0.3, 3.0, 0.1);

    // settings of the ported modes (Marker, Ring, Ghosts, Constellation, Bloom, Swords, Test)
    private final ColorSetting color2 = new ColorSetting("Color 2", 0xFF9D93E6);
    private final BoolSetting hitFlash = new BoolSetting("Подсветка ударов", true);
    private final ModeSetting markerSkin = new ModeSetting("Тип маркера", 0, "Marker", "Marker1", "Marker2", "Marker3");
    private final SliderSetting markerSize = new SliderSetting("Размер маркера", 0.55, 0.2, 2.0, 0.05);
    private final SliderSetting bloomCount = new SliderSetting("Bloom: количество", 36.0, 12.0, 64.0, 2.0);
    private final SliderSetting bloomSize = new SliderSetting("Bloom: размер", 0.2, 0.08, 0.5, 0.02);
    private final SliderSetting bloomSpeed = new SliderSetting("Bloom: скорость", 1.0, 0.2, 3.0, 0.1);
    private final SliderSetting swordSpeed = new SliderSetting("Мечи: период оборота (мс)", 1200.0, 200.0, 4000.0, 50.0);
    private final SliderSetting swordRadius = new SliderSetting("Мечи: радиус", 1.0, 0.3, 2.5, 0.05);
    private final SliderSetting swordSize = new SliderSetting("Мечи: размер", 1.0, 0.3, 2.5, 0.05);
    private final SliderSetting swordBob = new SliderSetting("Мечи: качание", 0.12, 0.0, 0.5, 0.01);
    private final SliderSetting swordCount = new SliderSetting("Мечи: количество", 8.0, 2.0, 12.0, 1.0);
    private final BoolSetting swordGlow = new BoolSetting("Мечи: свечение", true);
    private final BoolSetting swordPhysics = new BoolSetting("Мечи: физика", true);
    private final SliderSetting testSpeed = new SliderSetting("Test: скорость", 1.0, 0.2, 3.0, 0.05);
    private final SliderSetting testSize = new SliderSetting("Test: размер", 1.0, 0.3, 2.5, 0.05);
    private final SliderSetting testRadius = new SliderSetting("Test: радиус", 0.725, 0.3, 1.5, 0.025);
    private final SliderSetting conStars = new SliderSetting("Созвездие: звёзды", 8.0, 3.0, 16.0, 1.0);
    private final SliderSetting conSpeed = new SliderSetting("Созвездие: скорость", 1.0, 0.2, 3.0, 0.1);
    private final SliderSetting conOrbit = new SliderSetting("Созвездие: орбита", 1.2, 0.5, 2.5, 0.1);
    private final BoolSetting conLines = new BoolSetting("Созвездие: линии", true);
    private final SliderSetting conPointSize = new SliderSetting("Созвездие: размер точек", 0.27, 0.1, 0.6, 0.01);
    private final SliderSetting conGlow = new SliderSetting("Созвездие: свечение", 0.5, 0.0, 1.0, 0.05);

    private LivingEntity lastClassicTarget;
    private float alpha;
    private LivingEntity shown;
    private long lastFrame = System.nanoTime();

    public TargetEsp() {
        super("TargetESP", "Effect around the target you are fighting", Category.RENDER);
        instance = this;
        with(this.mode, this.color, this.size, this.thickness, this.glow, this.speed, this.souls, this.trail,
                this.color2, this.hitFlash, this.markerSkin, this.markerSize, this.bloomCount, this.bloomSize, this.bloomSpeed,
                this.swordSpeed, this.swordRadius, this.swordSize, this.swordBob, this.swordCount, this.swordGlow, this.swordPhysics,
                this.testSpeed, this.testSize, this.testRadius, this.conStars, this.conSpeed, this.conOrbit, this.conLines,
                this.conPointSize, this.conGlow);
        this.souls.visibleIf(() -> this.mode.is("Souls"));
        this.trail.visibleIf(() -> this.mode.is("Souls"));
        for (dev.hatek.client.module.setting.Setting setting : java.util.List.of(this.color2, this.hitFlash)) {
            setting.visibleIf(() -> ClassicEsp.isClassic(this.mode.value()));
        }
        this.markerSkin.visibleIf(() -> this.mode.is(ClassicEsp.MARKER));
        this.markerSize.visibleIf(() -> this.mode.is(ClassicEsp.MARKER));
        for (dev.hatek.client.module.setting.Setting setting : java.util.List.of(this.bloomCount, this.bloomSize, this.bloomSpeed)) {
            setting.visibleIf(() -> this.mode.is(ClassicEsp.BLOOM));
        }
        for (dev.hatek.client.module.setting.Setting setting : java.util.List.of(this.swordSpeed, this.swordRadius, this.swordSize,
                this.swordBob, this.swordCount, this.swordGlow, this.swordPhysics)) {
            setting.visibleIf(() -> this.mode.is(ClassicEsp.SWORDS));
        }
        for (dev.hatek.client.module.setting.Setting setting : java.util.List.of(this.testSpeed, this.testSize, this.testRadius)) {
            setting.visibleIf(() -> this.mode.is(ClassicEsp.TEST));
        }
        for (dev.hatek.client.module.setting.Setting setting : java.util.List.of(this.conStars, this.conSpeed, this.conOrbit,
                this.conLines, this.conPointSize, this.conGlow)) {
            setting.visibleIf(() -> this.mode.is(ClassicEsp.CONSTELLATION));
        }
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
                ClassicEsp.reset();
            }
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(true);
        Vec3 base = this.shown.getPosition(partial);
        double time = (now - START) / 1.0e9 * this.speed.value();

        Render2D.begin(gg);
        try {
            Render2D.pushAlpha(this.alpha);
            if (this.mode.is("Souls")) {
                boolean visible = mc.player.hasLineOfSight(this.shown);
                SoulsEsp.render(gg, this.shown, base, time, this.color.value(), (int) Math.round(this.souls.value()),
                        (float) this.size.value() / 100.0f, (float) this.thickness.value() / 100.0f,
                        (float) this.trail.value() / 100.0f, (float) this.glow.value() / 100.0f, visible ? 1.0f : 0.45f);
            } else if (ClassicEsp.isClassic(this.mode.value())) {
                if (this.lastClassicTarget != this.shown) {
                    this.lastClassicTarget = this.shown;
                }
                fillClassic();
                ClassicEsp.render(gg, this.mode.value(), this.shown, partial, this.alpha, seconds,
                        this.color.value(), this.color2.value(), this.hitFlash.value());
            } else if (this.mode.is("Crystals")) {
                boolean visible = mc.player.hasLineOfSight(this.shown);
                CrystalsEsp.render(gg, this.shown, base, (float) this.speed.value(),
                        (float) this.size.value() / 100.0f, (float) this.glow.value() / 100.0f,
                        this.color.value(), this.alpha, partial, visible);
            } else {
                LightningEsp.render(gg, this.shown, base, time, this.color.value(), partial,
                        (float) this.size.value() / 100.0f, (float) this.thickness.value() / 100.0f,
                        (float) this.glow.value() / 100.0f);
            }
            Render2D.popAlpha();
        } finally {
            Render2D.end(gg);
        }
    }

    private void fillClassic() {
        ClassicEsp.Params p = ClassicEsp.P;
        p.markerSkin = Math.max(0, this.markerSkin.options().indexOf(this.markerSkin.value()));
        p.markerSize = (float) this.markerSize.value();
        p.bloomCount = (int) Math.round(this.bloomCount.value());
        p.bloomSize = (float) this.bloomSize.value();
        p.bloomSpeed = (float) this.bloomSpeed.value();
        p.swordSpeed = (float) this.swordSpeed.value();
        p.swordRadius = (float) this.swordRadius.value();
        p.swordSize = (float) this.swordSize.value();
        p.swordBob = (float) this.swordBob.value();
        p.swordCount = (int) Math.round(this.swordCount.value());
        p.swordGlow = this.swordGlow.value();
        p.swordPhysics = this.swordPhysics.value();
        p.testSpeed = (float) this.testSpeed.value();
        p.testSize = (float) this.testSize.value();
        p.testRadius = (float) this.testRadius.value();
        p.conStars = (int) Math.round(this.conStars.value());
        p.conSpeed = (float) this.conSpeed.value();
        p.conOrbit = (float) this.conOrbit.value();
        p.conLines = this.conLines.value();
        p.conPointSize = (float) this.conPointSize.value();
        p.conGlow = (float) this.conGlow.value();
    }
}
