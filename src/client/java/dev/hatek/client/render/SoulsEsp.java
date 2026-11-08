package dev.hatek.client.render;

import dev.hatek.client.ui.render.Draw;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Souls target effect: a few small ghosts (rounded dome, straight sides, a scalloped three-legged hem, and two
 * simple eyes — the classic Pac-Man-style silhouette) float and orbit around the target. Each ghost is a flat
 * screen-space billboard, always facing the camera, drawn by fanning triangles from an interior centre point out to
 * the outline. A fainter, eyeless echo of its previous position trails just behind it for a floaty motion.
 */
public final class SoulsEsp {
    private static final int DOME_STEPS = 14;      // triangles across the rounded top
    private static final int HEM_STEPS = 24;       // samples along the scalloped bottom
    private static final int LEGS = 3;             // classic three-legged ghost silhouette

    private SoulsEsp() {
    }

    public static void render(GuiGraphicsExtractor gg, LivingEntity entity, Vec3 base, double time, int color,
                              int souls, float sizeMul, float thicknessMul, float trailMul, float glow, float visibility) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 camera = WorldProjection.cameraPos();
        double height = entity.getBbHeight();
        double radius = Math.max(0.75, entity.getBbWidth() * 1.3) * sizeMul;
        double worldSize = 0.55 * sizeMul;                   // ghost width in blocks
        double echoSeconds = 0.16 * trailMul;                // how far behind the fading echo sits
        double fov = Math.toRadians(mc.options.fov().get());
        double pixelsPerUnit = Render2D.screenHeight() / (2.0 * Math.tan(fov / 2.0));
        double maxPixels = 110.0 * thicknessMul;

        int bodyColor = 0xFF000000 | (color & 0xFFFFFF);
        int coreColor = Render2D.lerp(bodyColor, 0xFFFFFFFF, 0.5f);

        for (int soul = 0; soul < souls; soul++) {
            double phase = soul * Math.PI * 2.0 / souls;

            Vec3 echoPoint = position(base, height, radius, time - echoSeconds, phase, soul);
            float[] echoScreen = WorldProjection.toScreen(echoPoint);
            if (echoScreen != null) {
                double echoDistance = Math.max(0.3, echoPoint.distanceTo(camera));
                float echoSize = (float) Math.min(maxPixels, worldSize * pixelsPerUnit / echoDistance);
                drawGhost(gg, echoScreen[0], echoScreen[1], echoSize, bodyColor, coreColor,
                        0.24f * visibility, false);
            }

            Vec3 point = position(base, height, radius, time, phase, soul);
            float[] screen = WorldProjection.toScreen(point);
            if (screen == null) {
                continue;
            }
            double distance = Math.max(0.3, point.distanceTo(camera));
            float size = (float) Math.min(maxPixels, worldSize * pixelsPerUnit / distance);

            if (glow > 0.0f) {
                Render2D.glow(gg, screen[0], screen[1], Math.max(28.0f, size * 2.4f),
                        Render2D.withAlpha(bodyColor, Mth.clamp(0.42f * glow * visibility, 0.0f, 1.0f)));
            }
            drawGhost(gg, screen[0], screen[1], size, bodyColor, coreColor, 0.92f * visibility, true);
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
     * Draws one ghost billboard centred on (cx, cy): a rounded dome on top, straight sides and a scalloped
     * {@value #LEGS}-legged hem, filled by fanning triangles from an interior centre point to the outline, with a
     * lighter core tint over the body and (when {@code withEyes}) two simple eyes.
     */
    private static void drawGhost(GuiGraphicsExtractor gg, float cx, float cy, float size, int bodyColor, int coreColor,
                                  float alpha, boolean withEyes) {
        if (size < 1.0f) {
            return;
        }
        float r = size * 0.5f;
        float bodyMidY = cy - r * 0.05f;          // where the dome meets the straight sides (the widest row)
        float domeTop = bodyMidY - r * 0.95f;
        float hemY = cy + r * 0.45f;              // baseline the legs hang from
        float legDepth = r * 0.5f;

        int total = DOME_STEPS + HEM_STEPS + 2;
        float[] xs = new float[total];
        float[] ys = new float[total];
        int n = 0;
        for (int i = 0; i <= DOME_STEPS; i++) {
            double a = Math.PI + Math.PI * i / DOME_STEPS;
            xs[n] = cx + r * (float) Math.cos(a);
            ys[n] = bodyMidY + (bodyMidY - domeTop) * (float) Math.sin(a);
            n++;
        }
        for (int i = 0; i <= HEM_STEPS; i++) {
            double u = 1.0 - (double) i / HEM_STEPS;
            xs[n] = cx + r * (float) (2.0 * u - 1.0);
            ys[n] = hemY + legDepth * (float) Math.abs(Math.sin(LEGS * Math.PI * u));
            n++;
        }

        float midY = (domeTop + hemY) * 0.5f;
        int fill = Render2D.withAlpha(bodyColor, alpha);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            Draw.triangle(gg, cx, midY, xs[i], ys[i], xs[j], ys[j], fill);
        }

        // a softer, lighter patch over the dome gives the body a glassy, two-tone look
        int core = Render2D.withAlpha(coreColor, alpha * 0.55f);
        for (int i = 0; i <= DOME_STEPS; i++) {
            int j = Math.min(i + 1, DOME_STEPS);
            if (i == j) {
                continue;
            }
            Draw.triangle(gg, cx, bodyMidY, xs[i], ys[i], xs[j], ys[j], core);
        }

        if (withEyes) {
            float eyeY = bodyMidY - r * 0.22f;
            float eyeD = r * 0.46f;
            float pupilD = r * 0.2f;
            int pupil = Render2D.withAlpha(0xFF14121C, alpha);
            int white = Render2D.withAlpha(0xFFFFFFFF, alpha);
            for (float side : new float[]{-1.0f, 1.0f}) {
                float ex = cx + side * r * 0.34f;
                Render2D.circle(gg, ex - eyeD / 2.0f, eyeY - eyeD / 2.0f, eyeD, white);
                Render2D.circle(gg, ex - pupilD / 2.0f, eyeY - pupilD / 2.0f + r * 0.05f, pupilD, pupil);
            }
        }
    }
}
