package dev.hatek.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.hatek.client.module.impl.render.Removals;
import net.minecraft.client.renderer.fog.environment.MobEffectFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** No fog from Blindness and Darkness. */
@Mixin(MobEffectFogEnvironment.class)
public abstract class MobEffectFogEnvironmentMixin {
    @ModifyReturnValue(method = "isApplicable", at = @At("RETURN"))
    private boolean hatek$noEffectFog(boolean applicable) {
        return applicable && !Removals.removesBlindness();
    }
}
