package dev.hatek.client.module.impl.combat.aura;

import dev.hatek.client.module.impl.combat.AttackAura;
import dev.hatek.client.module.impl.combat.aura.rotation.ServerView;
import dev.hatek.client.module.impl.combat.aura.util.AttackPace;
import dev.hatek.client.module.impl.combat.aura.util.AuraProfile;
import dev.hatek.client.module.impl.combat.aura.util.AuraRandom;
import dev.hatek.client.module.impl.combat.aura.util.PlayerStats;
import dev.hatek.client.module.impl.combat.aura.util.AuraTimer;
import dev.hatek.client.module.impl.combat.aura.util.AuraUtil;
import dev.hatek.client.module.impl.combat.aura.util.RayTraceUtil;
import dev.hatek.mixin.accessor.LivingEntityAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

public final class Attack {
    private static final AuraTimer COOLDOWN_TIMER = new AuraTimer();

    public static final double HIT_TOLERANCE = 0.05;

    private static final float CHARGE = 1.0F;

    public static long hitCounterCPSBypass;

    private static float critFallHeight = nextCritFallHeight();

    private static final float CHARGE_SLACK = 0.04F;

    private static final float CRIT_CHARGE = 0.91F;

    private static float chargeTarget = CHARGE;

    private static final float AURA_GAP_MIN = 205.0F;
    private static final float AURA_GAP_SPREAD = 60.0F;

    private static long auraGap = (long) AURA_GAP_MIN;

    private static final AttackPace PACE = new AttackPace();

    private static boolean auraAttacking;

    private static long extraCooldown;

    private static long sprintLead = nextSprintLead();

    private static boolean missDetected;
    private static int counterTo0PostMissHits;

    private static boolean attackedLastTick;

    private static final int FAR_PAST = Integer.MIN_VALUE / 2;

    private static int lastAirTick = FAR_PAST;

    private static final int GROUND_PATIENCE_TICKS = 14;

    private Attack() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static void hitCounterCPSBypassNext() {
        hitCounterCPSBypass++;
    }

    public static void hitCounterCPSBypassReset() {
        hitCounterCPSBypass = 0L;
    }

    public static boolean cpsBypassTrigger() {
        return hitCounterCPSBypass % 7L == 3L;
    }

    public static Player getSelf() {
        return mc().player;
    }

    public static Level getWorld() {
        return mc().level;
    }

    public static int getAxeSlot() {
        Minecraft mc = mc();
        if (mc.player == null) {
            return -1;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() instanceof AxeItem) {
                return i;
            }
        }
        return -1;
    }

    public static Runnable[] hitShieldBreakTaskForUse(LivingEntity livingIn, boolean enabled) {
        Runnable[] prePost = {() -> {
        }, () -> {
        }};
        Minecraft mc = mc();
        if (!enabled || mc.player == null) {
            return prePost;
        }
        if (livingIn instanceof Player player) {
            if (!player.isBlocking()) {
                return prePost;
            }
            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            Item mainItem = main.isEmpty() ? null : main.getItem();
            Item offItem = off.isEmpty() ? null : off.getItem();

            if (mainItem == Items.SHIELD || offItem == Items.SHIELD) {
                int handSlot = mc.player.getInventory().getSelectedSlot();
                int slot = getAxeSlot();

                if (slot != -1 && slot != handSlot) {
                    prePost[0] = () -> {
                        if (mc.getConnection() != null) {
                            mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
                        }
                    };
                    prePost[1] = () -> {
                        if (mc.getConnection() != null) {
                            mc.getConnection().send(new ServerboundSetCarriedItemPacket(handSlot));
                        }
                    };
                }
            }
        }
        return prePost;
    }

    public static Runnable[] resetShieldSilentTaskForUse(boolean enabled) {
        Runnable[] prePost = {() -> {
        }, () -> {
        }};
        Minecraft mc = mc();
        if (!enabled || mc.player == null) {
            return prePost;
        }
        if (mc.player.isBlocking()) {
            InteractionHand active = mc.player.getUsedItemHand();
            if (active == null) {
                return prePost;
            }
            prePost[0] = () -> mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
            prePost[1] = () -> mc.getConnection().send(new ServerboundUseItemPacket(
                    active, 0, mc.player.getYRot(), mc.player.getXRot()));
        }
        return prePost;
    }

    public static Runnable[] skipSilentSprintingTaskForUse(boolean enabled, boolean serverSprinting) {
        Runnable[] prePost = {() -> {
        }, () -> {
        }};
        Minecraft mc = mc();
        if (!enabled || mc.player == null || mc.getConnection() == null) {
            return prePost;
        }
        if (!serverSprinting && !mc.player.isSprinting()) {
            return prePost;
        }
        if (mc.player.isEyeInFluid(FluidTags.WATER)) {
            return prePost;
        }
        boolean restore = mc.player.isSprinting() || serverSprinting;

        prePost[0] = () -> {
            mc.player.setSprinting(false);
            mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player,
                    ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        };
        prePost[1] = () -> {
            if (!restore) {
                return;
            }
            mc.player.setSprinting(true);
            mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player,
                    ServerboundPlayerCommandPacket.Action.START_SPRINTING));
        };
        return prePost;
    }

    public static double getYCapacityOnPlayerPos(int rangeY) {
        Minecraft mc = mc();
        if (mc.level == null || mc.player == null) {
            return 1.0;
        }
        Vec3 eyePos = mc.player.getEyePosition();
        double minDst = rangeY * 2.0;
        double maxY = 320.0;
        double minY = -64.0;
        float selfWD2 = mc.player.getBbWidth() / 2.0F - 0.01F;

        for (Vec3 vec : Arrays.asList(
                eyePos.add(-selfWD2, 0.0, -selfWD2),
                eyePos.add(selfWD2, 0.0, selfWD2),
                eyePos.add(selfWD2, 0.0, -selfWD2),
                eyePos.add(-selfWD2, 0.0, selfWD2))) {
            HitResult first = mc.level.clip(new ClipContext(vec, vec.add(0.0, -rangeY, 0.0),
                    ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, mc.player));
            HitResult second = mc.level.clip(new ClipContext(vec, vec.add(0.0, rangeY, 0.0),
                    ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, mc.player));

            if (maxY > second.getLocation().y) {
                maxY = second.getLocation().y;
            }
            if (minY < first.getLocation().y) {
                minY = first.getLocation().y;
            }
            double dst = maxY - minY;
            if (minDst > dst) {
                minDst = dst;
            }
        }
        return minDst - mc.player.getBbHeight();
    }

    public static boolean isBestMomentToHit(boolean fallCheck) {
        Minecraft mc = mc();
        if (!fallCheck || mc.player == null) {
            return true;
        }
        if (critStateReady()) {
            return true;
        }
        if (!AttackAura.extra(AttackAura.SMART_CRITS)) {
            return false;
        }
        if (critImpossible()) {
            return true;
        }
        if (mc.player.onGround()) {
            return !fallComing();
        }
        return false;
    }

    private static boolean critStateReady() {
        Minecraft mc = mc();
        return mc.player != null
                && mc.player.fallDistance > critFallHeight
                && !mc.player.onGround()
                && !mc.player.onClimbable()
                && !mc.player.isInWater()
                && !mc.player.isPassenger()
                && !mc.player.isMobilityRestricted();
    }

    public static boolean critPending() {
        Minecraft mc = mc();
        return mc.player != null && mc.level != null
                && !mc.player.onGround() && !critImpossible();
    }

    private static boolean critImpossible() {
        Minecraft mc = mc();
        boolean isInWeb = mc.level.getBlockState(mc.player.blockPosition()).is(Blocks.COBWEB);
        boolean jumping = ((LivingEntityAccessor) mc.player).hatek$isJumping();
        boolean badLiquidMoment = !jumping && (mc.player.isInWater() || mc.player.isInLava())
                || mc.player.isEyeInFluid(FluidTags.WATER)
                || mc.player.isEyeInFluid(FluidTags.LAVA)
                || isInWeb;

        return badLiquidMoment
                || mc.player.onClimbable()
                || mc.player.isPassenger()
                || mc.player.hasEffect(MobEffects.BLINDNESS)
                || mc.player.hasEffect(MobEffects.LEVITATION)
                || mc.player.hasEffect(MobEffects.SLOW_FALLING)
                || mc.player.getAbilities().flying
                || getYCapacityOnPlayerPos(2) < 0.1F;
    }

    private static final float SMASH_FALL = 1.6F;

    public static boolean holdingMaceSmash() {
        Minecraft mc = mc();
        if (mc.player == null) {
            return false;
        }
        if (!mc.player.getMainHandItem().is(Items.MACE)) {
            return false;
        }
        if (mc.player.onGround()) {
            return false;
        }
        return !readyToSmash();
    }

    private static boolean readyToSmash() {
        Minecraft mc = mc();
        return mc.player.getY() < mc.player.yo - 0.05 && mc.player.fallDistance > SMASH_FALL;
    }

    public static boolean useEntity(LivingEntity livingIn, Runnable preHit, Runnable postHit,
                                    InteractionHand hand, boolean cpsBypass) {
        Minecraft mc = mc();
        if (preHit != null) {
            preHit.run();
        }

        if (livingIn != null && mc.gameMode != null && mc.player != null && attackTickGapReached()) {
            auraAttacking = true;
            try {
                mc.gameMode.attack(mc.player, livingIn);
            } finally {
                auraAttacking = false;
            }
            attackedLastTick = true;

            PACE.markAura(mc.player.tickCount);

            if (hand != null) {
                mc.player.swing(hand);
            }
            if (cpsBypass) {
                hitCounterCPSBypassNext();
            } else {
                hitCounterCPSBypassReset();
            }
            COOLDOWN_TIMER.reset();

            critFallHeight = nextCritFallHeight();
            sprintLead = nextSprintLead();
            rollNextHit();
        }

        if (postHit != null) {
            postHit.run();
        }
        return livingIn != null;
    }

    private static float nextCritFallHeight() {
        return AuraRandom.human(0.02F, 0.06F);
    }

    private static void rollNextHit() {
        chargeTarget = CHARGE;

        if (PlayerStats.clickReady()) {
            float sampled = PlayerStats.click(AuraRandom.range(0.0F, 1.0F));
            auraGap = (long) Math.max(AURA_GAP_MIN, sampled);
        } else {
            auraGap = (long) (AuraRandom.human(AURA_GAP_MIN, AURA_GAP_MIN + AURA_GAP_SPREAD)
                    * AuraProfile.tempo());

            if (AuraRandom.chance(0.06F)) {
                auraGap += (long) AuraRandom.human(40.0F, 230.0F);
            }
        }

        if (!AttackAura.chargeSpreadEnabled()) {
            extraCooldown = 0L;
            return;
        }
        extraCooldown = (long) AuraRandom.human(0.0F, 10.0F);
    }

    private static long nextSprintLead() {
        return (long) AuraRandom.human(80.0F, 190.0F);
    }

    public static long getMsCooldown() {
        Minecraft mc = mc();
        if (mc.player == null) {
            return 500L;
        }
        double attackSpeed = mc.player.getAttributeValue(Attributes.ATTACK_SPEED);
        return (long) (1.0 / attackSpeed * 1000.0 * chargeTarget) + extraCooldown;
    }

    public static boolean chargeReached() {
        Minecraft mc = mc();
        if (mc.player == null) {
            return true;
        }
        return mc.player.getAttackStrengthScale(0.5F) > chargeFloor();
    }

    private static float chargeFloor() {
        float full = Math.max(CRIT_CHARGE, chargeTarget - CHARGE_SLACK);
        return critWindowOpen() ? CRIT_CHARGE : full;
    }

    private static boolean critWindowOpen() {
        return AttackAura.extra(AttackAura.SMART_CRITS) && critStateReady();
    }

    private static long attackInterval() {
        Minecraft mc = mc();
        if (mc.player == null) {
            return Math.max(getMsCooldown(), auraGap);
        }
        double attackSpeed = mc.player.getAttributeValue(Attributes.ATTACK_SPEED);
        long forCharge = (long) (1.0 / attackSpeed * 1000.0 * chargeFloor()) + extraCooldown;
        return Math.max(Math.min(getMsCooldown(), forCharge), auraGap);
    }

    public static boolean attackTickGapReached() {
        Minecraft mc = mc();
        if (mc.player == null) {
            return true;
        }
        return PACE.auraGapReached(mc.player.tickCount);
    }

    public static boolean wouldRefuseAttack() {
        Minecraft mc = mc();
        if (mc.player == null) {
            return false;
        }
        AttackAura aura = AttackAura.instance();
        if (aura == null || !aura.isEnabled()) {
            return false;
        }
        return PACE.manualRefused(mc.player.tickCount);
    }

    public static boolean allowAttack() {
        Minecraft mc = mc();
        if (mc.player == null) {
            return true;
        }
        if (auraAttacking) {
            if (!attackTickGapReached()) {
                return false;
            }
            PACE.markAura(mc.player.tickCount);
            return true;
        }
        if (wouldRefuseAttack()) {
            return false;
        }
        PACE.markManual(mc.player.tickCount);
        PlayerStats.click();
        return true;
    }

    public static boolean msCooldownReached(long msOffset) {
        return COOLDOWN_TIMER.finished(attackInterval() + msOffset);
    }

    public static boolean msCooldownReached() {
        return msCooldownReached(0L);
    }

    public static boolean msCooldownHasMs(long ms) {
        return COOLDOWN_TIMER.finished(ms);
    }

    public static float msCooldownPC01() {
        return Math.min((float) COOLDOWN_TIMER.elapsedTime() / (float) getMsCooldown(), 1.0F);
    }

    public static float msCooldownReach() {
        return COOLDOWN_TIMER.elapsedTime();
    }

    public static boolean anyEntityOnRay(LivingEntity livingIn, double range) {
        Minecraft mc = mc();
        if (livingIn == null || mc.player == null) {
            return false;
        }
        float yaw = Mth.wrapDegrees(ServerView.yaw(mc.player.getYRot()));
        float pitch = ServerView.pitch(mc.player.getXRot());
        return onRay(livingIn.getBoundingBox(), yaw, pitch, range, HIT_TOLERANCE);
    }

    public static boolean onRay(AABB box, float yaw, float pitch, double range, double tolerance) {
        Minecraft mc = mc();
        if (mc.player == null || box == null) {
            return false;
        }
        AABB target = box.inflate(tolerance);
        if (target.maxX <= target.minX || target.maxY <= target.minY || target.maxZ <= target.minZ) {
            return false;
        }
        Vec3 eyes = ServerView.eyePos(mc.player);
        Vec3 look = RayTraceUtil.getVectorForRotation(pitch, yaw);
        return target.contains(eyes)
                || target.clip(eyes, eyes.add(look.scale(range + 0.5))).isPresent();
    }

    public static boolean shouldAttack(LivingEntity livingTarget, boolean rayCast,
                                       boolean distanceCheck, boolean fallCheck,
                                       long cooldownMSOffset, float[] ranges) {
        if (distanceCheck && livingTarget != null
                && !AuraUtil.validDistance(livingTarget, ranges[0], true)) {
            return false;
        }
        if (!msCooldownReached(cooldownMSOffset)) {
            return false;
        }
        if (!attackTickGapReached()) {
            return false;
        }
        if (!chargeReached()) {
            return false;
        }
        boolean validNext = isBestMomentToHit(fallCheck);
        if (validNext && rayCast && !anyEntityOnRay(livingTarget, ranges[0])) {
            validNext = false;
        }
        return validNext;
    }

    public static boolean shouldAttack(LivingEntity livingTarget, boolean rayCast,
                                       boolean fallCheck, long cooldownMSOffset, float[] ranges) {
        return shouldAttack(livingTarget, rayCast, true, fallCheck, cooldownMSOffset, ranges);
    }

    public static boolean resetSprintTickP(LivingEntity targetIn, float[] ranges) {
        Minecraft mc = mc();
        return targetIn != null
                && shouldAttack(targetIn, false, false, -sprintLead - 20L, ranges)
                && !mc.player.onGround()
                && !mc.player.isEyeInFluid(FluidTags.WATER)
                && mc.player.getDeltaMovement().y <= 0.0030162615090425808;
    }

    public static boolean resetSprintTick(LivingEntity targetIn, float[] ranges) {
        Minecraft mc = mc();
        return targetIn != null
                && shouldAttack(targetIn, false, false, -sprintLead, ranges)
                && !mc.player.onGround()
                && !mc.player.isEyeInFluid(FluidTags.WATER)
                && mc.player.getDeltaMovement().y <= 0.42F;
    }

    private static int maxHitsCountOnMiss() {
        return 3;
    }

    public static void antiMissesHittingReset() {
        missDetected = false;
        counterTo0PostMissHits = 0;
    }

    public static void antiMissesHittingUpdate(LivingEntity targetIn, boolean cpsBypass,
                                               boolean rayCastCheck, boolean enabled) {
        Minecraft mc = mc();
        if (targetIn == null || counterTo0PostMissHits == 0 || !enabled || targetIn.hurtTime != 0) {
            antiMissesHittingReset();
        }
        if (enabled && targetIn != null
                && msCooldownHasMs(cpsBypassTrigger() ? 250L : 150L) && mc.player.swinging) {
            if (!missDetected && counterTo0PostMissHits == 0 && targetIn.hurtTime == 0) {
                missDetected = true;
                counterTo0PostMissHits = maxHitsCountOnMiss();
            }
            if (missDetected
                    && counterTo0PostMissHits > 0
                    && (!rayCastCheck || anyEntityOnRay(targetIn, 6.0))
                    && useEntity(targetIn, () -> {
            }, () -> {
            }, InteractionHand.MAIN_HAND, cpsBypass)) {
                counterTo0PostMissHits--;
            }
        }
    }

    public static void observe() {
        Minecraft mc = mc();
        if (mc.player == null) {
            return;
        }
        if (mc.player.tickCount < lastAirTick) {
            lastAirTick = FAR_PAST;
        }
        if (!mc.player.onGround()) {
            lastAirTick = mc.player.tickCount;
        }
    }

    private static boolean fallComing() {
        Minecraft mc = mc();
        if (mc.player == null || mc.options == null) {
            return false;
        }
        return mc.options.keyJump.isDown()
                || mc.player.tickCount - lastAirTick <= GROUND_PATIENCE_TICKS;
    }

    public static void clampCooldownDebt() {
        if (attackedLastTick) {
            attackedLastTick = false;
            return;
        }
        if (mc().player == null) {
            return;
        }
        long cooldown = attackInterval();
        if (COOLDOWN_TIMER.elapsedTime() > cooldown) {
            COOLDOWN_TIMER.setMs(cooldown);
        }
    }

    public static AuraTimer getCooldownTimer() {
        return COOLDOWN_TIMER;
    }
}
