package dev.hatek.client.module.impl.combat.aura.rotation;

import dev.hatek.client.module.impl.combat.AttackAura;
import dev.hatek.client.module.impl.combat.aura.Attack;
import dev.hatek.client.module.impl.combat.aura.util.AuraPlayerUtil;
import dev.hatek.client.module.impl.combat.aura.util.AuraProfile;
import dev.hatek.client.module.impl.combat.aura.util.AuraRandom;
import dev.hatek.client.module.impl.combat.aura.util.GCDUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public class RotationProcess extends Component {
    private static float deadZone() {
        return AuraProfile.deadZone();
    }

    private static float minStep() {
        return AuraProfile.minStep();
    }

    private static float maxStep() {
        return AuraProfile.maxStep();
    }

    private static float returnSpeed() {
        return AuraProfile.returnSpeed();
    }

    private static float resetMaxStep() {
        return returnSpeed() * 1.25F;
    }

    private static final int RESET_GRACE = 10;

    private static final AuraRandom.Cached GAIN = new AuraRandom.Cached(0.55F, 0.75F, 400L, 1100L);

    private static Rotation previousTarget;

    private static float lastYawStep;
    private static float lastPitchStep;
    private static int lastStepTick = Integer.MIN_VALUE;

    public static RotationTask currentTask = RotationTask.IDLE;
    public static float currentYawSpeed;
    public static float currentPitchSpeed;
    public static float currentYawReturnSpeed;
    public static float currentPitchReturnSpeed;
    public static int currentPriority;
    public static int currentTimeout;
    public static int idleTicks;
    public static Rotation targetRotation;

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static boolean isRotating() {
        return !currentTask.equals(RotationTask.IDLE);
    }

    private void resetRotation() {
        Rotation target = new Rotation(FreeLookUtil.freeYaw, FreeLookUtil.freePitch);
        if (updateRotation(target,
                Math.min(currentYawReturnSpeed, returnSpeed()),
                Math.min(currentPitchReturnSpeed, returnSpeed()))) {
            stopRotation();
        }
    }

    public void onRender3D() {
        Minecraft mc = mc();
        if (mc.player != null && isRotating()) {
            mc.player.yBodyRot = AuraPlayerUtil.calculateCorrectYawOffset(mc.player.getYRot());
        }
    }

    public static void requestReset() {
        if (currentTask.equals(RotationTask.IDLE)) {
            return;
        }
        currentTask = RotationTask.RESET;
        currentPriority = 0;
        idleTicks = 0;
    }

    public void onTick() {
        if (currentTask.equals(RotationTask.AIM) && idleTicks > currentTimeout + RESET_GRACE) {
            currentTask = RotationTask.RESET;
        }
        if (currentTask.equals(RotationTask.RESET)) {
            resetRotation();
        }
        idleTicks++;
    }

    public static void updateFrame(Rotation target, float yawSpeed, float pitchSpeed,
                                 float yawReturnSpeed, float pitchReturnSpeed,
                                 int timeout, int priority) {
        Minecraft mc = mc();
        if (currentPriority > priority || mc.player == null) {
            return;
        }
        if (currentTask.equals(RotationTask.IDLE)) {
            FreeLookUtil.active = true;
        }
        currentYawSpeed = yawSpeed;
        currentPitchSpeed = pitchSpeed;
        currentYawReturnSpeed = yawReturnSpeed;
        currentPitchReturnSpeed = pitchReturnSpeed;
        currentTimeout = timeout;
        currentPriority = priority;
        currentTask = RotationTask.AIM;
        targetRotation = target;

        float yawDelta = Mth.wrapDegrees(target.yaw - mc.player.getYRot());
        float pitchDelta = target.pitch - mc.player.getXRot();
        applyFrame(GCDUtil.getSensitivity(Mth.clamp(yawDelta, -yawSpeed, yawSpeed)),
                GCDUtil.getSensitivity(Mth.clamp(pitchDelta, -pitchSpeed, pitchSpeed)));
        idleTicks = 0;
    }

    private static void applyFrame(float yawStep, float pitchStep) {
        Minecraft mc = mc();
        if (mc.player == null) {
            return;
        }
        float cap = maxStep();
        float yawStep2 = Mth.clamp(yawStep, -cap, cap);
        if (yawStep2 != yawStep) {
            yawStep2 = GCDUtil.floorToGrid(yawStep2);
        }
        float yaw = mc.player.getYRot() + yawStep2;
        mc.player.setYRot(yaw);
        mc.player.yHeadRot = yaw;

        float pitch = mc.player.getXRot();
        float room = Mth.clamp(pitchStep, -cap, cap);
        room = Mth.clamp(room, -90.0F - pitch, 90.0F - pitch);
        if (room != pitchStep) {
            room = GCDUtil.floorToGrid(room);
        }
        mc.player.setXRot(pitch + room);
    }

    private static void apply(float yawStep, float pitchStep) {
        Minecraft mc = mc();
        if (mc.player == null) {
            return;
        }
        float cap = currentTask.equals(RotationTask.RESET) ? resetMaxStep() : maxStep();

        float carriedYaw = carried(lastYawStep, ACCEL_BLEED);
        float carriedPitch = carried(lastPitchStep, ACCEL_BLEED);
        lastStepTick = mc.player.tickCount;

        float accel = AuraProfile.accel();
        float yawIn = Mth.clamp(yawStep,
                Math.min(0.0F, carriedYaw) - accel, Math.max(0.0F, carriedYaw) + accel);
        float pitchIn = Mth.clamp(pitchStep,
                Math.min(0.0F, carriedPitch) - accel, Math.max(0.0F, carriedPitch) + accel);

        float yawStep2 = GCDUtil.floorToGrid(Mth.clamp(yawIn, -cap, cap));
        float yaw = mc.player.getYRot() + yawStep2;
        mc.player.setYRot(yaw);
        mc.player.yHeadRot = yaw;

        float pitch = mc.player.getXRot();
        float room = Mth.clamp(pitchIn, -cap, cap);
        room = Mth.clamp(room, -90.0F - pitch, 90.0F - pitch);
        float pitchStep2 = GCDUtil.floorToGrid(room);
        mc.player.setXRot(pitch + pitchStep2);

        lastYawStep = yawStep2;
        lastPitchStep = pitchStep2;
    }

    private static final float ACCEL_BLEED = 0.45F;

    private static float carried(float step, float bleed) {
        Minecraft mc = mc();
        if (mc.player == null) {
            return 0.0F;
        }
        int gap = mc.player.tickCount - lastStepTick;
        if (gap < 0 || gap > 6) {
            return 0.0F;
        }
        if (gap <= 1) {
            return step;
        }
        return step * (float) Math.pow(bleed, gap - 1);
    }

    private static float couple(float step, float other) {
        if (step != 0.0F || other == 0.0F || !AuraRandom.chance(0.85F)) {
            return step;
        }
        float amount = Math.abs(other) * AuraRandom.range(0.02F, 0.09F);
        return Math.max(amount, GCDUtil.getGCDValue()) * (AuraRandom.chance(0.5F) ? 1.0F : -1.0F);
    }

    static boolean updateRotation(Rotation targetRotation, float yawSpeed, float pitchSpeed) {
        return updateRotation(targetRotation, yawSpeed, pitchSpeed, 0.0F, 0.0F);
    }

    static boolean updateRotation(Rotation targetRotation, float yawSpeed, float pitchSpeed,
                                  float yawLead, float pitchLead) {
        Minecraft mc = mc();
        if (mc.player == null) {
            return false;
        }
        Rotation currentRotation = new Rotation(mc.player);
        float yawDelta = Mth.wrapDegrees(targetRotation.yaw - currentRotation.yaw);
        float pitchDelta = targetRotation.pitch - currentRotation.pitch;

        float yawStep = step(yawDelta, yawLead, yawSpeed, true);
        float pitchStep = step(pitchDelta, pitchLead, pitchSpeed, false);

        if (Math.abs(pitchDelta) < 2.0F && Math.abs(pitchLead) < 1.0F && AuraRandom.chance(0.25F)) {
            pitchStep = 0.0F;
        }

        yawStep = couple(yawStep, pitchStep);
        pitchStep = couple(pitchStep, yawStep);

        apply(GCDUtil.getSensitivity(yawStep), GCDUtil.getSensitivity(pitchStep));
        idleTicks = 0;

        return new Rotation(mc.player).getDelta(targetRotation) < 1.0F;
    }

    private static float step(float delta, float lead, float maxSpeed, boolean yaw) {
        float abs = Math.abs(delta);

        float dead = deadZone();
        if (abs <= dead && Math.abs(lead) <= 0.05F) {
            return 0.0F;
        }
        float pull = abs <= dead ? 0.0F
                : Math.copySign(Math.max(minStep(), abs * GAIN.get()), delta);
        float amount = pull + lead;
        float limit = maxSpeed * AuraRandom.range(0.88F, 1.12F);
        float cap = abs + Math.abs(lead);

        return Mth.clamp(Mth.clamp(amount, -limit, limit), -cap, cap);
    }

    public void stopRotation() {
        currentTask = RotationTask.IDLE;
        currentPriority = 0;
        lastYawStep = 0.0F;
        lastPitchStep = 0.0F;
        lastStepTick = Integer.MIN_VALUE;
        FreeLookUtil.active = false;
    }

    public enum RotationTask {
        AIM,
        RESET,
        IDLE
    }
}
