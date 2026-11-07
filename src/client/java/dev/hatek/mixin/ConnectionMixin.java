package dev.hatek.mixin;

import dev.hatek.client.module.impl.combat.aura.rotation.ComponentManager;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;"
                    + "Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD")
    )
    private void hatek$onSend(Packet<?> packet, ChannelFutureListener listener, boolean flush,
                              CallbackInfo ci) {
        ComponentManager.packetSent(packet);
    }
}
