package dev.hatek.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.hatek.client.render.ShaderHandsRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    private static final String RENDER_ALL = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures(Lnet/minecraft/client/renderer/SubmitNodeStorage;)V";

    @Inject(method = "renderItemInHand", at = @At(value = "INVOKE", target = RENDER_ALL, shift = At.Shift.BEFORE))
    private void hatek$beginHands(CameraRenderState camera, float partial, Matrix4fc modelView, CallbackInfo ci) {
        ShaderHandsRenderer.begin();
    }

    @Inject(method = "renderItemInHand", at = @At(value = "INVOKE", target = RENDER_ALL, shift = At.Shift.AFTER))
    private void hatek$endHands(CameraRenderState camera, float partial, Matrix4fc modelView, CallbackInfo ci) {
        if (ShaderHandsRenderer.isRedirecting()) {
            ShaderHandsRenderer.end();
        }
    }

    @Inject(method = "mainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void hatek$redirectMainTarget(CallbackInfoReturnable<RenderTarget> cir) {
        RenderTarget target = ShaderHandsRenderer.redirectTarget();
        if (target != null) {
            cir.setReturnValue(target);
        }
    }
}
