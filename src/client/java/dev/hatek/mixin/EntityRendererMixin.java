package dev.hatek.mixin;

import dev.hatek.client.module.impl.render.NameTags;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    /** NameTags draws its own card, so the vanilla name tag text is dropped (the "below name" score stays). */
    @Inject(method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V",
            at = @At("TAIL"))
    private void hatek$hideVanillaNameTag(Entity entity, EntityRenderState state, float partial, double nameTagDistance,
                                          double belowNameDistance, CallbackInfo ci) {
        if (NameTags.replacesVanilla(entity)) {
            state.nameTag = null;
        }
    }
}
