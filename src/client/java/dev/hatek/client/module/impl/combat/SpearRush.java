package dev.hatek.client.module.impl.combat;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * SpearRush: while you fly and charge a spear (right mouse held), the module accelerates you along the direction you
 * are aiming, faster and faster up to the chosen speed. You aim, the flight does the speeding up. The spear's charge
 * damage grows with the speed you move at (the server measures it from your movement), so a long, fast approach
 * hits very hard. Releasing the right mouse button ends the acceleration.
 */
public final class SpearRush extends Module {
    private static SpearRush instance;

    private final SliderSetting speed = new SliderSetting("Top speed (blocks/tick)", 2.0, 0.5, 5.0, 0.1);
    private final SliderSetting acceleration = new SliderSetting("Acceleration (blocks/tick per tick)", 0.15, 0.02, 1.0, 0.01);
    private final BoolSetting onlyFlying = new BoolSetting("Only while flying", true);
    private final BoolSetting aimAssist = new BoolSetting("Aim assist (small)", false);

    private double currentSpeed;

    public SpearRush() {
        super("SpearRush", "Accelerate along your aim while charging a spear in flight", Category.COMBAT);
        instance = this;
        with(this.speed, this.acceleration, this.onlyFlying, this.aimAssist);
    }

    public static SpearRush instance() {
        return instance;
    }

    @Override
    protected void onDisable() {
        this.currentSpeed = 0.0;
    }

    /** Start of LocalPlayer.tick. */
    public void beforeTick(LocalPlayer player) {
        if (!isEnabled()) {
            return;
        }
        boolean charging = player.isUsingItem() && player.getUseItem().is(ItemTags.SPEARS);
        boolean mayRush = !this.onlyFlying.value() || player.getAbilities().flying;
        if (!charging || !mayRush || !player.isAlive()) {
            this.currentSpeed = 0.0;
            return;
        }
        if (this.aimAssist.value()) {
            assistAim(player);
        }
        // start from the speed you already have, then speed up every tick up to the top speed
        double horizontalNow = Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z);
        this.currentSpeed = Math.min(this.speed.value(),
                Math.max(this.currentSpeed, Math.max(0.3, horizontalNow)) + this.acceleration.value());
        player.setDeltaMovement(player.getLookAngle().normalize().scale(this.currentSpeed));
    }

    /** Pulls the aim a little toward a player who is already almost in the crosshair (within about 8 degrees). */
    private void assistAim(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 best = null;
        double bestAngle = 8.0;
        for (Player other : mc.level.players()) {
            if (other == player || other.isSpectator() || !other.isAlive()) {
                continue;
            }
            Vec3 to = other.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            if (distance < 0.5 || distance > 100.0) {
                continue;
            }
            double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, look.dot(to.normalize())))));
            if (angle < bestAngle) {
                bestAngle = angle;
                best = to;
            }
        }
        if (best == null) {
            return;
        }
        double horizontal = Math.hypot(best.x, best.z);
        float wantYaw = (float) (Math.toDegrees(Math.atan2(best.z, best.x)) - 90.0);
        float wantPitch = (float) -Math.toDegrees(Math.atan2(best.y, horizontal));
        float dYaw = net.minecraft.util.Mth.wrapDegrees(wantYaw - player.getYRot());
        player.setYRot(player.getYRot() + dYaw * 0.35f);
        player.setXRot(player.getXRot() + (wantPitch - player.getXRot()) * 0.35f);
    }
}
