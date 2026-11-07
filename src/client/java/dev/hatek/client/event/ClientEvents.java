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

        dev.hatek.client.module.impl.movement.AirStuck airStuck = dev.hatek.client.module.impl.movement.AirStuck.instance();
        if (airStuck != null) {
            airStuck.beforeTick(mc.player);
        }
        dev.hatek.client.module.impl.combat.AutoSwap autoSwap = dev.hatek.client.module.impl.combat.AutoSwap.instance();
        if (autoSwap != null) {
            autoSwap.beforeTick(mc.player);
        }
        dev.hatek.client.module.impl.combat.SpearRush spearRush = dev.hatek.client.module.impl.combat.SpearRush.instance();
        if (spearRush != null) {
            spearRush.beforeTick(mc.player);
        }
        dev.hatek.client.module.impl.movement.DragonFly dragonFly = dev.hatek.client.module.impl.movement.DragonFly.instance();
        if (dragonFly != null) {
            dragonFly.beforeTick(mc.player);
        }

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

    public static void playerTickEnd() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        dev.hatek.client.module.impl.movement.AirStuck airStuck = dev.hatek.client.module.impl.movement.AirStuck.instance();
        if (airStuck != null) {
            airStuck.afterTick(mc.player);
        }
        dev.hatek.client.module.impl.combat.AutoSwap autoSwap = dev.hatek.client.module.impl.combat.AutoSwap.instance();
        if (autoSwap != null) {
            autoSwap.afterTick();
        }
    }

    public static void render3D() {
        ComponentManager.render3D();
    }
}
