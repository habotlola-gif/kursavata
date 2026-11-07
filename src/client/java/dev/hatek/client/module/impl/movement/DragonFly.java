package dev.hatek.client.module.impl.movement;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * DragonFly: faster flight while the server lets you fly (creative mode or /fly), plus instant, full-speed
 * horizontal movement. Works only while the player is actually flying. Ported from another client's module.
 */
public final class DragonFly extends Module {
    private static final float VANILLA_FLY_SPEED = 0.05f;

    private static DragonFly instance;

    private final SliderSetting speed = new SliderSetting("Speed", 2.0, 0.5, 5.0, 0.1);
    private final BoolSetting instantMotion = new BoolSetting("Instant motion", true);

    public DragonFly() {
        super("DragonFly", "Faster flight when flying is allowed (/fly), with instant motion", Category.MOVEMENT);
        instance = this;
        with(this.speed, this.instantMotion);
    }

    public static DragonFly instance() {
        return instance;
    }

    /** Start of LocalPlayer.tick, before the movement of this tick is calculated. */
    public void beforeTick(LocalPlayer player) {
        if (!isEnabled()) {
            return;
        }
        if (!player.getAbilities().flying) {
            player.getAbilities().setFlyingSpeed(VANILLA_FLY_SPEED);
            return;
        }
        player.getAbilities().setFlyingSpeed((float) this.speed.value() * VANILLA_FLY_SPEED);
        if (this.instantMotion.value()) {
            applyInstantMotion(player);
        }
    }

    private void applyInstantMotion(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 velocity = player.getDeltaMovement();
        double speedValue = this.speed.value();

        float forward = (mc.options.keyUp.isDown() ? 1.0f : 0.0f) - (mc.options.keyDown.isDown() ? 1.0f : 0.0f);
        float sideways = (mc.options.keyLeft.isDown() ? 1.0f : 0.0f) - (mc.options.keyRight.isDown() ? 1.0f : 0.0f);

        double velX = 0.0;
        double velZ = 0.0;
        if (forward != 0.0f || sideways != 0.0f) {
            double yaw = Math.toRadians(player.getYRot());
            double forwardX = -Math.sin(yaw);
            double forwardZ = Math.cos(yaw);
            double leftX = Math.cos(yaw);
            double leftZ = Math.sin(yaw);
            velX = (forwardX * forward + leftX * sideways) * speedValue;
            velZ = (forwardZ * forward + leftZ * sideways) * speedValue;
        }
        double velY = velocity.y;
        if (mc.options.keyJump.isDown()) {
            velY = speedValue * 0.2;
        } else if (mc.options.keyShift.isDown()) {
            velY = -speedValue * 0.2;
        }
        player.setDeltaMovement(velX, velY, velZ);
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.getAbilities().setFlyingSpeed(VANILLA_FLY_SPEED);
        }
    }
}
