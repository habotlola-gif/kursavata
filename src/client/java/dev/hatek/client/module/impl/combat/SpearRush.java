package dev.hatek.client.module.impl.combat;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * SpearRush: the module is on and waits. While you are flying and charging a spear (right mouse held) and a player is
 * in the direction you are aiming, it launches you through that player at high speed and keeps the aim on them. The
 * spear's charge damage grows with the speed you move at (the server measures it from your movement), so the rush
 * hits very hard. One rush per charge: release the right mouse button and charge again to rush again.
 */
public final class SpearRush extends Module {
    private static final int POST_CONTACT_TICKS = 10;

    private static SpearRush instance;

    private final SliderSetting range = new SliderSetting("Range (blocks)", 60.0, 10.0, 100.0, 1.0);
    private final SliderSetting cone = new SliderSetting("Aim cone (degrees)", 12.0, 2.0, 45.0, 1.0);
    private final SliderSetting speed = new SliderSetting("Speed (blocks/tick)", 2.0, 0.5, 5.0, 0.1);
    private final SliderSetting maxTicks = new SliderSetting("Max rush (ticks)", 40.0, 10.0, 120.0, 5.0);
    private final BoolSetting onlyFlying = new BoolSetting("Only while flying", true);
    private final BoolSetting correctAim = new BoolSetting("Keep aim on target", true);

    private LivingEntity target;
    private boolean rushing;
    private boolean finished;     // this charge is used up; wait for the button to be released
    private int ticks;
    private int contactTicks;

    public SpearRush() {
        super("SpearRush", "Rush through the player you aim at with a charged spear while flying", Category.COMBAT);
        instance = this;
        with(this.range, this.cone, this.speed, this.maxTicks, this.onlyFlying, this.correctAim);
    }

    public static SpearRush instance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        stop(false);
    }

    @Override
    protected void onDisable() {
        stop(false);
    }

    private void stop(boolean finishedCharge) {
        this.rushing = false;
        this.target = null;
        this.ticks = 0;
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
            stop(false);
            return;
        }
        if (this.finished) {
            return;
        }
        if (!this.rushing) {
            this.target = findAimedTarget(player);
            if (this.target == null) {
                return;
            }
            this.rushing = true;
            this.ticks = 0;
            this.contactTicks = 0;
        }
        if (!this.target.isAlive() || this.target.level() != player.level()
                || ++this.ticks > Math.round(this.maxTicks.value()) || this.contactTicks > POST_CONTACT_TICKS) {
            stop(true);
            return;
        }

        Vec3 aim = this.target.getBoundingBox().getCenter();
        Vec3 toTarget = aim.subtract(player.getEyePosition());
        if (this.correctAim.value()) {
            double horizontal = Math.hypot(toTarget.x, toTarget.z);
            player.setYRot((float) (Math.toDegrees(Math.atan2(toTarget.z, toTarget.x)) - 90.0));
            player.setXRot((float) -Math.toDegrees(Math.atan2(toTarget.y, horizontal)));
        }
        if (toTarget.length() < 2.5) {
            this.contactTicks++;
        }
        player.setDeltaMovement(toTarget.normalize().scale(this.speed.value()));
    }

    /** The player closest to the line of sight (within the aim cone and the range). */
    private LivingEntity findAimedTarget(LocalPlayer player) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double maxRange = this.range.value();
        double maxAngle = this.cone.value();
        LivingEntity best = null;
        double bestAngle = Double.MAX_VALUE;
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
            if (angle <= maxAngle && angle < bestAngle) {
                bestAngle = angle;
                best = other;
            }
        }
        return best;
    }
}
