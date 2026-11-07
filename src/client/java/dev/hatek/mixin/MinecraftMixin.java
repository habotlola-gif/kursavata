package dev.hatek.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.hatek.client.module.impl.render.ShaderEsp;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    /** Highlighted entities go into the entity-outline buffer, which ShaderESP then composites. */
    @ModifyReturnValue(method = "shouldEntityAppearGlowing", at = @At("RETURN"))
    private boolean hatek$espGlowing(boolean glowing, Entity entity) {
        ShaderEsp esp = ShaderEsp.instance();
        return glowing || (esp != null && esp.shouldHighlight(entity));
    }

    /** While SuperSpear owns the left click, the ordinary attack / stab of that click is skipped. */
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void hatek$spearOwnsClick(CallbackInfoReturnable<Boolean> cir) {
        if (dev.hatek.client.module.impl.combat.SuperSpear.ownsAttackClick()) {
            cir.setReturnValue(false);
        }
    }
}
