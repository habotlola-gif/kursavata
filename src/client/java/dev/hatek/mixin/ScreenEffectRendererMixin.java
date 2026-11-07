package dev.hatek.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.hatek.client.module.impl.render.Removals;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
    @ModifyExpressionValue(method = "submit",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isOnFire()Z"))
    private boolean hatek$noFire(boolean onFire) {
        return onFire && !Removals.removesFire();
    }

    /** "Lower" mode: the fire overlay is moved down so it no longer fills the view. */
    @Inject(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
    private void hatek$lowerFire(boolean firstPerson, boolean sleeping, float partial, SubmitNodeCollector collector,
                                 boolean hideGui, CallbackInfo ci, @Local PoseStack poseStack) {
        float offset = Removals.fireOffset();
        if (offset != 0.0f) {
            poseStack.translate(0.0f, offset, 0.0f);
        }
    }

    @ModifyExpressionValue(method = "submit",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean hatek$noWaterOverlay(boolean inFluid) {
        return inFluid && !Removals.removesFluidOverlay();
    }

    @ModifyExpressionValue(method = "submit",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;getViewBlockingState(Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState hatek$noBlockOverlay(BlockState state) {
        return Removals.removesFluidOverlay() ? null : state;
    }

    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
    private void hatek$noTotemAnimation(net.minecraft.world.item.ItemStack stack, net.minecraft.util.RandomSource random, CallbackInfo ci) {
        if (Removals.removesTotem()) {
            ci.cancel();
        }
    }
}
