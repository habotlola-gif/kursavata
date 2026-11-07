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

/**
 * Movement keys are ignored while an inventory swap needs the player to stand still, and while SuperSpear steers the
 * player into a target (the player's own input must not fight the rush).
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void hatek$pause(CallbackInfo ci) {
        if (OffhandSwapper.pauseMovement() || dev.hatek.client.module.impl.combat.SuperSpear.isActive()
                || dev.hatek.client.module.impl.render.Freecam.active()) {
            this.keyPresses = new Input(false, false, false, false, false, false, false);
            this.moveVector = Vec2.ZERO;
            return;
        }
        // AttackAura "Free" movement: the keys keep their own state, the movement just follows the camera
        float cameraYaw = dev.hatek.client.module.impl.combat.AttackAura.freeMoveYaw();
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (!Float.isNaN(cameraYaw) && player != null && (this.moveVector.x != 0.0f || this.moveVector.y != 0.0f)) {
            float magnitude = (float) Math.hypot(this.moveVector.x, this.moveVector.y);
            float[] turned = dev.hatek.client.module.impl.combat.aura.util.MoveUtil.reproject(
                    this.moveVector.y, this.moveVector.x, cameraYaw, player.getYRot());
            float length = (float) Math.hypot(turned[0], turned[1]);
            if (length > 1.0E-4f) {
                this.moveVector = new Vec2(turned[1] / length * magnitude, turned[0] / length * magnitude);
            }
        }
    }
}
