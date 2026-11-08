package dev.hatek.client.module.impl.combat;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.impl.combat.aura.Attack;
import dev.hatek.client.module.impl.combat.aura.rotation.ComponentManager;
import dev.hatek.client.module.impl.combat.aura.rotation.FreeLookUtil;
import dev.hatek.client.module.impl.combat.aura.rotation.RotationProcess;
import dev.hatek.client.module.impl.combat.aura.rotation.NeuralRotation;
import dev.hatek.client.module.impl.combat.aura.rotation.SmoothRotation;
import dev.hatek.client.module.impl.combat.aura.rotation.SpaceTime;
import dev.hatek.client.module.impl.combat.aura.util.AuraProfile;
import dev.hatek.client.module.impl.combat.aura.util.AuraRandom;
import dev.hatek.client.module.impl.combat.aura.util.AuraUtil;
import dev.hatek.client.module.impl.combat.aura.util.MoveUtil;
import dev.hatek.client.module.impl.combat.aura.util.MovementManager;
import dev.hatek.client.module.impl.combat.aura.util.RayTraceUtil;
import dev.hatek.client.module.impl.movement.AutoSprint;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.MultiSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class AttackAura extends Module {
    public static final String MOVE_DEFAULT = "Default";

    public static final String MOVE_FREE = "Free";

    public static final String ROT_UNIVERSAL = "Universal Aura";
    public static final String ROT_SMOOTH = "Smooth Rotation";
    public static final String ROT_NEURAL = "Neural Rotation";

    public static final String SPACE_TIME = "SpaceTime";

    public static final String LEGIT_SPRINT = "Legit Sprint";
    public static final String SMART_CRITS = "Smart Crits";
    public static final String SLOW_TURN = "Slow Turn";

    public static final String THROUGH_WALLS = "Through Walls";
    public static final String WEAPON_ONLY = "Weapon Only";
    public static final String NO_EATING = "Not While Eating";
    public static final String NO_CONTAINER = "Not In Container";
    public static final String BREAK_SHIELD = "Break Shield";
    public static final String DROP_SHIELD = "Drop Shield";

    public static final String PLAYERS = "Players";
    public static final String NAKED = "Naked";
    public static final String MOBS = "Mobs";

    private static AttackAura instance;

    private final SliderSetting attackRange = new SliderSetting("Attack Range", 3.0, 3.0, 6.0, 0.1);
    private final SliderSetting preRange = new SliderSetting("Detect Range", 1.0, 0.0, 5.0, 0.1);

    private final ModeSetting movementType =
            new ModeSetting("Movement", 0, MOVE_DEFAULT, MOVE_FREE);
    private final ModeSetting rotationMode =
            new ModeSetting("Rotation", 0, ROT_UNIVERSAL, ROT_SMOOTH, ROT_NEURAL);
    private final SliderSetting neuralChaos = new SliderSetting("Neural: хаотичность (%)", 60.0, 10.0, 120.0, 5.0);
    private final SliderSetting neuralAssist = new SliderSetting("Neural: подправка к цели (%)", 40.0, 0.0, 100.0, 5.0);
    private final SliderSetting smoothSpeed = new SliderSetting("Smooth: aim speed", 1.0, 0.4, 2.5, 0.1);
    private final SliderSetting smoothJerk = new SliderSetting("Smooth: jerk (speed bursts, %)", 45.0, 0.0, 100.0, 5.0);
    private final ModeSetting smoothPattern = new ModeSetting("Smooth: multipoint pattern", 0,
            SmoothRotation.NORMAL, SmoothRotation.SWAP, SmoothRotation.CIRCLE, SmoothRotation.FIGURE_8,
            SmoothRotation.ZIGZAG, SmoothRotation.RANDOM);
    private final SliderSetting smoothPatternSpeed = new SliderSetting("Smooth: pattern speed", 0.6, 0.2, 3.0, 0.1);
    private final SliderSetting smoothPatternSize = new SliderSetting("Smooth: pattern size (%)", 30.0, 10.0, 95.0, 5.0);
    private final BoolSetting smoothLowerInAir = new BoolSetting("Smooth: aim lower while jumping", true);
    private final MultiSetting targets = new MultiSetting("Targets",
            List.of(PLAYERS, NAKED, MOBS), PLAYERS, NAKED, MOBS);
    private final MultiSetting misc = new MultiSetting("Checks",
            List.of(THROUGH_WALLS, WEAPON_ONLY, NO_EATING, NO_CONTAINER, BREAK_SHIELD, DROP_SHIELD),
            NO_EATING);
    private final MultiSetting extras = new MultiSetting("Extra",
            List.of(LEGIT_SPRINT, SMART_CRITS, SLOW_TURN),
            LEGIT_SPRINT, SMART_CRITS, SLOW_TURN);

    private final SliderSetting holdMargin = new SliderSetting("Hold target beyond range (blocks)", 1.5, 0.0, 3.0, 0.25);
    private final ModeSetting attackCore = new ModeSetting("Attack core", 0, "New (max damage)", "Classic (humanized)");
    private final SliderSetting minCharge = new SliderSetting("New: min charge before a hit (%)", 96.0, 70.0, 100.0, 1.0);
    private final ModeSetting critMode = new ModeSetting("New: crits", 1, "Off (hit at once)", "Smart (wait for the fall in the air)");
    private final ModeSetting priority = new ModeSetting("Target priority", 0, "Distance", "Health", "Armor", "Crosshair");
    private final SliderSetting targetFov = new SliderSetting("Target FOV (degrees)", 120.0, 30.0, 360.0, 5.0);
    private int airWaitTicks;
    private int lastStrikeTick = -10;
    private final BoolSetting debug = new BoolSetting("Debug: писать в чат, почему не бьёт", false);
    private long lastDebugAt;
    private final BoolSetting chargeSpread = new BoolSetting("Charge Spread", true);

    private static final double LOOK_RANGE = 32.0;


    private static final float ACQUIRE_FOV = 65.0F;

    public static LivingEntity target;
    public static boolean canSwap;

    private static LivingEntity preferred;

    private static boolean serverSprinting;

    private static boolean sprintHeld;

    public AttackAura() {
        super("AttackAura", "Automatically attacks the enemy near you, targeting you",
                Category.COMBAT);
        instance = this;
        keybind(GLFW.GLFW_KEY_R);
        with(this.attackRange, this.preRange, this.movementType, this.rotationMode, this.neuralChaos, this.neuralAssist, this.smoothSpeed, this.smoothJerk,
                this.smoothPattern, this.smoothPatternSpeed, this.smoothPatternSize, this.smoothLowerInAir, this.targets, this.misc,
                this.extras, this.holdMargin, this.attackCore, this.minCharge, this.critMode, this.priority, this.targetFov, this.debug, this.chargeSpread);

        this.minCharge.visibleIf(AttackAura::newCore);
        this.critMode.visibleIf(AttackAura::newCore);
        this.neuralChaos.visibleIf(AttackAura::neuralRotation);
        this.neuralAssist.visibleIf(AttackAura::neuralRotation);
        // the Smooth Rotation settings exist only while that rotation is selected
        for (dev.hatek.client.module.setting.Setting setting : List.of(this.smoothSpeed, this.smoothJerk,
                this.smoothPattern, this.smoothPatternSpeed, this.smoothPatternSize, this.smoothLowerInAir)) {
            setting.visibleIf(AttackAura::smoothRotation);
        }

        ComponentManager.getInstance().init();
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static AttackAura instance() {
        return instance;
    }

    public LivingEntity getTarget() {
        return target;
    }

    public static float attackRange() {
        return instance == null ? 3.0F : (float) instance.attackRange.value();
    }

    public static float preRange() {
        return instance == null ? 1.0F : (float) instance.preRange.value();
    }

    public static boolean extra(String name) {
        return instance != null && instance.extras.isSelected(name);
    }

    public static boolean misc(String name) {
        return instance != null && instance.misc.isSelected(name);
    }

    public static boolean target(String name) {
        return instance != null && instance.targets.isSelected(name);
    }

    public static boolean chargeSpreadEnabled() {
        return instance == null || instance.chargeSpread.value();
    }

    public static float[] getRanges() {
        return new float[]{attackRange(), preRange()};
    }

    public void onTick() {
        Minecraft mc = mc();
        if (!isEnabled() || mc.player == null || mc.level == null) {
            return;
        }

        Attack.clampCooldownDebt();

        Attack.observe();

        AuraProfile.tick();

        if (mc.gui.screen() != null) {
            target = null;
            MoveUtil.restoreSprintKey();
            MoveUtil.restoreMovementKeys();
            return;
        }

        onUpdate();

        if (target != null) {
            updateRotation();
            idleSwing();
        }
    }

    private void idleSwing() {
        Minecraft mc = mc();
        if (mc.player == null || mc.player.isUsingItem() || mc.player.isBlocking()) {
            return;
        }
        if (Attack.msCooldownPC01() > 0.55F || mc.player.getAttackStrengthScale(0.5F) > 0.6F) {
            return;
        }
        if (!AuraRandom.chance(0.006F)) {
            return;
        }
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void onUpdate() {
        Minecraft mc = mc();
        if (!mc.player.isAlive()) {
            setEnabled(false);
            return;
        }

        serverSprinting = mc.player.isSprinting();

        if (preferred != null && (!preferred.isAlive() || preferred.isRemoved())) {
            preferred = null;
        }

        boolean keepPreferred = preferred != null
                && (preferred == target ? canKeep(preferred) : isValidTarget(preferred));

        if (keepPreferred) {
            target = preferred;
        } else if (target == null || !canKeep(target)) {
            updateTarget();
        }

        if (target == null) {
            reset();
            return;
        }

        // the pressed keys are turned into the keys that move the (rotated) body the way the player wants: toward the
        // target ("Default") or relative to the camera ("Free"); the server sees keys that match the movement
        if (!MovementManager.getInstance().isMovementLocked()) {
            boolean steer = this.movementType.is(MOVE_DEFAULT) && target != null;
            MoveUtil.fixMovement(steer ? bearingToTarget() : FreeLookUtil.freeYaw);
        }

        boolean nearEnough = target != null
                && AuraUtil.getStrictDistance(target) <= attackRange() + 0.6F;

        boolean airNoSprint = nearEnough
                && !packetSprint()
                && !mc.player.onGround()
                && mc.player.getDeltaMovement().y <= 0.08
                && !mc.player.isInWater()
                && !mc.player.isUnderWater()
                && !mc.player.isInLava()
                && !mc.player.onClimbable();

        if (!packetSprint() && Attack.resetSprintTick(target, getRanges()) || airNoSprint) {
            holdSprint();
        } else {
            releaseSprint();

            MoveUtil.restoreSprintKey();
        }

        if (!checkToAttack()) {
            attackEntity();
        } else {
            why("проверки Checks блокируют удар (ест, открыт инвентарь, в руке не меч/топор при Weapon Only, булава)");
        }
    }

    private static final double STANDOFF = 1.0;

    private static int circleSide = 1;
    private static long nextFlip;

    private float bearingToTarget() {
        Minecraft mc = mc();
        if (mc.player == null || target == null) {
            return FreeLookUtil.freeYaw;
        }
        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        float direct = (float) Math.toDegrees(Math.atan2(-dx, dz));

        double flat = Math.hypot(dx, dz);
        double outer = STANDOFF * 1.7;
        if (flat >= outer) {
            return direct;
        }

        long now = System.currentTimeMillis();
        if (mc.options.keyLeft.isDown()) {
            circleSide = -1;
        } else if (mc.options.keyRight.isDown()) {
            circleSide = 1;
        } else if (now >= nextFlip) {
            circleSide = AuraRandom.chance(0.5F) ? 1 : -1;
            nextFlip = now + (long) AuraRandom.range(2600.0F, 7000.0F);
        }

        float bend;
        if (flat >= STANDOFF) {
            bend = 90.0F * (float) ((outer - flat) / (outer - STANDOFF));
        } else {
            bend = 90.0F + 28.0F * (float) Mth.clamp(1.0 - flat / STANDOFF, 0.0, 1.0);
        }
        return Mth.wrapDegrees(direct + bend * circleSide);
    }

    private void holdSprint() {
        Minecraft mc = mc();
        if (!sprintHeld) {
            AutoSprint.pushPause(0L);
            sprintHeld = true;
        }
        mc.player.setSprinting(false);
        mc.options.keySprint.setDown(false);
    }

    private void releaseSprint() {
        Minecraft mc = mc();
        if (!sprintHeld) {
            return;
        }
        sprintHeld = false;
        AutoSprint.popPause();

        if (!AutoSprint.isSprinting() || mc.player == null || mc.options == null) {
            return;
        }
        if (!mc.player.onGround() || !mc.options.keyUp.isDown()) {
            return;
        }
        mc.options.keySprint.setDown(true);
        mc.player.setSprinting(true);
    }

    private boolean packetSprint() {
        Minecraft mc = mc();
        return mc.player != null
                && !mc.player.hasEffect(MobEffects.BLINDNESS)
                && !mc.player.isPassenger()
                && !extra(LEGIT_SPRINT);
    }

    public static boolean spaceTime() {
        return instance != null && !smoothRotation() && !neuralRotation();
    }

    public static boolean newCore() {
        return instance == null || instance.attackCore.is("New (max damage)");
    }

    public static boolean neuralRotation() {
        return instance != null && instance.rotationMode.is(ROT_NEURAL);
    }

    public static float neuralChaos() {
        return instance == null ? 0.6f : (float) instance.neuralChaos.value() / 100.0f;
    }

    public static float neuralAssist() {
        return instance == null ? 0.4f : (float) instance.neuralAssist.value() / 100.0f;
    }

    public static boolean smoothRotation() {
        return instance != null && instance.rotationMode.is(ROT_SMOOTH);
    }

    public static float smoothSpeed() {
        return instance == null ? 1.0F : (float) instance.smoothSpeed.value();
    }

    public static float smoothJerk() {
        return instance == null ? 0.65F : (float) instance.smoothJerk.value() / 100.0F;
    }

    public static String smoothPattern() {
        return instance == null ? SmoothRotation.NORMAL : instance.smoothPattern.value();
    }

    public static float smoothPatternSpeed() {
        return instance == null ? 1.0F : (float) instance.smoothPatternSpeed.value();
    }

    public static float smoothPatternSize() {
        return instance == null ? 0.55F : (float) instance.smoothPatternSize.value() / 100.0F;
    }

    public static boolean smoothLowerInAir() {
        return instance != null && instance.smoothLowerInAir.value();
    }

    private static boolean aimedNow() {
        if (neuralRotation()) {
            return NeuralRotation.aimed();
        }
        return smoothRotation() ? SmoothRotation.aimed() : SpaceTime.aimed();
    }

    public static float targetAngularHalf() {
        Minecraft mc = mc();
        if (mc.player == null || instance == null || instance.target == null) {
            return 0.0F;
        }
        AABB box = instance.target.getBoundingBox();
        double distance = Math.max(0.35, mc.player.getEyePosition().distanceTo(box.getCenter()));
        double half = Math.max(box.getXsize(), box.getZsize()) * 0.5;
        return (float) Math.toDegrees(Math.atan2(half, distance));
    }

    public boolean isRayCastRuleToAttack() {
        return true;
    }

    private void why(String reason) {
        long now = System.currentTimeMillis();
        if (this.debug.value() && now - this.lastDebugAt > 1000L && mc().player != null) {
            this.lastDebugAt = now;
            mc().player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§bAttackAura: §f" + reason));
        }
    }

    public void attackEntity() {
        Minecraft mc = mc();
        if (target == null) {
            return;
        }
        if (AuraUtil.getStrictDistance(target) >= attackRange()) {
            why(String.format(java.util.Locale.ROOT, "цель далеко для удара: %.2f >= %.2f", AuraUtil.getStrictDistance(target), attackRange()));
            return;
        }

        float[] base = getRanges();
        float[] ranges = new float[]{base[0], base[1], base[0] + base[1]};

        boolean canPacket = packetSprint();
        boolean fresh = newCore();

        if (fresh) {
            if (!newCoreReady(mc)) {
                return;
            }
        } else {
            Attack.antiMissesHittingUpdate(target, true, isRayCastRuleToAttack(), false);

            boolean canAttack = Attack.shouldAttack(target, isRayCastRuleToAttack(), true,
                    true, 0L, ranges);

            if (!canAttack) {
                why("Attack.shouldAttack = нет: " + Attack.explain(target, ranges));
                return;
            }

            if (!aimedNow()) {
                why("не наведён на цель (голова ещё не на хитбоксе)");
                return;
            }

            if (!SpaceTime.settled()) {
                why("ждёт settled");
                return;
            }

            if (!canPacket && (serverSprinting || mc.player.isSprinting()) && Attack.critPending()) {
                why("ждёт крит (сброс спринта)");
                return;
            }
        }

        Runnable[] shieldBreak = Attack.hitShieldBreakTaskForUse(target, misc(BREAK_SHIELD));
        Runnable[] shieldPressBypass = Attack.resetShieldSilentTaskForUse(true);
        Runnable[] skipSilentSprint = Attack.skipSilentSprintingTaskForUse(canPacket, serverSprinting);

        Runnable preHit = () -> {
            skipSilentSprint[0].run();
            shieldPressBypass[0].run();
            shieldBreak[0].run();
        };
        Runnable postHit = () -> {
            shieldBreak[1].run();
            shieldPressBypass[1].run();
            skipSilentSprint[1].run();
        };

        if (misc(DROP_SHIELD) && mc.player.getUseItem().getItem().equals(Items.SHIELD)
                && mc.player.isUsingItem()) {
            mc.gameMode.releaseUsingItem(mc.player);
        }

        if (!canPacket) {
            mc.player.setSprinting(false);
            mc.options.keySprint.setDown(false);
        }

        if (fresh) {
            if (Attack.strike(target, preHit, postHit, InteractionHand.MAIN_HAND)) {
                this.lastStrikeTick = mc.player.tickCount;
                this.airWaitTicks = 0;
            }
        } else {
            Attack.useEntity(target, preHit, postHit, InteractionHand.MAIN_HAND, true);
        }
    }

    /**
     * The new attack core: a hit goes out when (1) the target is in reach, (2) the ray from the angle the server knows
     * really crosses its hitbox, (3) the weapon is charged and (4) the crit rule allows it. No random waiting.
     */
    private boolean newCoreReady(Minecraft mc) {
        if (mc.player.tickCount == this.lastStrikeTick) {
            return false;
        }
        if (!Attack.anyEntityOnRay(target, attackRange())) {
            why("луч не попадает по хитбоксу цели (голова ещё не там)");
            return false;
        }
        float charge = mc.player.getAttackStrengthScale(0.5F);
        float need = (float) this.minCharge.value() / 100.0F;
        if (charge < need) {
            why(String.format(java.util.Locale.ROOT, "оружие заряжено на %.0f%% из %.0f%%", charge * 100.0F, need * 100.0F));
            return false;
        }
        if (this.critMode.value().startsWith("Smart")) {
            boolean inAir = !mc.player.onGround() && !mc.player.onClimbable() && !mc.player.isInWater()
                    && !mc.player.isInLava() && !mc.player.isPassenger() && !mc.player.getAbilities().flying;
            boolean falling = mc.player.fallDistance > 0.0F && mc.player.getDeltaMovement().y < 0.0;
            if (!inAir) {
                this.airWaitTicks = 0;
            }
            if (inAir && !falling && this.airWaitTicks < 8) {
                // rising: a crit comes with the fall; wait a little, but never longer than 8 ticks
                this.airWaitTicks++;
                why("ждёт падения для крита (макс. 8 тиков)");
                return false;
            }
        }
        return true;
    }

    private void updateRotation() {
        float[] base = getRanges();
        float[] ranges = new float[]{base[0], base[1], base[0] + base[1]};

        boolean canAttack = Attack.shouldAttack(target, false, true, 0L, ranges);
        SpaceTime.onRotation(target, canAttack);
        SmoothRotation.onRotation(target, canAttack);
        if (neuralRotation()) {
            NeuralRotation.onTick(target);
        }
    }

    private void updateTarget() {
        Minecraft mc = mc();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        float fov = (float) this.targetFov.value() / 2.0F;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !isValidTarget(living)) {
                continue;
            }
            double fromCamera = AuraUtil.calculateFOVFromCamera(living);
            if (this.targetFov.value() < 360.0 && fromCamera > fov) {
                continue;
            }
            double score = switch (this.priority.value()) {
                case "Health" -> living.getHealth() + living.getAbsorptionAmount();
                case "Armor" -> living.getArmorValue() * 100.0 + mc.player.distanceToSqr(living);
                case "Crosshair" -> fromCamera;
                default -> mc.player.distanceToSqr(living);
            };
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }

        target = best;
    }

    private LivingEntity lookingAt() {
        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) {
            return null;
        }
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !isAllowedTarget(living)) {
                continue;
            }
            double distance = mc.player.distanceTo(living);
            if (distance > LOOK_RANGE || distance >= bestDistance) {
                continue;
            }
            if (!RayTraceUtil.rayTraceEntity(yaw, pitch, LOOK_RANGE, living)) {
                continue;
            }
            bestDistance = distance;
            best = living;
        }
        return best;
    }

    private float auraDist() {
        return attackRange() + preRange();
    }

    private boolean isValidTarget(LivingEntity entity) {
        return AuraUtil.getStrictDistance(entity) <= auraDist() && isAllowedTarget(entity);
    }

    private boolean canKeep(LivingEntity entity) {
        return AuraUtil.getStrictDistance(entity) <= auraDist() + (float) this.holdMargin.value()
                && isAllowedTarget(entity);
    }

    private boolean isAllowedTarget(LivingEntity entity) {
        Minecraft mc = mc();
        if (entity instanceof LocalPlayer || dev.hatek.client.feature.command.Friends.is(entity)) {
            return false;
        }
        if (!misc(THROUGH_WALLS) && !mc.player.hasLineOfSight(entity)) {
            return false;
        }
        if (entity instanceof Player && !target(PLAYERS)) {
            return false;
        }
        if (entity instanceof Player && entity.getArmorValue() == 0 && !target(NAKED)) {
            return false;
        }
        if (entity instanceof Player player && player.isCreative()) {
            return false;
        }
        if ((entity instanceof Monster || entity instanceof Slime || entity instanceof Villager
                || entity instanceof Animal) && !target(MOBS)) {
            return false;
        }
        return !entity.isInvulnerable() && entity.isAlive() && !(entity instanceof ArmorStand);
    }

    @Override
    protected void onEnable() {
        AuraProfile.roll();

        SpaceTime.reshuffleAll();
        SmoothRotation.reshuffleAll();
        NeuralRotation.reshuffleAll();

        Attack.getCooldownTimer().reset();

        reset();

        preferred = lookingAt();
    }

    @Override
    protected void onDisable() {
        reset();

        RotationProcess.requestReset();
    }

    private void reset() {
        Minecraft mc = mc();
        target = null;
        preferred = null;

        SpaceTime.forget();
        SmoothRotation.forget();
        NeuralRotation.forget();

        releaseSprint();

        if (mc.player != null) {
            MoveUtil.restoreSprintKey();

            MoveUtil.restoreMovementKeys();
        }

        MovementManager.getInstance().unlockMovement("Aura");
    }

    private boolean checkToAttack() {
        Minecraft mc = mc();
        return Attack.holdingMaceSmash()
                || mc.player.isUsingItem() && misc(NO_EATING)
                        && !(mc.player.getUseItem().getItem() instanceof ShieldItem)
                || mc.gui.screen() != null && misc(NO_CONTAINER)
                || !mc.player.getMainHandItem().is(ItemTags.SWORDS)
                        && !mc.player.getMainHandItem().is(ItemTags.AXES)
                        && misc(WEAPON_ONLY);
    }
}
