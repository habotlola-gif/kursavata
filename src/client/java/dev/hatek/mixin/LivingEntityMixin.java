package dev.hatek.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.hatek.client.module.impl.render.SwingAnimations;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyReturnValue(method = "getCurrentSwingDuration", at = @At("RETURN"))
    private int hatek$swingSpeed(int duration) {
        SwingAnimations animations = SwingAnimations.instance();
        if ((Object) this instanceof LocalPlayer && animations != null) {
            return animations.adjustDuration(duration);
        }
        return duration;
    }
}
