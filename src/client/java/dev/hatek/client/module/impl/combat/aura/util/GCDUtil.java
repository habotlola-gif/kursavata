package dev.hatek.client.module.impl.combat.aura.util;

import net.minecraft.client.Minecraft;

public final class GCDUtil {
    private GCDUtil() {
    }

    public static float getSensitivity(float rot) {
        return getDeltaMouse(rot) * getGCDValue();
    }

    public static float getGCDValue() {
        return (float) (getGCD() * 0.15);
    }

    public static float getGCD() {
        float f1 = (float) (Minecraft.getInstance().options.sensitivity().get() * 0.6 + 0.2);
        return f1 * f1 * f1 * 8.0F;
    }

    public static float getDeltaMouse(float delta) {
        return Math.round(delta / getGCDValue());
    }

    public static float floorToGrid(float delta) {
        float gcd = getGCDValue();
        if (gcd <= 0.0F) {
            return delta;
        }
        return (float) (Math.floor(Math.abs(delta) / gcd) * gcd) * Math.signum(delta);
    }
}
