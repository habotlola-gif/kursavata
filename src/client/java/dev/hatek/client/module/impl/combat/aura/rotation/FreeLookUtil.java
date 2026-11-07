package dev.hatek.client.module.impl.combat.aura.rotation;

import net.minecraft.util.Mth;

public class FreeLookUtil extends Component {
    public static boolean active;
    public static float freeYaw;
    public static float freePitch;

    public static boolean detached() {
        return active || RotationProcess.isRotating();
    }

    public boolean onLook(double deltaYaw, double deltaPitch) {
        if (!detached()) {
            dev.hatek.client.module.impl.combat.aura.util.PlayerStats.frame(
                    deltaYaw * 0.15, deltaPitch * 0.15);
            return false;
        }
        rotateTowards(deltaYaw, deltaPitch);
        return true;
    }

    public float[] onRotation(float yaw, float pitch) {
        if (detached()) {
            return new float[]{freeYaw, freePitch};
        }
        freeYaw = yaw;
        freePitch = pitch;
        return new float[]{yaw, pitch};
    }

    private void rotateTowards(double targetYaw, double targetPitch) {
        freePitch = Mth.clamp((float) (freePitch + targetPitch * 0.15), -90.0F, 90.0F);
        freeYaw = (float) (freeYaw + targetYaw * 0.15);
    }
}
