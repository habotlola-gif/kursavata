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
 * "Helix Wave": the head is pulled toward the upper body and a helix (a circle in yaw/pitch) is wound on top of that
 * pull. It is a reworked version of the forum script HelixWave (a fixed 68 % pull per call plus two sine waves of
 * +-20 degrees). What was changed:
 * <ul>
 *   <li>the pull is time based (exponential), so it behaves the same at 30 and at 300 FPS;</li>
 *   <li>the wave size is a fraction of the target's hitbox as seen on screen, so the crosshair keeps travelling
 *       over the body and the ray check still passes (a fixed 14 degrees would leave a player at 3 blocks, whose
 *       hitbox is only about 6 degrees wide, most of the time);</li>
 *   <li>two waves with a ratio that never repeats, a drifting frequency and a slowly changing size, so the motion has
 *       no clean period that a spectrum check could pick out;</li>
 *   <li>the wave calms down while a hit is ready, hand tremor, a reaction delay on a new target and a limit on the
 *       turning speed.</li>
 * </ul>
 * Runs once per rendered frame.
 */
public final class HelixRotation {
    private static final float PULL_RATE = 16.0F;          // 1/s at "Pull speed" 1.0
    private static final float MAX_SPEED = 720.0F;         // degrees per second

    private static LivingEntity currentTarget;
    private static LivingEntity pendingTarget;
    private static boolean lastCanAttack;
    private static boolean aimed;

    private static long lastFrameNanos;
    private static long reactUntilMs;

    // the wave
    private static float phaseA;
    private static float phaseB;
    private static float freqDrift;        // slow random change of the frequency, -1..1
    private static float sizeDrift;        // slow random change of the size, 0..1
    private static float calm = 1.0F;      // 1 = full wave, lower while a hit is ready
    private static float tremYaw;
    private static float tremPitch;

    private HelixRotation() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static void reshuffleAll() {
        forget();
        phaseA = ThreadLocalRandom.current().nextFloat() * Mth.TWO_PI;
        phaseB = ThreadLocalRandom.current().nextFloat() * Mth.TWO_PI;
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
        if (aura == null || !aura.isEnabled() || !AttackAura.helixRotation() || pendingTarget == null) {
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
            // a person needs a moment to notice a new target
            reactUntilMs = System.currentTimeMillis() + 90L + rnd.nextInt(120);
        }

        // ---------------------------------------------------------------- slow random drifts and tremor
        float k = (float) Math.sqrt(dt);
        freqDrift = Mth.clamp(freqDrift - freqDrift * dt * 0.8F + (float) rnd.nextGaussian() * k * 0.9F, -1.0F, 1.0F);
        sizeDrift = Mth.clamp(sizeDrift - (sizeDrift - 0.7F) * dt * 0.6F + (float) rnd.nextGaussian() * k * 0.5F, 0.35F, 1.0F);
        tremYaw = Mth.clamp(tremYaw - tremYaw * dt * 4.0F + (float) rnd.nextGaussian() * k * 0.5F, -0.4F, 0.4F);
        tremPitch = Mth.clamp(tremPitch - tremPitch * dt * 5.0F + (float) rnd.nextGaussian() * k * 0.3F, -0.25F, 0.25F);

        // ---------------------------------------------------------------- the helix
        float speed = AttackAura.helixSpeed();
        float omega = 7.0F * speed * (1.0F + 0.25F * freqDrift);          // rad/s, about 1.1 turns per second
        phaseA += omega * dt;
        phaseB += omega * 0.47F * dt;                                      // a ratio that never closes
        phaseA %= Mth.TWO_PI;
        phaseB %= Mth.TWO_PI;
        boolean ready = mc.player.getAttackStrengthScale(0.5F) > 0.9F && lastCanAttack;
        calm += ((ready ? 0.45F : 1.0F) - calm) * Math.min(1.0F, dt * 8.0F);

        Vec3 eye = mc.player.getEyePosition();
        AABB box = entity.getBoundingBox();
        double distance = Math.max(0.4, eye.distanceTo(box.getCenter()));
        float halfWidth = (float) Math.toDegrees(Math.atan2(Math.min(box.getXsize(), box.getZsize()) * 0.5, distance));
        float halfHeight = (float) Math.toDegrees(Math.atan2(box.getYsize() * 0.5, distance));
        float size = AttackAura.helixSize() * calm * sizeDrift;
        float yawWave = halfWidth * 0.8F * size * (0.75F * Mth.sin(phaseA) + 0.25F * Mth.sin(phaseB));
        float pitchWave = halfHeight * 0.30F * size * (0.75F * Mth.cos(phaseA) + 0.25F * Mth.cos(phaseB));

        // ---------------------------------------------------------------- the pull toward the upper body
        double base = mc.player.onGround() ? 0.62 : 0.50;
        Vec3 motion = entity.getDeltaMovement();
        Vec3 point = new Vec3(entity.getX() + motion.x * 1.2, box.minY + box.getYsize() * base, entity.getZ() + motion.z * 1.2);
        double dx = point.x - mc.player.getX();
        double dy = point.y - eye.y;
        double dz = point.z - mc.player.getZ();
        float aimYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0) + yawWave + tremYaw;
        float aimPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))) + pitchWave + tremPitch;

        float curYaw = mc.player.getYRot();
        float curPitch = mc.player.getXRot();
        float deltaYaw = Mth.wrapDegrees(aimYaw - curYaw);
        float deltaPitch = aimPitch - curPitch;

        float stepYaw = 0.0F;
        float stepPitch = 0.0F;
        if (System.currentTimeMillis() >= reactUntilMs) {
            float follow = 1.0F - (float) Math.exp(-dt * PULL_RATE * AttackAura.helixPull());
            float cap = MAX_SPEED * AttackAura.helixPull() * dt;
            stepYaw = Mth.clamp(deltaYaw * follow, -cap, cap);
            stepPitch = Mth.clamp(deltaPitch * follow * 0.8F, -cap * 0.5F, cap * 0.5F);
        }
        RotationProcess.updateFrame(new Rotation(curYaw + stepYaw, Mth.clamp(curPitch + stepPitch, -90.0F, 90.0F)),
                60.0F, 60.0F, 60.0F, 60.0F, 10, 1);

        // ---------------------------------------------------------------- can we hit from here
        Vec3 end = eye.add(RayTraceUtil.getVectorForRotation(mc.player.getXRot(), mc.player.getYRot())
                .scale(AttackAura.attackRange() + 0.5));
        aimed = box.clip(eye, end).isPresent();
    }
}
