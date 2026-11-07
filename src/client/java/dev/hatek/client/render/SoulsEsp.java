package dev.hatek.client.render;

import dev.hatek.client.ui.render.Draw;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Souls target effect: a few glowing comets circle the target at different heights. Each soul is a bright head
 * with a long tapering trail that follows the path it just flew. Trails are drawn as tapered ribbons
 * (two triangles per segment), with a wide soft pass under a bright core and a halo on the head.
 */
public final class SoulsEsp {
    private static final int POINTS = 20;                       // samples along the trail
    private static final int[] HALO_SIZES = {16, 24, 32, 48, 64};

    private SoulsEsp() {
    }

    public static void render(GuiGraphicsExtractor gg, LivingEntity entity, Vec3 base, double time, int color,
                              int souls, float sizeMul, float thicknessMul, float trailMul, float glow, float visibility) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 camera = WorldProjection.cameraPos();
        double height = entity.getBbHeight();
        double radius = Math.max(0.75, entity.getBbWidth() * 1.3) * sizeMul;
        double trailSeconds = 0.6 * trailMul;
        double worldSize = 0.2 * thicknessMul;
        double fov = Math.toRadians(mc.options.fov().get());
        double pixelsPerUnit = Render2D.screenHeight() / (2.0 * Math.tan(fov / 2.0));

        int coreColor = Render2D.lerp(0xFF000000 | (color & 0xFFFFFF), 0xFFFFFFFF, 0.6f);
        int glowColor = 0xFF000000 | (color & 0xFFFFFF);

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
                double width = worldSize * pixelsPerUnit / distance * (0.10 + 0.90 * Math.pow(fade, 0.9));
                pts[k] = new float[]{screen[0], screen[1], (float) Math.min(width, 120.0), fade};
            }
            if (glow > 0.0f) {
                ribbon(gg, pts, 3.0f, glowColor, 0.30f * glow * visibility);
            }
            ribbon(gg, pts, 1.0f, coreColor, 0.95f * visibility);

            float[] head = pts[0];
            if (head != null && glow > 0.0f) {
                for (int ring = 0; ring < 2; ring++) {
                    int d = haloSize(head[2] * (3.2f - ring * 1.2f));
                    Render2D.circle(gg, head[0] - d / 2.0f, head[1] - d / 2.0f, d,
                            Render2D.withAlpha(glowColor, Mth.clamp(0.16f * glow * visibility, 0.0f, 1.0f)));
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

    /** A tapered ribbon along the points; each point is {x, y, width, fade}. */
    private static void ribbon(GuiGraphicsExtractor gg, float[][] pts, float widthMul, int color, float alpha) {
        for (int i = 0; i + 1 < pts.length; i++) {
            float[] a = pts[i];
            float[] b = pts[i + 1];
            if (a == null || b == null) {
                continue;
            }
            float dx = b[0] - a[0];
            float dy = b[1] - a[1];
            float len = (float) Math.hypot(dx, dy);
            if (len < 0.01f) {
                continue;
            }
            float nx = -dy / len;
            float ny = dx / len;
            float wa = Math.max(0.5f, a[2] * widthMul) * 0.5f;
            float wb = Math.max(0.5f, b[2] * widthMul) * 0.5f;
            float fade = (a[3] + b[3]) * 0.5f;
            int segment = Render2D.withAlpha(color, Mth.clamp(alpha * (float) Math.pow(fade, 1.1), 0.0f, 1.0f));
            float ax1 = a[0] + nx * wa, ay1 = a[1] + ny * wa;
            float ax2 = a[0] - nx * wa, ay2 = a[1] - ny * wa;
            float bx1 = b[0] + nx * wb, by1 = b[1] + ny * wb;
            float bx2 = b[0] - nx * wb, by2 = b[1] - ny * wb;
            Draw.triangle(gg, ax1, ay1, ax2, ay2, bx1, by1, segment);
            Draw.triangle(gg, ax2, ay2, bx2, by2, bx1, by1, segment);
        }
    }

    /** Halo discs use a few fixed sizes only (a texture is generated per size). */
    private static int haloSize(float wanted) {
        for (int size : HALO_SIZES) {
            if (wanted <= size) {
                return size;
            }
        }
        return HALO_SIZES[HALO_SIZES.length - 1];
    }
}
