package dev.hatek.client.module.impl.combat;

import dev.hatek.client.feature.command.Friends;
import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.impl.combat.aura.Attack;
import dev.hatek.client.module.impl.combat.aura.rotation.Rotation;
import dev.hatek.client.module.impl.combat.aura.rotation.RotationProcess;
import dev.hatek.client.module.impl.combat.aura.rotation.ServerView;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Crystal aura: places end crystals on obsidian / bedrock next to the nearest enemy and breaks them, choosing the
 * spots by the damage the explosion would do (the vanilla formula with the exposure of the body behind blocks) to the
 * enemy and to you.
 *
 * <p>Built for the order of packets an anticheat expects: the head is turned in one tick and the click goes out at the
 * start of the next one, when the server already knows where the head looks and the ray from that angle really hits
 * the crystal or the block. One action per tick, nothing while an item is being used, no slot packets of its own
 * (the game sends the slot when the item is used).
 */
public final class CrystalAura extends Module {
    private static CrystalAura instance;

    private final SliderSetting targetRange = new SliderSetting("Target range (blocks)", 10.0, 4.0, 16.0, 0.5);
    private final SliderSetting placeRange = new SliderSetting("Place range (blocks)", 4.0, 2.0, 4.5, 0.1);
    private final SliderSetting breakRange = new SliderSetting("Break range (blocks)", 2.9, 2.0, 3.0, 0.1);
    private final SliderSetting minDamage = new SliderSetting("Min damage to the enemy", 5.0, 1.0, 36.0, 0.5);
    private final SliderSetting maxSelf = new SliderSetting("Max damage to you", 6.0, 0.0, 20.0, 0.5);
    private final BoolSetting safe = new BoolSetting("Never risk dying", true);
    private final ModeSetting hands = new ModeSetting("Crystals from", 1, "Hands only", "Hotbar (silent switch)");
    private final SliderSetting placeDelay = new SliderSetting("Place delay (ticks)", 1.0, 0.0, 10.0, 1.0);
    private final SliderSetting breakDelay = new SliderSetting("Break delay (ticks)", 0.0, 0.0, 10.0, 1.0);
    private final BoolSetting randomize = new BoolSetting("Randomize delays", true);
    private final BoolSetting rotate = new BoolSetting("Turn the head (the server checks the angle)", true);
    private final BoolSetting players = new BoolSetting("Ignore friends", true);

    private int ticksSincePlace = 99;
    private int ticksSinceBreak = 99;
    private int placeJitter;
    private int breakJitter;
    private boolean failed;

    private record Spot(BlockPos pos, double target, double self) {
        double score() {
            return this.target - this.self * 0.8;
        }
    }

    public CrystalAura() {
        super("CrystalAura", "Places and breaks end crystals around the nearest enemy", Category.COMBAT);
        instance = this;
        with(this.targetRange, this.placeRange, this.breakRange, this.minDamage, this.maxSelf, this.safe, this.hands,
                this.placeDelay, this.breakDelay, this.randomize, this.rotate, this.players);
    }

    public static CrystalAura instance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        this.failed = false;
        this.ticksSincePlace = 99;
        this.ticksSinceBreak = 99;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** Called in the player tick, after the aura and before the item engines (ClientEvents.playerTick). */
    public static void tick() {
        CrystalAura self = instance;
        if (self == null || !self.isEnabled() || self.failed) {
            return;
        }
        try {
            self.run();
        } catch (Throwable t) {
            self.failed = true;
            Minecraft mc = mc();
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§cCrystalAura: ошибка, модуль остановлен (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")"));
            }
            t.printStackTrace();
        }
    }

    // ------------------------------------------------------------------------------------------------ the loop

    private void run() {
        Minecraft mc = mc();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || !player.isAlive() || player.isSpectator()
                || mc.gui.screen() != null) {
            return;
        }
        this.ticksSincePlace++;
        this.ticksSinceBreak++;
        // one action per tick, never while an item is used or another module is busy with items
        if (Attack.attackedThisTick() || player.isUsingItem() || ClickAction.busy() || OffhandSwapper.busy()) {
            return;
        }
        LivingEntity target = pickTarget(player);
        if (target == null) {
            return;
        }

        // 1) break a crystal that hurts the enemy and is safe for us
        EndCrystal crystal = bestCrystal(player, target);
        if (crystal != null) {
            if (this.ticksSinceBreak < this.breakDelay.value() + this.breakJitter) {
                return;
            }
            Vec3 aim = crystal.getBoundingBox().getCenter();
            if (!rayHitsBox(player, crystal.getBoundingBox().inflate(0.02), breakReach())) {
                turnTo(player, aim);
                return;
            }
            if (Attack.hitEntity(crystal, InteractionHand.MAIN_HAND)) {
                this.ticksSinceBreak = 0;
                this.breakJitter = this.randomize.value() ? ThreadLocalRandom.current().nextInt(2) : 0;
            }
            return;
        }

        // 2) place a new one
        if (this.ticksSincePlace < this.placeDelay.value() + this.placeJitter) {
            return;
        }
        InteractionHand hand = crystalHand(player);
        int slot = hand == null ? hotbarCrystalSlot(player) : -1;
        if (hand == null && slot < 0) {
            return;
        }
        Spot spot = bestSpot(player, target);
        if (spot == null) {
            return;
        }
        BlockHitResult face = faceOf(player, spot.pos(), false);
        if (face == null) {
            return;
        }
        BlockHitResult served = faceOf(player, spot.pos(), true);
        if (served == null) {
            turnTo(player, face.getLocation());
            return;
        }
        if (hand == null) {
            hand = InteractionHand.MAIN_HAND;
        }
        int original = player.getInventory().getSelectedSlot();
        if (slot >= 0) {
            player.getInventory().setSelectedSlot(slot);
        }
        InteractionResult result = mc.gameMode.useItemOn(player, hand, served);
        if (slot >= 0) {
            player.getInventory().setSelectedSlot(original);
        }
        if (result.consumesAction()) {
            player.swing(hand);
            this.ticksSincePlace = 0;
            this.placeJitter = this.randomize.value() ? ThreadLocalRandom.current().nextInt(2) : 0;
        }
    }

    private float breakReach() {
        return (float) this.breakRange.value();
    }

    // ------------------------------------------------------------------------------------------------ targets

    private LivingEntity pickTarget(LocalPlayer self) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        double range = this.targetRange.value();
        for (Entity entity : mc().level.entitiesForRendering()) {
            if (!(entity instanceof Player other) || other == self || !other.isAlive() || other.isSpectator()
                    || other.isCreative()) {
                continue;
            }
            if (this.players.value() && Friends.is(other.getGameProfile().name())) {
                continue;
            }
            double distance = self.distanceTo(other);
            if (distance < range && distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------------------------------------ crystals

    private EndCrystal bestCrystal(LocalPlayer player, LivingEntity target) {
        Vec3 eye = ServerView.eyePos(player);
        EndCrystal best = null;
        double bestScore = -1.0e9;
        for (Entity entity : mc().level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal) || !crystal.isAlive()) {
                continue;
            }
            AABB box = crystal.getBoundingBox();
            if (closest(box, eye).distanceTo(eye) > this.breakRange.value()) {
                continue;
            }
            Vec3 center = crystal.position();
            double enemy = damage(center, target);
            double mine = damage(center, player);
            if (!acceptable(player, enemy, mine, target)) {
                continue;
            }
            double score = enemy - mine * 0.8;
            if (score > bestScore) {
                bestScore = score;
                best = crystal;
            }
        }
        return best;
    }

    private boolean acceptable(LocalPlayer player, double enemy, double mine, LivingEntity target) {
        double health = target.getHealth() + target.getAbsorptionAmount();
        double need = health <= 8.0 ? 1.0 : this.minDamage.value();   // a finishing blow needs less
        if (enemy < need) {
            return false;
        }
        if (mine > this.maxSelf.value()) {
            return false;
        }
        return !this.safe.value() || player.getHealth() + player.getAbsorptionAmount() - mine > 1.0;
    }

    private InteractionHand crystalHand(LocalPlayer player) {
        if (player.getOffhandItem().is(Items.END_CRYSTAL)) {
            return InteractionHand.OFF_HAND;
        }
        return player.getMainHandItem().is(Items.END_CRYSTAL) ? InteractionHand.MAIN_HAND : null;
    }

    private int hotbarCrystalSlot(LocalPlayer player) {
        if (!this.hands.value().startsWith("Hotbar")) {
            return -1;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).is(Items.END_CRYSTAL)) {
                return slot;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------------------------------------ places

    private Spot bestSpot(LocalPlayer player, LivingEntity target) {
        Minecraft mc = mc();
        Vec3 eye = ServerView.eyePos(player);
        int radius = (int) Math.ceil(this.placeRange.value());
        BlockPos origin = BlockPos.containing(eye);
        List<Entity> crowd = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!entity.isSpectator() && entity.position().distanceToSqr(eye) < 64.0) {
                crowd.add(entity);
            }
        }
        record Candidate(BlockPos pos, double bound) {}
        List<Candidate> candidates = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    var base = mc.level.getBlockState(cursor);
                    if (!base.is(Blocks.OBSIDIAN) && !base.is(Blocks.BEDROCK)) {
                        continue;
                    }
                    if (!mc.level.getBlockState(cursor.above()).isAir() || !mc.level.getBlockState(cursor.above(2)).isAir()) {
                        continue;
                    }
                    BlockPos pos = cursor.immutable();
                    Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                    if (center.distanceTo(eye) > this.placeRange.value() + 0.5 || center.distanceTo(target.position()) > 10.0) {
                        continue;
                    }
                    AABB space = new AABB(pos.getX(), pos.getY() + 1.0, pos.getZ(), pos.getX() + 1.0, pos.getY() + 3.0, pos.getZ() + 1.0);
                    boolean blocked = false;
                    for (Entity entity : crowd) {
                        if (entity.getBoundingBox().intersects(space)) {
                            blocked = true;
                            break;
                        }
                    }
                    if (blocked) {
                        continue;
                    }
                    // an upper bound of the damage (nothing in the way): cheap, filters most of the places
                    double bound = rawDamage(center, target) * 1.0;
                    if (bound < 1.0) {
                        continue;
                    }
                    candidates.add(new Candidate(pos, bound));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::bound).reversed());
        Spot best = null;
        int checked = 0;
        for (Candidate candidate : candidates) {
            if (checked++ >= 10) {
                break;
            }
            Vec3 center = new Vec3(candidate.pos().getX() + 0.5, candidate.pos().getY() + 1.0, candidate.pos().getZ() + 0.5);
            double enemy = damage(center, target);
            double mine = damage(center, player);
            if (!acceptable(player, enemy, mine, target)) {
                continue;
            }
            if (faceOf(player, candidate.pos(), false) == null) {
                continue;
            }
            Spot spot = new Spot(candidate.pos(), enemy, mine);
            if (best == null || spot.score() > best.score()) {
                best = spot;
            }
        }
        return best;
    }

    /**
     * The visible face of the block: the first hit of a ray from the eye to the middle of the block. With
     * {@code fromServerAngles} the ray goes along the angles the server knows (what it will check), otherwise
     * straight at the block (where the head should turn).
     */
    private BlockHitResult faceOf(LocalPlayer player, BlockPos pos, boolean fromServerAngles) {
        Minecraft mc = mc();
        Vec3 eye = ServerView.eyePos(player);
        Vec3 end;
        if (fromServerAngles) {
            float yaw = ServerView.yaw(player.getYRot());
            float pitch = ServerView.pitch(player.getXRot());
            end = eye.add(look(yaw, pitch).scale(this.placeRange.value() + 0.5));
        } else {
            end = Vec3.atCenterOf(pos);
        }
        BlockHitResult hit = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) {
            return null;
        }
        if (hit.getLocation().distanceTo(eye) > this.placeRange.value()) {
            return null;
        }
        return hit;
    }

    // ------------------------------------------------------------------------------------------------ rays and turning

    private static Vec3 look(float yaw, float pitch) {
        return dev.hatek.client.module.impl.combat.aura.util.RayTraceUtil.getVectorForRotation(pitch, yaw);
    }

    private static Vec3 closest(AABB box, Vec3 point) {
        return new Vec3(Mth.clamp(point.x, box.minX, box.maxX), Mth.clamp(point.y, box.minY, box.maxY),
                Mth.clamp(point.z, box.minZ, box.maxZ));
    }

    /** True when the ray along the angles the server knows hits the box within the reach and is not blocked. */
    private boolean rayHitsBox(LocalPlayer player, AABB box, float reach) {
        Vec3 eye = ServerView.eyePos(player);
        Vec3 end = eye.add(look(ServerView.yaw(player.getYRot()), ServerView.pitch(player.getXRot())).scale(reach + 0.3));
        var hit = box.clip(eye, end);
        if (hit.isEmpty()) {
            return false;
        }
        BlockHitResult wall = mc().level.clip(new ClipContext(eye, hit.get(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return wall.getType() == HitResult.Type.MISS;
    }

    private void turnTo(LocalPlayer player, Vec3 point) {
        if (!this.rotate.value()) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        RotationProcess.updateFrame(new Rotation(yaw, Mth.clamp(pitch, -90.0F, 90.0F)), 90.0F, 60.0F, 90.0F, 60.0F, 10, 1);
    }

    // ------------------------------------------------------------------------------------------------ damage

    /** The explosion damage without anything in the way and without armor. */
    private static double rawDamage(Vec3 center, LivingEntity entity) {
        double distance = entity.position().distanceTo(center) / 12.0;
        if (distance > 1.0) {
            return 0.0;
        }
        double impact = 1.0 - distance;
        return (impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0;
    }

    /** The vanilla "seen percent": the share of rays from the blast to the body that are not blocked. */
    private static double exposure(Vec3 center, LivingEntity entity) {
        AABB box = entity.getBoundingBox();
        double sx = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double sy = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double sz = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        double ox = (1.0 - Math.floor(1.0 / sx) * sx) / 2.0;
        double oz = (1.0 - Math.floor(1.0 / sz) * sz) / 2.0;
        if (sx < 0.0 || sy < 0.0 || sz < 0.0) {
            return 0.0;
        }
        int seen = 0;
        int total = 0;
        for (double x = 0.0; x <= 1.0; x += sx) {
            for (double y = 0.0; y <= 1.0; y += sy) {
                for (double z = 0.0; z <= 1.0; z += sz) {
                    Vec3 point = new Vec3(Mth.lerp(x, box.minX, box.maxX) + ox, Mth.lerp(y, box.minY, box.maxY),
                            Mth.lerp(z, box.minZ, box.maxZ) + oz);
                    BlockHitResult hit = mc().level.clip(new ClipContext(point, center, ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, entity));
                    if (hit.getType() == HitResult.Type.MISS) {
                        seen++;
                    }
                    total++;
                }
            }
        }
        return total == 0 ? 0.0 : (double) seen / total;
    }

    /** Damage a crystal exploding at {@code center} does to the entity: distance, exposure, difficulty, armor. */
    private static double damage(Vec3 center, LivingEntity entity) {
        double distance = entity.position().distanceTo(center) / 12.0;
        if (distance > 1.0) {
            return 0.0;
        }
        double impact = (1.0 - distance) * exposure(center, entity);
        double damage = (impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0;
        if (entity instanceof Player) {
            Difficulty difficulty = mc().level.getDifficulty();
            if (difficulty == Difficulty.PEACEFUL) {
                return 0.0;
            }
            if (difficulty == Difficulty.EASY) {
                damage = Math.min(damage / 2.0 + 1.0, damage);
            } else if (difficulty == Difficulty.HARD) {
                damage = damage * 3.0 / 2.0;
            }
        }
        double armor = entity.getArmorValue();
        double toughness = entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double f = 2.0 + toughness / 4.0;
        double g = Mth.clamp(armor - damage / f, armor * 0.2, 20.0);
        damage = damage * (1.0 - g / 25.0);
        if (entity.hasEffect(MobEffects.RESISTANCE)) {
            int level = entity.getEffect(MobEffects.RESISTANCE).getAmplifier() + 1;
            damage = damage * Math.max(0.0, 1.0 - level * 0.2);
        }
        return Math.max(0.0, damage);
    }
}
