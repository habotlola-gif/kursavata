package dev.hatek.client.module.impl.combat.aura.util;

import dev.hatek.client.module.impl.player.NoJumpDelay;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class CritUtil {
    private static final int WATER_CRIT_INTENT_TICKS = 8;
    private static final int WATER_CRIT_CONTACT_TICKS = 10;
    private static final double WATER_CRIT_MIN_UPWARD_VELOCITY = 0.05;

    private static int lastWaterContactAge = Integer.MIN_VALUE;
    private static int lastWaterCritIntentAge = Integer.MIN_VALUE;

    private CritUtil() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static float getAICooldown() {
        Minecraft mc = mc();
        if (mc.player.getMainHandItem().getItem() == Items.AIR) {
            return 0.9F;
        }
        return !(mc.player.getMainHandItem().getItem() instanceof AxeItem)
                && !(mc.player.getMainHandItem().getItem() instanceof ShovelItem)
                ? 0.93F
                : 0.95F;
    }

    public static boolean canAIFall() {
        Minecraft mc = mc();
        BlockPos posWater = BlockPos.containing(mc.player.position().add(0.0, -0.4F, 0.0));
        if (mc.level.getBlockState(posWater).is(Blocks.WATER)) {
            return true;
        }
        return getBlock(0.0, 3.0, 0.0) == Blocks.AIR
                && getBlock(0.0, 2.0, 0.0) == Blocks.AIR
                && getBlock(0.0, 1.0, 0.0) == Blocks.AIR
                || mc.player.fallDistance < (getBlock(0.0, 2.0, 0.0) != Blocks.AIR ? 0.08F : 0.6F)
                || mc.player.fallDistance > 1.2F;
    }

    public static boolean canCritical(LivingEntity target) {
        updateWaterCritState();

        if (isTryingWaterCrit()) {
            return isWaterCritWindow();
        }
        if (isNoJumpDelayCeilingCritIntent()) {
            return isNoJumpDelayCeilingCritWindow();
        }
        if (isNoJumpDelayJumpCritIntent()) {
            return isNoJumpDelayJumpCritWindow();
        }
        if (cannotPerformCrit()) {
            return true;
        }
        return isFalling();
    }

    private static boolean isFalling() {
        Minecraft mc = mc();
        return !mc.player.onGround() && mc.player.getDeltaMovement().y < 0.0
                && mc.player.fallDistance > 0.0;
    }

    public static boolean canPacketCrit() {
        return isInCobweb() || mc().player.hasEffect(MobEffects.SLOW_FALLING);
    }

    private static boolean isNoJumpDelayCeilingCritIntent() {
        Minecraft mc = mc();
        return NoJumpDelay.enabled() && mc.options != null && mc.options.keyJump.isDown()
                && hasLowCeilingForJumpCrit();
    }

    private static boolean isNoJumpDelayJumpCritIntent() {
        Minecraft mc = mc();
        return NoJumpDelay.enabled() && mc.options != null && mc.options.keyJump.isDown();
    }

    private static boolean isNoJumpDelayCeilingCritWindow() {
        Minecraft mc = mc();
        return mc.player != null
                && !mc.player.onGround()
                && mc.player.getDeltaMovement().y <= 0.01
                && !mc.player.isInWater()
                && !mc.player.isUnderWater()
                && !mc.player.isInLava()
                && !mc.player.onClimbable()
                && !mc.player.isPassenger()
                && !mc.player.getAbilities().flying;
    }

    public static boolean isNoJumpDelayJumpCritWindow() {
        Minecraft mc = mc();
        return mc.player != null
                && mc.level != null
                && NoJumpDelay.enabled()
                && mc.options != null
                && mc.options.keyJump.isDown()
                && !mc.player.onGround()
                && mc.player.getDeltaMovement().y < 0.0
                && !mc.player.isInWater()
                && !mc.player.isUnderWater()
                && !mc.player.isInLava()
                && !mc.player.onClimbable()
                && !mc.player.isPassenger()
                && !mc.player.getAbilities().flying
                && !mc.player.hasEffect(MobEffects.LEVITATION)
                && !mc.player.hasEffect(MobEffects.SLOW_FALLING)
                && !mc.player.hasEffect(MobEffects.BLINDNESS)
                && !mc.player.isFallFlying()
                && !isInCobweb();
    }

    private static boolean hasLowCeilingForJumpCrit() {
        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) {
            return false;
        }
        AABB box = mc.player.getBoundingBox().deflate(0.03);
        AABB headBox = new AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY + 0.32, box.maxZ);

        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(headBox.minX), Mth.floor(headBox.minY), Mth.floor(headBox.minZ),
                Mth.floor(headBox.maxX), Mth.floor(headBox.maxY), Mth.floor(headBox.maxZ))) {
            BlockState state = mc.level.getBlockState(pos);
            if (!state.isAir() && !state.getCollisionShape(mc.level, pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void updateWaterCritState() {
        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) {
            lastWaterContactAge = Integer.MIN_VALUE;
            lastWaterCritIntentAge = Integer.MIN_VALUE;
            return;
        }
        if (!isNearWaterSurface()) {
            return;
        }
        lastWaterContactAge = mc.player.tickCount;
        if (isWaterCritIntentState()) {
            lastWaterCritIntentAge = mc.player.tickCount;
        }
    }

    private static boolean isWaterCritIntentState() {
        Minecraft mc = mc();
        return mc.player != null && mc.options != null
                && mc.options.keyJump.isDown()
                && !mc.player.onGround()
                && !mc.player.isUnderWater()
                && mc.player.getDeltaMovement().y > WATER_CRIT_MIN_UPWARD_VELOCITY;
    }

    private static boolean isTryingWaterCrit() {
        Minecraft mc = mc();
        return mc.player != null && mc.options != null && mc.options.keyJump.isDown()
                && mc.player.tickCount - lastWaterCritIntentAge <= WATER_CRIT_INTENT_TICKS
                && mc.player.tickCount - lastWaterContactAge <= WATER_CRIT_CONTACT_TICKS;
    }

    private static boolean isWaterCritWindow() {
        Minecraft mc = mc();
        return mc.player != null
                && !mc.player.onGround()
                && !mc.player.isInWater()
                && !mc.player.isUnderWater()
                && mc.player.fallDistance > 0.0
                && mc.player.getDeltaMovement().y < 0.0;
    }

    private static boolean isNearWaterSurface() {
        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) {
            return false;
        }
        BlockPos below = BlockPos.containing(mc.player.position().add(0.0, -0.4F, 0.0));
        return mc.player.isInWater() || mc.player.isUnderWater()
                || mc.level.getBlockState(below).is(Blocks.WATER);
    }

    private static boolean cannotPerformCrit() {
        Minecraft mc = mc();
        BlockPos posWater = BlockPos.containing(
                mc.player.position().add(0.0, mc.player.getBbHeight() / 2.0F, 0.0));

        return mc.player.isInLava()
                || mc.player.onClimbable()
                || mc.level.getBlockState(posWater).is(Blocks.WATER)
                || mc.player.hasEffect(MobEffects.LEVITATION)
                || mc.player.hasEffect(MobEffects.SLOW_FALLING)
                || mc.player.hasEffect(MobEffects.BLINDNESS)
                || isInCobweb()
                || mc.player.isFallFlying()
                || mc.player.isPassenger()
                || mc.player.getAbilities().flying
                || mc.player.isInWater();
    }

    public static boolean isInCobweb() {
        Minecraft mc = mc();
        AABB box = mc.player.getBoundingBox();

        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
            if (mc.level.getBlockState(pos).is(Blocks.COBWEB)) {
                return true;
            }
        }
        return false;
    }

    public static Block getBlock(double x, double y, double z) {
        Minecraft mc = mc();
        return mc.level.getBlockState(
                mc.player.blockPosition().offset((int) x, (int) y, (int) z)).getBlock();
    }
}
