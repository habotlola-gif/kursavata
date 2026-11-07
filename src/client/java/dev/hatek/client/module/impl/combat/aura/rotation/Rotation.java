package dev.hatek.client.module.impl.combat.aura.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

public class Rotation {
    public float yaw;
    public float pitch;

    public Rotation(Entity entity) {
        this.yaw = entity.getYRot();
        this.pitch = entity.getXRot();
    }

    public Rotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float getDelta(Rotation target) {
        float yawDelta = Mth.wrapDegrees(target.yaw - this.yaw);
        float pitchDelta = target.pitch - this.pitch;
        return (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));
    }

    public double getDeltaDouble(Rotation target) {
        double yawDelta = Mth.wrapDegrees(target.yaw - this.yaw);
        double pitchDelta = Mth.wrapDegrees(target.pitch - this.pitch);
        return Math.hypot(yawDelta, pitchDelta);
    }

    public float getYaw() {
        return this.yaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    public Vec3 toVector() {
        float pitchRad = this.pitch * (float) (Math.PI / 180.0);
        float yawRad = -this.yaw * (float) (Math.PI / 180.0);
        float cosYaw = Mth.cos(yawRad);
        float sinYaw = Mth.sin(yawRad);
        float cosPitch = Mth.cos(pitchRad);
        float sinPitch = Mth.sin(pitchRad);
        return new Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }

    public static Vector2f camera() {
        return new Vector2f(cameraYaw(), cameraPitch());
    }

    public static float cameraYaw() {
        Minecraft mc = Minecraft.getInstance();
        return Mth.wrapDegrees(mc.gameRenderer.mainCamera().yRot()
                + (mc.gameRenderer.mainCamera().isDetached() ? 180 : 0));
    }

    public static float cameraPitch() {
        Minecraft mc = Minecraft.getInstance();
        return (mc.gameRenderer.mainCamera().isDetached() ? -1 : 1) * mc.gameRenderer.mainCamera().xRot();
    }
}
