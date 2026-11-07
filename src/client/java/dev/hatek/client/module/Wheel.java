package dev.hatek.client.module;

import dev.hatek.client.module.setting.KeySetting;
import dev.hatek.client.module.setting.Setting;

/**
 * The mouse wheel as a bindable key. Scrolling up is button 8 and scrolling down is button 9 (the numbers continue
 * after the real mouse buttons); a scroll counts as "held" for a short moment so the usual press detection sees it.
 */
public final class Wheel {
    public static final int UP = 8;
    public static final int DOWN = 9;
    private static final long HOLD_MS = 120L;

    private static long upAt;
    private static long downAt;

    private Wheel() {
    }

    public static boolean isWheel(int button) {
        return button == UP || button == DOWN;
    }

    /** Called for every scroll event of the game window. */
    public static void record(double yOffset) {
        if (yOffset > 0.0) {
            upAt = System.currentTimeMillis();
        } else if (yOffset < 0.0) {
            downAt = System.currentTimeMillis();
        }
    }

    public static boolean active(int button) {
        long at = button == UP ? upAt : downAt;
        return System.currentTimeMillis() - at < HOLD_MS;
    }

    public static String label(int button) {
        return button == UP ? "WHEEL UP" : "WHEEL DOWN";
    }

    /** True when an enabled module (or one of its key settings) is bound to the wheel: the hotbar must not scroll then. */
    public static boolean isBound() {
        int up = Module.MOUSE_BASE - UP;
        int down = Module.MOUSE_BASE - DOWN;
        for (Module module : ModuleManager.all()) {
            if (!module.isEnabled()) {
                continue;
            }
            if (module.keyCode() == up || module.keyCode() == down) {
                return true;
            }
            for (Setting setting : module.settings()) {
                if (setting instanceof KeySetting key && (key.key() == up || key.key() == down)) {
                    return true;
                }
            }
        }
        return false;
    }
}
