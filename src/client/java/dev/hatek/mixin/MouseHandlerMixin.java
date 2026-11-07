package dev.hatek.mixin;

import dev.hatek.client.module.impl.combat.aura.rotation.ComponentManager;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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
        if (dev.hatek.client.module.impl.render.Freecam.active()) {
            dev.hatek.client.module.impl.render.Freecam.turn(yaw, pitch);
            return;
        }
        if (!ComponentManager.look(yaw, pitch)) {
            player.turn(yaw, pitch);
        }
    }

    /** Records the wheel for wheel binds; when a module is bound to the wheel the hotbar does not scroll. */
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void hatek$wheel(long window, double xOffset, double yOffset, CallbackInfo ci) {
        dev.hatek.client.module.Wheel.record(yOffset);
        if (net.minecraft.client.Minecraft.getInstance().gui.screen() == null
                && dev.hatek.client.module.Wheel.isBound()) {
            ci.cancel();
        }
    }
}
