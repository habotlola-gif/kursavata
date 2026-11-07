package dev.hatek.client.render;

import dev.hatek.client.ui.render.Draw;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Crystals target effect: 15 faceted gems standing upright in three tidy tiers of five (feet, chest, head)
 * circling the target. Each gem has a soft glow, faces of different brightness and a light outline.
 * Faces are projected with {@link WorldProjection}; far gems are drawn first, back faces are culled.
 */
public final class CrystalsEsp {
    private static final int TIERS = 3;
    private static final int PER_TIER = 5;
    private static final int COUNT = TIERS * PER_TIER;
    private static final float[] TIER_HEIGHT = {0.14f, 0.52f, 0.90f};

    private CrystalsEsp() {
    }

    private record Face(Vec3 a, Vec3 b, Vec3 c, double depth, float shade) {}

    private record Crystal(double dist, float cx, float cy, float glowR, List<Face> faces) {}

    public static void render(net.minecraft.client.gui.GuiGraphicsExtractor gg, LivingEntity entity, Vec3 base,
                              float speed, float sizeMul, float glow, int color, float alpha, float partialTick,
                              boolean visible) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Vec3 camera = WorldProjection.cameraPos();
        float eased = easeOutCubic(alpha);
        float time = (mc.player.tickCount + partialTick) * 6.0f * speed;
        float height = entity.getBbHeight();
        float halfWidth = entity.getBbWidth() * 0.5f;
        float vis = visible ? 1.0f : 0.4f;

        Vec3 light = new Vec3(0.35, 0.85, 0.40).normalize();
        List<Crystal> crystals = new ArrayList<>();

        for (int i = 0; i < COUNT; i++) {
            int tier = i / PER_TIER;
            int slot = i % PER_TIER;
            float angle = time + slot * (360.0f / PER_TIER) + tier * (180.0f / PER_TIER);
            float radius = halfWidth + 0.24f;
            double rad = Math.toRadians(angle);
            double bob = 0.03 * Math.sin(Math.toRadians(time * 1.2 + tier * 120.0));
            Vec3 c = base.add(radius * Math.cos(rad), TIER_HEIGHT[tier] * height + bob, radius * Math.sin(rad));

            float scale = 0.18f * eased * sizeMul;
            double half = scale * 1.35;

            Vec3 axis = new Vec3(0.0, 1.0, 0.0);
            Vec3 radial = new Vec3(Math.cos(rad), 0.0, Math.sin(rad));
            Vec3 tang = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
            double wr = scale * 0.50;
            double wt = scale * 0.78;

            Vec3 top = c.add(axis.scale(half));
            Vec3 bot = c.subtract(axis.scale(half));
            Vec3[] eq = {c.add(radial.scale(wr)), c.add(tang.scale(wt)), c.subtract(radial.scale(wr)), c.subtract(tang.scale(wt))};

            List<Face> faces = new ArrayList<>();
            for (int k = 0; k < 4; k++) {
                Vec3 e0 = eq[k];
                Vec3 e1 = eq[(k + 1) % 4];
                addFace(faces, top, e0, e1, c, camera, light);
                addFace(faces, bot, e1, e0, c, camera, light);
            }
            float[] sc = WorldProjection.toScreen(c);
            float[] scTop = WorldProjection.toScreen(top);
            if (sc == null || scTop == null || faces.isEmpty() || Math.abs(sc[0]) > 20000.0f || Math.abs(sc[1]) > 20000.0f) {
                continue;
            }
            double dist = Math.max(0.3, c.distanceTo(camera));
            float glowR = (float) Math.hypot(scTop[0] - sc[0], scTop[1] - sc[1]) * 1.8f;
            crystals.add(new Crystal(dist, sc[0], sc[1], glowR, faces));
        }
        crystals.sort(Comparator.comparingDouble((Crystal cr) -> -cr.dist()));

        int darkCol = Render2D.lerp(0xFF000000, 0xFF000000 | (color & 0xFFFFFF), 0.50f);
        int lightCol = Render2D.lerp(0xFF000000 | (color & 0xFFFFFF), 0xFFFFFFFF, 0.80f);
        float th = Math.max(1.0f, 1.1f * Render2D.guiScale());
        float faceAlpha = Mth.clamp(alpha * 0.85f * vis, 0.0f, 1.0f);

        for (Crystal cr : crystals) {
            if (glow > 0.0f) {
                // soft glow: three translucent discs of quantised size (keeps the texture cache small)
                for (int ring = 0; ring < 3; ring++) {
                    int d = glowSize(cr.glowR() * (2.0f - ring * 0.55f));
                    Render2D.circle(gg, cr.cx() - d / 2.0f, cr.cy() - d / 2.0f, d,
                            Render2D.withAlpha(0xFF000000 | (color & 0xFFFFFF), Mth.clamp(0.10f * glow * vis * alpha, 0.0f, 1.0f)));
                }
            }
            cr.faces().sort(Comparator.comparingDouble((Face f) -> -f.depth()));
            for (Face f : cr.faces()) {
                float[] a = WorldProjection.toScreen(f.a());
                float[] b = WorldProjection.toScreen(f.b());
                float[] c = WorldProjection.toScreen(f.c());
                if (a == null || b == null || c == null) {
                    continue;
                }
                float sh = (float) Math.pow(f.shade(), 1.4);
                int face = Render2D.lerp(darkCol, lightCol, sh);
                Draw.triangle(gg, a[0], a[1], b[0], b[1], c[0], c[1], Render2D.withAlpha(0xFF000000 | (face & 0xFFFFFF), faceAlpha));
                int edge = Render2D.withAlpha(0xFF000000 | (lightCol & 0xFFFFFF), faceAlpha);
                Draw.line(gg, a[0], a[1], b[0], b[1], th, edge);
                Draw.line(gg, b[0], b[1], c[0], c[1], th, edge);
                Draw.line(gg, c[0], c[1], a[0], a[1], th, edge);
            }
        }
    }

    // The disc textures are generated per diameter, so only a handful of small sizes are ever allowed.
    // Free-form sizes (a crystal right at the camera gives thousands of pixels) made the game freeze.
    private static final int[] GLOW_SIZES = {16, 24, 32, 48, 64, 96, 128};

    private static int glowSize(float wanted) {
        for (int size : GLOW_SIZES) {
            if (wanted <= size) {
                return size;
            }
        }
        return GLOW_SIZES[GLOW_SIZES.length - 1];
    }

    private static void addFace(List<Face> out, Vec3 a, Vec3 b, Vec3 c, Vec3 center, Vec3 camera, Vec3 light) {
        Vec3 n = b.subtract(a).cross(c.subtract(a)).normalize();
        Vec3 mid = a.add(b).add(c).scale(1.0 / 3.0);
        if (n.dot(mid.subtract(center)) < 0.0) {
            n = n.scale(-1.0);
        }
        if (n.dot(mid.subtract(camera)) >= 0.0) {
            return;
        }
        float shade = Mth.clamp((float) (0.15 + 0.85 * Math.abs(n.dot(light))), 0.0f, 1.0f);
        out.add(new Face(a, b, c, mid.distanceTo(camera), shade));
    }

    private static float easeOutCubic(float t) {
        float u = 1.0f - Mth.clamp(t, 0.0f, 1.0f);
        return 1.0f - u * u * u;
    }
}
