package dev.hatek.client.ui.render;

import net.minecraft.util.Mth;

public final class Anim {
    private static float deltaSeconds;
    private static int frame;

    public static void beginFrame(float delta) {
        deltaSeconds = Mth.clamp(delta, 0.0f, 0.1f);
        frame++;
    }

    public static float delta() {
        return deltaSeconds;
    }

    public static float easeOut(float t) {
        float k = 1.0f - Mth.clamp(t, 0.0f, 1.0f);
        return 1.0f - k * k * k;
    }

    public static float easeInOut(float t) {
        float k = Mth.clamp(t, 0.0f, 1.0f);
        return k < 0.5f ? 4.0f * k * k * k : 1.0f - (float) Math.pow(-2.0f * k + 2.0f, 3.0) / 2.0f;
    }

    public static float easeBack(float t) {
        float k = Mth.clamp(t, 0.0f, 1.0f);
        float c = 1.70158f + 1.0f;
        float u = k - 1.0f;
        return 1.0f + (c + 1.0f) * u * u * u + c * u * u;
    }

    private final float speed;
    private float value;
    private float target;
    private int seen = -1;

    public Anim(float initial, float speed) {
        this.value = initial;
        this.target = initial;
        this.speed = speed;
    }

    public Anim to(float newTarget) {
        this.target = newTarget;
        return this;
    }

    public Anim to(boolean on) {
        return to(on ? 1.0f : 0.0f);
    }

    public float get() {
        if (this.seen != frame) {
            this.seen = frame;
            float step = 1.0f - (float) Math.exp(-this.speed * deltaSeconds);
            this.value += (this.target - this.value) * step;
            if (Math.abs(this.target - this.value) < 0.0005f) {
                this.value = this.target;
            }
        }
        return this.value;
    }

    public float target() {
        return this.target;
    }

    public void snap(float v) {
        this.value = v;
        this.target = v;
    }

    public boolean settled() {
        return this.value == this.target;
    }
}
