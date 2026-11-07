package dev.hatek.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.hatek.client.render.WorldProjection;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void hatek$captureCamera(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean renderOutline,
                                     CameraRenderState camera, Matrix4fc modelView, GpuBufferSlice fogBuffer,
                                     Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        WorldProjection.capture(camera);
    }
}
