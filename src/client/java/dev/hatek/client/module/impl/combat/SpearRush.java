package dev.hatek.client.module.impl.combat;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.impl.movement.DragonFly;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * SpearRush: while you fly and charge a spear (right mouse held), the module picks the player closest to where you
 * are looking, aims at them for you and accelerates you toward them on the DragonFly flight (DragonFly is switched on
 * for you and its Speed is the top speed). The spear's charge damage grows with the speed you move at (the server
 * measures it from your movement). To make the hit register every time, the last step before the target is shortened
 * to land exactly in the target's middle, and then the rush goes on straight through it with the speed it had.
 * One rush per charge: release the right mouse button and charge again to rush again.
 */
public final class SpearRush extends Module {
    private static final int PASS_TICKS = 8;

    private static SpearRush instance;

    private final SliderSetting speed = new SliderSetting("Top speed (blocks/tick)", 4.0, 0.5, 8.0, 0.1);
    private final SliderSetting acceleration = new SliderSetting("Acceleration (blocks/tick per tick)", 0.6, 0.05, 3.0, 0.05);
    private final SliderSetting range = new SliderSetting("Range (blocks)", 80.0, 10.0, 120.0, 1.0);
    private final SliderSetting cone = new SliderSetting("Target search cone (degrees)", 60.0, 5.0, 180.0, 5.0);
    private final SliderSetting aimSpeed = new SliderSetting("Aim speed (%)", 100.0, 10.0, 100.0, 5.0);
    private final BoolSetting useDragonFly = new BoolSetting("Use DragonFly (switch it on, take the faster speed)", true);
    private final BoolSetting onlyFlying = new BoolSetting("Only while flying", true);

    private LivingEntity target;
    private boolean finished;        // this charge is used up; wait for the button to be released
    private double currentSpeed;
    private boolean passing;         // the target middle was reached: keep going straight through it
    private Vec3 passDirection = Vec3.ZERO;
    private int passTicks;
    private float startHealth;       // target health when it was locked (with absorption)
    private float lowestHealth;      // the lowest health seen since: a drop means the server registered the hit
    private boolean noTargetNotified;
    private boolean active;          // true on a tick where the rush is steering the player

    public SpearRush() {
        super("SpearRush", "Auto-aim and accelerate into a player on DragonFly while charging a spear", Category.COMBAT);
        instance = this;
        with(this.speed, this.acceleration, this.range, this.cone, this.aimSpeed, this.useDragonFly, this.onlyFlying);
    }

    public static SpearRush instance() {
        return instance;
    }

    /** True while SpearRush is steering the player (DragonFly and the keys must not fight its momentum). */
    public static boolean isActive() {
        return instance != null && instance.active;
    }

    @Override
    protected void onEnable() {
        reset(false);
    }

    @Override
    protected void onDisable() {
        reset(false);
    }

    private static void message(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(text));
        }
    }

    private static float healthOf(LivingEntity entity) {
        return entity.getHealth() + entity.getAbsorptionAmount();
    }

    /** Tells in the chat whether the server registered a hit on the locked target. */
    private void reportResult() {
        if (this.target == null) {
            return;
        }
        String name = this.target.getName().getString();
        float lost = this.startHealth - Math.min(this.lowestHealth, healthOf(this.target));
        if (!this.target.isAlive() || healthOf(this.target) <= 0.0f) {
            message("§aУдар от сервера был: " + name + " убит");
        } else if (lost > 0.01f) {
            message(String.format(Locale.ROOT, "§aУдар от сервера был: %s потерял %.1f хп", name, lost));
        } else {
            message("§cУдара от сервера не было: " + name + " не получил урон");
        }
    }

    private void reset(boolean finishedCharge) {
        this.noTargetNotified = false;
        this.target = null;
        this.currentSpeed = 0.0;
        this.passing = false;
        this.passTicks = 0;
        this.finished = finishedCharge;
    }

    private double topSpeed() {
        DragonFly fly = DragonFly.instance();
        double own = this.speed.value();
        // the faster of the two wins: DragonFly's own Speed never slows the rush down
        return this.useDragonFly.value() && fly != null && fly.isEnabled() ? Math.max(own, fly.speedValue()) : own;
    }

    /** Start of LocalPlayer.tick (after DragonFly). */
    public void beforeTick(LocalPlayer player) {
        this.active = false;
        if (!isEnabled()) {
            return;
        }
        boolean charging = player.isUsingItem() && player.getUseItem().is(ItemTags.SPEARS);
        boolean mayRush = !this.onlyFlying.value() || player.getAbilities().flying;
        if (!charging || !mayRush || !player.isAlive()) {
            if (this.target != null && !this.finished) {
                this.lowestHealth = Math.min(this.lowestHealth, healthOf(this.target));
                reportResult();                      // the charge was released in the middle of the rush
            }
            reset(false);
            return;
        }
        if (this.finished) {
            return;
        }
        if (this.target == null || !this.target.isAlive() || this.target.level() != player.level()) {
            if (this.target != null && !this.target.isAlive()) {
                reportResult();                      // the target died mid-rush
                reset(true);
                return;
            }
            this.target = findTarget(player);
            if (this.target == null) {
                if (!this.noTargetNotified) {
                    this.noTargetNotified = true;
                    message("§cЦель не найдена");
                }
                return;
            }
            DragonFly fly = DragonFly.instance();
            if (this.useDragonFly.value() && fly != null && !fly.isEnabled()) {
                fly.setEnabled(true);
                message("§eВключил DragonFly для разгона");
            }
            this.currentSpeed = 0.0;
            this.passing = false;
            this.passTicks = 0;
            this.startHealth = healthOf(this.target);
            this.lowestHealth = this.startHealth;
            message(String.format(Locale.ROOT, "§aНашёл цель: %s (%.0f бл.), атакую",
                    this.target.getName().getString(), this.target.distanceTo(player)));
        }
        this.lowestHealth = Math.min(this.lowestHealth, healthOf(this.target));

        double top = topSpeed();
        // start from the speed you already have, then speed up every tick up to the top speed
        double horizontalNow = Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z);
        this.currentSpeed = Math.min(top, Math.max(this.currentSpeed, Math.max(1.0, horizontalNow)) + this.acceleration.value());

        if (this.passing) {
            // through the target and on, straight, with the speed it had
            player.setDeltaMovement(this.passDirection.scale(this.currentSpeed));
            this.active = true;
            if (++this.passTicks > PASS_TICKS) {
                reportResult();
                reset(true);
            }
            return;
        }

        Vec3 toTarget = this.target.getBoundingBox().getCenter().subtract(player.getEyePosition());
        double horizontal = Math.hypot(toTarget.x, toTarget.z);
        float wantYaw = (float) (Math.toDegrees(Math.atan2(toTarget.z, toTarget.x)) - 90.0);
        float wantPitch = (float) -Math.toDegrees(Math.atan2(toTarget.y, horizontal));
        float k = (float) (this.aimSpeed.value() / 100.0);
        player.setYRot(player.getYRot() + Mth.wrapDegrees(wantYaw - player.getYRot()) * k);
        player.setXRot(player.getXRot() + (wantPitch - player.getXRot()) * k);

        double distance = toTarget.length();
        Vec3 direction = toTarget.normalize();
        if (distance <= this.currentSpeed + 0.3) {
            // last step: land exactly in the middle of the target so the server sees us inside it, then go through
            player.setDeltaMovement(direction.scale(distance));
            this.passing = true;
            this.passDirection = direction;
            this.passTicks = 0;
        } else {
            player.setDeltaMovement(direction.scale(this.currentSpeed));
        }
        this.active = true;
    }

    /** The player closest to the line of sight within the search cone and the range. */
    private LivingEntity findTarget(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double maxRange = this.range.value();
        double maxAngle = this.cone.value();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Player other : mc.level.players()) {
            if (other == player || other.isSpectator() || !other.isAlive()) {
                continue;
            }
            Vec3 to = other.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            if (distance > maxRange || distance < 0.5) {
                continue;
            }
            double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, look.dot(to.normalize())))));
            if (angle > maxAngle) {
                continue;
            }
            double score = angle + distance * 0.2;      // closest to the crosshair first, a nearer one on a tie
            if (score < bestScore) {
                bestScore = score;
                best = other;
            }
        }
        return best;
    }
}
