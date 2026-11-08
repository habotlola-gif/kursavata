package dev.hatek.client.render;

import dev.hatek.client.module.impl.combat.AttackAura;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.jspecify.annotations.Nullable;

/** The entity the player is fighting: aura target, last hit (3 s), or the one under the crosshair. */
public final class TargetTracker {
    private static final long HIT_MEMORY_MS = 3000L;
    private static final double MAX_DISTANCE = 12.0;
    private static @Nullable LivingEntity lastHit;
    private static long lastHitAt;

    private TargetTracker() {
    }

    public static void onAttack(Entity target) {
        if (target instanceof LivingEntity living && !(target instanceof ArmorStand)) {
            lastHit = living;
            lastHitAt = System.currentTimeMillis();
            dev.hatek.client.module.impl.render.Particles.onAttack(living);
        }
    }

    public static @Nullable LivingEntity current() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return null;
        }
        LivingEntity aura = AttackAura.target;
        if (aura != null && isValid(player, aura)) {
            return aura;
        }
        if (lastHit != null && isValid(player, lastHit) && System.currentTimeMillis() - lastHitAt < HIT_MEMORY_MS) {
            return lastHit;
        }
        Entity looked = mc.crosshairPickEntity;
        if (looked instanceof LivingEntity living && !(looked instanceof ArmorStand) && isValid(player, living)) {
            return living;
        }
        return null;
    }

    private static boolean isValid(LocalPlayer player, LivingEntity entity) {
        return entity.isAlive() && entity.level() == player.level() && !entity.isInvisibleTo(player)
                && player.distanceTo(entity) <= MAX_DISTANCE;
    }
}
