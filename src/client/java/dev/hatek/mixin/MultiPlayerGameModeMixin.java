package dev.hatek.mixin;

import dev.hatek.client.module.impl.combat.aura.Attack;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Inject(
            method = "attack(Lnet/minecraft/world/entity/player/Player;"
                    + "Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void hatek$onAttack(Player player, Entity target, CallbackInfo ci) {
        if (!Attack.allowAttack()) {
            ci.cancel();
        }
    }
}
