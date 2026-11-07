package dev.hatek.mixin;

import dev.hatek.client.module.impl.combat.OffhandSwapper;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stand still while AutoSwap does an inventory click (servers check movement during inventory actions). */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void hatek$pause(CallbackInfo ci) {
        if (OffhandSwapper.pauseMovement()) {
            this.keyPresses = new Input(false, false, false, false, false, false, false);
            this.moveVector = Vec2.ZERO;
        }
    }
}
