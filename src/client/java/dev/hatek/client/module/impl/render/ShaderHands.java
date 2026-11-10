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

    private final ModeSetting mode = new ModeSetting("Mode", 0, "Fill + glow", "Fill only", "Glow only", "Bloom");
    private final ColorSetting colorA = new ColorSetting("Color 1", 0xFF7C4DFF);
    private final ColorSetting colorB = new ColorSetting("Color 2", 0xFF18D2E8);
    private final SliderSetting fill = new SliderSetting("Fill (%)", 70.0, 0.0, 100.0, 5.0);
    private final SliderSetting glow = new SliderSetting("Glow (px)", 16.0, 2.0, 30.0, 1.0);
    private final SliderSetting speed = new SliderSetting("Speed", 1.0, 0.0, 3.0, 0.1);
    private final ColorSetting bloomLight = new ColorSetting("Bloom: highlight", 0xFFFFFFFF);
    private final ColorSetting bloomTint = new ColorSetting("Bloom: tint", 0xFFD6C4FF);
    private final SliderSetting bloomStrength = new SliderSetting("Bloom: strength (%)", 85.0, 10.0, 150.0, 5.0);
    private final SliderSetting bloomShadow = new SliderSetting("Bloom: shadow under the item (%)", 45.0, 0.0, 100.0, 5.0);
    private final BoolSetting trail = new BoolSetting("Fire trail", true);
    private final ModeSetting trailPalette = new ModeSetting("Trail color", 0, "Fire", "Hand colors");
    private final SliderSetting trailLength = new SliderSetting("Trail length (%)", 100.0, 30.0, 250.0, 10.0);
    private final SliderSetting trailBrightness = new SliderSetting("Trail brightness (%)", 100.0, 20.0, 200.0, 10.0);

    public ShaderHands() {
        super("ShaderHands", "Gradient hands with glow and a fire trail", Category.RENDER);
        instance = this;
        this.colorA.visibleIf(() -> !bloom());
        this.colorB.visibleIf(() -> !bloom());
        this.fill.visibleIf(() -> !bloom());
        this.bloomLight.visibleIf(this::bloom);
        this.bloomTint.visibleIf(this::bloom);
        this.bloomStrength.visibleIf(this::bloom);
        this.bloomShadow.visibleIf(this::bloom);
        with(this.mode, this.colorA, this.colorB, this.fill, this.bloomLight, this.bloomTint, this.bloomStrength,
                this.bloomShadow, this.glow, this.speed, this.trail,
                this.trailPalette, this.trailLength, this.trailBrightness);
    }

    public static ShaderHands instance() {
        return instance;
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    private boolean bloom() {
        return this.mode.is("Bloom");
    }

    public int modeIndex() {
        return this.mode.is("Fill + glow") ? 0 : this.mode.is("Fill only") ? 1 : this.mode.is("Glow only") ? 2 : 3;
    }

    /** In the Bloom mode the two colours are the highlight and the tint. */
    public int colorA() {
        return bloom() ? this.bloomLight.value() : this.colorA.value();
    }

    public int colorB() {
        return bloom() ? this.bloomTint.value() : this.colorB.value();
    }

    /** In the Bloom mode: how strongly the item is lit up. */
    public float fill() {
        return (float) (bloom() ? this.bloomStrength.value() : this.fill.value()) / 100.0f;
    }

    /** The soft dark shadow under the item (Bloom mode only). */
    public float shadow() {
        return bloom() ? (float) this.bloomShadow.value() / 100.0f : 0.0f;
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
