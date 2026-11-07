package dev.hatek.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.hatek.client.module.impl.render.SwingAnimations;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void hatek$customSwing(float progress, PoseStack pose, int side, HumanoidArm arm, CallbackInfo ci) {
        SwingAnimations animations = SwingAnimations.instance();
        if (animations != null && animations.replacesSwing()) {
            animations.applySwing(pose, progress, side);
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"))
    private float hatek$noAttackDip(float scale) {
        SwingAnimations animations = SwingAnimations.instance();
        return animations != null && animations.replacesSwing() ? 1.0f : scale;
    }

    @ModifyVariable(method = "applyItemArmTransform", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float hatek$noEquipDip(float equipProgress) {
        SwingAnimations animations = SwingAnimations.instance();
        return animations != null && animations.skipEquipDip() ? 0.0f : equipProgress;
    }
}
