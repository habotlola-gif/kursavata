package dev.hatek.client.module.impl.combat;

import dev.hatek.client.feature.command.Friends;
import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.impl.combat.aura.Attack;
import dev.hatek.client.module.impl.combat.aura.rotation.AspectRotation;
import dev.hatek.client.module.impl.combat.aura.rotation.FreeLookUtil;
import dev.hatek.client.module.impl.combat.aura.util.AuraUtil;
import dev.hatek.client.module.impl.combat.aura.util.MoveUtil;
import dev.hatek.client.module.impl.combat.aura.util.MovementManager;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

/**
 * AspectAura: the killaura of the aspectdlc client (CC0 sources) with its ShardAC / ShardAC-AI rotations, as a module
 * of its own. The rotation is the original one; the attack is the plain one of that client (the weapon is charged,
 * the target is in reach and under the crosshair, optionally a hit on the fall), with the click sent in the order an
 * anticheat expects (the angle is known to the server first). It does not touch the sprint at all.
 *
 * <p>Do not run it together with AttackAura: both turn the head, so enabling one switches the other off.
 */
public final class AspectAura extends Module {
    private static AspectAura instance;

    private final ModeSetting rotationMode = new ModeSetting("Rotation", 0, "ShardAC", "ShardAC-AI");
    private final SliderSetting range = new SliderSetting("Attack range (blocks)", 3.0, 2.5, 4.0, 0.1);
    private final SliderSetting detect = new SliderSetting("Detect range (blocks)", 1.0, 0.0, 4.0, 0.1);
    private final SliderSetting minCharge = new SliderSetting("Min charge before a hit (%)", 93.0, 60.0, 100.0, 1.0);
    private final ModeSetting crits = new ModeSetting("Crits", 0, "Fall (wait for the fall in the air)", "Off (hit at once)");
    private final BoolSetting players = new BoolSetting("Players", true);
    private final BoolSetting mobs = new BoolSetting("Mobs", false);
    private final BoolSetting ignoreFriends = new BoolSetting("Ignore friends", true);
    private final BoolSetting rayCheck = new BoolSetting("Only with the crosshair on the target", true);
    private final BoolSetting debug = new BoolSetting("Debug: say why it does not hit", false);

    private final AspectRotation shardAc = new AspectRotation(AspectRotation.Variant.SHARD_AC);
    private final AspectRotation shardAi = new AspectRotation(AspectRotation.Variant.SHARD_AC_AI);
    private LivingEntity target;
    private int airWait;
    private int lastHitTick = -10;
    private long lastWhyAt;

    public AspectAura() {
        super("AspectAura", "The killaura of aspectdlc: ShardAC / ShardAC-AI rotation", Category.COMBAT);
        instance = this;
        with(this.rotationMode, this.range, this.detect, this.minCharge, this.crits, this.players, this.mobs,
                this.ignoreFriends, this.rayCheck, this.debug);
    }

    public static AspectAura instance() {
        return instance;
    }

    public LivingEntity getTarget() {
        return this.target;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    @Override
    protected void onEnable() {
        AttackAura other = AttackAura.instance();
        if (other != null && other.isEnabled()) {
            other.toggle();
            say("AttackAura выключена, вместе они мешали бы друг другу");
        }
        this.shardAc.reset();
        this.shardAi.reset();
        this.target = null;
        this.airWait = 0;
    }

    @Override
    protected void onDisable() {
        this.target = null;
        dev.hatek.client.module.impl.combat.aura.rotation.RotationProcess.requestReset();
        MoveUtil.restoreSprintKey();
    }

    private AspectRotation rotation() {
        return this.rotationMode.is("ShardAC-AI") ? this.shardAi : this.shardAc;
    }

    private void say(String text) {
        Minecraft mc = mc();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§bAspectAura: §f" + text));
        }
    }

    private void why(String text) {
        long now = System.currentTimeMillis();
        if (this.debug.value() && now - this.lastWhyAt > 1000L) {
            this.lastWhyAt = now;
            say(text);
        }
    }

    /** Called in the player tick, after AttackAura (ClientEvents.playerTick). */
    public static void tick() {
        AspectAura self = instance;
        if (self != null && self.isEnabled()) {
            self.run();
        }
    }

    private void run() {
        Minecraft mc = mc();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !player.isAlive() || player.isSpectator() || mc.gui.screen() != null) {
            return;
        }
        this.target = pickTarget(player);
        if (this.target == null) {
            this.shardAc.reset();
            this.shardAi.reset();
            this.airWait = 0;
            return;
        }
        // the movement keys keep working relative to the camera while the head is turned by the rotation
        if (!MovementManager.getInstance().isMovementLocked()) {
            MoveUtil.fixMovement(FreeLookUtil.freeYaw);
        }
        rotation().update(this.target);
        attack(player);
    }

    private LivingEntity pickTarget(LocalPlayer self) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        double reach = this.range.value() + this.detect.value();
        for (Entity entity : mc().level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living == self || !living.isAlive() || living.isSpectator()
                    || living.isInvulnerable()) {
                continue;
            }
            boolean isPlayer = living instanceof Player;
            if (isPlayer ? !this.players.value() : !(this.mobs.value() && living instanceof Monster)) {
                continue;
            }
            if (isPlayer && this.ignoreFriends.value() && Friends.is(((Player) living).getGameProfile().name())) {
                continue;
            }
            if (isPlayer && ((Player) living).isCreative()) {
                continue;
            }
            double distance = AuraUtil.getStrictDistance(living);
            if (distance <= reach && distance < bestDistance && self.hasLineOfSight(living)) {
                bestDistance = distance;
                best = living;
            }
        }
        return best;
    }

    private void attack(LocalPlayer player) {
        LivingEntity target = this.target;
        if (player.tickCount == this.lastHitTick || Attack.attackedThisTick() || player.isUsingItem()
                || ClickAction.busy() || OffhandSwapper.acting()) {
            return;
        }
        if (AuraUtil.getStrictDistance(target) > this.range.value()) {
            why("цель дальше дальности удара");
            return;
        }
        float need = (float) this.minCharge.value() / 100.0F;
        if (player.getAttackStrengthScale(1.5F) < need) {
            why("оружие не заряжено");
            return;
        }
        if (this.crits.value().startsWith("Fall")) {
            boolean inAir = !player.onGround() && !player.onClimbable() && !player.isInWater() && !player.isInLava()
                    && !player.isPassenger() && !player.getAbilities().flying;
            boolean falling = player.fallDistance > 0.06F && player.getDeltaMovement().y < 0.0;
            if (inAir && !falling && this.airWait < 12) {
                this.airWait++;
                why("ждёт падения для крита");
                return;
            }
        }
        // the angle must be the one the server knows, otherwise the reach / hitbox check sees something else
        if (this.rayCheck.value() && !Attack.anyEntityOnRay(target, this.range.value())) {
            why("прицел ещё не на цели (угол ещё не дошёл до сервера)");
            return;
        }
        if (Attack.strike(target, null, null, InteractionHand.MAIN_HAND)) {
            this.lastHitTick = player.tickCount;
            this.airWait = 0;
            rotation().onAttack();
        }
    }
}
