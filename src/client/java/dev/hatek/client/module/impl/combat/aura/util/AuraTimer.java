package dev.hatek.client.module.impl.combat.aura.util;

public final class AuraTimer {
    private long startTime = System.currentTimeMillis();
    private long lastMs = System.currentTimeMillis();

    public void reset() {
        this.startTime = System.currentTimeMillis();
        this.lastMs = System.currentTimeMillis();
    }

    public boolean isReached(long time) {
        return System.currentTimeMillis() - this.lastMs > time;
    }

    public boolean finished(double delay) {
        return System.currentTimeMillis() - delay >= this.startTime;
    }

    public boolean every(double delay) {
        boolean finished = finished(delay);
        if (finished) {
            reset();
        }
        return finished;
    }

    public long elapsedTime() {
        return System.currentTimeMillis() - this.startTime;
    }

    public void setMs(long ms) {
        this.startTime = System.currentTimeMillis() - ms;
    }

    public long getStartTime() {
        return this.startTime;
    }
}
