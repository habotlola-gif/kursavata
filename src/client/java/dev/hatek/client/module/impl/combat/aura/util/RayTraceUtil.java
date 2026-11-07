package dev.hatek.client.module.impl.combat.aura.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.function.Predicate;

public final class RayTraceUtil {
    private RayTraceUtil() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static float partialTick(boolean runsNormally) {
        return mc().getDeltaTracker().getGameTimeDeltaPartialTick(runsNormally);
    }

    public static EntityHitResult traceEntities(Entity shooter, Vec3 startVector, Vec3 endVector,
                                                AABB boundingBox, Predicate<Entity> filter,
                                                double distance) {
        Level level = mc().level;
        double closestDistance = distance;
        Entity closestEntity = null;
        Vec3 closestHitVector = null;

        for (Entity entity : level.getEntities(shooter, boundingBox, filter)) {
            AABB entityBoundingBox = entity.getBoundingBox().inflate(entity.getPickRadius());
            Optional<Vec3> optional = entityBoundingBox.clip(startVector, endVector);

            if (entityBoundingBox.contains(startVector) || optional.isPresent()) {
                double distanceToHit = optional.map(startVector::distanceToSqr).orElse(0.0);
                distanceToHit = Math.sqrt(distanceToHit);

                if ((distanceToHit < closestDistance || closestDistance == 0.0)
                        && entity.getRootVehicle() != shooter.getRootVehicle()) {
                    closestEntity = entity;
                    closestHitVector = optional.orElse(startVector);
                    closestDistance = distanceToHit;
                }
            }
        }
        return closestEntity == null ? null : new EntityHitResult(closestEntity, closestHitVector);
    }

    public static HitResult calculateRayTrace(double distance, float yaw, float pitch,
                                              Entity entity, boolean ignoreBlocks) {
        Minecraft mc = mc();
        float tickDelta = partialTick(true);
        Vec3 startVector = mc.player.getEyePosition(tickDelta);
        Vec3 directionVector = getVectorForRotation(pitch, yaw);
        Vec3 endVector = startVector.add(directionVector.scale(distance));
        HitResult blockResult = traceBlock(startVector, endVector,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE);
        double entityDistance = blockResult.getLocation().distanceToSqr(startVector);
        AABB entityBoundingBox = entity.getBoundingBox()
                .expandTowards(directionVector.scale(distance)).inflate(1.0);
        EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(entity,
                startVector, endVector, entityBoundingBox,
                x -> !x.isSpectator() && x.isAlive() && x.isPickable(), distance * distance);

        return entityHitResult == null
                || !ignoreBlocks
                && !(entityHitResult.getLocation().distanceToSqr(startVector) < entityDistance)
                ? blockResult
                : entityHitResult;
    }

    public static boolean rayTraceEntity(float yaw, float pitch, double distance, Entity entity) {
        return rayTraceEntity(mc().player.getEyePosition(partialTick(true)),
                yaw, pitch, distance, entity, 0.16);
    }

    public static boolean rayTraceEntity(Vec3 eyeVec, float yaw, float pitch, double distance,
                                         Entity entity, double tolerance) {
        Vec3 lookVec = getVectorForRotation(pitch, yaw);
        Vec3 endVec = eyeVec.add(lookVec.scale(distance));
        AABB entityBox = entity.getBoundingBox().inflate(entity.getPickRadius() + tolerance);
        return entityBox.contains(eyeVec) || entityBox.clip(eyeVec, endVec).isPresent();
    }

    public static Vec3 getVectorForRotation(float pitch, float yaw) {
        float yawRadians = -yaw * (float) (Math.PI / 180.0) - (float) Math.PI;
        float pitchRadians = -pitch * (float) (Math.PI / 180.0);
        float cosYaw = (float) Math.cos(yawRadians);
        float sinYaw = (float) Math.sin(yawRadians);
        float cosPitch = -(float) Math.cos(pitchRadians);
        float sinPitch = (float) Math.sin(pitchRadians);
        return new Vec3(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
    }

    public static HitResult traceBlock(Vec3 startVec, Vec3 endVec,
                                       ClipContext.Block blockMode, ClipContext.Fluid fluidMode) {
        Minecraft mc = mc();
        return mc.level.clip(new ClipContext(startVec, endVec, blockMode, fluidMode, mc.player));
    }

    public static Vec3 calculateViewVector(float yaw, float pitch) {
        float pitchRad = (float) (pitch * (Math.PI / 180.0));
        float yawRad = (float) (-yaw * (Math.PI / 180.0));
        float cosYaw = (float) Math.cos(yawRad);
        float sinYaw = (float) Math.sin(yawRad);
        float cosPitch = (float) Math.cos(pitchRad);
        float sinPitch = (float) Math.sin(pitchRad);
        return new Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }
}
