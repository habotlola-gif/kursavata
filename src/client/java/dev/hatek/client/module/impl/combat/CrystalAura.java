package dev.hatek.client.module.impl.combat;

import dev.hatek.client.feature.command.Friends;
import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.impl.combat.aura.Attack;
import dev.hatek.client.module.impl.combat.aura.rotation.Rotation;
import dev.hatek.client.module.impl.combat.aura.rotation.RotationProcess;
import dev.hatek.client.module.impl.combat.aura.rotation.ServerView;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ColorSetting;
import dev.hatek.client.module.setting.KeySetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
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

    private final KeySetting shotKey = new KeySetting("Single shot (key)", -1);
    private final BoolSetting auto = new BoolSetting("Auto (continuous)", true);
    private final BoolSetting dig = new BoolSetting("Dig + obsidian when there is no base", true);
    private final BoolSetting render = new BoolSetting("Highlight the spots", true);
    private final BoolSetting debug = new BoolSetting("Debug: say why nothing happens", false);
    private final ColorSetting obsidianColor = new ColorSetting("Highlight: obsidian / dig", 0xFFB05CFF);
    private final ColorSetting crystalColor = new ColorSetting("Highlight: crystal", 0xFF4FE8FF);

    private enum Phase { IDLE, CRYSTAL, PREPARE }

    private Phase phase = Phase.IDLE;
    private BlockPos shotBase;
    private int shotTicks;
    private boolean keyWas;
    private boolean shotRequested;
    private boolean digging;
    private BlockPos digPos;
    private int digSlot = -1;

    // what is drawn: the block that will get obsidian (or be dug), the base of the next crystal, the crystal to break
    private BlockPos espObsidian;
    private BlockPos espBase;
    private AABB espCrystal;
    private long espAt;

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
                this.placeDelay, this.breakDelay, this.randomize, this.rotate, this.players, this.shotKey, this.auto, this.dig,
                this.render, this.obsidianColor, this.crystalColor, this.debug);
        this.obsidianColor.visibleIf(this.render::value);
        this.crystalColor.visibleIf(this.render::value);
    }

    public static CrystalAura instance() {
        return instance;
    }

    @Override
    public void onClientTick() {
        boolean down = this.shotKey.isDown();
        if (down && !this.keyWas && mc().gui.screen() == null) {
            this.shotRequested = true;
        }
        this.keyWas = down;
    }

    @Override
    protected void onDisable() {
        this.phase = Phase.IDLE;
        this.shotRequested = false;
        stopDigging();
    }

    @Override
    protected void onEnable() {
        this.failed = false;
        this.phase = Phase.IDLE;
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
        if (this.prepareCooldown > 0) {
            this.prepareCooldown--;
        }
        // one action per tick, never while an item is used or another module is busy with items
        if (Attack.attackedThisTick() || player.isUsingItem() || ClickAction.busy() || OffhandSwapper.busy()) {
            return;
        }
        LivingEntity target = pickTarget(player);
        if (this.shotRequested) {
            this.shotRequested = false;
            if (this.phase != Phase.IDLE) {
                cancelShot(null);
            } else if (target != null) {
                this.silentPrepare = false;
                startShot(player, target);
            } else {
                say("нет цели рядом");
            }
        }
        if (target == null) {
            if (this.phase != Phase.IDLE) {
                cancelShot("цель пропала");
            }
            why("нет цели: игрок в радиусе " + (int) this.targetRange.value() + " блоков не найден (друзья и креатив пропускаются)");
            return;
        }
        if (this.phase != Phase.IDLE) {
            runShot(player, target);
            return;
        }
        if (!this.auto.value()) {
            return;
        }

        // 1) break a crystal that hurts the enemy and is safe for us
        EndCrystal crystal = bestCrystal(player, target);
        if (crystal != null) {
            this.espCrystal = crystal.getBoundingBox();
            this.espAt = System.currentTimeMillis();
            if (this.ticksSinceBreak < this.breakDelay.value() + this.breakJitter) {
                return;
            }
            if (breakCrystal(player, crystal)) {
                this.ticksSinceBreak = 0;
                this.breakJitter = this.randomize.value() ? ThreadLocalRandom.current().nextInt(2) : 0;
            }
            return;
        }

        // 2) place a new one
        if (this.ticksSincePlace < this.placeDelay.value() + this.placeJitter) {
            return;
        }
        Spot spot = bestSpot(player, target);
        if (spot == null) {
            why("нет места для кристалла: " + this.spotInfo);
            if (this.dig.value() && this.prepareCooldown <= 0) {
                // no obsidian near the enemy: make a base (dig the block, put obsidian in)
                BlockPos base = bestPrepare(player, target);
                if (base != null) {
                    this.phase = Phase.PREPARE;
                    this.shotBase = base;
                    this.shotTicks = 0;
                    this.silentPrepare = true;
                    why("готовлю основание под обсидиан");
                } else {
                    this.prepareCooldown = 10;
                    why("нет места и для нового обсидиана рядом с целью");
                }
            }
            return;
        }
        if (crystalHand(player) == null && hotbarCrystalSlot(player) < 0) {
            why("нет кристаллов: " + (this.hands.value().startsWith("Hotbar") ? "в хотбаре" : "в руках") + " их нет");
            return;
        }
        if (placeCrystalAt(player, spot.pos())) {
            this.ticksSincePlace = 0;
            this.placeJitter = this.randomize.value() ? ThreadLocalRandom.current().nextInt(2) : 0;
        } else {
            why("кристалл: разворачивается к блоку / угол ещё не дошёл до сервера");
        }
    }

    // ------------------------------------------------------------------------------------------------ single shot

    private void why(String text) {
        long now = System.currentTimeMillis();
        if (this.debug.value() && now - this.lastWhyAt > 1000L) {
            this.lastWhyAt = now;
            say(text);
        }
    }

    private void say(String text) {
        Minecraft mc = mc();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§bCrystalAura: §f" + text));
        }
    }

    private void cancelShot(String reason) {
        boolean silent = this.silentPrepare;
        this.phase = Phase.IDLE;
        this.silentPrepare = false;
        this.prepareCooldown = 20;
        stopDigging();
        if (reason != null) {
            if (silent) {
                why(reason);
            } else {
                say(reason);
            }
        }
    }

    /**
     * The key: with an obsidian base near the enemy it places one crystal and breaks it once; with no base it makes
     * one (digs the block and puts obsidian into the hole, or just puts obsidian on the empty place).
     */
    private void startShot(LocalPlayer player, LivingEntity target) {
        this.shotTicks = 0;
        Spot spot = bestSpot(player, target);
        if (spot != null) {
            this.phase = Phase.CRYSTAL;
            this.shotBase = spot.pos();
            return;
        }
        if (!this.dig.value()) {
            say("нет обсидиана рядом с целью");
            return;
        }
        BlockPos base = bestPrepare(player, target);
        if (base == null) {
            say("нет места под обсидиан");
            return;
        }
        this.phase = Phase.PREPARE;
        this.shotBase = base;
    }

    private void runShot(LocalPlayer player, LivingEntity target) {
        if (++this.shotTicks > 60) {
            cancelShot("не получилось вовремя, отмена");
            return;
        }
        if (this.phase == Phase.CRYSTAL) {
            this.espBase = this.shotBase;
            this.espAt = System.currentTimeMillis();
            EndCrystal existing = crystalAt(this.shotBase);
            if (existing != null) {
                this.espCrystal = existing.getBoundingBox();
                Vec3 center = existing.position();
                if (!acceptable(player, damage(center, target), damage(center, player), target)) {
                    cancelShot("кристалл невыгоден или опасен, отмена");
                    return;
                }
                if (breakCrystal(player, existing)) {
                    this.phase = Phase.IDLE;
                }
                return;
            }
            if (this.ticksSincePlace >= 2 || this.shotTicks == 1) {
                if (placeCrystalAt(player, this.shotBase)) {
                    this.ticksSincePlace = 0;
                }
            }
            return;
        }
        // PREPARE: dig what is there, then obsidian
        this.espObsidian = this.shotBase;
        this.espAt = System.currentTimeMillis();
        BlockState state = mc().level.getBlockState(this.shotBase);
        if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK)) {
            stopDigging();
            this.phase = Phase.IDLE;
            if (this.silentPrepare) {
                this.silentPrepare = false;
            } else {
                say("обсидиан стоит, нажми ещё раз для взрыва");
            }
            return;
        }
        if (!state.isAir() && !state.canBeReplaced()) {
            digBlock(player, this.shotBase);
            return;
        }
        stopDigging();
        placeObsidianAt(player, this.shotBase);
    }

    private EndCrystal crystalAt(BlockPos base) {
        Vec3 center = new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
        for (Entity entity : mc().level.entitiesForRendering()) {
            if (entity instanceof EndCrystal crystal && crystal.isAlive() && crystal.position().distanceToSqr(center) < 1.0) {
                return crystal;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------ actions

    /** Turns toward the crystal and breaks it when the server already knows the angle. True when the hit went out. */
    private boolean breakCrystal(LocalPlayer player, EndCrystal crystal) {
        if (!rayHitsBox(player, crystal.getBoundingBox().inflate(0.02), breakReach())) {
            turnTo(player, crystal.getBoundingBox().getCenter());
            return false;
        }
        return Attack.hitEntity(crystal, InteractionHand.MAIN_HAND);
    }

    /** Puts a crystal on the base: turns first, clicks when the angle is known to the server. */
    private boolean placeCrystalAt(LocalPlayer player, BlockPos base) {
        Minecraft mc = mc();
        InteractionHand hand = crystalHand(player);
        int slot = hand == null ? hotbarCrystalSlot(player) : -1;
        if (hand == null && slot < 0) {
            return false;
        }
        this.espBase = base;
        this.espAt = System.currentTimeMillis();
        BlockHitResult face = faceOf(player, base, false);
        if (face == null) {
            return false;
        }
        BlockHitResult served = faceOf(player, base, true);
        if (served == null) {
            turnTo(player, face.getLocation());
            return false;
        }
        if (hand == null) {
            hand = InteractionHand.MAIN_HAND;
        }
        return useOn(player, hand, slot, served);
    }

    private boolean useOn(LocalPlayer player, InteractionHand hand, int slot, BlockHitResult hit) {
        Minecraft mc = mc();
        int original = player.getInventory().getSelectedSlot();
        if (slot >= 0) {
            player.getInventory().setSelectedSlot(slot);
        }
        InteractionResult result = mc.gameMode.useItemOn(player, hand, hit);
        if (slot >= 0) {
            player.getInventory().setSelectedSlot(original);
        }
        if (result.consumesAction()) {
            player.swing(hand);
            return true;
        }
        return false;
    }

    private int hotbarSlotOf(LocalPlayer player, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).is(item)) {
                return slot;
            }
        }
        return -1;
    }

    /** Puts obsidian on the empty place, against a neighbouring block. */
    private void placeObsidianAt(LocalPlayer player, BlockPos pos) {
        int slot = hotbarSlotOf(player, Items.OBSIDIAN);
        if (slot < 0) {
            cancelShot("нет обсидиана в хотбаре");
            return;
        }
        BlockHitResult aim = supportFace(player, pos, false);
        if (aim == null) {
            cancelShot("не к чему прислонить обсидиан");
            return;
        }
        BlockHitResult served = supportFace(player, pos, true);
        if (served == null) {
            turnTo(player, aim.getLocation());
            return;
        }
        useOn(player, InteractionHand.MAIN_HAND, slot, served);
    }

    /**
     * A neighbour of the empty place to put the block against: the first one (below, then the sides, then above) whose
     * face that looks at the place can be seen. {@code fromServerAngles}: along the angles the server knows.
     */
    private BlockHitResult supportFace(LocalPlayer player, BlockPos pos, boolean fromServerAngles) {
        Minecraft mc = mc();
        Vec3 eye = ServerView.eyePos(player);
        Direction[] order = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP};
        for (Direction direction : order) {
            BlockPos neighbour = pos.relative(direction);
            BlockState state = mc.level.getBlockState(neighbour);
            if (state.isAir() || state.canBeReplaced()) {
                continue;
            }
            Direction clicked = direction.getOpposite();
            Vec3 faceCenter = Vec3.atCenterOf(neighbour).relative(clicked, 0.5);
            Vec3 end = fromServerAngles
                    ? eye.add(look(ServerView.yaw(player.getYRot()), ServerView.pitch(player.getXRot())).scale(this.placeRange.value() + 0.5))
                    : faceCenter;
            BlockHitResult hit = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(neighbour) && hit.getDirection() == clicked
                    && hit.getLocation().distanceTo(eye) <= this.placeRange.value()) {
                return hit;
            }
        }
        return null;
    }

    /** Breaks a block the usual way: turn, start, keep the mining going with swings until it is gone. */
    private void digBlock(LocalPlayer player, BlockPos pos) {
        Minecraft mc = mc();
        BlockHitResult aim = faceOf(player, pos, false);
        if (aim == null) {
            cancelShot("блок не виден или далеко, подкоп отменён");
            return;
        }
        BlockHitResult served = faceOf(player, pos, true);
        if (served == null) {
            if (this.digging) {
                stopDigging();
            }
            turnTo(player, aim.getLocation());
            return;
        }
        if (!this.digging || !pos.equals(this.digPos)) {
            this.digSlot = bestToolSlot(player, mc.level.getBlockState(pos));
            this.digPos = pos;
            if (this.digSlot >= 0 && this.digSlot != player.getInventory().getSelectedSlot()) {
                this.savedSlot = player.getInventory().getSelectedSlot();
                player.getInventory().setSelectedSlot(this.digSlot);
            }
            if (mc.gameMode.startDestroyBlock(pos, served.getDirection())) {
                this.digging = true;
            }
        } else {
            mc.gameMode.continueDestroyBlock(pos, served.getDirection());
        }
        player.swing(InteractionHand.MAIN_HAND);
        // keep the head on the block for the movement packet of this tick as well
        turnTo(player, aim.getLocation());
    }

    private int savedSlot = -1;
    private boolean silentPrepare;
    private int prepareCooldown;
    private long lastWhyAt;
    private String spotInfo = "";

    private void stopDigging() {
        Minecraft mc = mc();
        if (this.digging && mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
        }
        this.digging = false;
        this.digPos = null;
        if (this.savedSlot >= 0 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(this.savedSlot);
        }
        this.savedSlot = -1;
    }

    private int bestToolSlot(LocalPlayer player, BlockState state) {
        int best = -1;
        float bestSpeed = 1.0f;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = slot;
            }
        }
        return best;
    }

    /**
     * Where to make a base when there is none: a block next to the enemy's feet (one level lower), empty or breakable,
     * with room above for a crystal, from where the explosion hurts the enemy and is safe for us.
     */
    private BlockPos bestPrepare(LocalPlayer player, LivingEntity target) {
        Minecraft mc = mc();
        Vec3 eye = ServerView.eyePos(player);
        BlockPos feet = target.blockPosition();
        BlockPos best = null;
        double bestScore = -1.0e9;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -2; dy <= -1; dy++) {
                    BlockPos pos = feet.offset(dx, dy, dz);
                    BlockState state = mc.level.getBlockState(pos);
                    boolean empty = state.isAir() || state.canBeReplaced();
                    if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK)) {
                        continue;
                    }
                    if (!empty) {
                        float hardness = state.getDestroySpeed(mc.level, pos);
                        if (hardness < 0.0f || !state.getFluidState().isEmpty()) {
                            continue;
                        }
                    }
                    if (!mc.level.getBlockState(pos.above()).isAir() || !mc.level.getBlockState(pos.above(2)).isAir()) {
                        continue;
                    }
                    Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                    if (center.distanceTo(eye) > this.placeRange.value() || center.distanceTo(target.position()) > 8.0) {
                        continue;
                    }
                    AABB space = new AABB(pos.getX(), pos.getY() + 1.0, pos.getZ(), pos.getX() + 1.0, pos.getY() + 3.0, pos.getZ() + 1.0);
                    if (target.getBoundingBox().intersects(space) || player.getBoundingBox().intersects(space)) {
                        continue;
                    }
                    if (rawDamage(center, target) < 1.0) {
                        continue;
                    }
                    if (empty ? supportFace(player, pos, false) == null : faceOf(player, pos, false) == null) {
                        continue;
                    }
                    double enemy = damage(center, target);
                    double mine = damage(center, player);
                    if (!acceptable(player, enemy, mine, target)) {
                        continue;
                    }
                    double score = enemy - mine * 0.8 - (empty ? -0.5 : 2.0);
                    if (score > bestScore) {
                        bestScore = score;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------------------------------------ highlight

    /** HUD element: boxes over the obsidian / dig place and the crystal (HatekClient). */
    public void renderEsp(GuiGraphicsExtractor gg, DeltaTracker delta) {
        Minecraft mc = mc();
        if (!isEnabled() || !this.render.value() || mc.player == null || mc.level == null
                || !dev.hatek.client.render.WorldProjection.isValid()
                || System.currentTimeMillis() - this.espAt > 600L) {
            return;
        }
        dev.hatek.client.ui.render.Render2D.begin(gg);
        try {
            int obsidian = this.obsidianColor.value();
            int crystal = this.crystalColor.value();
            if (this.espObsidian != null && this.phase == Phase.PREPARE) {
                box(gg, new AABB(this.espObsidian), obsidian, 0.16f);
                box(gg, new AABB(this.espObsidian.above()).deflate(0.2, 0.0, 0.2), crystal, 0.06f);
            }
            if (this.espBase != null) {
                BlockPos base = this.espBase;
                box(gg, new AABB(base), obsidian, 0.10f);
                box(gg, new AABB(base.getX() + 0.15, base.getY() + 1.0, base.getZ() + 0.15,
                        base.getX() + 0.85, base.getY() + 1.9, base.getZ() + 0.85), crystal, 0.14f);
            }
            if (this.espCrystal != null) {
                box(gg, this.espCrystal.deflate(0.3), crystal, 0.2f);
            }
        } finally {
            dev.hatek.client.ui.render.Render2D.end(gg);
        }
    }

    private static void box(GuiGraphicsExtractor gg, AABB b, int argb, float fill) {
        Vec3[] c = {
                new Vec3(b.minX, b.minY, b.minZ), new Vec3(b.maxX, b.minY, b.minZ), new Vec3(b.maxX, b.minY, b.maxZ), new Vec3(b.minX, b.minY, b.maxZ),
                new Vec3(b.minX, b.maxY, b.minZ), new Vec3(b.maxX, b.maxY, b.minZ), new Vec3(b.maxX, b.maxY, b.maxZ), new Vec3(b.minX, b.maxY, b.maxZ)};
        float[][] p = new float[8][];
        for (int i = 0; i < 8; i++) {
            p[i] = dev.hatek.client.render.WorldProjection.toScreen(c[i]);
            if (p[i] == null) {
                return;
            }
        }
        int line = (argb & 0x00FFFFFF) | 0xFF000000;
        int face = dev.hatek.client.ui.render.Render2D.withAlpha(line, fill);
        float th = Math.max(1.0f, 1.2f * dev.hatek.client.ui.render.Render2D.guiScale());
        int[][] faces = {{0, 1, 2, 3}, {4, 5, 6, 7}, {0, 1, 5, 4}, {1, 2, 6, 5}, {2, 3, 7, 6}, {3, 0, 4, 7}};
        for (int[] f : faces) {
            dev.hatek.client.ui.render.Draw.triangle(gg, p[f[0]][0], p[f[0]][1], p[f[1]][0], p[f[1]][1], p[f[2]][0], p[f[2]][1], face);
            dev.hatek.client.ui.render.Draw.triangle(gg, p[f[0]][0], p[f[0]][1], p[f[2]][0], p[f[2]][1], p[f[3]][0], p[f[3]][1], face);
        }
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            dev.hatek.client.ui.render.Draw.line(gg, p[e[0]][0], p[e[0]][1], p[e[1]][0], p[e[1]][1], th, line);
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
        int bases = 0;
        int inRange = 0;
        int roomed = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    var base = mc.level.getBlockState(cursor);
                    if (!base.is(Blocks.OBSIDIAN) && !base.is(Blocks.BEDROCK)) {
                        continue;
                    }
                    bases++;
                    if (!mc.level.getBlockState(cursor.above()).isAir() || !mc.level.getBlockState(cursor.above(2)).isAir()) {
                        continue;
                    }
                    BlockPos pos = cursor.immutable();
                    Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                    if (center.distanceTo(eye) > this.placeRange.value() + 0.5 || center.distanceTo(target.position()) > 10.0) {
                        continue;
                    }
                    inRange++;
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
        int tooWeak = 0;
        int unsafe = 0;
        int hidden = 0;
        double bestSeen = 0.0;
        for (Candidate candidate : candidates) {
            if (checked++ >= 10) {
                break;
            }
            Vec3 center = new Vec3(candidate.pos().getX() + 0.5, candidate.pos().getY() + 1.0, candidate.pos().getZ() + 0.5);
            double enemy = damage(center, target);
            double mine = damage(center, player);
            bestSeen = Math.max(bestSeen, enemy);
            if (!acceptable(player, enemy, mine, target)) {
                if (enemy < this.minDamage.value()) {
                    tooWeak++;
                } else {
                    unsafe++;
                }
                continue;
            }
            if (faceOf(player, candidate.pos(), false) == null) {
                hidden++;
                continue;
            }
            Spot spot = new Spot(candidate.pos(), enemy, mine);
            if (best == null || spot.score() > best.score()) {
                best = spot;
            }
        }
        this.spotInfo = String.format(java.util.Locale.ROOT,
                "обсидиана/бедрока в радиусе %d, подходящих по дальности %d, с местом сверху и по урону %d, мало урона врагу %d (макс. %.1f), опасно для тебя %d, не видно %d",
                bases, inRange, candidates.size(), tooWeak, bestSeen, unsafe, hidden);
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
