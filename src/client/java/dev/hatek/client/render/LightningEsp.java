package dev.hatek.client.render;

import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightning target effect: three arcs around the target, each flashing on its own phase and breaking into a
 * zigzag (recursive midpoint displacement with noise, plus side branches). Geometry is built in world
 * space, projected with {@link WorldProjection} and drawn as glowing polylines on the HUD layer.
 */
public final class LightningEsp {
    private static final int BOLTS = 3;
    private static final int DEPTH = 4;
    private static final float CELL_RATE = 6.0f;   // zigzag re-rolls this many times per second
    private static float visibility = 1.0f;        // fades the bolts behind walls
    private static long lastNanos;

    private LightningEsp() {
    }

    public static void render(GuiGraphicsExtractor gg, LivingEntity entity, Vec3 base, double time,
                              int color, float partialTick, float sizeMul, float thicknessMul, float glow) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.016f : Math.min((now - lastNanos) / 1.0e9f, 0.05f);
        lastNanos = now;
        boolean visible = mc.player != null && mc.player.hasLineOfSight(entity);
        visibility += ((visible ? 1.0f : 0.4f) - visibility) * (1.0f - (float) Math.exp(-dt * 8.0f));

        // red flash when the target is hit
        float hurt = Math.max(0.0f, entity.hurtTime - partialTick);
        float flash = (float) Math.sin(hurt * Math.PI / 10.0) * 0.5f;
        int tint = Render2D.lerp(color, 0xFFFF2020, Mth.clamp(flash, 0.0f, 1.0f));

        double radius = (entity.getBbWidth() * 0.75 + 0.16) * sizeMul;
        double height = entity.getBbHeight();
        float thickness = Math.max(2.0f, 1.8f * Render2D.guiScale() * thicknessMul);

        int cell = (int) Math.floor(time * CELL_RATE);
        float cellFrac = (float) (time * CELL_RATE - cell);

        for (int bolt = 0; bolt < BOLTS; bolt++) {
            double phase = time * 1.3 + (double) bolt / BOLTS;
            int flashCell = (int) Math.floor(phase);
            float flashFrac = (float) (phase - flashCell);
            float fadeIn = Mth.clamp(flashFrac / 0.08f, 0.0f, 1.0f);
            float fadeOut = 1.0f - Mth.clamp((flashFrac - 0.42f) / 0.3f, 0.0f, 1.0f);
            float intensity = fadeIn * fadeOut * Mth.clamp(0.55f + 0.45f * noise(bolt, 900, cell * 4), 0.0f, 1.0f);
            if (intensity <= 0.02f) {
                continue;
            }

            double bob = height * 0.16 * Math.sin(time * 1.7 + bolt * 2.1);
            double baseAngle = noise(bolt, 11, flashCell) * Math.PI;
            double arc = 0.6 + 0.5 * Math.abs(noise(bolt, 12, flashCell));
            double arcDir = noise(bolt, 13, flashCell) >= 0.0f ? 1.0 : -1.0;
            double startY = height * (0.85 + 0.05 * noise(bolt, 14, flashCell));
            double drop = height * 0.67;
            double boltRadius = radius * (1.0 + 0.12 * noise(bolt, 15, flashCell));
            double scroll = time * 0.9;

            Vec3[] ctrl = new Vec3[4];
            for (int i = 0; i < 4; i++) {
                double t = i / 3.0;
                double ang = baseAngle + scroll + arcDir * arc * Math.PI * 2.0 * t;
                ctrl[i] = base.add(Math.cos(ang) * boltRadius, startY - drop * t + bob, Math.sin(ang) * boltRadius);
            }

            List<Vec3> main = new ArrayList<>();
            List<List<Vec3>> branches = new ArrayList<>();
            main.add(ctrl[0]);
            for (int i = 0; i < 3; i++) {
                int seed = ((bolt * 131 + i + 1) * 17 + flashCell * 101) | 1;
                split(main, branches, ctrl[i], ctrl[i + 1], DEPTH, seed, 0, cell, cellFrac);
            }

            Render2D.pushAlpha(Mth.clamp(intensity * visibility, 0.0f, 1.0f));
            float th = thickness * (0.7f + 0.5f * intensity);
            drawLuminous(gg, project(main, false), th, tint, glow);
            for (List<Vec3> br : branches) {
                drawLuminous(gg, project(br, true), th * 0.55f, tint, glow * 0.8f);
            }
            Render2D.popAlpha();
        }
    }

    /** World points to screen points {x, y, intensity}; branches fade toward their tips. */
    private static float[][] project(List<Vec3> pts, boolean taper) {
        float[][] out = new float[pts.size()][];
        int n = pts.size();
        for (int i = 0; i < n; i++) {
            float[] p = WorldProjection.toScreen(pts.get(i));
            if (p == null) {
                continue;
            }
            float k = taper ? 1.0f - (float) i / Math.max(1, n - 1) : 1.0f;
            out[i] = new float[]{p[0], p[1], 0.35f + 0.65f * k};
        }
        return out;
    }

    /** Core line plus two wider translucent passes for the glow. */
    private static void drawLuminous(GuiGraphicsExtractor gg, float[][] pts, float thickness, int color, float glow) {
        if (glow > 0.0f) {
            polyline(gg, pts, thickness * 5.0f, Render2D.withAlpha(color, 0.48f * glow));
            polyline(gg, pts, thickness * 2.8f, Render2D.withAlpha(0xFFFFFFFF, 0.68f * glow));
        }
        polyline(gg, pts, thickness, Render2D.withAlpha(0xFFFFFFFF, 0.98f));
    }

    private static void polyline(GuiGraphicsExtractor gg, float[][] pts, float thickness, int argb) {
        for (int i = 0; i + 1 < pts.length; i++) {
            float[] a = pts[i], b = pts[i + 1];
            if (a == null || b == null) {
                continue;
            }
            int shade = Render2D.withAlpha(argb, (a[2] + b[2]) * 0.5f);
            segment(gg, a[0], a[1], b[0], b[1], thickness, shade);
            Render2D.circle(gg, a[0] - thickness / 2.0f, a[1] - thickness / 2.0f, thickness, shade);
        }
        if (pts.length > 0 && pts[pts.length - 1] != null) {
            float[] e = pts[pts.length - 1];
            Render2D.circle(gg, e[0] - thickness / 2.0f, e[1] - thickness / 2.0f, thickness, Render2D.withAlpha(argb, e[2]));
        }
    }

    private static void segment(GuiGraphicsExtractor gg, float x1, float y1, float x2, float y2, float thickness, int argb) {
        float length = (float) Math.hypot(x2 - x1, y2 - y1);
        if (length < 0.5f) {
            return;
        }
        gg.pose().pushMatrix();
        gg.pose().translate(x1, y1);
        gg.pose().rotate((float) Math.atan2(y2 - y1, x2 - x1));
        Render2D.rect(gg, 0.0f, -thickness / 2.0f, length, thickness, argb);
        gg.pose().popMatrix();
    }

    private static void split(List<Vec3> out, List<List<Vec3>> branches, Vec3 a, Vec3 b, int depth,
                              int seed, int level, int cell, float frac) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (depth <= 0 || len < 1.0e-4) {
            out.add(b);
            return;
        }
        Vec3 dir = d.scale(1.0 / len);
        Vec3 helper = Math.abs(dir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 p1 = dir.cross(helper).normalize();
        Vec3 p2 = dir.cross(p1);

        float h1 = smoothNoise(seed, 0, cell, frac);
        float h2 = smoothNoise(seed, 1, cell, frac);
        Vec3 mid = a.add(b).scale(0.5).add(p1.scale(h1 * len * 0.3)).add(p2.scale(h2 * len * 0.3));

        split(out, branches, a, mid, depth - 1, seed * 2 + 7, level, cell, frac);

        if (level < 2 && depth >= 2 && smoothNoise(seed, 2, cell, frac) > 0.45f) {
            float b1 = smoothNoise(seed, 3, cell, frac);
            float b2 = smoothNoise(seed, 4, cell, frac);
            Vec3 bd = dir.scale(0.4).add(p1.scale(b1)).add(p2.scale(b2));
            double bl = bd.length();
            if (bl > 1.0e-5) {
                Vec3 end = mid.add(bd.scale(len * 0.75 / bl));
                List<Vec3> branch = new ArrayList<>();
                branch.add(mid);
                split(branch, branches, mid, end, Math.min(depth - 1, 3), seed * 3 + 13, level + 1, cell, frac);
                branches.add(branch);
            }
        }

        split(out, branches, mid, b, depth - 1, seed * 2 + 9, level, cell, frac);
    }

    /** Deterministic noise in [-1, 1]. */
    static float noise(int a, int b, int c) {
        int h = a * 374761393 + b * 668265263 + c * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 32768.0f - 1.0f;
    }

    /** Smooth blend between neighbouring noise cells so the zigzag crawls instead of jumping. */
    static float smoothNoise(int a, int b, int cell, float t) {
        float from = noise(a, b, cell * 4);
        float to = noise(a, b, (cell + 1) * 4);
        float s = t * t * (3.0f - 2.0f * t);
        return from + (to - from) * s;
    }
}
