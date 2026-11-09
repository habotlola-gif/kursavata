package dev.hatek.client.module.impl.combat.aura.rotation;

import dev.hatek.client.module.impl.combat.AttackAura;
import dev.hatek.client.module.impl.combat.aura.util.RayTraceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.concurrent.ThreadLocalRandom;

/**
 * "Shard Rotation": a rotation built from the notes about the Shard anticheat (forum tutorial of the 2nd league):
 * <ul>
 *   <li><b>dynamic, laggy speed</b>: the head moves with a speed that is re-rolled every 30-120 ms; now and then the
 *       yaw freezes completely, and as soon as the head is 5 degrees off the hitbox it jumps back at once;</li>
 *   <li><b>fast, huge multipoints</b>: the aim point changes every 40-160 ms, the turning speed limits are drawn per
 *       point (yaw 15-36, pitch 4-24 degrees per tick, in steps of 0.5);</li>
 *   <li><b>different body parts</b>: belly/chest for a while, then the head for a short time, then the belly again;</li>
 *   <li><b>no repeated spots</b>: a new point is never close to one of the last nine;</li>
 *   <li><b>look-aways</b>: at random moments the head leaves the hitbox for 10-200 ms and comes back.</li>
 * </ul>
 * Runs once per rendered frame. The attack itself waits for the ray to hit the hitbox, so the look-aways cost a few
 * swings, which is exactly their purpose.
 */
public final class ShardRotation {
    private enum Phase { TRACK, LAG, AWAY }

    private static final int RECENT = 9;
    private static final float MIN_SPOT_DISTANCE = 0.24F;

    private static LivingEntity currentTarget;
    private static LivingEntity pendingTarget;
    private static boolean lastCanAttack;
    private static boolean aimed;

    private static long lastFrameNanos;
    private static long reactUntilMs;

    private static Phase phase = Phase.TRACK;
    private static float phaseLeft;
    private static float nextLagIn = 0.4F;
    private static float awayYaw;
    private static float awayPitch;

    private static boolean headRegion;     // false = belly / chest, true = head
    private static float regionLeft;
    private static float spotU;            // across the body, -1..1 of the half width
    private static float spotV;            // height, 0..1 of the box
    private static float spotLeft;
    private static float yawCap = 24.0F;   // degrees per tick
    private static float pitchCap = 12.0F;
    private static float speedMul = 1.0F;
    private static float speedLeft;
    private static final ArrayDeque<float[]> RECENT_SPOTS = new ArrayDeque<>();

    private static float tremYaw;
    private static float tremPitch;

    private ShardRotation() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static void reshuffleAll() {
        forget();
        RECENT_SPOTS.clear();
    }

    public static void forget() {
        currentTarget = null;
        pendingTarget = null;
        aimed = false;
        lastFrameNanos = 0L;
        phase = Phase.TRACK;
    }

    public static boolean aimed() {
        return aimed;
    }

    public static void onRotation(LivingEntity target, boolean canAttack) {
        pendingTarget = target;
        lastCanAttack = canAttack;
    }

    private static float range(ThreadLocalRandom rnd, float min, float max) {
        return min + rnd.nextFloat() * (max - min);
    }

    /** Called once per rendered frame. */
    public static void onFrame() {
        AttackAura aura = AttackAura.instance();
        if (aura == null || !aura.isEnabled() || !AttackAura.shardRotation() || pendingTarget == null) {
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
        float multi = AttackAura.shardSpeed();
        float[] tune = AttackAura.shardTuning();

        if (currentTarget != entity) {
            currentTarget = entity;
            reactUntilMs = System.currentTimeMillis() + 80L + rnd.nextInt(110);
            phase = Phase.TRACK;
            spotLeft = 0.0F;
            regionLeft = 0.0F;
        }

        // ---------------------------------------------------------------- the body part: belly -> head -> belly
        regionLeft -= dt;
        if (regionLeft <= 0.0F) {
            headRegion = !headRegion;
            regionLeft = headRegion ? range(rnd, 0.25F, 0.9F) : range(rnd, 0.9F, 2.2F);
            spotLeft = 0.0F;
        }

        // ---------------------------------------------------------------- multipoint: a new spot every 40-160 ms
        spotLeft -= dt;
        if (spotLeft <= 0.0F) {
            spotLeft = range(rnd, 0.04F, 0.16F) / multi;
            float bestU = 0.0F;
            float bestV = 0.5F;
            float bestScore = -1.0F;
            for (int attempt = 0; attempt < 8; attempt++) {
                float u = range(rnd, -0.85F, 0.85F);
                float v = headRegion ? range(rnd, 0.84F, 0.96F)
                        : (rnd.nextFloat() < 0.12F ? range(rnd, 0.28F, 0.42F) : range(rnd, 0.44F, 0.76F));
                float nearest = 9.0F;
                for (float[] old : RECENT_SPOTS) {
                    nearest = Math.min(nearest, (float) Math.hypot(u - old[0], (v - old[1]) * 1.6F));
                }
                if (nearest > bestScore) {
                    bestScore = nearest;
                    bestU = u;
                    bestV = v;
                }
                if (nearest >= MIN_SPOT_DISTANCE) {
                    break;
                }
            }
            spotU = bestU;
            spotV = bestV;
            RECENT_SPOTS.addLast(new float[]{spotU, spotV});
            while (RECENT_SPOTS.size() > RECENT) {
                RECENT_SPOTS.removeFirst();
            }
            // turning speed limits of this point, in steps of 0.5 degree per tick
            yawCap = Math.round(range(rnd, tune[0], tune[1]) * 2.0F) / 2.0F;
            pitchCap = Math.round(range(rnd, tune[2], tune[3]) * 2.0F) / 2.0F;
        }

        // ---------------------------------------------------------------- dynamic speed
        speedLeft -= dt;
        if (speedLeft <= 0.0F) {
            speedLeft = range(rnd, 0.03F, 0.12F);
            float r = rnd.nextFloat();
            speedMul = 0.35F + 1.45F * r * r * 0.6F + 0.6F * rnd.nextFloat();
        }
        float k = (float) Math.sqrt(dt);
        tremYaw = Mth.clamp(tremYaw - tremYaw * dt * 4.0F + (float) rnd.nextGaussian() * k * 0.5F, -0.4F, 0.4F);
        tremPitch = Mth.clamp(tremPitch - tremPitch * dt * 5.0F + (float) rnd.nextGaussian() * k * 0.3F, -0.25F, 0.25F);

        // ---------------------------------------------------------------- geometry
        Vec3 eye = mc.player.getEyePosition();
        AABB box = entity.getBoundingBox();
        double dx0 = entity.getX() - mc.player.getX();
        double dz0 = entity.getZ() - mc.player.getZ();
        double length = Math.hypot(dx0, dz0);
        double acrossX = length < 1.0E-4 ? 1.0 : -dz0 / length;
        double acrossZ = length < 1.0E-4 ? 0.0 : dx0 / length;
        double halfBody = Math.max(0.1, Math.min(box.getXsize(), box.getZsize()) * 0.5 - 0.06);
        Vec3 motion = entity.getDeltaMovement();
        Vec3 point = new Vec3(entity.getX() + acrossX * spotU * halfBody + motion.x * 1.2,
                box.minY + box.getYsize() * spotV,
                entity.getZ() + acrossZ * spotU * halfBody + motion.z * 1.2);
        double dx = point.x - mc.player.getX();
        double dy = point.y - eye.y;
        double dz = point.z - mc.player.getZ();
        float aimYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0) + tremYaw;
        float aimPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))) + tremPitch;

        double distance = Math.max(0.4, eye.distanceTo(box.getCenter()));
        float halfWidthDeg = (float) Math.toDegrees(Math.atan2(Math.min(box.getXsize(), box.getZsize()) * 0.5, distance));
        float halfHeightDeg = (float) Math.toDegrees(Math.atan2(box.getYsize() * 0.5, distance));

        float curYaw = mc.player.getYRot();
        float curPitch = mc.player.getXRot();
        boolean ready = mc.player.getAttackStrengthScale(0.5F) > 0.9F && lastCanAttack;

        // ---------------------------------------------------------------- phases: track / lag / look away
        phaseLeft -= dt;
        nextLagIn -= dt;
        if (phase == Phase.TRACK) {
            float awayRate = AttackAura.shardAway() * (mc.player.getAttackStrengthScale(0.5F) < 0.5F ? 3.0F : 1.0F);
            if (!ready && rnd.nextFloat() < awayRate * dt) {
                phase = Phase.AWAY;
                phaseLeft = (10.0F + Math.max(0.0F, tune[5] - 10.0F) * (float) Math.pow(rnd.nextFloat(), 1.4)) / 1000.0F;
                awayYaw = (rnd.nextBoolean() ? 1.0F : -1.0F) * range(rnd, 10.0F, 45.0F);
                awayPitch = (rnd.nextBoolean() ? 1.0F : -1.0F) * range(rnd, 5.0F, 20.0F);
            } else if (nextLagIn <= 0.0F) {
                phase = Phase.LAG;
                phaseLeft = range(rnd, 0.04F, Math.max(0.05F, tune[4] / 1000.0F));
                nextLagIn = range(rnd, 0.25F, 0.9F);
            }
        } else if (phaseLeft <= 0.0F) {
            phase = Phase.TRACK;
        }

        float stepYaw = 0.0F;
        float stepPitch = 0.0F;
        boolean waiting = System.currentTimeMillis() < reactUntilMs;
        if (!waiting) {
            float follow = 1.0F - (float) Math.exp(-dt * 38.0F * speedMul);
            float yawLimit = yawCap * 20.0F * dt;
            float pitchLimit = pitchCap * 20.0F * dt;
            switch (phase) {
                case TRACK -> {
                    stepYaw = Mth.clamp(Mth.wrapDegrees(aimYaw - curYaw) * follow, -yawLimit, yawLimit);
                    stepPitch = Mth.clamp((aimPitch - curPitch) * follow, -pitchLimit, pitchLimit);
                }
                case LAG -> {
                    // yaw is frozen completely, pitch only crawls; 5 degrees off the hitbox and it is back at once
                    float offYaw = Math.max(0.0F, Math.abs(Mth.wrapDegrees(aimYaw - curYaw)) - halfWidthDeg);
                    float offPitch = Math.max(0.0F, Math.abs(aimPitch - curPitch) - halfHeightDeg);
                    if (Math.hypot(offYaw, offPitch) > 5.0) {
                        phase = Phase.TRACK;
                        stepYaw = Mth.wrapDegrees(aimYaw - curYaw);
                        stepPitch = aimPitch - curPitch;
                    } else {
                        stepPitch = Mth.clamp((aimPitch - curPitch) * follow * 0.3F, -pitchLimit, pitchLimit);
                    }
                }
                case AWAY -> {
                    float toYaw = Mth.wrapDegrees(aimYaw + awayYaw - curYaw);
                    float toPitch = aimPitch + awayPitch - curPitch;
                    float fast = 1.0F - (float) Math.exp(-dt * 45.0F);
                    stepYaw = toYaw * fast;
                    stepPitch = toPitch * fast;
                }
            }
        }
        RotationProcess.updateFrame(new Rotation(curYaw + stepYaw, Mth.clamp(curPitch + stepPitch, -90.0F, 90.0F)),
                90.0F, 90.0F, 90.0F, 90.0F, 10, 1);

        // ---------------------------------------------------------------- can we hit from here
        Vec3 end = eye.add(RayTraceUtil.getVectorForRotation(mc.player.getXRot(), mc.player.getYRot())
                .scale(AttackAura.attackRange() + 0.5));
        aimed = phase != Phase.AWAY && box.clip(eye, end).isPresent();
    }
}
