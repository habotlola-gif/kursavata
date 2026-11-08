package dev.hatek.client.module.impl.combat.aura.rotation;

import dev.hatek.client.module.impl.combat.AttackAura;
import dev.hatek.client.module.impl.combat.aura.util.RayTraceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * "Smooth Rotation": a head that moves in bursts. The speed alternates between quick flicks and slow drifting (both
 * blended smoothly, nothing snaps), and the aim point walks around inside the target's hitbox along a pattern
 * (multipoint): Normal, Swap, Circle, Figure 8, Zigzag or Random shapes. Runs once per rendered frame and is
 * independent of the frame rate.
 */
public final class SmoothRotation {
    public static final String NORMAL = "Normal";
    public static final String SWAP = "Swap";
    public static final String CIRCLE = "Circle";
    public static final String FIGURE_8 = "Figure 8";
    public static final String ZIGZAG = "Zigzag";
    public static final String RANDOM = "Random shapes";

    private static LivingEntity currentTarget;
    private static LivingEntity pendingTarget;
    private static boolean lastCanAttack;
    private static boolean aimed;

    private static long lastFrameNanos;
    private static float velYaw;
    private static float velPitch;

    // human behaviour: reaction time, hand tremor, a slowly changing aim spot
    private static long reactUntilMs;
    private static float tremYaw;
    private static float tremPitch;
    private static float spotU;
    private static float spotV;
    private static float spotTargetU;
    private static float spotTargetV;
    private static float spotLeft;

    // jerky speed
    private static boolean flicking;
    private static float phaseLeft;
    private static float speedMul = 0.3F;

    // pattern
    private static float patternPhase;
    private static boolean swapSecond;
    private static float swapLeft;
    private static float swapU;
    private static float swapV;
    private static float shapeLeft;
    private static float shapeA = 1.0F;
    private static float shapeB = 2.0F;
    private static float shapeShift;
    private static float noiseU;
    private static float noiseV;

    private SmoothRotation() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static void reshuffleAll() {
        forget();
        lastFrameNanos = 0L;
        velYaw = 0.0F;
        velPitch = 0.0F;
    }

    public static void forget() {
        currentTarget = null;
        pendingTarget = null;
        aimed = false;
        lastFrameNanos = 0L;
    }

    public static boolean aimed() {
        return aimed;
    }

    public static void onRotation(LivingEntity target, boolean canAttack) {
        pendingTarget = target;
        lastCanAttack = canAttack;
    }

    /** Called once per rendered frame. */
    public static void onFrame() {
        AttackAura aura = AttackAura.instance();
        if (aura == null || !aura.isEnabled() || !AttackAura.smoothRotation() || pendingTarget == null) {
            return;
        }
        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (mc.player.isBlocking()) {
            lastFrameNanos = 0L;
            return;
        }
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0.016F : Mth.clamp((now - lastFrameNanos) / 1.0E9F, 0.001F, 0.05F);
        lastFrameNanos = now;
        LivingEntity entity = pendingTarget;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        if (currentTarget != entity) {
            currentTarget = entity;
            velYaw = 0.0F;
            velPitch = 0.0F;
            patternPhase = rnd.nextFloat() * Mth.TWO_PI;
            swapSecond = rnd.nextBoolean();
            swapLeft = 0.0F;
            shapeLeft = 0.0F;
            phaseLeft = 0.0F;
            flicking = true;
            // a person needs a moment to notice a new target before the head starts moving
            reactUntilMs = System.currentTimeMillis() + 90L + rnd.nextInt(110);
            spotLeft = 0.0F;
        }
        updateHumanNoise(dt, rnd);

        // ---------------------------------------------------------------- aim point (multipoint)
        float[] aim = anglesTo(aimPoint(entity, dt, rnd));
        aim[0] += tremYaw;
        aim[1] += tremPitch;
        float curYaw = mc.player.getYRot();
        float curPitch = mc.player.getXRot();
        float deltaYaw = Mth.wrapDegrees(aim[0] - curYaw);
        float deltaPitch = aim[1] - curPitch;

        // ---------------------------------------------------------------- jerky speed
        float jerk = AttackAura.smoothJerk();
        boolean ready = mc.player.getAttackStrengthScale(0.5F) > 0.9F && lastCanAttack;
        phaseLeft -= dt;
        if (phaseLeft <= 0.0F) {
            flicking = !flicking;
            phaseLeft = flicking ? 0.06F + rnd.nextFloat() * 0.10F : 0.08F + rnd.nextFloat() * 0.22F;
            // bigger jerk: slower drift between flicks
        }
        float driftLevel = Mth.lerp(jerk, 0.55F, 0.10F);
        float wanted = flicking ? 1.0F : driftLevel;
        if (ready && !aimed) {
            wanted = Math.max(wanted, 0.85F);   // a flick to the target right before a hit
        }
        speedMul += (wanted - speedMul) * Math.min(1.0F, dt * 26.0F);

        float speed = AttackAura.smoothSpeed();
        float rate = 14.0F * speed * (0.2F + 0.8F * speedMul);              // fraction of the remaining angle per second
        float cap = 650.0F * speed * (0.25F + 0.75F * speedMul);            // degrees per second
        float wantYaw = Mth.clamp(deltaYaw * rate, -cap, cap);
        float wantPitch = Mth.clamp(deltaPitch * rate * 0.7F, -cap * 0.4F, cap * 0.4F);
        if (System.currentTimeMillis() < reactUntilMs) {
            wantYaw = 0.0F;
            wantPitch = 0.0F;
        }
        // the head cannot change speed instantly: limited angular acceleration (a little different every frame)
        float accel = (3400.0F + rnd.nextFloat() * 1600.0F) * speed;
        velYaw += Mth.clamp(wantYaw - velYaw, -accel * dt, accel * dt);
        velPitch += Mth.clamp(wantPitch - velPitch, -accel * 0.6F * dt, accel * 0.6F * dt);

        float stepYaw = velYaw * dt;
        float stepPitch = velPitch * dt;
        // a small overshoot of the aim point is allowed (people do it); pitch never overshoots
        float overshoot = 3.0F;
        if (Math.abs(deltaYaw) < 0.3F && Math.abs(velYaw) < 8.0F) {
            stepYaw = deltaYaw;
        } else if (Math.signum(stepYaw) == Math.signum(deltaYaw) && Math.abs(stepYaw) > Math.abs(deltaYaw) + overshoot) {
            stepYaw = deltaYaw + Math.signum(deltaYaw) * overshoot;
        }
        if (Math.abs(stepPitch) > Math.abs(deltaPitch)) {
            stepPitch = deltaPitch;
        }
        float newYaw = curYaw + stepYaw;
        float newPitch = Mth.clamp(curPitch + stepPitch, -90.0F, 90.0F);
        RotationProcess.updateFrame(new Rotation(newYaw, newPitch), 60.0F, 60.0F, 60.0F, 60.0F, 10, 1);

        // ---------------------------------------------------------------- can we hit from here
        float sentYaw = mc.player.getYRot();
        float sentPitch = mc.player.getXRot();
        float epsilon = Math.max(3.5F, AttackAura.targetAngularHalf() * 0.55F);
        aimed = (Math.abs(Mth.wrapDegrees(aim[0] - sentYaw)) <= epsilon && Math.abs(aim[1] - sentPitch) <= epsilon)
                || hits(sentYaw, sentPitch, entity.getBoundingBox());
    }

    /** Hand tremor (slow filtered noise) and the aim spot that wanders a little inside the hitbox every second or so. */
    private static void updateHumanNoise(float dt, ThreadLocalRandom rnd) {
        float k = (float) Math.sqrt(dt);
        tremYaw += -tremYaw * dt * 4.0F + (float) rnd.nextGaussian() * k * 0.55F;
        tremPitch += -tremPitch * dt * 5.0F + (float) rnd.nextGaussian() * k * 0.32F;
        tremYaw = Mth.clamp(tremYaw, -0.45F, 0.45F);
        tremPitch = Mth.clamp(tremPitch, -0.3F, 0.3F);
        spotLeft -= dt;
        if (spotLeft <= 0.0F) {
            spotLeft = 0.5F + rnd.nextFloat() * 1.0F;
            spotTargetU = (rnd.nextFloat() * 2.0F - 1.0F) * 0.55F;
            spotTargetV = (rnd.nextFloat() * 2.0F - 1.0F) * 0.5F;
        }
        float blend = Math.min(1.0F, dt * 2.2F);
        spotU += (spotTargetU - spotU) * blend;
        spotV += (spotTargetV - spotV) * blend;
    }

    // ------------------------------------------------------------------------------------------------ pattern

    /** Offset on the hitbox in units of "half size": u across (sideways), v up/down, both in [-1, 1]. */
    private static void pattern(String name, float dt, ThreadLocalRandom rnd, float[] out) {
        float speed = AttackAura.smoothPatternSpeed();
        patternPhase += dt * speed * Mth.TWO_PI * 0.45F;
        float t = patternPhase;
        switch (name) {
            case SWAP -> {
                swapLeft -= dt;
                if (swapLeft <= 0.0F) {
                    swapSecond = !swapSecond;
                    swapLeft = (0.45F + rnd.nextFloat() * 0.7F) / Math.max(0.3F, speed);
                    float side = swapSecond ? 1.0F : -1.0F;
                    swapU = side * (0.45F + rnd.nextFloat() * 0.4F);
                    swapV = -side * (0.15F + rnd.nextFloat() * 0.5F);
                }
                out[0] = swapU;
                out[1] = swapV;
            }
            case CIRCLE -> {
                out[0] = Mth.cos(t);
                out[1] = Mth.sin(t);
            }
            case FIGURE_8 -> {
                out[0] = Mth.sin(t);
                out[1] = Mth.sin(t * 2.0F) * 0.6F;
            }
            case ZIGZAG -> {
                out[0] = triangle(t * 0.9F);
                out[1] = triangle(t * 2.3F + 0.7F) * 0.8F;
            }
            case RANDOM -> {
                shapeLeft -= dt;
                if (shapeLeft <= 0.0F) {
                    // a new random figure every few seconds (Lissajous curve with random ratios)
                    shapeLeft = 2.5F + rnd.nextFloat() * 3.0F;
                    shapeA = 1 + rnd.nextInt(3);
                    shapeB = 2 + rnd.nextInt(3);
                    shapeShift = rnd.nextFloat() * Mth.TWO_PI;
                }
                out[0] = Mth.sin(t * shapeA + shapeShift);
                out[1] = Mth.sin(t * shapeB) * 0.8F;
            }
            default -> {
                out[0] = spotU;
                out[1] = spotV;
            }
        }
        // a little slow wandering on top of every pattern so it is never mechanical
        noiseU += (rnd.nextFloat() - 0.5F) * dt * 3.0F;
        noiseV += (rnd.nextFloat() - 0.5F) * dt * 3.0F;
        noiseU = Mth.clamp(noiseU * (1.0F - dt * 0.8F), -0.25F, 0.25F);
        noiseV = Mth.clamp(noiseV * (1.0F - dt * 0.8F), -0.25F, 0.25F);
        out[0] += noiseU;
        out[1] += noiseV;
    }

    private static float triangle(float x) {
        float f = x / Mth.TWO_PI;
        f -= (float) Math.floor(f);
        return 4.0F * Math.abs(f - 0.5F) - 1.0F;
    }

    private static Vec3 aimPoint(LivingEntity entity, float dt, ThreadLocalRandom rnd) {
        Minecraft mc = mc();
        AABB box = entity.getBoundingBox();
        float[] uv = new float[2];
        pattern(AttackAura.smoothPattern(), dt, rnd, uv);
        float size = AttackAura.smoothPatternSize();

        double width = Math.max(0.1, Math.min(box.getXsize(), box.getZsize()) * 0.5 - 0.06);
        double height = box.getYsize();

        // across = horizontal direction perpendicular to the way we look at the target
        double dx = entity.getX() - mc.player.getX();
        double dz = entity.getZ() - mc.player.getZ();
        double length = Math.hypot(dx, dz);
        double acrossX = length < 1.0E-4 ? 1.0 : -dz / length;
        double acrossZ = length < 1.0E-4 ? 0.0 : dx / length;

        // the base height is the upper body; in the air the aim goes a little lower
        double base = mc.player.onGround() ? 0.62 : 0.50;
        if (AttackAura.smoothLowerInAir() && !mc.player.onGround()) {
            base = 0.46;
        }
        double halfRange = height * 0.30 * size;
        double centerY = box.minY + height * base;
        double y = Mth.clamp(centerY + uv[1] * halfRange, box.minY + height * 0.18, box.maxY - height * 0.08);
        double side = uv[0] * width * size;

        Vec3 motion = entity.getDeltaMovement();
        return new Vec3(entity.getX() + acrossX * side + motion.x * 1.2, y, entity.getZ() + acrossZ * side + motion.z * 1.2);
    }

    private static float[] anglesTo(Vec3 point) {
        Minecraft mc = mc();
        double dx = point.x - mc.player.getX();
        double dy = point.y - mc.player.getEyeY();
        double dz = point.z - mc.player.getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        return new float[]{yaw, pitch};
    }

    private static boolean hits(float yaw, float pitch, AABB box) {
        Minecraft mc = mc();
        Vec3 eye = mc.player.getEyePosition();
        double reach = AttackAura.attackRange() + 0.5;
        Vec3 end = eye.add(RayTraceUtil.getVectorForRotation(pitch, yaw).scale(reach));
        return box.clip(eye, end).isPresent();
    }
}
