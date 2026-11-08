package dev.hatek.client.render;

import dev.hatek.client.ui.render.Draw;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Star particles around the target: soft five-pointed stars in white, pink, yellow, green and lavender that spawn
 * around the body, drift outward and upward, spin and fade away. A few appear all the time and every hit sends a
 * burst. Each star is a screen-space billboard with a soft glow behind it.
 */
public final class StarsEsp {
    private static final int[] PALETTE = {0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFB8F0, 0xFFFFF08A, 0xFF9DF58E, 0xFFC9A8FF};
    private static final int MAX_STARS = 140;

    private static final class Star {
        Vec3 pos;
        Vec3 vel;
        float life;
        float age;
        float size;      // in blocks
        float spin;
        float spinSpeed;
        int color;
    }

    private static final List<Star> STARS = new ArrayList<>();
    private static double spawnDebt;

    private StarsEsp() {
    }

    public static void clear() {
        STARS.clear();
        spawnDebt = 0.0;
    }

    private static void spawn(LivingEntity entity, Vec3 base, boolean burst) {
        if (STARS.size() >= MAX_STARS) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Star star = new Star();
        double height = entity.getBbHeight();
        double radius = Math.max(0.4, entity.getBbWidth() * 0.9);
        double angle = rnd.nextDouble() * Math.PI * 2.0;
        double r = radius * (0.5 + rnd.nextDouble() * 0.9);
        star.pos = base.add(Math.cos(angle) * r, height * (0.1 + rnd.nextDouble() * 0.9), Math.sin(angle) * r);
        double speed = burst ? 0.9 + rnd.nextDouble() * 1.6 : 0.15 + rnd.nextDouble() * 0.35;
        star.vel = new Vec3(Math.cos(angle) * speed, (burst ? 0.4 : 0.25) * (0.3 + rnd.nextDouble()) + 0.1,
                Math.sin(angle) * speed);
        star.life = burst ? 0.7f + rnd.nextFloat() * 0.7f : 1.1f + rnd.nextFloat() * 1.0f;
        star.size = 0.09f + rnd.nextFloat() * (burst ? 0.18f : 0.12f);
        star.spin = rnd.nextFloat() * Mth.TWO_PI;
        star.spinSpeed = (rnd.nextFloat() - 0.5f) * 5.0f;
        star.color = PALETTE[rnd.nextInt(PALETTE.length)];
        STARS.add(star);
    }

    /**
     * Updates and draws the stars. {@code rate} is the number of calm stars per second, {@code burst} the number
     * added at once (0 = none, e.g. when nothing was hit this frame).
     */
    public static void render(GuiGraphicsExtractor gg, LivingEntity entity, Vec3 base, float dt, float rate, int burst,
                              float sizeMul, float glow, float visibility, float masterAlpha) {
        Minecraft mc = Minecraft.getInstance();
        if (entity != null && masterAlpha > 0.05f) {
            spawnDebt += rate * dt;
            while (spawnDebt >= 1.0) {
                spawnDebt -= 1.0;
                spawn(entity, base, false);
            }
            for (int i = 0; i < burst; i++) {
                spawn(entity, base, true);
            }
        }
        if (STARS.isEmpty()) {
            return;
        }
        Vec3 camera = WorldProjection.cameraPos();
        double fov = Math.toRadians(mc.options.fov().get());
        double pixelsPerUnit = Render2D.screenHeight() / (2.0 * Math.tan(fov / 2.0));

        for (int i = STARS.size() - 1; i >= 0; i--) {
            Star star = STARS.get(i);
            star.age += dt;
            if (star.age >= star.life) {
                STARS.remove(i);
                continue;
            }
            star.pos = star.pos.add(star.vel.scale(dt));
            star.vel = star.vel.scale(Math.exp(-1.6 * dt));
            star.spin += star.spinSpeed * dt;

            float t = star.age / star.life;
            float appear = Mth.clamp(t / 0.15f, 0.0f, 1.0f);
            float fade = t > 0.6f ? 1.0f - (t - 0.6f) / 0.4f : 1.0f;
            float alpha = appear * fade * visibility * masterAlpha;
            if (alpha <= 0.01f) {
                continue;
            }
            float[] screen = WorldProjection.toScreen(star.pos);
            if (screen == null) {
                continue;
            }
            double distance = Math.max(0.3, star.pos.distanceTo(camera));
            float outer = (float) Math.min(70.0, star.size * sizeMul * pixelsPerUnit / distance * (0.6f + 0.4f * appear));
            if (outer < 1.5f) {
                continue;
            }
            if (glow > 0.0f) {
                Render2D.glow(gg, screen[0], screen[1], outer * 3.4f,
                        Render2D.withAlpha(star.color, Mth.clamp(0.45f * glow * alpha, 0.0f, 1.0f)));
            }
            drawStar(gg, screen[0], screen[1], outer, star.spin, Render2D.withAlpha(star.color, Mth.clamp(alpha, 0.0f, 1.0f)),
                    Render2D.withAlpha(0xFFFFFFFF, Mth.clamp(alpha * 0.55f, 0.0f, 1.0f)));
        }
    }

    /** A fat five-pointed star: ten outline points alternating outer and inner radius, fanned from the centre. */
    private static void drawStar(GuiGraphicsExtractor gg, float cx, float cy, float outer, float rotation, int color, int centre) {
        float inner = outer * 0.52f;
        float[] xs = new float[10];
        float[] ys = new float[10];
        for (int k = 0; k < 10; k++) {
            float radius = (k % 2 == 0) ? outer : inner;
            float a = rotation - Mth.HALF_PI + k * Mth.PI / 5.0f;
            xs[k] = cx + Mth.cos(a) * radius;
            ys[k] = cy + Mth.sin(a) * radius;
        }
        for (int k = 0; k < 10; k++) {
            int j = (k + 1) % 10;
            Draw.triangle(gg, cx, cy, xs[k], ys[k], xs[j], ys[j], color);
        }
        // a lighter core makes the star look soft and puffy
        float core = inner * 0.9f;
        for (int k = 0; k < 10; k++) {
            int j = (k + 1) % 10;
            float a0 = rotation - Mth.HALF_PI + k * Mth.PI / 5.0f;
            float a1 = rotation - Mth.HALF_PI + j * Mth.PI / 5.0f;
            Draw.triangle(gg, cx, cy, cx + Mth.cos(a0) * core, cy + Mth.sin(a0) * core,
                    cx + Mth.cos(a1) * core, cy + Mth.sin(a1) * core, centre);
        }
    }
}
