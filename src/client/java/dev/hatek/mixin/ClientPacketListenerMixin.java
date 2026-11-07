package dev.hatek.mixin;

import dev.hatek.client.module.impl.movement.VoidTeleport;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    /** A position correction from the server ends a VoidTeleport trip. */
    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void hatek$onServerPosition(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        VoidTeleport.onServerPosition();
    }
}
