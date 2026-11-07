package dev.hatek.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/** Projects world positions to framebuffer pixels using the matrices of the last rendered world frame. */
public final class WorldProjection {
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static Vec3 cameraPos = Vec3.ZERO;
    private static boolean valid;

    private WorldProjection() {
    }

    public static void capture(CameraRenderState state) {
        if (state == null || state.projectionMatrix == null || state.viewRotationMatrix == null) {
            return;
        }
        VIEW_PROJECTION.set(state.projectionMatrix).mul(state.viewRotationMatrix);
        cameraPos = state.pos;
        valid = true;
    }

    public static boolean isValid() {
        return valid;
    }

    public static Vec3 cameraPos() {
        return cameraPos;
    }

    /** Screen position {x, y} in framebuffer pixels, or null when the point is behind the camera. */
    public static float @Nullable [] toScreen(Vec3 world) {
        if (!valid) {
            return null;
        }
        Vector4f clip = new Vector4f((float) (world.x - cameraPos.x), (float) (world.y - cameraPos.y),
                (float) (world.z - cameraPos.z), 1.0f).mul(VIEW_PROJECTION);
        if (clip.w <= 0.05f) {
            return null;
        }
        var window = Minecraft.getInstance().getWindow();
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        return new float[]{(ndcX * 0.5f + 0.5f) * window.getWidth(), (0.5f - ndcY * 0.5f) * window.getHeight()};
    }
}
