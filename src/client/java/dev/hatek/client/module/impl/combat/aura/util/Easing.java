package dev.hatek.client.module.impl.combat.aura.util;

public final class Easing {
    @FunctionalInterface
    public interface Fn {
        float ease(float t);
    }

    private Easing() {
    }

    private static float clamp01(float t) {
        return t < 0.0F ? 0.0F : Math.min(t, 1.0F);
    }

    public static float easeInSine(float t) {
        return 1.0F - (float) Math.cos(clamp01(t) * Math.PI / 2.0);
    }

    public static float easeOutSine(float t) {
        return (float) Math.sin(clamp01(t) * Math.PI / 2.0);
    }

    public static float easeInOutSine(float t) {
        return -((float) Math.cos(Math.PI * clamp01(t)) - 1.0F) / 2.0F;
    }

    public static float easeInQuad(float t) {
        t = clamp01(t);
        return t * t;
    }

    public static float easeOutQuad(float t) {
        t = clamp01(t);
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    public static float easeInOutQuad(float t) {
        t = clamp01(t);
        return t < 0.5F ? 2.0F * t * t : 1.0F - (float) Math.pow(-2.0 * t + 2.0, 2.0) / 2.0F;
    }

    public static float easeInCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    public static float easeOutCubic(float t) {
        t = clamp01(t);
        return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
    }

    public static float easeInOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0 * t + 2.0, 3.0) / 2.0F;
    }

    public static float easeInQuart(float t) {
        t = clamp01(t);
        return t * t * t * t;
    }

    public static float easeOutQuart(float t) {
        t = clamp01(t);
        return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t) * (1.0F - t);
    }

    public static float easeInOutQuart(float t) {
        t = clamp01(t);
        return t < 0.5F ? 8.0F * t * t * t * t : 1.0F - (float) Math.pow(-2.0 * t + 2.0, 4.0) / 2.0F;
    }

    public static float easeInQuint(float t) {
        t = clamp01(t);
        return t * t * t * t * t;
    }

    public static float easeOutQuint(float t) {
        t = clamp01(t);
        return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t) * (1.0F - t) * (1.0F - t);
    }

    public static float easeInOutQuint(float t) {
        t = clamp01(t);
        return t < 0.5F ? 16.0F * t * t * t * t * t
                : 1.0F - (float) Math.pow(-2.0 * t + 2.0, 5.0) / 2.0F;
    }

    public static float easeInExpo(float t) {
        t = clamp01(t);
        return t == 0.0F ? 0.0F : (float) Math.pow(2.0, 10.0 * t - 10.0);
    }

    public static float easeOutExpo(float t) {
        t = clamp01(t);
        return t == 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0, -10.0 * t);
    }

    public static float easeInOutExpo(float t) {
        t = clamp01(t);
        if (t == 0.0F) {
            return 0.0F;
        }
        if (t == 1.0F) {
            return 1.0F;
        }
        return t < 0.5F
                ? (float) Math.pow(2.0, 20.0 * t - 10.0) / 2.0F
                : (2.0F - (float) Math.pow(2.0, -20.0 * t + 10.0)) / 2.0F;
    }

    public static float easeInCirc(float t) {
        t = clamp01(t);
        return 1.0F - (float) Math.sqrt(1.0 - t * t);
    }

    public static float easeOutCirc(float t) {
        t = clamp01(t);
        return (float) Math.sqrt(1.0 - (t - 1.0F) * (t - 1.0F));
    }

    public static float easeInOutCirc(float t) {
        t = clamp01(t);
        return t < 0.5F
                ? (1.0F - (float) Math.sqrt(1.0 - 4.0F * t * t)) / 2.0F
                : ((float) Math.sqrt(1.0 - (-2.0F * t + 2.0F) * (-2.0F * t + 2.0F)) + 1.0F) / 2.0F;
    }

    private static final float BACK_C1 = 1.70158F;
    private static final float BACK_C2 = BACK_C1 * 1.525F;

    public static float easeInBack(float t) {
        t = clamp01(t);
        return (BACK_C1 + 1.0F) * t * t * t - BACK_C1 * t * t;
    }

    public static float easeOutBack(float t) {
        t = clamp01(t);
        float u = t - 1.0F;
        return 1.0F + (BACK_C1 + 1.0F) * u * u * u + BACK_C1 * u * u;
    }

    public static float easeInOutBack(float t) {
        t = clamp01(t);
        if (t < 0.5F) {
            float u = 2.0F * t;
            return u * u * ((BACK_C2 + 1.0F) * u - BACK_C2) / 2.0F;
        }
        float u = 2.0F * t - 2.0F;
        return (u * u * ((BACK_C2 + 1.0F) * u + BACK_C2) + 2.0F) / 2.0F;
    }

    public static float easeInElastic(float t) {
        t = clamp01(t);
        if (t == 0.0F) {
            return 0.0F;
        }
        if (t == 1.0F) {
            return 1.0F;
        }
        return -(float) Math.pow(2.0, 10.0 * t - 10.0)
                * (float) Math.sin((t * 10.0 - 10.75) * (2.0 * Math.PI / 3.0));
    }

    public static float easeOutElastic(float t) {
        t = clamp01(t);
        if (t == 0.0F) {
            return 0.0F;
        }
        if (t == 1.0F) {
            return 1.0F;
        }
        return (float) Math.pow(2.0, -10.0 * t)
                * (float) Math.sin((t * 10.0 - 0.75) * (2.0 * Math.PI / 3.0)) + 1.0F;
    }

    public static float easeInOutElastic(float t) {
        t = clamp01(t);
        if (t == 0.0F) {
            return 0.0F;
        }
        if (t == 1.0F) {
            return 1.0F;
        }
        double c = 2.0 * Math.PI / 4.5;
        return t < 0.5
                ? -((float) Math.pow(2.0, 20.0 * t - 10.0)
                * (float) Math.sin((20.0 * t - 11.125) * c)) / 2.0F
                : ((float) Math.pow(2.0, -20.0 * t + 10.0)
                * (float) Math.sin((20.0 * t - 11.125) * c)) / 2.0F + 1.0F;
    }

    public static float easeOutBounce(float t) {
        t = clamp01(t);
        float n = 7.5625F;
        float d = 2.75F;

        if (t < 1.0F / d) {
            return n * t * t;
        }
        if (t < 2.0F / d) {
            return n * (t -= 1.5F / d) * t + 0.75F;
        }
        if (t < 2.5F / d) {
            return n * (t -= 2.25F / d) * t + 0.9375F;
        }
        return n * (t -= 2.625F / d) * t + 0.984375F;
    }

    public static float easeInBounce(float t) {
        return 1.0F - easeOutBounce(1.0F - clamp01(t));
    }

    public static float easeInOutBounce(float t) {
        t = clamp01(t);
        return t < 0.5F
                ? (1.0F - easeOutBounce(1.0F - 2.0F * t)) / 2.0F
                : (1.0F + easeOutBounce(2.0F * t - 1.0F)) / 2.0F;
    }
}
