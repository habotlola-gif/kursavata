package dev.hatek.client.event;

import dev.hatek.client.module.Module;
import dev.hatek.client.module.ModuleManager;
import dev.hatek.client.module.impl.combat.AttackAura;
import dev.hatek.client.module.impl.combat.aura.rotation.ComponentManager;
import dev.hatek.client.module.impl.movement.AutoSprint;
import dev.hatek.client.module.impl.player.NoJumpDelay;
import net.minecraft.client.Minecraft;

public final class ClientEvents {
    private ClientEvents() {
    }

    public static void playerTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        ComponentManager.tick();

        for (Module module : ModuleManager.all()) {
            if (module instanceof NoJumpDelay jump) {
                jump.onTick();
            } else if (module instanceof AutoSprint sprint) {
                sprint.onTick();
            }
        }

        AttackAura aura = AttackAura.instance();
        if (aura != null) {
            aura.onTick();
        }
    }

    public static void render3D() {
        ComponentManager.render3D();
    }
}
