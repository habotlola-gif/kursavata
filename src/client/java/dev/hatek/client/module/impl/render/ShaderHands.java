package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ColorSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;

/** First-person hands and held items drawn with an animated gradient fill, glow and an optional fire trail. */
public final class ShaderHands extends Module {
    private static ShaderHands instance;

    private final ModeSetting mode = new ModeSetting("Mode", 0, "Fill + glow", "Fill only", "Glow only");
    private final ColorSetting colorA = new ColorSetting("Color 1", 0xFF7C4DFF);
    private final ColorSetting colorB = new ColorSetting("Color 2", 0xFF18D2E8);
    private final SliderSetting fill = new SliderSetting("Fill (%)", 70.0, 0.0, 100.0, 5.0);
    private final SliderSetting glow = new SliderSetting("Glow (px)", 16.0, 2.0, 30.0, 1.0);
    private final SliderSetting speed = new SliderSetting("Speed", 1.0, 0.0, 3.0, 0.1);
    private final BoolSetting trail = new BoolSetting("Fire trail", true);
    private final ModeSetting trailPalette = new ModeSetting("Trail color", 0, "Fire", "Hand colors");
    private final SliderSetting trailLength = new SliderSetting("Trail length (%)", 100.0, 30.0, 250.0, 10.0);
    private final SliderSetting trailBrightness = new SliderSetting("Trail brightness (%)", 100.0, 20.0, 200.0, 10.0);

    public ShaderHands() {
        super("ShaderHands", "Gradient hands with glow and a fire trail", Category.RENDER);
        instance = this;
        with(this.mode, this.colorA, this.colorB, this.fill, this.glow, this.speed, this.trail,
                this.trailPalette, this.trailLength, this.trailBrightness);
    }

    public static ShaderHands instance() {
        return instance;
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    public int modeIndex() {
        return this.mode.is("Fill + glow") ? 0 : this.mode.is("Fill only") ? 1 : 2;
    }

    public int colorA() {
        return this.colorA.value();
    }

    public int colorB() {
        return this.colorB.value();
    }

    public float fill() {
        return (float) this.fill.value() / 100.0f;
    }

    public float glowPx() {
        return (float) this.glow.value();
    }

    public float speed() {
        return (float) this.speed.value();
    }

    public boolean trail() {
        return this.trail.value();
    }

    public int trailPaletteIndex() {
        return this.trailPalette.is("Fire") ? 0 : 1;
    }

    public float trailLength() {
        return (float) this.trailLength.value() / 100.0f;
    }

    public float trailBrightness() {
        return (float) this.trailBrightness.value() / 100.0f;
    }
}
