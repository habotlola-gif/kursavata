package dev.hatek.client.module.impl.combat.aura.util;

public final class AuraProfile {
    private static float handSpeed = 1.0F;

    private static float steadiness = 1.0F;

    private static float tempo = 1.0F;

    private static int reactionBias;

    private static float overshootBias = 1.0F;

    private static float pauseTempo = 1.0F;

    private static float aimSpread = 1.0F;

    private static float deadZone = 0.65F;

    private static float minStep = 0.4F;

    private static float aimedEpsilon = 2.2F;

    private static float settleSpeed = 13.0F;

    private static float accel = 30.0F;

    private static float latticeHeight;
    private static float latticeSide;

    private static final AuraRandom.Walk SPEED = new AuraRandom.Walk(0.16F, 5, 20);

    private static final AuraRandom.Walk WAVE = new AuraRandom.Walk(0.09F, 12, 45);

    private static final AuraRandom.Walk FATIGUE = new AuraRandom.Walk(0.010F, 200, 700);

    private static float speedNow = 1.0F;
    private static float waveNow = 1.0F;
    private static float fatigueNow = 1.0F;

    private AuraProfile() {
    }

    public static void roll() {
        handSpeed = AuraRandom.range(0.90F, 1.14F);
        steadiness = AuraRandom.range(0.78F, 1.26F);
        tempo = AuraRandom.range(0.95F, 1.09F);
        reactionBias = AuraRandom.range(-1, 1);
        overshootBias = AuraRandom.range(0.6F, 1.7F);
        pauseTempo = AuraRandom.range(0.72F, 1.35F);
        aimSpread = AuraRandom.range(0.75F, 1.35F);
        deadZone = AuraRandom.range(0.45F, 0.85F);
        minStep = AuraRandom.range(0.30F, 0.55F);
        aimedEpsilon = AuraRandom.range(2.2F, 4.0F);
        settleSpeed = AuraRandom.range(18.0F, 26.0F);
        accel = AuraRandom.range(36.0F, 50.0F);
        latticeHeight = AuraRandom.range(-0.045F, 0.045F);
        latticeSide = AuraRandom.range(-0.10F, 0.10F);

        SPEED.reset();
        WAVE.reset();
        FATIGUE.reset();
    }

    public static void tick() {
        speedNow = SPEED.next(0.86F, 1.14F);
        waveNow = WAVE.next(0.90F, 1.10F);
        fatigueNow = FATIGUE.next(0.94F, 1.06F);
    }

    public static float speedNoise() {
        return speedNow * fatigueNow;
    }

    public static float wave() {
        return waveNow;
    }

    public static float handSpeed() {
        return handSpeed * fatigueNow;
    }

    public static float steadiness() {
        return steadiness;
    }

    public static float tempo() {
        return tempo / fatigueNow;
    }

    public static int reaction() {
        return Math.max(1, AuraRandom.human(2, 4) + reactionBias);
    }

    public static float overshootChance(float base) {
        return Math.min(0.5F, base * overshootBias);
    }

    public static float pauseDelay(float base) {
        if (!PlayerStats.pauseReady()) {
            return base * pauseTempo * steadiness;
        }
        float everyMs = PlayerStats.pauseEvery() * 50.0F;
        return Math.max(600.0F, Math.min(9000.0F, everyMs)) * AuraRandom.range(0.7F, 1.35F);
    }

    public static int pauseLength() {
        if (!PlayerStats.pauseReady()) {
            return AuraRandom.chance(0.78F) ? 1 : 2;
        }
        return Math.min(4, PlayerStats.pauseLength(AuraRandom.range(0.0F, 1.0F)));
    }

    public static float tremorAmount() {
        if (!PlayerStats.microReady()) {
            return 0.0F;
        }
        return PlayerStats.micro(AuraRandom.range(0.0F, 1.0F));
    }

    public static float returnSpeed() {
        if (!PlayerStats.turnReady()) {
            return 18.0F;
        }
        return Math.max(8.0F, Math.min(40.0F, PlayerStats.turn(0.75F)));
    }

    public static float aimSpread() {
        return aimSpread;
    }

    public static float deadZone() {
        if (!PlayerStats.microReady()) {
            return deadZone;
        }
        return Math.max(0.15F, Math.min(1.4F, PlayerStats.micro(0.55F)));
    }

    public static float minStep() {
        if (!PlayerStats.microReady()) {
            return minStep;
        }
        return Math.max(0.12F, Math.min(1.0F, PlayerStats.micro(0.70F)));
    }

    public static float aimedEpsilon() {
        return aimedEpsilon;
    }

    public static float settleSpeed() {
        if (!PlayerStats.turnReady()) {
            return settleSpeed;
        }
        return Math.max(22.0F, Math.min(50.0F, PlayerStats.turn(0.97F)));
    }

    public static float settleResidual() {
        return AuraRandom.human(0.4F, 1.8F);
    }

    public static float accel() {
        float base = PlayerStats.turnReady()
                ? Math.max(30.0F, Math.min(95.0F, PlayerStats.accel(0.99F)))
                : accel;
        return base * AuraRandom.range(0.88F, 1.14F);
    }

    public static float maxStep() {
        if (!PlayerStats.turnReady()) {
            return 90.0F;
        }
        return Math.max(50.0F, Math.min(90.0F, PlayerStats.turn(0.995F) * 1.15F));
    }

    public static float latticeHeight() {
        return latticeHeight;
    }

    public static float latticeSide() {
        return latticeSide;
    }
}
