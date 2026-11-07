package dev.hatek.client.module.impl.combat;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * SpearRush: while you fly and charge a spear (right mouse held), the module picks the player closest to where you
 * are looking, aims at them for you and accelerates you toward them through flight, faster every tick up to the top
 * speed. The spear's charge damage grows with the speed you move at (the server measures it from your movement), so a
 * long, fast approach hits very hard. One rush per charge: release the right mouse button and charge again to rush again.
 */
public final class SpearRush extends Module {
    private static final int POST_CONTACT_TICKS = 10;

    private static SpearRush instance;

    private final SliderSetting speed = new SliderSetting("Top speed (blocks/tick)", 2.0, 0.5, 5.0, 0.1);
    private final SliderSetting acceleration = new SliderSetting("Acceleration (blocks/tick per tick)", 0.15, 0.02, 1.0, 0.01);
    private final SliderSetting range = new SliderSetting("Range (blocks)", 80.0, 10.0, 120.0, 1.0);
    private final SliderSetting cone = new SliderSetting("Target search cone (degrees)", 60.0, 5.0, 180.0, 5.0);
    private final SliderSetting aimSpeed = new SliderSetting("Aim speed (%)", 100.0, 10.0, 100.0, 5.0);
    private final BoolSetting onlyFlying = new BoolSetting("Only while flying", true);

    private LivingEntity target;
    private boolean finished;        // this charge is used up; wait for the button to be released
    private double currentSpeed;
    private int contactTicks;

    public SpearRush() {
        super("SpearRush", "Auto-aim and accelerate into a player while charging a spear in flight", Category.COMBAT);
        instance = this;
        with(this.speed, this.acceleration, this.range, this.cone, this.aimSpeed, this.onlyFlying);
    }

    public static SpearRush instance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        reset(false);
    }

    @Override
    protected void onDisable() {
        reset(false);
    }

    private void reset(boolean finishedCharge) {
        this.target = null;
        this.currentSpeed = 0.0;
        this.contactTicks = 0;
        this.finished = finishedCharge;
    }

    /** Start of LocalPlayer.tick. */
    public void beforeTick(LocalPlayer player) {
        if (!isEnabled()) {
            return;
        }
        boolean charging = player.isUsingItem() && player.getUseItem().is(ItemTags.SPEARS);
        boolean mayRush = !this.onlyFlying.value() || player.getAbilities().flying;
        if (!charging || !mayRush || !player.isAlive()) {
            reset(false);
            return;
        }
        if (this.finished) {
            return;
        }
        if (this.target == null || !this.target.isAlive() || this.target.level() != player.level()) {
            this.target = findTarget(player);
            if (this.target == null) {
                return;
            }
            this.currentSpeed = 0.0;
            this.contactTicks = 0;
        }
        if (this.contactTicks > POST_CONTACT_TICKS) {
            reset(true);
            return;
        }

        Vec3 toTarget = this.target.getBoundingBox().getCenter().subtract(player.getEyePosition());
        double horizontal = Math.hypot(toTarget.x, toTarget.z);
        float wantYaw = (float) (Math.toDegrees(Math.atan2(toTarget.z, toTarget.x)) - 90.0);
        float wantPitch = (float) -Math.toDegrees(Math.atan2(toTarget.y, horizontal));
        float k = (float) (this.aimSpeed.value() / 100.0);
        player.setYRot(player.getYRot() + Mth.wrapDegrees(wantYaw - player.getYRot()) * k);
        player.setXRot(player.getXRot() + (wantPitch - player.getXRot()) * k);

        if (toTarget.length() < 2.5) {
            this.contactTicks++;
        }
        // start from the speed you already have, then speed up every tick up to the top speed
        double horizontalNow = Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z);
        this.currentSpeed = Math.min(this.speed.value(),
                Math.max(this.currentSpeed, Math.max(0.3, horizontalNow)) + this.acceleration.value());
        player.setDeltaMovement(toTarget.normalize().scale(this.currentSpeed));
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
