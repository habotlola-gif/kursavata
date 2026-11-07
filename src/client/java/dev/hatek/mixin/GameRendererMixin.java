package dev.hatek.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.hatek.client.module.impl.render.Removals;
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

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void hatek$noHurtCam(CameraRenderState camera, com.mojang.blaze3d.vertex.PoseStack pose, CallbackInfo ci) {
        if (Removals.removesHurtCam()) {
            ci.cancel();
        }
    }

    /** The world distortion of nausea and nether portals: the strength is zeroed. */
    @ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
    private float hatek$noNauseaWobble(float strength) {
        return Removals.removesNausea() ? 0.0f : strength;
    }

    @Inject(method = "mainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void hatek$redirectMainTarget(CallbackInfoReturnable<RenderTarget> cir) {
        RenderTarget target = ShaderHandsRenderer.redirectTarget();
        if (target != null) {
            cir.setReturnValue(target);
        }
    }
}
