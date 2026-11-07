package dev.hatek.client.module.impl.combat.aura.util;

import java.util.Random;

public final class AuraRandom {
    private static final Random RANDOM = new Random();

    private AuraRandom() {
    }

    public static float range(float min, float max) {
        return min >= max ? min : min + RANDOM.nextFloat() * (max - min);
    }

    public static double range(double min, double max) {
        return min >= max ? min : min + RANDOM.nextDouble() * (max - min);
    }

    public static int range(int min, int max) {
        return min >= max ? min : min + RANDOM.nextInt(max - min + 1);
    }

    public static boolean chance(float probability) {
        return RANDOM.nextFloat() < probability;
    }

    public static float human(float min, float max) {
        if (min >= max) {
            return min;
        }
        float bell = (RANDOM.nextFloat() + RANDOM.nextFloat()) * 0.5F;
        return min + (float) Math.pow(bell, 1.6) * (max - min);
    }

    public static int human(int min, int max) {
        return min >= max ? min : Math.round(human((float) min, (float) max));
    }

    public static int ranked(int count) {
        if (count <= 1) {
            return 0;
        }
        for (int i = 0; i < count - 1; i++) {
            if (RANDOM.nextFloat() < 0.55F) {
                return i;
            }
        }
        return count - 1;
    }

    public static final class Walk {
        private final float rate;
        private float value;
        private float target;
        private int left;
        private final int minHold;
        private final int maxHold;

        public Walk(float rate, int minHold, int maxHold) {
            this.rate = rate;
            this.minHold = minHold;
            this.maxHold = maxHold;
            this.value = range(-0.5F, 0.5F);
            this.target = range(-1.0F, 1.0F);
            this.left = range(minHold, maxHold);
        }

        public float next() {
            if (--this.left <= 0) {
                this.target = range(-1.0F, 1.0F);
                this.left = range(this.minHold, this.maxHold);
            }
            this.value += (this.target - this.value) * this.rate * range(0.6F, 1.4F);
            return Math.max(-1.0F, Math.min(1.0F, this.value));
        }

        public float next(float min, float max) {
            float mid = (min + max) * 0.5F;
            return mid + next() * (max - min) * 0.5F;
        }

        public void reset() {
            this.value = range(-0.3F, 0.3F);
            this.target = range(-1.0F, 1.0F);
            this.left = range(this.minHold, this.maxHold);
        }
    }

    public static final class Cached {
        private final float min;
        private final float max;
        private final long minDelay;
        private final long maxDelay;

        private float value;
        private long nextRoll;

        public Cached(float min, float max, long minDelay, long maxDelay) {
            this.min = min;
            this.max = max;
            this.minDelay = minDelay;
            this.maxDelay = maxDelay;
            this.value = range(min, max);
        }

        public float get() {
            long now = System.currentTimeMillis();
            if (now >= this.nextRoll) {
                this.value = range(this.min, this.max);
                this.nextRoll = now + (long) range((float) this.minDelay, (float) this.maxDelay);
            }
            return this.value;
        }

        public void reset() {
            this.nextRoll = 0L;
        }
    }
}
