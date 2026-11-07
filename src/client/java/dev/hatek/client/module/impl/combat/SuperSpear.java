package dev.hatek.client.module.impl.combat;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.impl.movement.DragonFly;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * SuperSpear: turn the module on (its own key, a keyboard key or a side mouse button such as MOUSE 4), then press the
 * right mouse button with a spear in hand while aiming near a player. The module marks the player nearest to your
 * crosshair, charges the spear (it only starts to hurt after its charge delay), then rushes through the mark at high
 * speed and checks in the chat whether the server registered the hit.
 *
 *   SuperSpear: Метка: ник. Подготовка удара.
 *   SuperSpear: Попадание подтверждено сервером; метка снята.
 *
 * The rush speeds up every tick (DragonFly's Speed counts if it is higher), keeps the aim on the target and lands its
 * last step exactly in the target's middle before passing through, so the server sees you inside the target.
 */
public final class SuperSpear extends Module {
    private static final int PASS_TICKS = 4;
    private static final int MAX_PREPARE_TICKS = 60;

    private enum Phase { IDLE, PREPARE, DASH, PASS }

    private static SuperSpear instance;

    private final BoolSetting onlyWithSpear = new BoolSetting("Only with a spear in hand", true);
    private final SliderSetting speed = new SliderSetting("Top speed (blocks/tick)", 4.0, 0.5, 8.0, 0.1);
    private final SliderSetting acceleration = new SliderSetting("Acceleration (blocks/tick per tick)", 0.6, 0.05, 3.0, 0.05);
    private final SliderSetting range = new SliderSetting("Range (blocks)", 100.0, 10.0, 150.0, 1.0);
    private final SliderSetting cone = new SliderSetting("Mark cone (degrees)", 25.0, 3.0, 180.0, 1.0);
    private final BoolSetting useDragonFly = new BoolSetting("Use DragonFly (switch it on, take the faster speed)", true);
    private final BoolSetting stabOnContact = new BoolSetting("Also stab on contact", true);
    private final BoolSetting stopAfter = new BoolSetting("Stop after the pierce or attempt", true);

    private Phase phase = Phase.IDLE;
    private boolean keyWas;
    private LivingEntity target;
    private int ticks;
    private double currentSpeed;
    private Vec3 passDirection = Vec3.ZERO;
    private boolean stabbed;
    private float startHealth;
    private float lowestHealth;
    private boolean active;          // true on a tick where the module steers the player

    public SuperSpear() {
        super("SuperSpear", "Right click with a spear aimed at a player: mark, charge, rush through", Category.COMBAT);
        instance = this;
        with(this.onlyWithSpear, this.speed, this.acceleration, this.range, this.cone, this.useDragonFly,
                this.stabOnContact, this.stopAfter);
    }

    public static SuperSpear instance() {
        return instance;
    }

    /** True while the module steers the player (DragonFly and the movement keys must not fight its momentum). */
    public static boolean isActive() {
        return instance != null && instance.active;
    }

    @Override
    protected void onDisable() {
        abort(false);
    }

    private static void message(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§bSuperSpear: §f" + text));
        }
    }

    private static float healthOf(LivingEntity entity) {
        return entity.getHealth() + entity.getAbsorptionAmount();
    }

    private void abort(boolean announce) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.keyUse.setDown(false);
        }
        if (announce && this.phase != Phase.IDLE) {
            message("Удар отменён, метка снята.");
        }
        this.phase = Phase.IDLE;
        this.target = null;
        this.ticks = 0;
        this.currentSpeed = 0.0;
        this.stabbed = false;
    }

    private double topSpeed() {
        DragonFly fly = DragonFly.instance();
        double own = this.speed.value();
        return this.useDragonFly.value() && fly != null && fly.isEnabled() ? Math.max(own, fly.speedValue()) : own;
    }

    /** Start of LocalPlayer.tick (after DragonFly). */
    public void beforeTick(LocalPlayer player) {
        this.active = false;
        if (!isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // the trigger is a right click (use) with the module on, aimed near a player
        boolean down = mc.options.keyUse.isDown();
        boolean pressed = down && !this.keyWas && mc.gui.screen() == null;
        this.keyWas = down;

        if (mc.gameMode == null || mc.level == null || !player.isAlive() || mc.gui.screen() != null) {
            if (this.phase != Phase.IDLE) {
                abort(false);
            }
            return;
        }
        if (pressed && this.phase == Phase.IDLE
                && (!this.onlyWithSpear.value() || player.getMainHandItem().is(ItemTags.SPEARS))) {
            start(player);
            return;
        }
        if (this.phase == Phase.IDLE) {
            return;
        }
        if (this.target == null || !this.target.isAlive() || this.target.level() != player.level()) {
            if (this.target != null && !this.target.isAlive()) {
                finish(player, true);
            } else {
                abort(false);
            }
            return;
        }
        this.lowestHealth = Math.min(this.lowestHealth, healthOf(this.target));

        switch (this.phase) {
            case PREPARE -> prepare(player, mc);
            case DASH -> dash(player, mc);
            case PASS -> pass(player);
            default -> {
            }
        }
    }

    private void start(LocalPlayer player) {
        LivingEntity mark = findTarget(player);
        if (mark == null) {
            message("Цель не найдена.");
            return;
        }
        int slot = findSpear(player);
        if (slot < 0) {
            message("Копьё в хотбаре не найдено.");
            return;
        }
        player.getInventory().setSelectedSlot(slot);
        DragonFly fly = DragonFly.instance();
        if (this.useDragonFly.value() && fly != null && !fly.isEnabled()) {
            fly.setEnabled(true);
        }
        this.target = mark;
        this.startHealth = healthOf(mark);
        this.lowestHealth = this.startHealth;
        this.ticks = 0;
        this.currentSpeed = 0.0;
        this.stabbed = false;
        this.phase = Phase.PREPARE;
        message("Метка: " + mark.getName().getString() + ". Подготовка удара.");
    }

    /** Charge the spear until its delay has passed, with the aim already on the target. */
    private void prepare(LocalPlayer player, Minecraft mc) {
        if (++this.ticks > MAX_PREPARE_TICKS) {
            message("Копьё не зарядилось, удар отменён.");
            abort(false);
            return;
        }
        aimAt(player, this.target.getBoundingBox().getCenter());
        mc.options.keyUse.setDown(true);
        if (!player.isUsingItem()) {
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        }
        ItemStack spear = player.getMainHandItem();
        if (!spear.is(ItemTags.SPEARS)) {
            message("Копьё не в руке, удар отменён.");
            abort(false);
            return;
        }
        KineticWeapon kinetic = spear.get(DataComponents.KINETIC_WEAPON);
        int delay = kinetic != null ? kinetic.delayTicks() : 0;
        if (player.isUsingItem() && player.getTicksUsingItem() >= delay + 1) {
            this.phase = Phase.DASH;
            this.ticks = 0;
        }
    }

    private void dash(LocalPlayer player, Minecraft mc) {
        mc.options.keyUse.setDown(true);
        if (!player.isUsingItem()) {
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        }
        if (++this.ticks > 200) {
            finish(player, false);
            return;
        }
        double top = topSpeed();
        double horizontalNow = Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z);
        this.currentSpeed = Math.min(top, Math.max(this.currentSpeed, Math.max(1.0, horizontalNow)) + this.acceleration.value());

        Vec3 toTarget = this.target.getBoundingBox().getCenter().subtract(player.getEyePosition());
        aimAt(player, this.target.getBoundingBox().getCenter());
        double distance = toTarget.length();
        Vec3 direction = toTarget.normalize();
        if (distance <= this.currentSpeed + 0.3) {
            // last step: land exactly in the middle of the target, then go through it
            player.setDeltaMovement(direction.scale(distance));
            this.passDirection = direction;
            this.phase = Phase.PASS;
            this.ticks = 0;
        } else {
            player.setDeltaMovement(direction.scale(this.currentSpeed));
        }
        this.active = true;
    }

    private void pass(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (this.stabOnContact.value() && !this.stabbed) {
            this.stabbed = true;
            mc.gameMode.attack(player, this.target);
            player.swing(InteractionHand.MAIN_HAND);
        }
        boolean hitSeen = healthOf(this.target) < this.startHealth - 0.01f;
        if (++this.ticks > PASS_TICKS || (hitSeen && this.ticks > 1)) {
            finish(player, false);
            return;
        }
        player.setDeltaMovement(this.passDirection.scale(this.currentSpeed));
        this.active = true;
    }

    /** Ends the attempt: stop, release the spear and tell the chat what the server did. */
    private void finish(LocalPlayer player, boolean targetDied) {
        LivingEntity mark = this.target;
        String name = mark != null ? mark.getName().getString() : "цель";
        float lost = mark != null ? this.startHealth - Math.min(this.lowestHealth, healthOf(mark)) : 0.0f;
        if (this.stopAfter.value()) {
            player.setDeltaMovement(Vec3.ZERO);
            this.active = true;
        }
        if (targetDied || (mark != null && !mark.isAlive())) {
            message("Попадание подтверждено сервером; " + name + " убит, метка снята.");
        } else if (lost > 0.01f) {
            message(String.format(Locale.ROOT, "Попадание подтверждено сервером (-%.1f хп); метка снята.", lost));
        } else {
            message("Попадания нет: сервер не засчитал удар по " + name + "; метка снята.");
        }
        abort(false);
    }

    private static void aimAt(LocalPlayer player, Vec3 point) {
        Vec3 to = point.subtract(player.getEyePosition());
        double horizontal = Math.hypot(to.x, to.z);
        player.setYRot((float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0));
        player.setXRot(Mth.clamp((float) -Math.toDegrees(Math.atan2(to.y, horizontal)), -90.0f, 90.0f));
    }

    private static int findSpear(LocalPlayer player) {
        if (player.getMainHandItem().is(ItemTags.SPEARS)) {
            return player.getInventory().getSelectedSlot();
        }
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).is(ItemTags.SPEARS)) {
                return slot;
            }
        }
        return -1;
    }

    /** The player closest to the crosshair within the mark cone and the range. */
    private LivingEntity findTarget(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double maxRange = this.range.value();
        double maxAngle = this.cone.value();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Player other : mc.level.players()) {
            if (other == player || other.isSpectator() || !other.isAlive()) {
                continue;
            }
            Vec3 to = other.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            if (distance > maxRange || distance < 0.5) {
                continue;
            }
            double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, look.dot(to.normalize())))));
            if (angle > maxAngle) {
                continue;
            }
            double score = angle + distance * 0.1;
            if (score < bestScore) {
                bestScore = score;
                best = other;
            }
        }
        return best;
    }
}
