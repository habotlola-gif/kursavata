package dev.hatek.client.render;

import dev.hatek.client.ui.render.Draw;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

/**
 * The target markers ported from the "divine" client: Marker, Ring, Ghosts, Constellation, Bloom, Swords and Test.
 * Everything is drawn in screen space (the same way as the other TargetESP effects) from world positions projected
 * through {@link WorldProjection}: glows are soft discs, markers and swords are polygons, stars use the star texture.
 */
public final class ClassicEsp {
    public static final String MARKER = "Маркер";
    public static final String RING = "Кольцо";
    public static final String GHOSTS = "Призраки";
    public static final String CONSTELLATION = "Созвездие";
    public static final String BLOOM = "Bloom";
    public static final String SWORDS = "Мечи";
    public static final String TEST = "Test";
    public static final List<String> MODES = List.of(MARKER, RING, GHOSTS, CONSTELLATION, BLOOM, SWORDS, TEST);

    /** Settings the modes read; TargetEsp fills this before every frame. */
    public static final class Params {
        public int markerSkin;
        public float markerSize = 0.55f;
        public int bloomCount = 36;
        public float bloomSize = 0.2f;
        public float bloomSpeed = 1.0f;
        public float swordSpeed = 1200.0f;
        public float swordRadius = 1.0f;
        public float swordSize = 1.0f;
        public float swordBob = 0.12f;
        public int swordCount = 8;
        public boolean swordGlow = true;
        public boolean swordPhysics = true;
        public float testSpeed = 1.0f;
        public float testSize = 1.0f;
        public float testRadius = 0.725f;
        public int conStars = 8;
        public float conSpeed = 1.0f;
        public float conOrbit = 1.2f;
        public float conPointSize = 0.27f;
        public float conGlow = 0.5f;
        public boolean conLines = true;
    }

    public static final Params P = new Params();

    private static final float TAU = (float) (Math.PI * 2.0);
    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("hatek_client", "textures/gui/" + name + ".png");
    }

    private static final Identifier STAR = tex("tesp_star");
    private static final Identifier GLOW = tex("tesp_glow");
    private static final Identifier[] MARKERS = {tex("tesp_marker0"), tex("tesp_marker1"), tex("tesp_marker2"), tex("tesp_marker3")};
    private static final float[][] MARKER_PARAMS = {
            {110.0f, 2.8f, 0.5f, 1.6f, 0.06f, 3.0f}, {220.0f, 2.2f, 0.0f, 2.4f, 0.07f, 4.0f},
            {-180.0f, 2.6f, 1.0f, 1.8f, 0.12f, 0.0f}, {140.0f, 3.0f, 2.0f, 1.2f, 0.1f, 6.0f}};

    // ---- frame context
    private static Minecraft mc;
    private static GuiGraphicsExtractor gg;
    private static Vec3 cam = Vec3.ZERO;
    private static double ppu;
    private static float gs;
    private static float alpha;
    private static float dt;
    private static long now;
    private static int color0;
    private static int color1;
    private static boolean flashOnHit;
    private static float hurt;

    // ---- shared state
    private static LivingEntity current;
    private static long epoch = System.currentTimeMillis();
    private static long lastFrame;
    private static float circleStep;
    private static float circleSpeed;
    private static boolean reverseSpeed;
    private static double smoothY;
    private static double smoothY2;

    // ---- ghosts
    private static final Vec3[] ghostHistory = new Vec3[256];
    private static final long[] ghostHistoryTimes = new long[256];
    private static Vec3 ghostAnchor;
    private static Vec3 ghostVelocity = Vec3.ZERO;
    private static int ghostCursor = -1;
    private static int ghostCount;
    private static LivingEntity ghostTracked;
    private static Vec3 flightFrom;
    private static Vec3 flightSide = Vec3.ZERO;
    private static long flightStarted;
    private static long flightDuration;
    private static float flightSideAmount;
    private static float flightArc;
    private static boolean switching;
    private static float flightStrength;
    private static long ghostEpoch = System.currentTimeMillis();

    // ---- bloom
    private static final int MAX_BLOOM = 64;
    private static final BloomSeed[] SEEDS = new BloomSeed[MAX_BLOOM];

    private record BloomSeed(float heightBase, float radiusFactor, float angleOffset, float orbitSpeed, float verticalSpeed,
                             float pulseSpeed, float pulsePhase, float sizeFactor, float radialFreq, float radialAmp) {
    }

    static {
        for (int i = 0; i < MAX_BLOOM; i++) {
            Random rnd = new Random((long) i * 2654435761L + 987654321L);
            float h = (float) i / MAX_BLOOM + (rnd.nextFloat() - 0.5f) * 0.06f;
            float r = 0.45f + rnd.nextFloat() * 0.55f;
            float ang = rnd.nextFloat() * TAU;
            float dir = i % 2 == 0 ? 1.0f : -1.0f;
            SEEDS[i] = new BloomSeed(h, r, ang, dir * (0.8f + rnd.nextFloat() * 1.2f), 0.25f + rnd.nextFloat() * 0.45f,
                    2.0f + rnd.nextFloat() * 3.5f, rnd.nextFloat() * TAU, 0.75f + rnd.nextFloat() * 0.5f,
                    1.2f + rnd.nextFloat() * 2.0f, 0.04f + rnd.nextFloat() * 0.09f);
        }
    }

    // ---- swords
    private static final int MAX_SWORDS = 12;

    private static final class SwordState {
        double x, y, z, vx, vy, vz;
        float roll, pitch;
        boolean initialized;
    }

    private static final SwordState[] SWORD_STATES = new SwordState[MAX_SWORDS];
    private static final Vec3[] swordWorld = new Vec3[MAX_SWORDS];
    private static final int[] swordColors = new int[MAX_SWORDS];
    private static LivingEntity swordTarget;
    private static int swordPrevHurt;
    private static long swordLastUpdate;

    static {
        for (int i = 0; i < MAX_SWORDS; i++) {
            SWORD_STATES[i] = new SwordState();
            swordWorld[i] = Vec3.ZERO;
        }
    }

    private ClassicEsp() {
    }

    public static boolean isClassic(String mode) {
        return MODES.contains(mode);
    }

    /** Forget every effect's state (target lost, mode changed). */
    public static void reset() {
        current = null;
        ghostAnchor = null;
        ghostVelocity = Vec3.ZERO;
        ghostCursor = -1;
        ghostCount = 0;
        ghostTracked = null;
        flightFrom = null;
        switching = false;
        flightStrength = 0.0f;
        swordTarget = null;
        swordPrevHurt = 0;
        swordLastUpdate = 0L;
        for (SwordState s : SWORD_STATES) {
            s.initialized = false;
        }
        smoothY = 0.0;
        smoothY2 = 0.0;
        lastFrame = 0L;
    }

    // ------------------------------------------------------------------------------------------------ entry

    public static void render(GuiGraphicsExtractor graphics, String mode, LivingEntity target, float partial, float visibility,
                              float deltaSeconds, int first, int second, boolean hitFlash) {
        mc = Minecraft.getInstance();
        gg = graphics;
        cam = WorldProjection.cameraPos();
        double fov = Math.toRadians(mc.options.fov().get());
        ppu = Render2D.screenHeight() / (2.0 * Math.tan(fov / 2.0));
        gs = Render2D.guiScale();
        alpha = visibility;
        dt = Mth.clamp(deltaSeconds, 0.001f, 0.1f);
        now = System.currentTimeMillis();
        color0 = first;
        color1 = second;
        flashOnHit = hitFlash;

        if (current != target) {
            current = target;
            epoch = now;
        }
        float frame = dt * 60.0f;
        hurt = target.hurtTime > 0 ? target.hurtTime / 10.0f : Math.max(0.0f, hurt - 0.1f * frame);
        circleStep += 0.06f * frame;
        if (circleSpeed > 25.0f) {
            reverseSpeed = true;
        }
        if (circleSpeed < -25.0f) {
            reverseSpeed = false;
        }
        circleSpeed += (reverseSpeed ? -0.5f : 0.5f) * frame;
        circleStep += circleSpeed * 8.0e-4f * frame;

        switch (mode) {
            case MARKER -> marker(target, partial);
            case RING -> ring(target, partial);
            case GHOSTS -> ghosts(target, partial);
            case CONSTELLATION -> constellation(target, partial);
            case BLOOM -> bloom(target, partial);
            case SWORDS -> swords(target, partial);
            case TEST -> test(target, partial);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static int mul(int color, float factor) {
        return Render2D.withAlpha(color, factor);
    }

    /** The two theme colours with the given alpha applied (and a red flash while the target is hurt). */
    private static int[] palette(float a) {
        int first = color0;
        int second = color1;
        if (flashOnHit && hurt > 0.0f) {
            first = Render2D.lerp(first, 0xFFFF1B1A, hurt);
            second = Render2D.lerp(second, 0xFFFF1B1A, hurt);
        }
        float k = Mth.clamp(a, 0.0f, 1.0f);
        return new int[]{mul(first | 0xFF000000, k), mul(second | 0xFF000000, k)};
    }

    private static Vec3 position(LivingEntity entity, float partial) {
        return entity.getPosition(partial);
    }

    /** Pixels covered by {@code blocks} of world size at the position. */
    private static float pixels(Vec3 world, double blocks) {
        double dist = Math.max(0.6, world.distanceTo(cam));
        return (float) (blocks * ppu / dist);
    }

    private static void glow(float x, float y, float diameter, int argb) {
        Render2D.glow(gg, x, y, Math.max(3.0f, diameter), argb);
    }

    private static void dot(float x, float y, float diameter, int argb) {
        float d = Math.max(1.5f, diameter);
        Render2D.circle(gg, x - d / 2.0f, y - d / 2.0f, d, argb);
    }

    private static void sprite(Identifier tex, int texSize, float cx, float cy, float size, float rotation, int argb) {
        int s = Math.max(3, Math.round(size));
        gg.pose().pushMatrix();
        gg.pose().translate(cx, cy);
        gg.pose().rotate(rotation);
        gg.blit(RenderPipelines.GUI_TEXTURED, tex, -s / 2, -s / 2, 0.0f, 0.0f, s, s, texSize, texSize, texSize, texSize,
                Render2D.tint(argb));
        gg.pose().popMatrix();
    }

    /** The soft glowing dot (dashbloom): a bright core fading out smoothly. */
    private static void soft(float x, float y, float diameter, int argb) {
        sprite(GLOW, 256, x, y, Math.max(3.0f, diameter), 0.0f, argb);
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double t) {
        return new Vec3(Mth.lerp(t, from.x, to.x), Mth.lerp(t, from.y, to.y), Mth.lerp(t, from.z, to.z));
    }

    // ------------------------------------------------------------------------------------------------ marker

    private static void marker(LivingEntity target, float partial) {
        Vec3 center = position(target, partial).add(0.0, target.getBbHeight() * 0.5, 0.0);
        float[] screen = WorldProjection.toScreen(center);
        if (screen == null) {
            return;
        }
        int skin = Mth.clamp(P.markerSkin, 0, 3);
        float[] p = MARKER_PARAMS[skin];
        float time = (now - epoch) / 1000.0f;
        float omega = TAU / p[1];
        float angle = -(p[0] / omega) * Mth.cos(omega * (time + p[2])) + p[5] * Mth.sin(time * 0.7f);
        float pulse = Mth.sin(time * p[3] * TAU);
        float size = P.markerSize * (1.0f + p[4] * pulse) * (1.0f + 0.25f * hurt);
        float half = pixels(center, size) * 0.8f;
        int[] pal = palette(alpha * (0.85f + 0.15f * pulse));
        float rad = (float) Math.toRadians(angle);

        glow(screen[0], screen[1], half * 1.9f, mul(pal[0], 0.30f));
        // the real marker texture: its half size in the world is `size` blocks (the quad spans -size..size)
        sprite(MARKERS[skin], 384, screen[0], screen[1], half * 2.0f / 0.8f, rad, Render2D.lerp(pal[0], pal[1], 0.5f));
    }

    private static float[] rotate(float x, float y, float c, float s, float[] origin) {
        return new float[]{origin[0] + x * c - y * s, origin[1] + x * s + y * c};
    }

    // ------------------------------------------------------------------------------------------------ ring

    private static void ring(LivingEntity target, float partial) {
        Vec3 base = position(target, partial);
        float height = target.getBbHeight();
        double wave = 0.5 - 0.5 * Math.cos(circleStep);
        double wave2 = 0.5 - 0.5 * Math.cos(circleStep - 0.35);
        smoothY += (wave * height - smoothY) * (1.0 - Math.exp(-dt * 7.5));
        smoothY2 += (wave2 * height - smoothY2) * (1.0 - Math.exp(-dt * 6.0));
        float radius = target.getBbWidth() * (1.0f + (float) Math.sin(Math.min(hurt * 2.0f, 1.0f) * Math.PI) * 0.18f);
        int[] pal = palette(alpha);
        int n = 64;
        float[][] top = new float[n + 1][];
        float[][] bottom = new float[n + 1][];
        for (int i = 0; i <= n; i++) {
            double a = i * Math.PI * 2.0 / n;
            top[i] = WorldProjection.toScreen(base.add(Math.cos(a) * radius, smoothY, Math.sin(a) * radius));
            bottom[i] = WorldProjection.toScreen(base.add(Math.cos(a) * radius, smoothY2, Math.sin(a) * radius));
        }
        int strips = 4;
        for (int s = 0; s < strips; s++) {
            float t0 = (float) s / strips;
            float t1 = (float) (s + 1) / strips;
            float fade = (float) Math.pow(1.0f - t0, 1.4) * 0.55f;
            for (int i = 0; i < n; i++) {
                if (top[i] == null || top[i + 1] == null || bottom[i] == null || bottom[i + 1] == null) {
                    continue;
                }
                float g = (float) (0.5 - 0.5 * Math.cos(i * Math.PI * 2.0 / n));
                int col = mul(Render2D.lerp(pal[0], pal[1], g), fade);
                float ax = Mth.lerp(t0, top[i][0], bottom[i][0]);
                float ay = Mth.lerp(t0, top[i][1], bottom[i][1]);
                float bx = Mth.lerp(t0, top[i + 1][0], bottom[i + 1][0]);
                float by = Mth.lerp(t0, top[i + 1][1], bottom[i + 1][1]);
                float cx = Mth.lerp(t1, top[i + 1][0], bottom[i + 1][0]);
                float cy = Mth.lerp(t1, top[i + 1][1], bottom[i + 1][1]);
                float dx = Mth.lerp(t1, top[i][0], bottom[i][0]);
                float dy = Mth.lerp(t1, top[i][1], bottom[i][1]);
                Draw.triangle(gg, ax, ay, bx, by, cx, cy, col);
                Draw.triangle(gg, ax, ay, cx, cy, dx, dy, col);
            }
        }
        for (int i = 0; i < n; i++) {
            if (top[i] == null || top[i + 1] == null) {
                continue;
            }
            float g = (float) (0.5 - 0.5 * Math.cos(i * Math.PI * 2.0 / n));
            int col = Render2D.lerp(pal[0], pal[1], g);
            Draw.line(gg, top[i][0], top[i][1], top[i + 1][0], top[i + 1][1], Math.max(1.5f, gs * 0.9f), col);
            if (i % 4 == 0) {
                glow(top[i][0], top[i][1], gs * 7.0f, mul(col, 0.5f));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ ghosts

    private static float updateGhostMotion(LivingEntity target, float partial, long time) {
        if (ghostAnchor != null && (!target.isAlive() || target.isRemoved())) {
            switching = false;
            ghostVelocity = ghostVelocity.scale(Math.exp(-dt * 6.0));
            ghostAnchor = ghostAnchor.add(ghostVelocity.scale(dt));
            flightStrength *= (float) Math.exp(-dt * 2.5);
            remember(time);
            return Mth.clamp(flightStrength, 0.0f, 1.0f);
        }
        Vec3 targetPosition = position(target, partial);
        if (ghostAnchor == null || !finite(ghostAnchor) || targetPosition.distanceTo(ghostAnchor) > 14.0) {
            ghostAnchor = targetPosition;
            ghostVelocity = Vec3.ZERO;
            ghostTracked = target;
            switching = false;
            flightFrom = null;
            flightStrength = 0.0f;
            ghostCursor = -1;
            ghostCount = 0;
            remember(time);
            return 0.0f;
        }
        if (ghostTracked != target) {
            Vec3 toTarget = targetPosition.subtract(ghostAnchor);
            double distance = toTarget.length();
            Vec3 side = new Vec3(-toTarget.z, 0.0, toTarget.x);
            if (side.lengthSqr() < 1.0E-6) {
                side = new Vec3((target.getId() & 1) == 0 ? 1.0 : -1.0, 0.0, 0.0);
            } else {
                side = side.normalize();
            }
            double direction = (target.getId() & 1) == 0 ? 1.0 : -1.0;
            flightFrom = ghostAnchor;
            flightSide = side.scale(direction);
            flightStarted = time;
            flightDuration = (long) Math.max(1150.0, Math.min(1900.0, 1050.0 + distance * 95.0));
            flightSideAmount = (float) Math.min(1.35, 0.22 + distance * 0.14);
            flightArc = (float) Math.min(1.15, 0.24 + distance * 0.09);
            switching = true;
            ghostVelocity = ghostVelocity.scale(0.2);
            ghostTracked = target;
            flightStrength = Math.max(flightStrength, 0.25f);
        }
        Vec3 motion = target.getDeltaMovement();
        double motionLength = motion.length();
        if (motionLength > 0.8) {
            motion = motion.scale(0.8 / motionLength);
        }
        double distanceBefore = targetPosition.distanceTo(ghostAnchor);
        double leadTicks = 2.1 + Math.min(distanceBefore * 0.16, 2.2);
        Vec3 desired = targetPosition.add(motion.scale(leadTicks));
        double totalStep = Math.max(0.001, Math.min(dt, 0.1));
        float switchVisual = 0.0f;
        if (switching && flightFrom != null) {
            float progress = Mth.clamp((float) (time - flightStarted) / (float) flightDuration, 0.0f, 1.0f);
            float eased = progress * progress * progress * (progress * (progress * 6.0f - 15.0f) + 10.0f);
            float arc = Mth.sin(progress * Mth.PI);
            Vec3 next = lerp(flightFrom, desired, eased).add(flightSide.scale(arc * flightSideAmount)).add(0.0, arc * flightArc, 0.0);
            ghostVelocity = next.subtract(ghostAnchor).scale(1.0 / totalStep);
            ghostAnchor = next;
            switchVisual = arc;
            if (progress >= 1.0f) {
                switching = false;
                ghostVelocity = motion.scale(20.0);
            }
        } else {
            int subSteps = Math.max(1, (int) Math.ceil(totalStep / (1.0 / 60.0)));
            double step = totalStep / subSteps;
            for (int i = 0; i < subSteps; i++) {
                Vec3 offset = desired.subtract(ghostAnchor);
                double distance = offset.length();
                double stiffness = 25.0 + Math.min(distance * 2.0, 22.0);
                double damping = 2.0 * Math.sqrt(stiffness) * 1.18;
                Vec3 acceleration = offset.scale(stiffness).subtract(ghostVelocity.scale(damping));
                ghostVelocity = ghostVelocity.add(acceleration.scale(step));
                double speed = ghostVelocity.length();
                double maxSpeed = 4.2 + Math.min(distance * 1.8, 14.0);
                if (speed > maxSpeed) {
                    ghostVelocity = ghostVelocity.scale(maxSpeed / speed);
                }
                ghostAnchor = ghostAnchor.add(ghostVelocity.scale(step));
            }
        }
        double remaining = targetPosition.distanceTo(ghostAnchor);
        double speed = ghostVelocity.length();
        float targetFlight = Mth.clamp((float) ((remaining - 0.18) / 3.8 + speed / 28.0 * 0.55), 0.0f, 1.0f);
        if (switching) {
            targetFlight = Math.max(targetFlight, 0.42f + switchVisual * 0.38f);
        }
        float response = 1.0f - (float) Math.exp(-dt * (targetFlight > flightStrength ? 5.0f : 2.4f));
        flightStrength += (targetFlight - flightStrength) * response;
        remember(time);
        return Mth.clamp(flightStrength, 0.0f, 1.0f);
    }

    private static boolean finite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    private static void remember(long time) {
        if (ghostAnchor == null) {
            return;
        }
        if (ghostCount > 0 && ghostHistoryTimes[ghostCursor] == time) {
            ghostHistory[ghostCursor] = ghostAnchor;
        } else {
            ghostCursor = (ghostCursor + 1) % 256;
            ghostHistory[ghostCursor] = ghostAnchor;
            ghostHistoryTimes[ghostCursor] = time;
            if (ghostCount < 256) {
                ghostCount++;
            }
        }
    }

    private static Vec3 sampleHistory(long sampleTime) {
        if (ghostCount <= 0 || ghostCursor < 0 || ghostAnchor == null) {
            return ghostAnchor == null ? Vec3.ZERO : ghostAnchor;
        }
        int newest = ghostCursor;
        if (sampleTime >= ghostHistoryTimes[newest]) {
            return ghostHistory[newest];
        }
        for (int i = 0; i < ghostCount - 1; i++) {
            int newer = (newest - i + 256) % 256;
            int older = (newer - 1 + 256) % 256;
            long newerTime = ghostHistoryTimes[newer];
            long olderTime = ghostHistoryTimes[older];
            if (sampleTime >= olderTime) {
                double progress = newerTime == olderTime ? 1.0 : (double) (sampleTime - olderTime) / (newerTime - olderTime);
                return lerp(ghostHistory[older], ghostHistory[newer], progress);
            }
        }
        return ghostHistory[(newest - ghostCount + 1 + 256) % 256];
    }

    private static void ghosts(LivingEntity target, float partial) {
        float flight = updateGhostMotion(target, partial, now);
        float time = (now - ghostEpoch) / 1000.0f;
        float hurtScale = 1.0f + hurt * 0.18f;
        int[] pal = palette(alpha);
        for (int lane = 0; lane < 3; lane++) {
            float laneOffset = lane * (TAU / 3.0f);
            for (int particle = 29; particle >= 0; particle--) {
                float trail = particle / 29.0f;
                long delay = (long) (particle * (18.0f + flight * 16.0f));
                float delayedTime = (now - delay - ghostEpoch) / 1000.0f;
                Vec3 base = sampleHistory(now - delay);
                float phase = delayedTime * (0.72f + flight * 0.1f) + laneOffset - trail * 0.32f;
                float radius = (0.72f + 0.1f * Mth.sin(delayedTime * 1.4f + lane)) * hurtScale * (1.0f - flight * 0.18f);
                Vec3 world = new Vec3(base.x + Mth.sin(phase) * radius,
                        base.y + target.getBbHeight() * (0.24f + lane * 0.24f)
                                + Mth.sin(delayedTime * 1.35f + particle * 0.12f + laneOffset) * (0.13f + flight * 0.05f),
                        base.z + Mth.cos(phase) * radius);
                float[] screen = WorldProjection.toScreen(world);
                if (screen == null) {
                    continue;
                }
                float pulse = 0.94f + 0.06f * Mth.sin(time * 4.2f + laneOffset - trail * 2.0f);
                float size = (0.27f - trail * 0.16f) * pulse * (1.0f + hurt * 0.22f + flight * (1.0f - trail) * 0.16f);
                float tailAlpha = 0.08f + 0.92f * (float) Math.pow(1.0f - trail, 1.45);
                int col = mul(Render2D.lerp(pal[0], pal[1], 0.5f + 0.5f * Mth.sin(phase + trail * 2.0f)), tailAlpha);
                float half = pixels(world, size);
                if (particle == 0) {
                    glow(screen[0], screen[1], half * 4.2f, mul(col, 0.35f + flight * 0.2f));
                }
                glow(screen[0], screen[1], half * 2.7f, mul(col, 0.5f));
                soft(screen[0], screen[1], half * 2.0f, mul(Render2D.lerp(col, 0xFFFFFFFF, 0.35f), 0.95f));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ constellation

    private static int constellationColor(float frac, float time, int[] pal) {
        float wave = 0.5f + 0.5f * Mth.sin(frac * TAU * 2.0f + time * 1.4f);
        return Render2D.lerp(pal[0], pal[1], wave);
    }

    private static void constellation(LivingEntity target, float partial) {
        int stars = Math.max(2, P.conStars);
        float time = (now - epoch) / 1000.0f;
        Vec3 base = position(target, partial);
        float spin = time * P.conSpeed * 1.2f;
        float orbit = P.conOrbit * target.getBbWidth();
        float halfHeight = target.getBbHeight() * 0.5f;
        Vec3[] points = new Vec3[stars];
        float[][] screen = new float[stars][];
        for (int s = 0; s < stars; s++) {
            float frac = (float) s / stars;
            float incl = (float) (Math.PI * 0.35 * Math.sin(frac * Math.PI * 1.5 + 0.5));
            float ang = spin + frac * TAU;
            float r = orbit * (0.85f + 0.15f * (float) Math.sin(frac * TAU * 2.3f));
            points[s] = new Vec3(base.x + Math.cos(ang) * r,
                    base.y + halfHeight + Math.sin(ang) * Math.sin(incl) * r * 0.7 + Math.sin(time * 1.2f + frac * TAU) * 0.2,
                    base.z + Math.sin(ang) * Math.cos(incl) * r);
            screen[s] = WorldProjection.toScreen(points[s]);
        }
        int[] pal = palette(alpha);
        if (P.conLines) {
            for (int s = 0; s < stars; s++) {
                float[] a = screen[s];
                float[] b = screen[(s + 1) % stars];
                if (a == null || b == null) {
                    continue;
                }
                int col = mul(constellationColor((float) s / stars, time, pal), 0.6f);
                Draw.line(gg, a[0], a[1], b[0], b[1], Math.max(1.5f, gs * 0.8f), col);
            }
        }
        for (int s = 0; s < stars; s++) {
            if (screen[s] == null) {
                continue;
            }
            float frac = (float) s / stars;
            int col = constellationColor(frac, time, pal);
            float pulse = 0.75f + 0.25f * Mth.sin(time * 3.0f + frac * TAU * 1.7f);
            float size = pixels(points[s], P.conPointSize * pulse * 1.4f * (1.0f + hurt * 0.2f)) * 1.6f;
            glow(screen[s][0], screen[s][1], size * 2.6f, mul(col, 0.35f * P.conGlow + 0.1f));
            sprite(STAR, 256, screen[s][0], screen[s][1], size * 2.0f, (time * 40.0f + s * 12.0f) * Mth.DEG_TO_RAD, col);
        }
    }

    // ------------------------------------------------------------------------------------------------ bloom

    private static void bloom(LivingEntity target, float partial) {
        Vec3 base = position(target, partial);
        float height = target.getBbHeight();
        float width = target.getBbWidth();
        int[] pal = palette(alpha);
        int theme = Render2D.lerp(pal[0], pal[1], 0.5f);
        int particleColor = Render2D.lerp(theme, mul(0xFFFF5569, alpha), Mth.clamp(hurt, 0.0f, 1.0f));
        double time = (System.currentTimeMillis() % 36000000L) / 1000.0 * P.bloomSpeed;
        int count = Math.min(MAX_BLOOM, Math.max(8, P.bloomCount));
        for (int i = 0; i < count; i++) {
            BloomSeed seed = SEEDS[i];
            float rawH = (float) ((seed.heightBase + time * seed.verticalSpeed * 0.22) % 1.15);
            if (rawH < 0.0f) {
                rawH += 1.15f;
            }
            float fade = 1.0f;
            if (rawH < 0.12f) {
                fade = rawH / 0.12f;
            } else if (rawH > 0.92f) {
                fade = Math.max(0.0f, (1.15f - rawH) / 0.23f);
            }
            double angle = seed.angleOffset + time * seed.orbitSpeed + Math.sin(time * seed.radialFreq + seed.pulsePhase) * 0.35;
            double radius = width * 0.65f * seed.radiusFactor + Math.sin(time * seed.radialFreq * 1.4 + seed.pulsePhase) * seed.radialAmp;
            Vec3 world = new Vec3(base.x + Math.cos(angle) * radius, base.y + rawH * height, base.z + Math.sin(angle) * radius);
            float[] screen = WorldProjection.toScreen(world);
            if (screen == null) {
                continue;
            }
            float pulse = 0.78f + 0.32f * (float) Math.sin(time * seed.pulseSpeed + seed.pulsePhase);
            float alphaFactor = fade * (0.6f + 0.4f * (float) Math.cos(time * seed.pulseSpeed + seed.pulsePhase));
            if (alphaFactor <= 0.01f) {
                continue;
            }
            float px = Mth.clamp(pixels(world, P.bloomSize * seed.sizeFactor * pulse * (1.0f + hurt * 0.2f)), 4.0f, 60.0f);
            glow(screen[0], screen[1], px * 2.6f, mul(particleColor, alphaFactor * 0.43f));
            soft(screen[0], screen[1], px * 1.6f, mul(particleColor, alphaFactor * 0.95f));
        }
    }

    // ------------------------------------------------------------------------------------------------ test

    private static void test(LivingEntity target, float partial) {
        Vec3 base = position(target, partial);
        float entityHeight = target.getBbHeight();
        float time = (now - epoch) / 1000.0f * P.testSpeed;
        float rotSpeed = time * 9.0f;
        float vertSpeed = time * 3.25f;
        int[] pal = palette(alpha);
        int theme = Render2D.lerp(pal[0], pal[1], 0.5f);
        for (int i = 0; i < 3; i++) {
            float angleOffset = i * (TAU / 3.0f);
            for (int j = 9; j >= 0; j--) {
                float trailDelay = j * 0.05f;
                float currentAngle = rotSpeed + angleOffset - j * 0.1f;
                Vec3 world = new Vec3(base.x + Math.cos(currentAngle) * P.testRadius,
                        base.y + (Math.sin(vertSpeed - trailDelay) * 0.5 + 0.5) * (entityHeight * 0.975),
                        base.z + Math.sin(currentAngle) * P.testRadius);
                float[] screen = WorldProjection.toScreen(world);
                if (screen == null) {
                    continue;
                }
                float size = Math.max(0.03f, (0.30f - j * 0.015f) * P.testSize);
                float px = Mth.clamp(pixels(world, size), 4.0f, 60.0f);
                float tailFade = Math.min(1.0f, 1.35f - j / 13.5f);
                int col = mul(theme, tailFade);
                glow(screen[0], screen[1], px * 2.6f, mul(col, 0.55f));
                soft(screen[0], screen[1], px * 1.6f, col);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ swords

    private static void swords(LivingEntity target, float partial) {
        int count = Math.max(2, Math.min(MAX_SWORDS, P.swordCount));
        double speed = Math.max(200.0, P.swordSpeed);
        Vec3 base = position(target, partial);
        double centerY = base.y + target.getBbHeight() * 0.45;
        double actualRadius = (target.getBbWidth() + 0.35) * P.swordRadius;
        if (swordLastUpdate == 0L) {
            swordLastUpdate = now;
        }
        float dtSword = Math.min(0.1f, (now - swordLastUpdate) / 1000.0f);
        swordLastUpdate = now;
        float step = Math.min(2.0f, Math.max(0.2f, dtSword * 60.0f));
        if (swordTarget != target) {
            swordTarget = target;
            swordPrevHurt = target.hurtTime;
            double initOrbit = ((double) (now % Math.max(200L, Math.round(speed))) / Math.max(200L, Math.round(speed))) * TAU;
            for (int i = 0; i < count; i++) {
                SwordState s = SWORD_STATES[i];
                double angle = initOrbit + i * (TAU / count);
                s.x = base.x + Math.cos(angle) * actualRadius;
                s.y = centerY;
                s.z = base.z + Math.sin(angle) * actualRadius;
                s.vx = s.vy = s.vz = 0.0;
                s.roll = 0.0f;
                s.pitch = 0.0f;
                s.initialized = true;
            }
        }
        double orbitAngle = ((double) (now % (long) speed) / speed) * TAU;
        int currentHurt = target.hurtTime;
        boolean justDamaged = currentHurt > swordPrevHurt && currentHurt >= 9;
        swordPrevHurt = currentHurt;
        float hurtPc = currentHurt > 0 ? (float) Math.sin(currentHurt * (Math.PI / 10.0)) : 0.0f;
        float yawRad = (float) Math.toRadians(mc.player == null ? 0.0f : mc.player.getYRot());
        int[] pal = palette(1.0f);
        for (int i = 0; i < count; i++) {
            SwordState s = SWORD_STATES[i];
            double swordAngle = orbitAngle + i * (TAU / count);
            double bob = Math.sin(now * 0.0035 + i * (Math.PI / 4.0)) * P.swordBob;
            double idealX = base.x + Math.cos(swordAngle) * actualRadius;
            double idealY = centerY + bob;
            double idealZ = base.z + Math.sin(swordAngle) * actualRadius;
            if (!s.initialized) {
                s.x = idealX;
                s.y = idealY;
                s.z = idealZ;
                s.vx = s.vy = s.vz = 0.0;
                s.roll = 0.0f;
                s.pitch = 0.0f;
                s.initialized = true;
            }
            if (justDamaged) {
                double dx = s.x - base.x;
                double dz = s.z - base.z;
                double len = Math.hypot(dx, dz);
                if (len > 0.001) {
                    double kick = 0.16 + Math.random() * 0.06;
                    s.vx += dx / len * kick;
                    s.vz += dz / len * kick;
                    s.vy += 0.07;
                }
                s.roll += (float) ((Math.random() - 0.5) * 28.0);
            }
            if (P.swordPhysics) {
                double spring = 0.22 * step;
                double damping = Math.pow(0.78, step);
                s.vx = (s.vx + (idealX - s.x) * spring) * damping;
                s.vy = (s.vy + (idealY - s.y) * spring) * damping;
                s.vz = (s.vz + (idealZ - s.z) * spring) * damping;
                s.x += s.vx * step;
                s.y += s.vy * step;
                s.z += s.vz * step;
                double camRightVel = -s.vx * Math.cos(yawRad) - s.vz * Math.sin(yawRad);
                float targetRoll = (float) Mth.clamp(camRightVel * 130.0, -42.0, 42.0);
                float targetPitch = (float) Mth.clamp(-s.vy * 85.0, -32.0, 32.0);
                s.roll = Mth.lerp(0.25f * step, s.roll, targetRoll);
                s.pitch = Mth.lerp(0.25f * step, s.pitch, targetPitch);
            } else {
                s.x = idealX;
                s.y = idealY;
                s.z = idealZ;
                s.vx = s.vy = s.vz = 0.0;
                s.roll = 0.0f;
                s.pitch = 0.0f;
            }
            swordWorld[i] = new Vec3(s.x, s.y, s.z);
            int theme = Render2D.lerp(pal[0], pal[1], 0.5f + 0.5f * (float) Math.sin(i * 0.785 + now / 450.0));
            swordColors[i] = Render2D.lerp(theme, 0xFFFF4178, Mth.clamp(hurtPc, 0.0f, 1.0f));
        }
        float halfH = 0.30f * P.swordSize;
        if (P.swordGlow) {
            for (int i = 0; i < count; i++) {
                float[] screen = WorldProjection.toScreen(swordWorld[i]);
                if (screen != null) {
                    glow(screen[0], screen[1], pixels(swordWorld[i], halfH * 2.2), mul(swordColors[i], 0.5f * alpha));
                }
            }
        }
        for (int i = 0; i < count; i++) {
            float[] screen = WorldProjection.toScreen(swordWorld[i]);
            if (screen != null) {
                float height = Mth.clamp(pixels(swordWorld[i], halfH * 2.0), 8.0f, 220.0f);
                drawSword(screen[0], screen[1], height, SWORD_STATES[i].roll, SWORD_STATES[i].pitch, swordColors[i], alpha);
            }
        }
    }

    // a 32 x 64 sprite: {x0, y0, x1, y1, r, g, b}
    private static final int[][] SWORD_RECTS = {
            {13, 6, 19, 43, 138, 148, 166}, {14, 6, 18, 43, 198, 208, 224}, {15, 6, 17, 43, 238, 245, 252},
            {7, 43, 25, 46, 214, 178, 60}, {7, 46, 25, 47, 150, 118, 40},
            {14, 47, 18, 58, 96, 62, 34}, {14, 49, 18, 50, 150, 106, 62}, {14, 52, 18, 53, 150, 106, 62}, {14, 55, 18, 56, 150, 106, 62},
            {13, 58, 19, 62, 214, 178, 60}};

    private static void drawSword(float cx, float cy, float heightPx, float rollDeg, float pitchDeg, int tint, float a) {
        float s = heightPx / 64.0f;
        float roll = (float) Math.toRadians(rollDeg);
        float c = Mth.cos(roll);
        float sn = Mth.sin(roll);
        float squash = Mth.cos((float) Math.toRadians(Mth.clamp(pitchDeg, -60.0f, 60.0f)));
        int tr = (tint >> 16) & 0xFF;
        int tg = (tint >> 8) & 0xFF;
        int tb = tint & 0xFF;
        // the tip
        int tipColor = (Math.round(Mth.clamp(a, 0.0f, 1.0f) * 255.0f) << 24) | ((238 * tr / 255) << 16) | ((245 * tg / 255) << 8) | (252 * tb / 255);
        float[][] tip = {{13.0f, 6.0f}, {19.0f, 6.0f}, {16.0f, 0.0f}};
        float[][] tp = new float[3][];
        for (int i = 0; i < 3; i++) {
            tp[i] = swordPoint(tip[i][0], tip[i][1], s, squash, c, sn, cx, cy);
        }
        Draw.triangle(gg, tp[0][0], tp[0][1], tp[1][0], tp[1][1], tp[2][0], tp[2][1], tipColor);
        for (int[] r : SWORD_RECTS) {
            int color = (Math.round(Mth.clamp(a, 0.0f, 1.0f) * 255.0f) << 24) | ((r[4] * tr / 255) << 16) | ((r[5] * tg / 255) << 8) | (r[6] * tb / 255);
            float[] p0 = swordPoint(r[0], r[1], s, squash, c, sn, cx, cy);
            float[] p1 = swordPoint(r[2], r[1], s, squash, c, sn, cx, cy);
            float[] p2 = swordPoint(r[2], r[3], s, squash, c, sn, cx, cy);
            float[] p3 = swordPoint(r[0], r[3], s, squash, c, sn, cx, cy);
            Draw.triangle(gg, p0[0], p0[1], p1[0], p1[1], p2[0], p2[1], color);
            Draw.triangle(gg, p0[0], p0[1], p2[0], p2[1], p3[0], p3[1], color);
        }
    }

    private static float[] swordPoint(float x, float y, float s, float squash, float c, float sn, float cx, float cy) {
        float dx = (x - 16.0f) * s;
        float dy = (y - 32.0f) * s * squash;
        return new float[]{cx + dx * c - dy * sn, cy + dx * sn + dy * c};
    }
}
