package dev.hatek.mixin.accessor;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    /** The key the player has bound to this action right now (not the default one). */
    @Accessor("key")
    InputConstants.Key hatek$boundKey();
}
