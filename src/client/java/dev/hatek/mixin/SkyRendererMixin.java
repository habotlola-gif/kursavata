package dev.hatek.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.hatek.client.module.impl.render.CustomSky;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CustomSky draws its gradient before the sun and moon, its stars and aurora after them. */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Shadow
    @Final
    private RenderTarget renderTarget;

    @Inject(method = "renderSunMoonAndStars(Lcom/mojang/blaze3d/vertex/PoseStack;FFFLnet/minecraft/world/level/MoonPhase;FF)V",
            at = @At("HEAD"))
    private void hatek$skyDome(PoseStack pose, float sunAngle, float moonAngle, float starAngle, MoonPhase phase,
                               float rainClear, float starAlpha, CallbackInfo ci) {
        CustomSky.drawDome(this.renderTarget, pose, rainClear, starAlpha);
    }

    @Inject(method = "renderSunMoonAndStars(Lcom/mojang/blaze3d/vertex/PoseStack;FFFLnet/minecraft/world/level/MoonPhase;FF)V",
            at = @At("TAIL"))
    private void hatek$skyStars(PoseStack pose, float sunAngle, float moonAngle, float starAngle, MoonPhase phase,
                                float rainClear, float starAlpha, CallbackInfo ci) {
        CustomSky.drawStarsAndAurora(this.renderTarget, pose, starAngle, rainClear, starAlpha);
    }

    @Inject(method = "renderStars(FLcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"), cancellable = true)
    private void hatek$noVanillaStars(float alpha, PoseStack pose, CallbackInfo ci) {
        if (CustomSky.replacesVanillaStars()) {
            ci.cancel();
        }
    }
}
