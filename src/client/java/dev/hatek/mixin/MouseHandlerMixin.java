package dev.hatek.mixin;

import dev.hatek.client.module.impl.combat.aura.rotation.ComponentManager;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Redirect(
            method = "turnPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"
            )
    )
    private void hatek$onLook(LocalPlayer player, double yaw, double pitch) {
        if (!ComponentManager.look(yaw, pitch)) {
            player.turn(yaw, pitch);
        }
    }
}
