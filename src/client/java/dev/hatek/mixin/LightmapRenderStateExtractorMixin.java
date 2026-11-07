package dev.hatek.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.hatek.client.module.impl.render.Fullbright;
import dev.hatek.client.module.impl.render.Removals;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** No darkening of the world from the Darkness effect. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {
    @ModifyExpressionValue(method = "extract",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
    private float hatek$noDarkness(float factor) {
        return Removals.removesBlindness() ? 0.0f : factor;
    }

    @Inject(method = "extract", at = @At("RETURN"))
    private void hatek$fullbright(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
        Fullbright.apply(renderState);
    }
}
