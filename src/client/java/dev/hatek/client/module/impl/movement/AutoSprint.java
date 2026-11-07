package dev.hatek.client.module.impl.movement;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.impl.combat.aura.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

public final class AutoSprint extends Module {
    private static AutoSprint instance;

    private final BoolSetting keepInWater = new BoolSetting("Keep In Water", true);

    private static boolean sprinting = true;
    private static long time;
    private static int pauseDepth;
    private static boolean restoreAfterPause;

    private LocalPlayer lastPlayer;

    public AutoSprint() {
        super("AutoSprint", "Keeps you sprinting without holding the key", Category.MOVEMENT);
        instance = this;
        keybind(GLFW.GLFW_KEY_X);
        with(this.keepInWater);
    }

    public static AutoSprint instance() {
        return instance;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    @Override
    protected void onEnable() {
        resetPauseState();
        sprinting = true;
    }

    @Override
    protected void onDisable() {
        Minecraft mc = mc();
        resetPauseState();
        sprinting = false;
        this.lastPlayer = null;

        if (mc.options != null) {
            mc.options.keySprint.setDown(false);
        }
        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
    }

    public void onTick() {
        Minecraft mc = mc();
        if (!isEnabled()) {
            return;
        }
        if (mc.player == null) {
            this.lastPlayer = null;
            resetPauseState();
            if (mc.options != null) {
                mc.options.keySprint.setDown(false);
            }
            return;
        }

        if (this.lastPlayer != mc.player) {
            this.lastPlayer = mc.player;
            resetPauseState();
            sprinting = true;
        }

        boolean inWater = mc.player.isInWater() || mc.player.isUnderWater();

        boolean mayStart = mc.player.onGround() || mc.player.isSprinting() || inWater;

        boolean shouldSprint = pauseDepth == 0
                && System.currentTimeMillis() >= time
                && sprinting
                && mayStart
                && MoveUtil.isMoving()
                && mc.options.keyUp.isDown()
                && !mc.player.isFallFlying();

        if (this.keepInWater.value() && inWater && mc.player.isSprinting()) {
            shouldSprint = true;
        }

        mc.options.keySprint.setDown(shouldSprint);
        mc.player.setSprinting(shouldSprint);
    }

    public boolean shouldKeepSprintInWater() {
        return isEnabled() && this.keepInWater.value();
    }

    public static boolean keepsSprintInWater() {
        return instance != null && instance.shouldKeepSprintInWater();
    }

    public static void pushPause(long delayMs) {
        Minecraft mc = mc();
        restoreAfterPause |= shouldRestoreAfterPause();
        pauseDepth++;
        time = Math.max(time, System.currentTimeMillis() + Math.max(0L, delayMs));
        sprinting = false;

        if (mc.options != null) {
            mc.options.keySprint.setDown(false);
        }
        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
    }

    public static void popPause() {
        if (pauseDepth > 0) {
            pauseDepth--;
        }
        if (pauseDepth > 0) {
            return;
        }
        time = 0L;
        restoreAfterPause = false;
        sprinting = instance != null && instance.isEnabled();
    }

    private static boolean shouldRestoreAfterPause() {
        if (mc().player != null && mc().player.isSprinting()) {
            return true;
        }
        return instance != null && instance.isEnabled() && sprinting;
    }

    private static void resetPauseState() {
        pauseDepth = 0;
        restoreAfterPause = false;
        time = 0L;
    }

    public static boolean isSprinting() {
        return sprinting;
    }

    public static void setSprinting(boolean value) {
        sprinting = value;
    }

    public static long getTime() {
        return time;
    }

    public static void setTime(long value) {
        time = value;
    }
}
