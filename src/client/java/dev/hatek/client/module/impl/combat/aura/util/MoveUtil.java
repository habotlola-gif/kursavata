package dev.hatek.client.module.impl.combat.aura.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public final class MoveUtil {
    private MoveUtil() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
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

    public static void restoreSprintKey() {
        Minecraft mc = mc();
        if (mc.options.keySprint.isDown()) {
            return;
        }
        if (InputConstants.isKeyDown(mc.getWindow(), mc.options.keySprint.getDefaultKey().getValue())) {
            mc.options.keySprint.setDown(true);
        }
    }

    public static void restoreMovementKeys() {
        Minecraft mc = mc();
        if (mc.options == null) {
            return;
        }
        mc.options.keyUp.setDown(physicallyDown(mc.options.keyUp));
        mc.options.keyDown.setDown(physicallyDown(mc.options.keyDown));
        mc.options.keyLeft.setDown(physicallyDown(mc.options.keyLeft));
        mc.options.keyRight.setDown(physicallyDown(mc.options.keyRight));
    }

    private static boolean physicallyDown(net.minecraft.client.KeyMapping key) {
        return InputConstants.isKeyDown(mc().getWindow(), key.getDefaultKey().getValue());
    }

    public static boolean isMoving() {
        Minecraft mc = mc();
        return mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
    }

    public static float[] getMovementFromKeys() {
        Minecraft mc = mc();
        float forward = 0.0F;
        float strafe = 0.0F;
        if (InputConstants.isKeyDown(mc.getWindow(), mc.options.keyUp.getDefaultKey().getValue())) {
            forward++;
        }
        if (InputConstants.isKeyDown(mc.getWindow(), mc.options.keyDown.getDefaultKey().getValue())) {
            forward--;
        }
        if (InputConstants.isKeyDown(mc.getWindow(), mc.options.keyLeft.getDefaultKey().getValue())) {
            strafe++;
        }
        if (InputConstants.isKeyDown(mc.getWindow(), mc.options.keyRight.getDefaultKey().getValue())) {
            strafe--;
        }
        return new float[]{forward, strafe};
    }

    public static void fixMovement(float cameraYaw) {
        float[] movement = getMovementFromKeys();
        float forward = movement[0];
        float strafe = movement[1];
        if (forward == 0.0F && strafe == 0.0F) {
            return;
        }
        apply(forward, strafe, cameraYaw);
    }

    private static void apply(float forward, float strafe, float wantedYaw) {
        Minecraft mc = mc();
        double angle = Mth.wrapDegrees(Math.toDegrees(
                direction(mc.player.isPassenger() ? mc.player.getYRot() : wantedYaw, forward, strafe)));
        float closestForward = 0.0F;
        float closestStrafe = 0.0F;
        float closestDifference = Float.MAX_VALUE;

        for (float predictedForward = -1.0F; predictedForward <= 1.0F; predictedForward++) {
            for (float predictedStrafe = -1.0F; predictedStrafe <= 1.0F; predictedStrafe++) {
                if (predictedStrafe == 0.0F && predictedForward == 0.0F) {
                    continue;
                }
                double predictedAngle = Mth.wrapDegrees(Math.toDegrees(
                        direction(mc.player.getYRot(), predictedForward, predictedStrafe)));
                double difference = Math.abs(angle - predictedAngle);
                if (difference < closestDifference) {
                    closestDifference = (float) difference;
                    closestForward = predictedForward;
                    closestStrafe = predictedStrafe;
                }
            }
        }

        mc.options.keyUp.setDown(closestForward > 0.0F);
        mc.options.keyDown.setDown(closestForward < 0.0F);
        mc.options.keyLeft.setDown(closestStrafe > 0.0F);
        mc.options.keyRight.setDown(closestStrafe < 0.0F);
    }

    public static float[] getSilentMove(float forward, float strafe, float playerYaw, float cameraYaw) {
        double desiredAngle = direction(cameraYaw, forward, strafe);
        double playerRad = Math.toRadians(playerYaw);
        double relativeAngle = desiredAngle - playerRad;
        float newForward = (float) Math.cos(relativeAngle);
        float newStrafe = (float) Math.sin(relativeAngle);
        float maxMag = Math.max(Math.abs(forward), Math.abs(strafe));
        float currentMag = (float) Math.hypot(newForward, newStrafe);

        if (currentMag != 0.0F) {
            newForward = newForward / currentMag * maxMag;
            newStrafe = newStrafe / currentMag * maxMag;
        }
        return new float[]{newForward, newStrafe};
    }
}
