package dev.hatek.client.module.impl.combat.aura.rotation;

import dev.hatek.client.module.impl.combat.AttackAura;
import dev.hatek.client.module.impl.combat.aura.util.GCDUtil;
import dev.hatek.client.module.impl.combat.aura.util.PlayerStats;
import dev.hatek.client.module.impl.combat.aura.util.RayTraceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

public final class SpaceTime {
    private static LivingEntity currentTarget;

    private static boolean syncedOnce;
    private static float serverYaw;
    private static float serverPitch;
    private static float speedOsc;
    private static boolean accelerating;
    private static float noiseSm;
    private static int tickCounter;
    private static float accelPeak;
    private static float decelFloor;
    private static float peakMul;
    private static float internalYaw;
    private static float internalPitch;
    private static float velYawSm;
    private static float velPitchSm;
    private static boolean initialized;
    private static int pauseTicks;
    private static float activity = 1.0F;
    private static float yawJitterSm;
    private static float pitchJitterSm;
    private static float prevAppliedYawVel;
    private static float prevAppliedPitchVel;
    private static float lastYawAccel;
    private static float drift;
    private static final int[] PERIODS = new int[]{16, 21};
    private static int periodIndex;
    private static long periodExpiry;
    private static float pendingOvershootYaw;
    private static float pendingOvershootPitch;
    private static int overshootCountdown = -1;

    private static Vec3 wanderOffset = Vec3.ZERO;
    private static Vec3 wanderVel = Vec3.ZERO;

    private static boolean lastCanAttack;

    private static LivingEntity pendingTarget;

    private static float prevAimYaw;
    private static float prevAimPitch;
    private static boolean haveAim;

    private static float prevBreathYaw;
    private static float prevBreathPitch;

    private static boolean aimed;

    private static final float AIMED_EPSILON = 3.5F;

    private static final float LEAD_CAP_YAW = 10.0F;
    private static final float LEAD_CAP_PITCH = 6.0F;

    private static final Multiplier MULTIPLIER = new Multiplier();
    private static final AttackTracker TRACKER = new AttackTracker();

    static {
        reshuffleAll();
    }

    private SpaceTime() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static void reshuffleAll() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        currentTarget = null;
        speedOsc = 0.0F;
        accelerating = false;
        noiseSm = 0.0F;
        tickCounter = 0;
        initialized = false;
        internalYaw = 0.0F;
        internalPitch = 0.0F;
        velYawSm = 0.0F;
        velPitchSm = 0.0F;
        prevAppliedYawVel = 0.0F;
        prevAppliedPitchVel = 0.0F;
        pauseTicks = 0;
        activity = 1.0F;
        yawJitterSm = 0.0F;
        pitchJitterSm = 0.0F;
        lastYawAccel = 0.0F;
        pendingOvershootYaw = 0.0F;
        pendingOvershootPitch = 0.0F;
        overshootCountdown = -1;
        pendingTarget = null;
        haveAim = false;
        prevBreathYaw = 0.0F;
        prevBreathPitch = 0.0F;
        wanderOffset = Vec3.ZERO;
        wanderVel = Vec3.ZERO;
        aimed = false;
        TRACKER.reset();
        drift = (rnd.nextBoolean() ? 1.0F : -1.0F) * (0.001F + rnd.nextFloat() * 4.0E-4F);
        accelPeak = 0.18F + rnd.nextFloat() * 0.1F;
        decelFloor = -0.02F - rnd.nextFloat() * 0.05F;
        peakMul = 0.8F + rnd.nextFloat() * 0.4F;
        reshufflePeriods();

        Minecraft mc = mc();
        syncedOnce = mc.player != null;
        if (mc.player != null) {
            serverYaw = mc.player.getYRot();
            serverPitch = mc.player.getXRot();
        } else {
            serverYaw = 0.0F;
            serverPitch = 0.0F;
        }
    }

    public static boolean aimed() {
        return aimed;
    }

    public static void forget() {
        currentTarget = null;
        pendingTarget = null;
        initialized = false;
        aimed = false;
        haveAim = false;
        prevBreathYaw = 0.0F;
        prevBreathPitch = 0.0F;
    }

    public static void onRotation(LivingEntity target, boolean canAttack) {
        lastCanAttack = canAttack;
        pendingTarget = target;
    }

    public static void onFrame() {
        if (!AttackAura.spaceTime() || pendingTarget == null) {
            return;
        }
        AttackAura aura = AttackAura.instance();
        if (aura == null || !aura.isEnabled()) {
            return;
        }
        updateRotations(pendingTarget);
    }

    public static boolean settled() {
        return true;
    }

    private static void updateRotations(LivingEntity entity) {
        Minecraft mc = mc();
        TRACKER.update();

        if (mc.player == null) {
            return;
        }
        boolean nullTarget = entity == null;
        if (nullTarget) {
            return;
        }

        if (mc.player.isBlocking()) {
            serverYaw = mc.player.getYRot();
            serverPitch = mc.player.getXRot();
            initialized = false;
            return;
        }

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (!syncedOnce) {
            serverYaw = mc.player.getYRot();
            serverPitch = mc.player.getXRot();
            internalYaw = serverYaw;
            internalPitch = serverPitch;
            velYawSm = 0.0F;
            velPitchSm = 0.0F;
            initialized = true;
            pauseTicks = 0;
            activity = 1.0F;
            syncedOnce = true;
        }

        if (currentTarget != entity) {
            currentTarget = entity;
            speedOsc = rnd.nextFloat() * 0.05F;
            accelerating = false;
            tickCounter = 0;
            accelPeak = 0.18F + rnd.nextFloat() * 0.1F;
            decelFloor = -0.02F - rnd.nextFloat() * 0.05F;
            peakMul = 0.8F + rnd.nextFloat() * 0.4F;
            serverYaw = mc.player.getYRot();
            serverPitch = mc.player.getXRot();
            internalYaw = serverYaw;
            internalPitch = serverPitch;
            velYawSm = 0.0F;
            velPitchSm = 0.0F;
            initialized = true;
            pauseTicks = 0;
            activity = 1.0F;
            overshootCountdown = -1;
            pendingOvershootYaw = 0.0F;
            pendingOvershootPitch = 0.0F;
            haveAim = false;
            prevBreathYaw = 0.0F;
            prevBreathPitch = 0.0F;
        }

        tickCounter++;
        noiseSm = noiseSm * 0.85F + (float) rnd.nextGaussian() * 0.045F;
        boolean windowClosed = System.currentTimeMillis() % 500L >= 370L;
        float gate = windowClosed ? 0.0F : 1.0F;

        Vec3 point = getMultipoint(entity, 128.0);
        float[] rot = getRotations(point);
        float aimYaw = rot[0];
        float aimPitch = rot[1];

        float yawDist = Math.abs(Mth.wrapDegrees(aimYaw - serverYaw));
        boolean ready = mc.player.getAttackStrengthScale(1.0F) > 0.9F && lastCanAttack;

        if (!accelerating) {
            float gain = 0.0055F;
            gain += (0.004F + 0.012F * (1.0F - (float) Math.exp(-yawDist / 35.0F))) * 1.8F;
            gain += rnd.nextFloat() * 0.0025F;
            if (ready) {
                gain += 0.012857143F;
            }
            speedOsc += gain * (1.6F + noiseSm);
            if (speedOsc >= accelPeak) {
                accelerating = true;
                decelFloor = -0.02F - rnd.nextFloat() * 0.05F;
            }
        } else {
            float drop = (ready ? 0.045F : 0.008F) * (0.85F + rnd.nextFloat() * 0.3F);
            speedOsc -= drop * (2.1F + noiseSm);
            if (speedOsc <= decelFloor) {
                accelerating = false;
                accelPeak = 0.18F + rnd.nextFloat() * 0.1F;
            }
        }

        boolean gliding = mc.player.isFallFlying();
        float stepCap = Mth.clamp(speedOsc, 0.0F, gliding ? 0.456F : 0.312F);
        if (ready) {
            stepCap = Math.min(stepCap + 0.1F, gliding ? 0.552F : 0.408F);
        }
        stepCap += noiseSm * 0.25F;
        if (tickCounter <= 3) {
            stepCap *= 0.2F * (float) tickCounter * peakMul;
        }

        if (!initialized) {
            serverYaw = mc.player.getYRot();
            serverPitch = mc.player.getXRot();
            internalYaw = serverYaw;
            internalPitch = serverPitch;
            velYawSm = 0.0F;
            velPitchSm = 0.0F;
            initialized = true;
        }

        float dYaw = Mth.wrapDegrees(aimYaw - internalYaw);
        float dPitch = aimPitch - internalPitch;
        if (pauseTicks > 0) {
            pauseTicks--;
        } else if (rnd.nextFloat() < 0.03F && tickCounter > 4 && !ready && yawDist < 10.0F) {
            pauseTicks = rnd.nextInt(3) + 2;
        }

        if (pauseTicks > 0) {
            activity += (0.0F - activity) * 0.35F;
        } else {
            activity += (1.0F - activity) * 0.35F;
        }

        float randScale = 0.9F + rnd.nextFloat() * 0.2F;
        float speedMul = getSpeedMultiplier();
        float maxYawStep = (gliding ? 86.4F : (ready ? 38.4F : 28.8F)) * randScale * speedMul;
        float maxPitchStep = (gliding ? 14.4F : (ready ? 5.4F : 3.36F)) * randScale * speedMul;
        dYaw = smoothClamp(dYaw, maxYawStep, rnd);
        dPitch = smoothClamp(dPitch, maxPitchStep, rnd);

        float moveYaw = stepCap * (0.94F + noiseSm * 0.92F) * gate;
        float movePitch = Math.min(stepCap * 0.836F, moveYaw * 0.96F) * gate;

        float leadYaw = 0.0F;
        float leadPitch = 0.0F;
        if (haveAim) {
            leadYaw = Mth.clamp(Mth.wrapDegrees(aimYaw - prevAimYaw), -LEAD_CAP_YAW, LEAD_CAP_YAW);
            leadPitch = Mth.clamp(aimPitch - prevAimPitch, -LEAD_CAP_PITCH, LEAD_CAP_PITCH);
        }
        prevAimYaw = aimYaw;
        prevAimPitch = aimPitch;
        haveAim = true;

        float wantVelYaw = dYaw * moveYaw * activity + leadYaw * activity;
        float wantVelPitch = dPitch * movePitch * activity + leadPitch * activity;
        float kYawBase = 0.16F + rnd.nextFloat() * 0.08F;
        float kPitchBase = 0.45F + rnd.nextFloat() * 0.15F;
        float adYaw = Math.abs(dYaw);
        float adPitch = Math.abs(dPitch);
        float kYawScale = adYaw < 3.0F ? 0.4F + 0.6F * (adYaw / 3.0F) : 1.0F;
        float kPitchScale = adPitch < 3.0F ? 0.4F + 0.6F * (adPitch / 3.0F) : 1.0F;
        float kYaw = kYawBase * kYawScale;
        float kPitch = kPitchBase * kPitchScale;
        float yawSigma = 0.006F + Math.abs(velYawSm) * 0.012F;
        float pitchSigma = 0.02F + Math.abs(velPitchSm) * 0.05F;
        yawJitterSm = yawJitterSm * 0.55F + (float) rnd.nextGaussian() * yawSigma * 0.45F;
        pitchJitterSm = pitchJitterSm * 0.55F + (float) rnd.nextGaussian() * pitchSigma * 0.45F;
        float accelYaw = (wantVelYaw - velYawSm) * kYaw + yawJitterSm;
        float accelBound = 0.7F + Math.abs(velYawSm) * 0.2F;
        accelYaw = Mth.clamp(accelYaw, lastYawAccel - accelBound, lastYawAccel + accelBound);
        velYawSm += accelYaw;
        lastYawAccel = accelYaw;
        velPitchSm += (wantVelPitch - velPitchSm) * kPitch + pitchJitterSm;
        velPitchSm = smoothClamp(velPitchSm, 4.8F, rnd);

        if (!windowClosed) {
            if (overshootCountdown == 0) {
                velYawSm += pendingOvershootYaw;
                velPitchSm += pendingOvershootPitch;
                pendingOvershootYaw = 0.0F;
                pendingOvershootPitch = 0.0F;
                overshootCountdown = -1;
            } else if (overshootCountdown > 0) {
                overshootCountdown--;
            }
        }

        float appliedYawVel = (velYawSm + prevAppliedYawVel) * 0.5F;
        float appliedPitchVel = (velPitchSm + prevAppliedPitchVel) * 0.5F;
        prevAppliedYawVel = velYawSm;
        prevAppliedPitchVel = velPitchSm;
        internalYaw += appliedYawVel;
        internalPitch = Mth.clamp(internalPitch + appliedPitchVel, -89.0F, 89.0F);

        float mult = MULTIPLIER.getMultiplier(entity);
        float breathYaw = breath(1.1F * mult, false, 18.0F);
        float breathPitch = breath(0.05F * mult, false, nextPeriod());
        internalYaw += (breathYaw - prevBreathYaw) * gate;
        internalPitch += (breathPitch - prevBreathPitch) * gate;
        prevBreathYaw = breathYaw;
        prevBreathPitch = breathPitch;

        double gcdValue = GCDUtil.getGCDValue();
        float g = (float) gcdValue;
        float outYaw;
        float outPitch;
        if (g > 0.0F) {
            double dyQ = (double) internalYaw - (double) serverYaw;
            double dpQ = (double) internalPitch - (double) serverPitch;
            dpQ = smoothClamp((float) dpQ, 4.8F, rnd);
            int stepsYaw = (int) Math.round(dyQ / gcdValue);
            int stepsPitch = (int) Math.round(dpQ / gcdValue);
            outYaw = serverYaw + (float) ((double) stepsYaw * gcdValue);
            outPitch = serverPitch + (float) ((double) stepsPitch * gcdValue);
        } else {
            outYaw = internalYaw;
            outPitch = internalPitch;
        }
        outPitch = Mth.clamp(outPitch, -89.0F, 89.0F);

        if (g > 0.0F) {
            if (Math.abs(Mth.wrapDegrees(internalYaw - outYaw)) > g * 3.0F) {
                internalYaw += (outYaw - internalYaw) * 0.5F;
                velYawSm *= 0.4F;
            }
            if (Math.abs(internalPitch - outPitch) > g * 3.0F) {
                internalPitch += (outPitch - internalPitch) * 0.5F;
                velPitchSm *= 0.4F;
            }
        }

        drift += (float) (rnd.nextGaussian() * 2.0E-5);
        drift = drift >= 0.0F ? Mth.clamp(drift, 7.0E-4F, 0.0016F) : Mth.clamp(drift, -0.0016F, -7.0E-4F);
        float rawDeltaYaw = outYaw - serverYaw;
        float sendYaw = serverYaw + rawDeltaYaw * (1.0F + drift);

        float speed = mc.player.isFallFlying() && entity.isFallFlying() ? 360.0F : 45.0F;
        RotationProcess.updateFrame(new Rotation(sendYaw, outPitch), speed, speed, speed, speed, 10, 1);

        serverYaw = sendYaw;
        serverPitch = outPitch;

        float epsilon = Math.max(AIMED_EPSILON, AttackAura.targetAngularHalf() * 0.55F);
        aimed = (Math.abs(Mth.wrapDegrees(aimYaw - serverYaw)) <= epsilon
                && Math.abs(aimPitch - serverPitch) <= epsilon)
                || rayTraceBox(RayTraceUtil.getVectorForRotation(serverPitch, serverYaw),
                        AttackAura.attackRange() + 0.5, entity.getBoundingBox());
    }

    private static Vec3 getMultipoint(LivingEntity entity, double fovDistance) {
        float minX = 0.005F;
        float maxX = 0.015F;
        float minY = 0.0015F;
        float maxY = 0.015F;
        AABB box = entity.getBoundingBox();
        double lenX = box.getXsize();
        double lenY = box.getYsize();
        double lenZ = box.getZsize();
        if (wanderVel.equals(Vec3.ZERO)) {
            wanderVel = new Vec3(randomBest(-0.02F, 0.02F), randomBest(-0.02F, 0.02F),
                    randomBest(-0.02F, 0.02F));
        }
        if (wanderOffset.equals(Vec3.ZERO)) {
            wanderOffset = new Vec3(0.0, lenY * 0.5, 0.0);
        }
        wanderOffset = wanderOffset.add(wanderVel);
        double xLim = (lenX - 0.1) / 2.0;
        double zLim = (lenZ - 0.1) / 2.0;
        if (wanderOffset.x >= xLim) {
            wanderVel = new Vec3(-randomBest(minX, maxX), wanderVel.y, wanderVel.z);
        } else if (wanderOffset.x <= -xLim) {
            wanderVel = new Vec3(randomBest(minX, maxX), wanderVel.y, wanderVel.z);
        }
        if (wanderOffset.y >= lenY * 0.75) {
            wanderVel = new Vec3(wanderVel.x, -randomBest(minY, maxY), wanderVel.z);
        } else if (wanderOffset.y <= lenY * 0.3) {
            wanderVel = new Vec3(wanderVel.x, randomBest(minY, maxY), wanderVel.z);
        }
        if (wanderOffset.z >= zLim) {
            wanderVel = new Vec3(wanderVel.x, wanderVel.y, -randomBest(minX, maxX));
        } else if (wanderOffset.z <= -zLim) {
            wanderVel = new Vec3(wanderVel.x, wanderVel.y, randomBest(minX, maxX));
        }
        wanderOffset.add(randomBest(-0.05F, 0.05F), 0.0, randomBest(-0.05F, 0.05F));

        Minecraft mc = mc();
        if (!rayTraceBox(mc.player.getLookAngle(), fovDistance, box)) {
            float halfX = (float) (lenX / 2.0) * 0.8F;
            Vec3 entityPos = new Vec3(entity.getX(), entity.getY(), entity.getZ());
            for (float dx = -halfX; dx <= halfX; dx += 0.1F) {
                for (float dz = -halfX; dz <= halfX; dz += 0.1F) {
                    for (float dy = (float) (lenY * 0.9); (double) dy >= lenY * 0.3; dy -= 0.1F) {
                        Vec3 candidate = new Vec3(
                                entity.getX() + (double) dx,
                                entity.getY() + (double) dy,
                                entity.getZ() + (double) dz);
                        float[] candRot = fromVec3d(candidate);
                        Vec3 candDir = RayTraceUtil.getVectorForRotation(candRot[1], candRot[0]);
                        if (rayTraceBox(candDir, fovDistance, box)) {
                            wanderOffset = new Vec3(dx, dy, dz);
                            return entityPos.add(wanderOffset);
                        }
                    }
                }
            }
        }
        return new Vec3(entity.getX(), entity.getY(), entity.getZ()).add(wanderOffset);
    }

    private static float getSpeedMultiplier() {
        if (!PlayerStats.turnReady()) {
            return 1.45F;
        }
        return Mth.clamp(PlayerStats.turn(0.90F) / 28.8F, 1.0F, 2.0F);
    }

    private static boolean rayTraceBox(Vec3 direction, double distance, AABB box) {
        Vec3 eye = mc().player.getEyePosition();
        if (box.contains(eye)) {
            return true;
        }
        return box.clip(eye, eye.add(direction.scale(distance))).isPresent();
    }

    private static float[] fromVec3d(Vec3 vec) {
        return new float[]{
                (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(vec.z, vec.x)) - 90.0),
                (float) Mth.wrapDegrees(Math.toDegrees(-Math.atan2(vec.y, Math.hypot(vec.x, vec.z))))
        };
    }

    private static float[] getRotations(Vec3 point) {
        Minecraft mc = mc();
        double dx = point.x - mc.player.getX();
        double dy = point.y - mc.player.getEyeY();
        double dz = point.z - mc.player.getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        return new float[]{yaw, pitch};
    }

    private static double randomBest(double min, double max) {
        return ThreadLocalRandom.current().nextDouble() * (max - min) + min;
    }

    private static void reshufflePeriods() {
        shufflePeriods();
        periodIndex = 0;
        periodExpiry = System.currentTimeMillis() + 6000L;
    }

    private static float nextPeriod() {
        long now = System.currentTimeMillis();
        while (now >= periodExpiry) {
            int first = PERIODS[periodIndex];
            periodIndex++;
            if (periodIndex == PERIODS.length) {
                shufflePeriods();
                if (PERIODS[0] == first) {
                    ThreadLocalRandom rnd = ThreadLocalRandom.current();
                    int swap = rnd.nextInt(1, PERIODS.length);
                    int t = PERIODS[0];
                    PERIODS[0] = PERIODS[swap];
                    PERIODS[swap] = t;
                }
                periodIndex = 0;
            }
            periodExpiry += 6000L;
        }
        return (float) PERIODS[periodIndex];
    }

    private static void shufflePeriods() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = PERIODS.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = PERIODS[i];
            PERIODS[i] = PERIODS[j];
            PERIODS[j] = t;
        }
    }

    private static float breath(float amplitude, boolean cosWave, float periodDivisor) {
        float t = (float) (System.currentTimeMillis() % 1000L) / periodDivisor;
        return (float) (cosWave ? Math.cos(t) : Math.sin(t)) * amplitude;
    }

    private static float smoothClamp(float value, float maxStep, ThreadLocalRandom rnd) {
        if (maxStep <= 0.0F) {
            return 0.0F;
        }
        float sign = Math.signum(value);
        float abs = Math.abs(value);
        float outer = maxStep * (0.85F + rnd.nextFloat() * 0.3F);
        float inner = outer * (0.5F + rnd.nextFloat() * 0.15F);
        if (abs <= inner) {
            return value;
        }
        float over = abs - inner;
        float range = outer - inner;
        float mid = range * (over / (over + range));
        float extra = Math.min(over * 0.1F, range * 0.8F);
        return sign * (inner + mid + extra);
    }

    private static final class Multiplier {
        float getMultiplier(LivingEntity target) {
            Vec3 eyes = mc().player.getEyePosition();
            AABB box = target.getBoundingBox();
            double cx = Mth.clamp(eyes.x, box.minX, box.maxX);
            double cz = Mth.clamp(eyes.z, box.minZ, box.maxZ);
            return Math.hypot(eyes.x - cx, eyes.z - cz) <= 0.9 ? 0.93F : 1.0F;
        }
    }

    private static final class AttackTracker {
        private float prevProgress;
        private long lastAttackMs = -1L;
        private boolean justAttackedFlag;

        boolean update() {
            this.justAttackedFlag = false;
            Minecraft mc = mc();
            if (mc.player == null) {
                return false;
            }
            float progress = mc.player.getAttackStrengthScale(0.0F);
            if (progress < this.prevProgress - 0.01F) {
                this.lastAttackMs = System.currentTimeMillis();
                this.justAttackedFlag = true;
            }
            this.prevProgress = progress;
            return this.justAttackedFlag;
        }

        void reset() {
            this.prevProgress = 0.0F;
            this.lastAttackMs = -1L;
            this.justAttackedFlag = false;
        }
    }
}
