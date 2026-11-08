package dev.hatek.client.module.impl.combat;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.SliderSetting;
import dev.hatek.client.render.TargetTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * AimRecorder: the first step of the neural aura. While it is on and you fight with your own mouse (the aura off),
 * every tick of a fight is written to config/hatek/aim_data/*.csv: where the target is relative to your crosshair,
 * how fast you and the target move, and how much you really turned your head that tick. The training script reads
 * those files and teaches a small network to aim the way you do.
 *
 * Columns: errYaw, errPitch, dist, relFwd, relSide, relUp, selfFwd, selfSide, prevDYaw, prevDPitch, prevDYaw2,
 * prevDPitch2, cooldown, onGround, attacked, dYaw (label), dPitch (label)
 */
public final class AimRecorder extends Module {
    private static final String HEADER = "errYaw,errPitch,dist,relFwd,relSide,relUp,selfFwd,selfSide,prevDYaw,prevDPitch,"
            + "prevDYaw2,prevDPitch2,cooldown,onGround,attacked,dYaw,dPitch";

    private final SliderSetting maxDistance = new SliderSetting("Дистанция записи (блоки)", 7.0, 3.0, 12.0, 0.5);
    private final SliderSetting combatSeconds = new SliderSetting("Бой длится после удара (с)", 4.0, 1.0, 10.0, 0.5);
    private final BoolSetting announce = new BoolSetting("Сообщать в чат", true);

    private BufferedWriter writer;
    private Path file;
    private int samples;
    private int unsaved;
    private int totalSamples;

    private float[] pending;                 // features of the previous tick (the input of the current label)
    private float lastYaw;
    private float lastPitch;
    private float prevDYaw;
    private float prevDPitch;
    private float prevDYaw2;
    private float prevDPitch2;
    private boolean haveLast;
    private int lastAttackTick;
    private LivingEntity lastTarget;

    public AimRecorder() {
        super("AimRecorder", "Records your own aim in fights (training data for the neural aura)", Category.COMBAT);
        with(this.maxDistance, this.combatSeconds, this.announce);
    }

    @Override
    protected void onEnable() {
        this.samples = 0;
        this.unsaved = 0;
        this.pending = null;
        this.haveLast = false;
        try {
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("hatek").resolve("aim_data");
            Files.createDirectories(dir);
            this.file = dir.resolve("aim_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");
            this.writer = Files.newBufferedWriter(this.file, StandardCharsets.UTF_8);
            this.writer.write(HEADER);
            this.writer.newLine();
        } catch (IOException e) {
            this.writer = null;
            say("не удалось создать файл записи: " + e.getMessage());
            setEnabled(false);
            return;
        }
        say("запись включена. Дерись своей мышкой (киллаура выключена). Файл: " + this.file.getFileName());
    }

    @Override
    protected void onDisable() {
        try {
            if (this.writer != null) {
                this.writer.flush();
                this.writer.close();
            }
        } catch (IOException ignored) {
            // nothing more to do
        }
        this.writer = null;
        if (this.file != null) {
            say("запись остановлена. Записано тиков боя: " + this.samples + " (" + this.file.getFileName() + ")");
        }
    }

    private void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (this.announce.value() || text.startsWith("не удалось"))) {
            mc.player.sendSystemMessage(Component.literal("§bAimRecorder: §f" + text));
        }
    }

    @Override
    public void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || this.writer == null) {
            this.haveLast = false;
            this.pending = null;
            return;
        }
        // rotation change of this tick
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        float dYaw = 0.0f;
        float dPitch = 0.0f;
        if (this.haveLast) {
            dYaw = Mth.wrapDegrees(yaw - this.lastYaw);
            dPitch = pitch - this.lastPitch;
        }
        this.lastYaw = yaw;
        this.lastPitch = pitch;
        boolean hadLast = this.haveLast;
        this.haveLast = true;

        boolean attackedNow = player.swinging && player.swingTime == 0;
        if (attackedNow) {
            this.lastAttackTick = player.tickCount;
        }

        // the label of the previous tick's features
        if (this.pending != null && hadLast) {
            this.pending[14] = attackedNow ? 1.0f : 0.0f;
            write(this.pending, dYaw, dPitch);
        }
        this.pending = null;

        LivingEntity target = TargetTracker.current();
        boolean inCombat = TargetTracker.lastHitAgeMs() < this.combatSeconds.value() * 1000.0;
        if (target == null || !inCombat || player.distanceTo(target) > this.maxDistance.value()) {
            this.prevDYaw = 0.0f;
            this.prevDPitch = 0.0f;
            this.prevDYaw2 = 0.0f;
            this.prevDPitch2 = 0.0f;
            return;
        }
        if (target != this.lastTarget) {
            this.lastTarget = target;
            this.prevDYaw = 0.0f;
            this.prevDPitch = 0.0f;
            this.prevDYaw2 = 0.0f;
            this.prevDPitch2 = 0.0f;
        }
        this.pending = features(player, target);
        this.prevDYaw2 = this.prevDYaw;
        this.prevDPitch2 = this.prevDPitch;
        this.prevDYaw = dYaw;
        this.prevDPitch = dPitch;
    }

    private float[] features(LocalPlayer player, LivingEntity target) {
        Vec3 eye = player.getEyePosition();
        Vec3 aim = target.getBoundingBox().getCenter();
        double dx = aim.x - eye.x;
        double dy = aim.y - eye.y;
        double dz = aim.z - eye.z;
        float wantYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float wantPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        float errYaw = Mth.wrapDegrees(wantYaw - player.getYRot());
        float errPitch = wantPitch - player.getXRot();
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        // velocities in the view frame of the player (forward, sideways)
        double yawRad = Math.toRadians(player.getYRot());
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        Vec3 rel = target.getDeltaMovement().subtract(player.getDeltaMovement());
        float relFwd = (float) (rel.x * fx + rel.z * fz);
        float relSide = (float) (rel.x * fz - rel.z * fx);
        Vec3 self = player.getDeltaMovement();
        float selfFwd = (float) (self.x * fx + self.z * fz);
        float selfSide = (float) (self.x * fz - self.z * fx);

        float[] f = new float[17];
        f[0] = errYaw;
        f[1] = errPitch;
        f[2] = dist;
        f[3] = relFwd;
        f[4] = relSide;
        f[5] = (float) rel.y;
        f[6] = selfFwd;
        f[7] = selfSide;
        f[8] = this.prevDYaw;
        f[9] = this.prevDPitch;
        f[10] = this.prevDYaw2;
        f[11] = this.prevDPitch2;
        f[12] = player.getAttackStrengthScale(0.0f);
        f[13] = player.onGround() ? 1.0f : 0.0f;
        return f;
    }

    private void write(float[] f, float dYaw, float dPitch) {
        StringBuilder row = new StringBuilder(160);
        for (int i = 0; i < 15; i++) {
            row.append(String.format(Locale.ROOT, "%.5f,", f[i]));
        }
        row.append(String.format(Locale.ROOT, "%.5f,%.5f", dYaw, dPitch));
        try {
            this.writer.write(row.toString());
            this.writer.newLine();
        } catch (IOException e) {
            this.writer = null;
            return;
        }
        this.samples++;
        this.totalSamples++;
        if (++this.unsaved >= 100) {
            this.unsaved = 0;
            try {
                this.writer.flush();
            } catch (IOException ignored) {
                // the next write reports it
            }
        }
        if (this.samples % 500 == 0) {
            say("записано тиков боя: " + this.samples);
        }
    }
}
