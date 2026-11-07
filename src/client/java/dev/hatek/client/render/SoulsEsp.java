package dev.hatek.client.render;

import dev.hatek.client.ui.render.Draw;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Souls target effect: a few glowing comets circle the target at different heights. Each soul is a teardrop: a fat
 * rounded head and a long tail that tapers to a point along the path it just flew, with a white core, a coloured
 * body and a soft halo. The tail is a ribbon whose left/right edge points are shared between neighbouring segments
 * (no seams), the head is capped with a small triangle fan. The shape was tuned against an offline preview.
 */
public final class SoulsEsp {
    private static final int POINTS = 16;           // samples along the trail
    private static final int CAP_STEPS = 10;        // triangles in the round head cap

    private SoulsEsp() {
    }

    public static void render(GuiGraphicsExtractor gg, LivingEntity entity, Vec3 base, double time, int color,
                              int souls, float sizeMul, float thicknessMul, float trailMul, float glow, float visibility) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 camera = WorldProjection.cameraPos();
        double height = entity.getBbHeight();
        double radius = Math.max(0.75, entity.getBbWidth() * 1.3) * sizeMul;
        double trailSeconds = 0.6 * trailMul;
        double worldSize = 0.15 * thicknessMul;                       // head width in blocks
        double fov = Math.toRadians(mc.options.fov().get());
        double pixelsPerUnit = Render2D.screenHeight() / (2.0 * Math.tan(fov / 2.0));
        double maxWidth = 80.0 * thicknessMul;

        int bodyColor = 0xFF000000 | (color & 0xFFFFFF);
        int coreColor = Render2D.lerp(bodyColor, 0xFFFFFFFF, 0.65f);

        for (int soul = 0; soul < souls; soul++) {
            double phase = soul * Math.PI * 2.0 / souls;
            float[][] pts = new float[POINTS][];
            for (int k = 0; k < POINTS; k++) {
                float fade = 1.0f - (float) k / (POINTS - 1);
                double t = time - trailSeconds * k / (POINTS - 1);
                Vec3 point = position(base, height, radius, t, phase, soul);
                float[] screen = WorldProjection.toScreen(point);
                if (screen == null) {
                    continue;
                }
                double distance = Math.max(0.3, point.distanceTo(camera));
                double width = worldSize * pixelsPerUnit / distance * (0.02 + 0.98 * Math.pow(fade, 0.85));
                pts[k] = new float[]{screen[0], screen[1], (float) Math.min(width, maxWidth), fade};
            }
            // soft edge, coloured body, white core
            ribbon(gg, pts, 1.7f, bodyColor, 0.22f * visibility * Math.min(1.0f, 0.4f + glow), 1.0f);
            ribbon(gg, pts, 1.0f, coreColor, 0.95f * visibility, 0.6f);
            ribbon(gg, pts, 0.45f, 0xFFFFFFFF, 0.90f * visibility, 0.8f);

            if (glow > 0.0f) {
                // soft halo along the trail (every third sample) and a bigger one on the head
                for (int k = POINTS - 1; k >= 1; k -= 3) {
                    float[] p = pts[k];
                    if (p != null) {
                        Render2D.glow(gg, p[0], p[1], Math.max(10.0f, p[2] * 4.5f),
                                Render2D.withAlpha(bodyColor, Mth.clamp(0.20f * glow * visibility * p[3], 0.0f, 1.0f)));
                    }
                }
                float[] head = pts[0];
                if (head != null) {
                    Render2D.glow(gg, head[0], head[1], Math.max(24.0f, head[2] * 6.0f),
                            Render2D.withAlpha(bodyColor, Mth.clamp(0.55f * glow * visibility, 0.0f, 1.0f)));
                }
            }
        }
    }

    /** Orbit of one soul: a circle around the body with a slow vertical sway and a little wobble. */
    private static Vec3 position(Vec3 base, double height, double radius, double t, double phase, int index) {
        double angle = t * 2.4 + phase;
        double wobble = Math.sin(t * 1.7 + index * 2.1);
        double y = height * (0.5 + 0.42 * Math.sin(t * 2.0 + phase * 1.5)) + wobble * 0.1;
        double r = radius * (0.92 + 0.12 * Math.sin(t * 3.1 + index));
        return base.add(Math.cos(angle) * r, y, Math.sin(angle) * r);
    }

    /**
     * A tapered ribbon along the points {x, y, width, fade}. Width is scaled by {@code widthMul}; the segment alpha
     * is {@code alpha * fade^alphaPow}. Neighbouring segments share their edge points, and the head is capped round.
     */
    private static void ribbon(GuiGraphicsExtractor gg, float[][] pts, float widthMul, int color, float alpha, float alphaPow) {
        int n = pts.length;
        float[][] left = new float[n][];
        float[][] right = new float[n][];
        for (int i = 0; i < n; i++) {
            if (pts[i] == null) {
                continue;
            }
            // averaged normal of the neighbouring segments
            float vx = 0.0f;
            float vy = 0.0f;
            for (int j = i - 1; j <= i; j++) {
                if (j >= 0 && j + 1 < n && pts[j] != null && pts[j + 1] != null) {
                    float dx = pts[j + 1][0] - pts[j][0];
                    float dy = pts[j + 1][1] - pts[j][1];
                    float len = (float) Math.hypot(dx, dy);
                    if (len > 0.01f) {
                        vx += -dy / len;
                        vy += dx / len;
                    }
                }
            }
            float nl = (float) Math.hypot(vx, vy);
            if (nl < 1.0e-4f) {
                continue;
            }
            vx /= nl;
            vy /= nl;
            float half = Math.max(0.4f, pts[i][2] * widthMul) * 0.5f;
            left[i] = new float[]{pts[i][0] + vx * half, pts[i][1] + vy * half};
            right[i] = new float[]{pts[i][0] - vx * half, pts[i][1] - vy * half};
        }
        for (int i = 0; i + 1 < n; i++) {
            if (left[i] == null || left[i + 1] == null) {
                continue;
            }
            float fade = (pts[i][3] + pts[i + 1][3]) * 0.5f;
            int c = Render2D.withAlpha(color, Mth.clamp(alpha * (float) Math.pow(fade, alphaPow), 0.0f, 1.0f));
            Draw.triangle(gg, left[i][0], left[i][1], right[i][0], right[i][1], left[i + 1][0], left[i + 1][1], c);
            Draw.triangle(gg, right[i][0], right[i][1], right[i + 1][0], right[i + 1][1], left[i + 1][0], left[i + 1][1], c);
        }
        // round head
        if (pts[0] != null && left[0] != null) {
            float r = Math.max(0.4f, pts[0][2] * widthMul) * 0.5f;
            int c = Render2D.withAlpha(color, Mth.clamp(alpha, 0.0f, 1.0f));
            for (int k = 0; k < CAP_STEPS; k++) {
                double a0 = Math.PI * 2.0 * k / CAP_STEPS;
                double a1 = Math.PI * 2.0 * (k + 1) / CAP_STEPS;
                Draw.triangle(gg, pts[0][0], pts[0][1],
                        pts[0][0] + (float) Math.cos(a0) * r, pts[0][1] + (float) Math.sin(a0) * r,
                        pts[0][0] + (float) Math.cos(a1) * r, pts[0][1] + (float) Math.sin(a1) * r, c);
            }
        }
    }
}
