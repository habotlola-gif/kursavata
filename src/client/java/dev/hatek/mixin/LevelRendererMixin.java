package dev.hatek.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.hatek.client.render.ShaderEspRenderer;
import dev.hatek.client.render.WorldProjection;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
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
    // the entityOutlineTarget() accessor returns null once the frame graph is done; vanilla blits the field
    @Shadow
    @Final
    private RenderTarget entityOutlineTarget;

    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    /** ShaderESP replaces the vanilla blur of the outline buffer, so the blur pass is skipped. */
    @WrapWithCondition(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/PostChain;addToFrame(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;IILnet/minecraft/client/renderer/PostChain$TargetBundle;)V",
            ordinal = 0))
    private boolean hatek$skipVanillaOutline(PostChain chain, FrameGraphBuilder builder, int width, int height,
                                             PostChain.TargetBundle targets) {
        return !ShaderEspRenderer.isActive();
    }

    @Inject(method = "doEntityOutline", at = @At("HEAD"), cancellable = true)
    private void hatek$shaderEsp(CallbackInfo ci) {
        if (ShaderEspRenderer.isActive()) {
            // like vanilla: only draw when entity outlines were rendered this frame (the buffer is cleared then)
            if (this.levelRenderState.shouldShowEntityOutlines && this.entityOutlineTarget != null) {
                ShaderEspRenderer.composite(this.entityOutlineTarget);
            }
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void hatek$captureCamera(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean renderOutline,
                                     CameraRenderState camera, Matrix4fc modelView, GpuBufferSlice fogBuffer,
                                     Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        WorldProjection.capture(camera);
    }
}
