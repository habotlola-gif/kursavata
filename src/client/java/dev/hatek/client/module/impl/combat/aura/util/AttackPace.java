package dev.hatek.client.module.impl.combat.aura.util;

public final class AttackPace {
    public static final int AURA_GAP_TICKS = 4;

    public static final int MANUAL_GAP_TICKS = 3;

    private static final int FAR_PAST = Integer.MIN_VALUE / 2;

    private int lastAttackTick = FAR_PAST;

    private int lastAuraAttackTick = FAR_PAST;

    public boolean auraGapReached(int age) {
        rewindIfNewLife(age);
        return age - this.lastAuraAttackTick >= AURA_GAP_TICKS
                && age - this.lastAttackTick >= AURA_GAP_TICKS;
    }

    public boolean manualRefused(int age) {
        rewindIfNewLife(age);
        return age - this.lastAuraAttackTick < AURA_GAP_TICKS
                || age - this.lastAttackTick < MANUAL_GAP_TICKS;
    }

    public void markAura(int age) {
        rewindIfNewLife(age);
        this.lastAttackTick = age;
        this.lastAuraAttackTick = age;
    }

    public void markManual(int age) {
        rewindIfNewLife(age);
        this.lastAttackTick = age;
    }

    private void rewindIfNewLife(int age) {
        if (age < this.lastAttackTick) {
            this.lastAttackTick = FAR_PAST;
            this.lastAuraAttackTick = FAR_PAST;
        }
    }
}
