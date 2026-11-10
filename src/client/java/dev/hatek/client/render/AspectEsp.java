package dev.hatek.client.render;

import dev.hatek.client.ui.render.Draw;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The "Души" (Souls) and "Кристаллы" (Crystals) target effects of the aspectdlc client (CC0 sources), drawn on the
 * screen from projected world points like the other target effects of this client.
 *
 * <p>Souls: three glowing balls on tilted circles around the middle of the body, each with a tail of 20 fading
 * sprites. Crystals: 18 thin double-pointed crystals on a ring around the target at pseudo-random heights, all
 * tilted to point at the body, slowly turning, each with a soft glow; the ring closes in while the effect fades in.
 */
public final class AspectEsp {
    private static final int RED = 0xFFFF0303;

    private AspectEsp() {
    }

    private static double pixelsPerUnit() {
        Minecraft mc = Minecraft.getInstance();
        double fov = Math.toRadians(mc.options.fov().get());
        return Render2D.screenHeight() / (2.0 * Math.tan(fov / 2.0));
    }

    private static float pixels(Vec3 world, Vec3 cam, double blocks, double ppu) {
        return (float) (blocks * ppu / Math.max(0.6, world.distanceTo(cam)));
    }

    private static float hurt(LivingEntity entity) {
        return entity.hurtTime > 0 ? Mth.clamp(entity.hurtTime / 10.0f, 0.0f, 1.0f) : 0.0f;
    }

    // ------------------------------------------------------------------------------------------------ souls

    /** Three balls with 20-sprite tails; {@code visibility} fades the whole effect in and out. */
    public static void souls(GuiGraphicsExtractor gg, LivingEntity entity, Vec3 base, int color, float glowAmount,
                             float visibility, boolean hitFlash) {
        Vec3 cam = WorldProjection.cameraPos();
        double ppu = pixelsPerUnit();
        Vec3 center = base.add(0.0, entity.getBbHeight() / 2.0, 0.0);
        long now = System.currentTimeMillis();
        float hurt = hitFlash ? hurt(entity) : 0.0f;
        int rgb = 0xFF000000 | (color & 0xFFFFFF);
        int shown = Render2D.lerp(rgb, RED, hurt);
        float strength = visibility * 0.6f * visibility * Math.max(0.2f, glowAmount);
        final double radius = 0.7;
        // the three paths of the original: the sign of every axis differs
        int[][] signs = {{1, 1, -1}, {-1, 1, -1}, {-1, -1, 1}};
        for (int[] sign : signs) {
            for (int i = 19; i >= 0; i--) {
                float fall = 1.0f - i / 20.0f * 0.7f;
                double a = 0.15 * (now - i * 10.0) / 25.0;
                double s = Math.sin(a) * radius;
                double c = Math.cos(a) * radius;
                double x;
                double y;
                double z;
                if (sign[0] == 1) {
                    // path 1: (sin, cos, -cos)
                    x = s;
                    y = c;
                    z = -c;
                } else if (sign[1] == 1) {
                    // path 2: (-sin, sin, -cos)
                    x = -s;
                    y = s;
                    z = -c;
                } else {
                    // path 3: (-sin, -sin, cos)
                    x = -s;
                    y = -s;
                    z = c;
                }
                Vec3 world = center.add(x, y, z);
                float[] screen = WorldProjection.toScreen(world);
                if (screen == null) {
                    continue;
                }
                float core = pixels(world, cam, 4.0 * fall * 0.12, ppu);
                float halo = pixels(world, cam, 4.0 * fall * 0.21, ppu);
                Render2D.glow(gg, screen[0], screen[1], Math.max(4.0f, halo * 2.0f),
                        Render2D.withAlpha(shown, Mth.clamp(strength * 0.45f, 0.0f, 1.0f)));
                Render2D.glow(gg, screen[0], screen[1], Math.max(3.0f, core * 2.0f),
                        Render2D.withAlpha(Render2D.lerp(shown, 0xFFFFFFFF, 0.25f), Mth.clamp(strength, 0.0f, 1.0f)));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ crystals

    private record Gem(double depth, float[][] corners, float cx, float cy, float glow) {}

    /** The ring of crystals; {@code visibility} (0..1) also closes the ring in while it fades in. */
    public static void crystals(GuiGraphicsExtractor gg, LivingEntity entity, Vec3 base, int color, float glowAmount,
                                float visibility, boolean hitFlash, double seconds) {
        Vec3 cam = WorldProjection.cameraPos();
        double ppu = pixelsPerUnit();
        float width = entity.getBbWidth();
        float height = entity.getBbHeight();
        float ringRadius = width * 1.5f * (1.2f - 0.5f * visibility);
        float turn = (float) (seconds * 16.0);                       // 0.8 degrees per tick
        float hurt = hitFlash ? hurt(entity) : 0.0f;
        int rgb = 0xFF000000 | (color & 0xFFFFFF);
        int shown = Render2D.lerp(rgb, RED, hurt);
        float half = 0.2f;
        float thin = 0.06f;
        List<Gem> gems = new ArrayList<>();
        for (int i = 0; i < 360; i += 20) {
            double a = Math.toRadians(i + turn);
            double x = Math.sin(a) * ringRadius;
            double z = Math.cos(a) * ringRadius;
            double y = 0.1 + height * Math.abs(Math.sin((double) i));
            Vec3 at = base.add(x, y, z);
            // tilt the crystal so that its axis points at the middle of the body
            Vec3 axis = new Vec3(-x, height / 2.0 - y, -z).normalize();
            Vec3[] frame = frame(axis);
            Vec3[] corners = {
                    at.add(axis.scale(half)), at.subtract(axis.scale(half)),
                    at.add(frame[0].scale(thin)), at.add(frame[1].scale(thin)),
                    at.subtract(frame[0].scale(thin)), at.subtract(frame[1].scale(thin))};
            float[][] pts = new float[6][];
            boolean ok = true;
            for (int k = 0; k < 6; k++) {
                pts[k] = WorldProjection.toScreen(corners[k]);
                if (pts[k] == null) {
                    ok = false;
                    break;
                }
            }
            float[] middle = WorldProjection.toScreen(at);
            if (!ok || middle == null) {
                continue;
            }
            gems.add(new Gem(at.distanceTo(cam), pts, middle[0], middle[1], pixels(at, cam, 0.55 + width * 0.25, ppu)));
        }
        gems.sort(Comparator.comparingDouble((Gem g) -> -g.depth()));
        int face = Render2D.withAlpha(shown, Mth.clamp(visibility, 0.0f, 1.0f));
        for (Gem g : gems) {
            if (glowAmount > 0.0f) {
                Render2D.glow(gg, g.cx(), g.cy(), Math.max(8.0f, g.glow() * 2.6f),
                        Render2D.withAlpha(shown, Mth.clamp(0.28f * glowAmount * visibility, 0.0f, 1.0f)));
            }
            // an octahedron: the two tips (0, 1) and the four corners of the belt (2, 3, 4, 5)
            int[][] triangles = {{0, 2, 3}, {0, 3, 4}, {0, 4, 5}, {0, 5, 2}, {1, 2, 3}, {1, 3, 4}, {1, 4, 5}, {1, 5, 2}};
            for (int[] t : triangles) {
                Draw.triangle(gg, g.corners()[t[0]][0], g.corners()[t[0]][1], g.corners()[t[1]][0], g.corners()[t[1]][1],
                        g.corners()[t[2]][0], g.corners()[t[2]][1], face);
            }
        }
    }

    /** Two unit vectors across the axis. */
    private static Vec3[] frame(Vec3 axis) {
        Vec3 ref = Math.abs(axis.y) < 0.95 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 u = axis.cross(ref).normalize();
        Vec3 v = axis.cross(u).normalize();
        return new Vec3[]{u, v};
    }
}
