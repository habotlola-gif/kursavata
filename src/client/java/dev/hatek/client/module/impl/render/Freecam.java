package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Freecam: the camera leaves the body and flies on its own (keys move it, the mouse turns it), while the player stands
 * still and nothing is sent to the server differently. Switching it off puts the view back on the player.
 */
public final class Freecam extends Module {
    private static Freecam instance;

    private final SliderSetting speed = new SliderSetting("Speed (blocks/tick)", 1.0, 0.1, 5.0, 0.1);
    private final BoolSetting showBody = new BoolSetting("Show own body", true);

    private Vec3 position = Vec3.ZERO;
    private Vec3 previous = Vec3.ZERO;
    private float yaw;
    private float pitch;

    public Freecam() {
        super("Freecam", "Fly the camera around while your body stays in place", Category.RENDER);
        instance = this;
        with(this.speed, this.showBody);
    }

    public static boolean active() {
        return instance != null && instance.isEnabled() && Minecraft.getInstance().player != null;
    }

    public static boolean showBody() {
        return instance != null && instance.showBody.value();
    }

    public static float yaw() {
        return instance.yaw;
    }

    public static float pitch() {
        return instance.pitch;
    }

    public static Vec3 position(float partial) {
        Freecam self = instance;
        return self.previous.lerp(self.position, partial);
    }

    /** Mouse movement turns the camera instead of the player (same scale as {@code Entity.turn}). */
    public static void turn(double dx, double dy) {
        Freecam self = instance;
        self.yaw += (float) dx * 0.15f;
        self.pitch = Mth.clamp(self.pitch + (float) dy * 0.15f, -90.0f, 90.0f);
    }

    @Override
    public boolean persistsEnabled() {
        return false;
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        this.position = mc.player.getEyePosition();
        this.previous = this.position;
        this.yaw = mc.player.getYRot();
        this.pitch = mc.player.getXRot();
    }

    @Override
    public void onClientTick() {
        if (!isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            setEnabled(false);
            return;
        }
        this.previous = this.position;
        if (mc.gui.screen() != null) {
            return;
        }
        double yawRad = Math.toRadians(this.yaw);
        double pitchRad = Math.toRadians(this.pitch);
        Vec3 forward = new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));
        Vec3 right = new Vec3(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));
        Vec3 move = Vec3.ZERO;
        if (mc.options.keyUp.isDown()) {
            move = move.add(forward);
        }
        if (mc.options.keyDown.isDown()) {
            move = move.subtract(forward);
        }
        if (mc.options.keyRight.isDown()) {
            move = move.add(right);
        }
        if (mc.options.keyLeft.isDown()) {
            move = move.subtract(right);
        }
        if (mc.options.keyJump.isDown()) {
            move = move.add(0.0, 1.0, 0.0);
        }
        if (mc.options.keyShift.isDown()) {
            move = move.add(0.0, -1.0, 0.0);
        }
        if (move.lengthSqr() > 1.0E-6) {
            double factor = this.speed.value() * (mc.options.keySprint.isDown() ? 2.0 : 1.0);
            this.position = this.position.add(move.normalize().scale(factor));
        }
    }
}
