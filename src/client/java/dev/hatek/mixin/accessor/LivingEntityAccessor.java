package dev.hatek.mixin.accessor;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("noJumpDelay")
    void hatek$setNoJumpDelay(int value);

    @Accessor("noJumpDelay")
    int hatek$getNoJumpDelay();

    @Accessor("jumping")
    boolean hatek$isJumping();
}
