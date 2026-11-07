package dev.hatek.client.module.impl.combat.aura.util;

import dev.hatek.client.module.impl.combat.aura.rotation.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;
import org.joml.Vector4f;

public final class AuraUtil {
    private AuraUtil() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static float partialTick() {
        return mc().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    public static double getStrictDistance(Entity entity) {
        return getClosestVec(entity).length();
    }

    public static Rotation cameraAngle() {
        return new Rotation(mc().player.getYRot(), mc().player.getXRot());
    }

    public static boolean validDistance(Entity entity, float distance, boolean smart) {
        return getStrictDistance(entity) < distance;
    }

    public static Vec3 getClosestVec(Entity entity) {
        Vec3 eyePosVec = mc().player.getEyePosition();
        return getClosestVec(eyePosVec, entity).subtract(eyePosVec);
    }

    public static Vec3 getClosestVec(Vec3 vec, AABB aabb) {
        return new Vec3(Mth.clamp(vec.x, aabb.minX, aabb.maxX),
                Mth.clamp(vec.y, aabb.minY, aabb.maxY),
                Mth.clamp(vec.z, aabb.minZ, aabb.maxZ));
    }

    public static Vec3 getClosestVec(Vec3 vec, Entity entity) {
        return getClosestVec(vec, entity.getBoundingBox());
    }

    public static Vec3 getVector4(LivingEntity target) {
        Minecraft mc = mc();
        double wHalf = target.getBbWidth() / 2.0F;
        double xExpand = Mth.clamp(mc.player.getX() - target.getX(), -wHalf, wHalf);
        double zExpand = Mth.clamp(mc.player.getZ() - target.getZ(), -wHalf, wHalf);
        return new Vec3(target.getX() - mc.player.getX() + xExpand,
                target.getY() - mc.player.getY() - 0.8F,
                target.getZ() - mc.player.getZ() + zExpand);
    }

    public static Vec3 getVector3(LivingEntity target) {
        Minecraft mc = mc();
        double xExpand = Mth.clamp(mc.player.getX() - target.getX(), 0.0, 0.0);
        double zExpand = Mth.clamp(mc.player.getZ() - target.getZ(), 0.0, 0.0);
        return new Vec3(target.getX() - mc.player.getX() + xExpand,
                target.getY() - mc.player.getY() - 0.8F,
                target.getZ() - mc.player.getZ() + zExpand);
    }

    public static Vec3 getVector2(LivingEntity target) {
        Minecraft mc = mc();
        double yExpand = Mth.clamp(target.getEyeY() - target.getY(), 0.0, target.getBbHeight());
        double xExpand = Mth.clamp(mc.player.getX() - target.getX(), 0.0, 0.0);
        double zExpand = Mth.clamp(mc.player.getZ() - target.getZ(), 0.0, 0.0);
        return new Vec3(target.getX() - mc.player.getX() + xExpand,
                target.getY() - mc.player.getEyeY() + yExpand,
                target.getZ() - mc.player.getZ() + zExpand);
    }

    public static Vec3 getVector(LivingEntity target) {
        Minecraft mc = mc();
        double wHalf = target.getBbWidth() / 2.0F;
        double yExpand = Mth.clamp(target.getEyeY() - target.getY(), 0.0, target.getBbHeight());
        double xExpand = Mth.clamp(mc.player.getX() - target.getX(), -wHalf, wHalf);
        double zExpand = Mth.clamp(mc.player.getZ() - target.getZ(), -wHalf, wHalf);
        return new Vec3(target.getX() - mc.player.getX() + xExpand,
                target.getY() - mc.player.getEyeY() + yExpand,
                target.getZ() - mc.player.getZ() + zExpand);
    }

    public static double direction(float rotationYaw, float moveForward, float moveStrafing) {
        if (moveForward < 0.0F) {
            rotationYaw += 180.0F;
        }
        float forward = 1.0F;
        if (moveForward < 0.0F) {
            forward = -0.5F;
        }
        if (moveForward > 0.0F) {
            forward = 0.5F;
        }
        if (moveStrafing > 0.0F) {
            rotationYaw -= 90.0F * forward;
        }
        if (moveStrafing < 0.0F) {
            rotationYaw += 90.0F * forward;
        }
        return Math.toRadians(rotationYaw);
    }

    public static Vec3 getClosestTargetPoint(Vec3 vec, Entity entity, float point) {
        if (entity == null) {
            return Vec3.ZERO;
        }
        AABB box = entity.getBoundingBox().inflate(-point);
        Vec3 center = box.getCenter();
        Vec3 closestPoint = null;
        double closestDistance = Double.MAX_VALUE;

        for (double offsetX = 0.0; offsetX <= (box.maxX - box.minX) / 2.0; offsetX += 0.1) {
            for (double offsetY = 0.0; offsetY <= (box.maxY - box.minY) / 2.0; offsetY += 0.1) {
                for (double offsetZ = 0.0; offsetZ <= (box.maxZ - box.minZ) / 2.0; offsetZ += 0.1) {
                    for (int signX : new int[]{-1, 1}) {
                        for (int signY : new int[]{-1, 1}) {
                            for (int signZ : new int[]{-1, 1}) {
                                double x = center.x + signX * offsetX;
                                double y = center.y + signY * offsetY;
                                double z = center.z + signZ * offsetZ;
                                Vec3 potentialPoint = new Vec3(x, y, z);
                                Vector2f rotation = calculate(potentialPoint);

                                if (RayTraceUtil.calculateRayTrace(6.0, rotation.x, rotation.y,
                                        mc().player, false) instanceof EntityHitResult entityTrace
                                        && entityTrace.getEntity().equals(entity)) {
                                    double distance = vec.distanceTo(potentialPoint);
                                    if (distance < closestDistance) {
                                        closestDistance = distance;
                                        closestPoint = potentialPoint;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (closestPoint != null) {
            return closestPoint;
        }
        return new Vec3(Mth.clamp(vec.x, box.minX, box.maxX),
                Mth.clamp(vec.y, box.minY, box.maxY),
                Mth.clamp(vec.z, box.minZ, box.maxZ));
    }

    public static Vector2f calculate(Vec3 toVec) {
        Minecraft mc = mc();
        return calculate(new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ())
                .add(0.0, mc.player.getEyeY(), 0.0), toVec);
    }

    public static Vector2f calculate(Vec3 fromVec, Vec3 toVec) {
        Vec3 diff = toVec.subtract(fromVec);
        double distance = Math.hypot(diff.x, diff.z);
        float yaw = (float) (Mth.atan2(diff.z, diff.x) * (180.0 / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(diff.y, distance) * (180.0 / Math.PI)));
        return new Vector2f(yaw, pitch);
    }

    public static Vec3 getClosestTargetPoint(Entity entity) {
        return getClosestTargetPoint(mc().player.getEyePosition(partialTick()), entity,
                Math.min(entity.getBbWidth(), entity.getBbHeight()) / 4.0F);
    }

    public static Vector4f calculateRotationFromCamera(LivingEntity target) {
        Minecraft mc = mc();
        Vec3 eyePos = mc.player.getEyePosition(partialTick());
        Vec3 vec = getClosestTargetPoint(target).subtract(eyePos);
        float rawYaw = Mth.wrapDegrees((float) (Math.toDegrees(Math.atan2(vec.z, vec.x)) - 90.0));
        float rawPitch = (float) (-Math.toDegrees(
                Math.atan2(vec.y, Math.sqrt(vec.x * vec.x + vec.z * vec.z))));
        float yawDelta = Mth.wrapDegrees(rawYaw - mc.player.getYRot());
        float pitchDelta = rawPitch - mc.player.getXRot();
        return new Vector4f(rawYaw, rawPitch, yawDelta, pitchDelta);
    }

    public static double calculateFOVFromCamera(LivingEntity target) {
        Vector4f rotation = calculateRotationFromCamera(target);
        float yawDelta = rotation.z;
        float pitchDelta = rotation.w;
        return Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
    }
}
