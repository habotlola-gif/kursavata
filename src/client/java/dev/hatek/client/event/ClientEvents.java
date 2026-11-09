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
        dev.hatek.client.module.impl.combat.OffhandSwapper.beforePlayerTick(mc.player);
        dev.hatek.client.module.impl.combat.ClickAction.beforePlayerTick(mc.player);
        dev.hatek.client.module.impl.movement.DragonFly dragonFly = dev.hatek.client.module.impl.movement.DragonFly.instance();
        if (dragonFly != null) {
            dragonFly.beforeTick(mc.player);
        }
        dev.hatek.client.module.impl.combat.SuperSpear spearRush = dev.hatek.client.module.impl.combat.SuperSpear.instance();
        if (spearRush != null) {
            spearRush.beforeTick(mc.player);
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

        dev.hatek.client.module.impl.combat.CrystalAura.tick();

        // the item engines send their packets here: still before this tick's movement packet (a real key press does
        // the same; at the end of the client tick they went out after it, which Grim reports as "Post"), and after
        // everything that turns the head in this tick, because the use-item packet carries the rotation and it must
        // equal the one of the movement packet that follows (BadPacketsJ)
        dev.hatek.client.module.impl.combat.OffhandSwapper.tick();
        dev.hatek.client.module.impl.combat.ClickAction.tick();
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
        dev.hatek.client.module.impl.combat.aura.rotation.NeuralRotation.afterTick(mc.player);
        dev.hatek.client.module.impl.combat.OffhandSwapper.afterPlayerTick();
        dev.hatek.client.module.impl.combat.ClickAction.afterPlayerTick();
    }

    public static void render3D() {
        ComponentManager.render3D();
    }
}
