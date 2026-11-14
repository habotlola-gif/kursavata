package dev.hatek.client.module.impl.combat.aura.rotation;

import dev.hatek.client.module.impl.combat.AttackAura;
import dev.hatek.client.module.impl.combat.aura.util.RayTraceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

/**
 * "Neural Rotation": the head is moved once per tick by a small network trained on the player's own recorded aim
 * (AimRecorder + the training script). The network sees where the target is relative to the crosshair, how everything
 * moves and what the last two turns were, and answers with the turn of this tick plus a learned spread of noise, so the
 * motion has the speed, lag and imperfection of a real hand. A pull toward the upper body of the target (the "assist")
 * makes sure the hits still land. The model is read from config/hatek/aim_model.json when that file exists, otherwise
 * the one inside the mod is used.
 */
public final class NeuralRotation {
    private static NeuralNet net;
    private static boolean loadTried;
    private static String loadedFrom = "";

    private static LivingEntity currentTarget;
    private static boolean aimed;
    private static float prevDYaw;
    private static float prevDPitch;
    private static float prevDYaw2;
    private static float prevDPitch2;
    private static float prevDYaw3;
    private static float prevDPitch3;
    private static float prevDYaw4;
    private static float prevDPitch4;
    private static double prevErrYaw;
    private static double prevErrPitch;
    private static boolean prevErrValid;
    private static int ticksSinceSwing = 40;
    private static int reactionTicks;
    private static double wanderU;
    private static double wanderV;
    private static int wanderLeft;

    // the angles before this tick's turn: the game copies the "old" angles at the end of the tick, which would make the
    // model jump by a whole step; they are put back afterwards so the head glides between the two ticks at any FPS
    private static boolean snapValid;
    private static float snapYaw;
    private static float snapPitch;
    private static float snapHead;

    private NeuralRotation() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static String modelSource() {
        return loadedFrom;
    }

    private static NeuralNet model() {
        if (!loadTried) {
            loadTried = true;
            try {
                Path external = mc().gameDirectory.toPath().resolve("config").resolve("hatek").resolve("aim_model.json");
                if (Files.isRegularFile(external)) {
                    try (InputStream stream = Files.newInputStream(external)) {
                        net = NeuralNet.load(stream);
                        loadedFrom = "config/hatek/aim_model.json";
                    }
                } else {
                    try (InputStream stream = NeuralRotation.class.getResourceAsStream("/assets/hatek_client/aim_model.json")) {
                        if (stream != null) {
                            net = NeuralNet.load(stream);
                            loadedFrom = "built-in model";
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                net = null;
            }
        }
        return net;
    }

    public static void reshuffleAll() {
        forget();
        loadTried = false;
    }

    public static void forget() {
        currentTarget = null;
        aimed = false;
        prevDYaw = 0.0f;
        prevDPitch = 0.0f;
        prevDYaw2 = 0.0f;
        prevDPitch2 = 0.0f;
        prevDYaw3 = prevDPitch3 = prevDYaw4 = prevDPitch4 = 0.0f;
        prevErrValid = false;
    }

    public static boolean aimed() {
        return aimed;
    }

    /** End of the player's tick: restore the "old" angles so the model is interpolated instead of stepping. */
    public static void afterTick(LocalPlayer player) {
        if (!snapValid) {
            return;
        }
        snapValid = false;
        player.yRotO = snapYaw;
        player.xRotO = snapPitch;
        player.yHeadRotO = snapHead;
    }

    /** Once per tick while the aura has a target. */
    public static void onTick(LivingEntity target) {
        Minecraft mc = mc();
        LocalPlayer player = mc.player;
        NeuralNet network = model();
        if (player == null || mc.level == null || network == null || target == null) {
            return;
        }
        if (player.isBlocking()) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (currentTarget != target) {
            currentTarget = target;
            prevDYaw = prevDPitch = prevDYaw2 = prevDPitch2 = 0.0f;
            prevDYaw3 = prevDPitch3 = prevDYaw4 = prevDPitch4 = 0.0f;
            prevErrValid = false;
            // a person notices a new target first: the head waits two to four ticks
            reactionTicks = 2 + rnd.nextInt(3);
            wanderLeft = 0;
        }
        if (wanderLeft-- <= 0) {
            wanderLeft = 10 + rnd.nextInt(24);
            wanderU = (rnd.nextDouble() * 2.0 - 1.0) * 0.22;
            wanderV = (rnd.nextDouble() * 2.0 - 1.0) * 0.12;
        }

        AABB box = target.getBoundingBox();
        Vec3 eye = player.getEyePosition();
        Vec3 center = box.getCenter();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        double yawRad = Math.toRadians(yaw);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);

        // ---- features (the same as the recorder columns)
        double dx = center.x - eye.x;
        double dy = center.y - eye.y;
        double dz = center.z - eye.z;
        double wantYaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double wantPitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        double errYaw = Mth.wrapDegrees((float) (wantYaw - yaw));
        double errPitch = wantPitch - pitch;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        Vec3 rel = target.getDeltaMovement().subtract(player.getDeltaMovement());
        Vec3 self = player.getDeltaMovement();
        float cooldown = player.getAttackStrengthScale(0.0f);
        double[] x;
        if (network.features() >= 22) {
            // v2 features: four turns of history, how fast the target moves on the screen, the size of the body on
            // the screen and the time since the last swing (the same as tools/aim_train/train2.py)
            ticksSinceSwing = player.swinging && player.swingTime == 0 ? 0 : Math.min(40, ticksSinceSwing + 1);
            double errYawNow = Mth.clamp(errYaw, -180.0, 180.0);
            double errPitchNow = Mth.clamp(errPitch, -90.0, 90.0);
            double lastErrYaw = prevErrValid ? prevErrYaw : errYawNow;
            double lastErrPitch = prevErrValid ? prevErrPitch : errPitchNow;
            prevErrYaw = errYawNow;
            prevErrPitch = errPitchNow;
            prevErrValid = true;
            x = new double[]{
                    errYawNow, errPitchNow, dist,
                    rel.x * fx + rel.z * fz, rel.x * fz - rel.z * fx, rel.y,
                    self.x * fx + self.z * fz, self.x * fz - self.z * fx,
                    Mth.clamp(prevDYaw, -60.0f, 60.0f), Mth.clamp(prevDPitch, -20.0f, 20.0f),
                    Mth.clamp(prevDYaw2, -60.0f, 60.0f), Mth.clamp(prevDPitch2, -20.0f, 20.0f),
                    Mth.clamp(prevDYaw3, -60.0f, 60.0f), Mth.clamp(prevDPitch3, -20.0f, 20.0f),
                    Mth.clamp(prevDYaw4, -60.0f, 60.0f), Mth.clamp(prevDPitch4, -20.0f, 20.0f),
                    cooldown, player.onGround() ? 1.0 : 0.0,
                    Math.toDegrees(Math.atan2(0.3, Math.max(dist, 0.3))),
                    Mth.clamp(errYawNow - lastErrYaw + prevDYaw, -40.0, 40.0),
                    Mth.clamp(errPitchNow - lastErrPitch + prevDPitch, -15.0, 15.0),
                    ticksSinceSwing};
        } else {
            x = new double[]{
                    Mth.clamp(errYaw, -180.0, 180.0), Mth.clamp(errPitch, -90.0, 90.0), dist,
                    rel.x * fx + rel.z * fz, rel.x * fz - rel.z * fx, rel.y,
                    self.x * fx + self.z * fz, self.x * fz - self.z * fx,
                    Mth.clamp(prevDYaw, -60.0f, 60.0f), Mth.clamp(prevDPitch, -20.0f, 20.0f),
                    Mth.clamp(prevDYaw2, -60.0f, 60.0f), Mth.clamp(prevDPitch2, -20.0f, 20.0f),
                    cooldown, player.onGround() ? 1.0 : 0.0};
        }

        // ---- the network's turn for this tick
        double[] step = network.sample(x, AttackAura.neuralChaos(), rnd);
        double turnYaw = step[0];
        double turnPitch = step[1];

        // ---- assist: a pull toward the upper body so that hits land (stronger when the hit is ready)
        double hitX = center.x + wanderU * box.getXsize();
        double hitY = box.minY + box.getYsize() * (0.72 + wanderV);
        double hitZ = center.z + wanderU * box.getZsize();
        double hx = hitX - eye.x;
        double hy = hitY - eye.y;
        double hz = hitZ - eye.z;
        double hitErrYaw = Mth.wrapDegrees((float) (Math.toDegrees(Math.atan2(hz, hx)) - 90.0 - yaw));
        double hitErrPitch = -Math.toDegrees(Math.atan2(hy, Math.hypot(hx, hz))) - pitch;
        double assist = AttackAura.neuralAssist() * (cooldown > 0.9f ? 1.0 : 0.5);
        turnYaw += assist * 0.45 * hitErrYaw;
        turnPitch += assist * 0.35 * hitErrPitch;

        if (reactionTicks > 0) {
            reactionTicks--;
            turnYaw = 0.0;
            turnPitch = 0.0;
        }
        turnYaw = Mth.clamp(turnYaw, -60.0, 60.0);
        turnPitch = Mth.clamp(turnPitch, -20.0, 20.0);

        snapYaw = player.getYRot();
        snapPitch = player.getXRot();
        snapHead = player.yHeadRot;
        snapValid = true;
        RotationProcess.updateFrame(new Rotation(yaw + (float) turnYaw, Mth.clamp(pitch + (float) turnPitch, -90.0f, 90.0f)),
                90.0f, 40.0f, 90.0f, 40.0f, 10, 1);

        // what really happened (the angles are rounded to the mouse grid)
        float achievedYaw = Mth.wrapDegrees(player.getYRot() - yaw);
        float achievedPitch = player.getXRot() - pitch;
        prevDYaw4 = prevDYaw3;
        prevDPitch4 = prevDPitch3;
        prevDYaw3 = prevDYaw2;
        prevDPitch3 = prevDPitch2;
        prevDYaw2 = prevDYaw;
        prevDPitch2 = prevDPitch;
        prevDYaw = achievedYaw;
        prevDPitch = achievedPitch;

        // ---- can we hit from here
        Vec3 end = eye.add(RayTraceUtil.getVectorForRotation(player.getXRot(), player.getYRot())
                .scale(AttackAura.attackRange() + 0.5));
        aimed = box.clip(eye, end).isPresent();
    }
}
