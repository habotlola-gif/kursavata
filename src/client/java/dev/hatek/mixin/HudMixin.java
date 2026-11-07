package dev.hatek.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.hatek.client.module.impl.render.Removals;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void hatek$noPortalOverlay(GuiGraphicsExtractor graphics, float intensity, CallbackInfo ci) {
        if (Removals.removesNausea()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractConfusionOverlay", at = @At("HEAD"), cancellable = true)
    private void hatek$noConfusionOverlay(GuiGraphicsExtractor graphics, float intensity, CallbackInfo ci) {
        if (Removals.removesNausea()) {
            ci.cancel();
        }
    }

    /** Pumpkin / carved-head blur and powder snow outline. */
    @WrapWithCondition(method = "extractCameraOverlays", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V"))
    private boolean hatek$noTextureOverlays(Hud hud, GuiGraphicsExtractor graphics, Identifier texture, float alpha) {
        return !Removals.removesOverlays();
    }
}
