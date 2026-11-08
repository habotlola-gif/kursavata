package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ColorSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import dev.hatek.client.render.TargetTracker;
import dev.hatek.client.render.WorldProjection;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Particles: star sparkles around you, around the target and a burst on every hit. The stars live in world
 * coordinates, are projected to the screen and drawn from the star textures (sharp or soft, filled or outline) with
 * a soft glow, far ones first. Port of the old project's Particles.
 */
public final class Particles extends Module {
    private static final int MAX_PARTICLES = 260;
    private static final int TEX = 256;
    private static final Random RANDOM = new Random();

    private static Particles instance;

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("hatek_client", "textures/gui/" + name + ".png");
    }

    private static final Identifier[] SOFT = {tex("star_filled"), tex("star_outline_thin"), tex("star_outline"), tex("star_outline_thick")};
    private static final Identifier[] SHARP = {tex("star_sharp_filled"), tex("star_sharp_outline_thin"), tex("star_sharp_outline"), tex("star_sharp_outline_thick")};

    private final ModeSetting shape = new ModeSetting("Форма", 1, "Острая", "Мягкая");
    private final BoolSetting aroundMe = new BoolSetting("Вокруг меня", true);
    private final BoolSetting aroundTarget = new BoolSetting("Вокруг цели", true);
    private final BoolSetting onHit = new BoolSetting("При ударе", true);
    private final ColorSetting color = new ColorSetting("Цвет", 0xFFFF8AD8);
    private final BoolSetting rainbow = new BoolSetting("Радужные", false);
    private final BoolSetting fill = new BoolSetting("Заливка", false);
    private final SliderSetting size = new SliderSetting("Размер (%)", 100.0, 30.0, 300.0, 5.0);
    private final SliderSetting amount = new SliderSetting("Количество (в секунду)", 10.0, 1.0, 40.0, 1.0);
    private final SliderSetting lifetime = new SliderSetting("Время жизни (с)", 2.0, 0.5, 5.0, 0.1);
    private final SliderSetting speed = new SliderSetting("Скорость (%)", 100.0, 0.0, 300.0, 10.0);
    private final SliderSetting rise = new SliderSetting("Подъём / падение (%)", 30.0, -100.0, 100.0, 5.0);
    private final SliderSetting spin = new SliderSetting("Вращение (%)", 100.0, 0.0, 400.0, 10.0);
    private final SliderSetting thickness = new SliderSetting("Толщина контура (%)", 100.0, 40.0, 250.0, 5.0);
    private final SliderSetting glow = new SliderSetting("Свечение (%)", 100.0, 0.0, 200.0, 5.0);

    private static final class Star {
        double x, y, z, vx, vy, vz;
        float age, life, size, rot, spin, hue;
    }

    private final List<Star> stars = new ArrayList<>();
    private long lastNanos;
    private double budgetMe;
    private double budgetTarget;

    public Particles() {
        super("Particles", "Star sparkles around you, the target and on every hit", Category.RENDER);
        instance = this;
        this.thickness.visibleIf(() -> !this.fill.value());
        with(this.shape, this.aroundMe, this.aroundTarget, this.onHit, this.color, this.rainbow, this.fill, this.size,
                this.amount, this.lifetime, this.speed, this.rise, this.spin, this.thickness, this.glow);
    }

    public static Particles instance() {
        return instance;
    }

    @Override
    protected void onDisable() {
        this.stars.clear();
    }

    /** A burst at the target (called from TargetTracker.onAttack). */
    public static void onAttack(LivingEntity target) {
        Particles self = instance;
        if (self == null || !self.isEnabled() || !self.onHit.value() || target == null) {
            return;
        }
        Vec3 c = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
        for (int i = 0; i < 14; i++) {
            self.spawn(c.x, c.y, c.z, true);
        }
    }

    private void spawn(double x, double y, double z, boolean burst) {
        if (this.stars.size() >= MAX_PARTICLES) {
            return;
        }
        Star a = new Star();
        a.x = x;
        a.y = y;
        a.z = z;
        float spd = (float) this.speed.value() / 100.0f;
        double ang = RANDOM.nextDouble() * Math.PI * 2;
        double horizontal = (burst ? 1.6 : 0.25) * spd * (0.5 + RANDOM.nextDouble());
        a.vx = Math.cos(ang) * horizontal;
        a.vz = Math.sin(ang) * horizontal;
        a.vy = (burst ? 0.6 + RANDOM.nextDouble() * 0.8 : (RANDOM.nextDouble() - 0.5) * 0.2) * spd;
        a.life = (float) (this.lifetime.value() * (burst ? 0.6 : 1.0) * (0.7 + RANDOM.nextDouble() * 0.6));
        a.size = (float) (0.07 * this.size.value() / 100.0 * (0.7 + RANDOM.nextDouble() * 0.6));
        a.rot = (float) (RANDOM.nextDouble() * Math.PI * 2);
        a.spin = (float) ((RANDOM.nextDouble() - 0.5) * 2.4 * this.spin.value() / 100.0);
        a.hue = RANDOM.nextFloat();
        this.stars.add(a);
    }

    private static double dist2(Star a, Vec3 c) {
        double dx = a.x - c.x;
        double dy = a.y - c.y;
        double dz = a.z - c.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** HUD element: called every frame after the world is drawn. */
    public void render(GuiGraphicsExtractor gg, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || mc.player == null || mc.level == null || !WorldProjection.isValid()) {
            this.stars.clear();
            this.budgetMe = 0;
            this.budgetTarget = 0;
            this.lastNanos = 0;
            return;
        }
        long now = System.nanoTime();
        float dt = this.lastNanos == 0 ? 0.016f : Math.min((now - this.lastNanos) / 1.0e9f, 0.05f);
        this.lastNanos = now;
        float partial = delta.getGameTimeDeltaPartialTick(true);

        // ---- spawning
        double rate = this.amount.value();
        if (this.aroundMe.value()) {
            this.budgetMe += rate * 0.6 * dt;
            Vec3 p = mc.player.getPosition(partial);
            while (this.budgetMe >= 1.0) {
                this.budgetMe -= 1.0;
                double ang = RANDOM.nextDouble() * Math.PI * 2;
                double rad = 0.8 + RANDOM.nextDouble() * 0.9;
                spawn(p.x + Math.cos(ang) * rad, p.y + RANDOM.nextDouble() * 2.0, p.z + Math.sin(ang) * rad, false);
            }
        } else {
            this.budgetMe = 0;
        }
        LivingEntity target = TargetTracker.current();
        if (this.aroundTarget.value() && target != null) {
            this.budgetTarget += rate * dt;
            Vec3 p = target.getPosition(partial);
            while (this.budgetTarget >= 1.0) {
                this.budgetTarget -= 1.0;
                double ang = RANDOM.nextDouble() * Math.PI * 2;
                double rad = target.getBbWidth() * 0.7 + 0.3 + RANDOM.nextDouble() * 0.5;
                spawn(p.x + Math.cos(ang) * rad, p.y + RANDOM.nextDouble() * target.getBbHeight(), p.z + Math.sin(ang) * rad, false);
            }
        } else {
            this.budgetTarget = 0;
        }

        // ---- update
        float riseMul = (float) this.rise.value() / 100.0f;
        for (int i = this.stars.size() - 1; i >= 0; i--) {
            Star a = this.stars.get(i);
            a.age += dt;
            if (a.age >= a.life) {
                this.stars.remove(i);
                continue;
            }
            a.x += a.vx * dt;
            a.y += (a.vy + riseMul * 0.5) * dt;
            a.z += a.vz * dt;
            a.vx *= 1.0 - 0.6 * dt;
            a.vz *= 1.0 - 0.6 * dt;
            a.rot += a.spin * dt;
        }
        if (this.stars.isEmpty()) {
            return;
        }

        // ---- draw (far ones first)
        Vec3 cam = WorldProjection.cameraPos();
        double fov = Math.toRadians(mc.options.fov().get());
        double pxPerUnit = Render2D.screenHeight() / (2.0 * Math.tan(fov / 2.0));
        List<Star> order = new ArrayList<>(this.stars);
        order.sort(Comparator.comparingDouble((Star a) -> -dist2(a, cam)));
        float glowAmount = (float) this.glow.value() / 100.0f;

        Render2D.begin(gg);
        try {
            for (Star a : order) {
                float[] scr = WorldProjection.toScreen(new Vec3(a.x, a.y, a.z));
                if (scr == null) {
                    continue;
                }
                double dist = Math.max(0.3, Math.sqrt(dist2(a, cam)));
                float r = (float) (a.size * pxPerUnit / dist);
                if (r < 2.5f) {
                    continue;
                }
                float fadeIn = Mth.clamp(a.age / 0.2f, 0.0f, 1.0f);
                float fadeOut = Mth.clamp((a.life - a.age) / 0.5f, 0.0f, 1.0f);
                float alpha = fadeIn * fadeOut;
                int rgb = this.rainbow.value()
                        ? java.awt.Color.HSBtoRGB((a.hue + a.age * 0.15f) % 1.0f, 0.65f, 0.97f) | 0xFF000000
                        : this.color.value();
                drawStar(gg, scr[0], scr[1], r * (0.75f + 0.25f * fadeOut), a.rot, rgb, alpha, glowAmount);
            }
        } finally {
            Render2D.end(gg);
        }
    }

    private void drawStar(GuiGraphicsExtractor gg, float cx, float cy, float r, float rot, int rgb, float alpha, float glowAmount) {
        float a = Mth.clamp(alpha, 0.0f, 1.0f);
        int size = Math.max(6, Math.round(r * 2.0f));
        Identifier[] set = this.shape.is("Острая") ? SHARP : SOFT;
        Identifier filled = set[0];
        Identifier tex;
        if (this.fill.value()) {
            tex = filled;
        } else {
            double th = this.thickness.value();
            tex = th < 75.0 ? set[1] : th < 150.0 ? set[2] : set[3];
        }
        gg.pose().pushMatrix();
        gg.pose().translate(cx, cy);
        gg.pose().rotate(rot);
        if (glowAmount > 0.0f) {
            // soft glow: the same star, bigger and almost transparent
            int big = Math.round(size * 1.45f);
            gg.blit(RenderPipelines.GUI_TEXTURED, filled, -big / 2, -big / 2, 0.0f, 0.0f, big, big, TEX, TEX, TEX, TEX,
                    Render2D.tint(Render2D.withAlpha(rgb, Math.min(0.30f, 0.14f * glowAmount) * a)));
        }
        gg.blit(RenderPipelines.GUI_TEXTURED, tex, -size / 2, -size / 2, 0.0f, 0.0f, size, size, TEX, TEX, TEX, TEX,
                Render2D.tint(Render2D.withAlpha(rgb, 0.97f * a)));
        gg.pose().popMatrix();
        if (glowAmount > 0.0f) {
            Render2D.glow(gg, cx, cy, Math.max(8.0f, r * 3.2f),
                    Render2D.withAlpha(rgb, Math.min(0.45f, 0.14f * glowAmount) * a));
        }
    }
}
