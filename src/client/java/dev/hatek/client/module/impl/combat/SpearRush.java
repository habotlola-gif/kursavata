package dev.hatek.client.module.impl.combat;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * SpearRush: press the module's key, it picks the player nearest to your crosshair, turns toward them, holds the
 * spear charge and rushes through them at high speed. The spear's charge damage grows with the speed you move at
 * (the server measures it from your movement), so a fast rush hits very hard. Turns itself off after the hit, when
 * the target is gone or when the time runs out.
 */
public final class SpearRush extends Module {
    private static final int POST_CONTACT_TICKS = 12;

    private static SpearRush instance;

    private final SliderSetting range = new SliderSetting("Range (blocks)", 40.0, 10.0, 80.0, 1.0);
    private final SliderSetting speed = new SliderSetting("Speed (blocks/tick)", 2.0, 0.5, 5.0, 0.1);
    private final SliderSetting duration = new SliderSetting("Max time (s)", 4.0, 1.0, 10.0, 0.5);
    private final BoolSetting autoSpear = new BoolSetting("Take a spear from hotbar", true);

    private LivingEntity target;
    private int ticks;
    private int contactTicks;
    private boolean started;

    public SpearRush() {
        super("SpearRush", "Rush at the nearest target with a charged spear", Category.COMBAT);
        instance = this;
        with(this.range, this.speed, this.duration, this.autoSpear);
    }

    public static SpearRush instance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        this.target = null;
        this.ticks = 0;
        this.contactTicks = 0;
        this.started = false;
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        this.target = null;
        if (mc.options != null) {
            mc.options.keyUse.setDown(false);
        }
    }

    private static void message(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(text));
        }
    }

    /** Start of LocalPlayer.tick. */
    public void beforeTick(LocalPlayer player) {
        if (!isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.level == null || !player.isAlive()) {
            toggle();
            return;
        }
        if (this.target == null) {
            this.target = findTarget(mc, player);
            if (this.target == null) {
                message("§cSpearRush: no target in range");
                toggle();
                return;
            }
            message("§aSpearRush: target " + this.target.getName().getString());
        }
        if (!this.target.isAlive() || this.target.level() != player.level()
                || this.ticks++ > Math.round(this.duration.value() * 20.0)
                || this.contactTicks > POST_CONTACT_TICKS) {
            toggle();
            return;
        }
        if (this.autoSpear.value() && !player.getMainHandItem().is(ItemTags.SPEARS)) {
            int slot = findSpear(player);
            if (slot < 0) {
                message("§cSpearRush: no spear in the hotbar");
                toggle();
                return;
            }
            player.getInventory().setSelectedSlot(slot);
        }
        if (!player.getMainHandItem().is(ItemTags.SPEARS)) {
            message("§cSpearRush: hold a spear");
            toggle();
            return;
        }

        Vec3 aim = this.target.getBoundingBox().getCenter();
        Vec3 eye = player.getEyePosition();
        Vec3 toTarget = aim.subtract(eye);
        double horizontal = Math.hypot(toTarget.x, toTarget.z);
        player.setYRot((float) (Math.toDegrees(Math.atan2(toTarget.z, toTarget.x)) - 90.0));
        player.setXRot((float) -Math.toDegrees(Math.atan2(toTarget.y, horizontal)));

        // charge the spear and keep it charged
        if (!this.started || !player.isUsingItem()) {
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            this.started = true;
        }
        mc.options.keyUse.setDown(true);

        // rush: velocity straight at the target at the chosen speed
        double distance = toTarget.length();
        if (distance < 2.5) {
            this.contactTicks++;
        }
        Vec3 direction = toTarget.normalize();
        player.setDeltaMovement(direction.scale(this.speed.value()));
    }

    private LivingEntity findTarget(Minecraft mc, LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        double maxRange = this.range.value();
        for (Player other : mc.level.players()) {
            if (other == player || other.isSpectator() || !other.isAlive()) {
                continue;
            }
            Vec3 to = other.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            if (distance > maxRange) {
                continue;
            }
            double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, look.dot(to.normalize())))));
            double score = angle + distance * 0.5;       // prefers the one under the crosshair, then the closer one
            if (score < bestScore) {
                bestScore = score;
                best = other;
            }
        }
        return best;
    }

    private static int findSpear(LocalPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).is(ItemTags.SPEARS)) {
                return slot;
            }
        }
        return -1;
    }
}
