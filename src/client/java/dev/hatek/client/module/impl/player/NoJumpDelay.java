package dev.hatek.client.module.impl.player;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.mixin.accessor.LivingEntityAccessor;
import net.minecraft.client.Minecraft;

public final class NoJumpDelay extends Module {
    private static NoJumpDelay instance;

    public NoJumpDelay() {
        super("NoJumpDelay", "Removes the cooldown between jumps", Category.PLAYER);
        instance = this;
    }

    public static boolean enabled() {
        return instance != null && instance.isEnabled();
    }

    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || mc.player == null || mc.level == null) {
            return;
        }
        ((LivingEntityAccessor) mc.player).hatek$setNoJumpDelay(0);
    }

    public static NoJumpDelay instance() {
        return instance;
    }
}
