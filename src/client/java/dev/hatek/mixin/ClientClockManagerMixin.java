package dev.hatek.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.hatek.client.module.impl.render.TimeChanger;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientClockManager.class)
public abstract class ClientClockManagerMixin {
    /** TimeChanger: the world clock the sky and lighting read is replaced on the client only. */
    @ModifyReturnValue(method = "getTotalTicks", at = @At("RETURN"))
    private long hatek$timeOfDay(long ticks) {
        return TimeChanger.override(ticks);
    }
}
