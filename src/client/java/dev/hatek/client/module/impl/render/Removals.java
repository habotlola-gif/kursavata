package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.MultiSetting;
import dev.hatek.client.module.setting.SliderSetting;

import java.util.List;

/** Removes visual annoyances: fire on the screen, nausea and portal distortion, hurt camera shake, blindness, overlays. */
public final class Removals extends Module {
    public static final String NAUSEA = "Nausea & portal";
    public static final String HURT_CAM = "Hurt camera";
    public static final String BLINDNESS = "Blindness & darkness";
    public static final String OVERLAYS = "Pumpkin & snow overlay";
    public static final String FLUID_OVERLAY = "Water & block overlay";
    public static final String TOTEM = "Totem animation";

    private static Removals instance;

    private final ModeSetting fire = new ModeSetting("Fire", 0, "Lower", "Remove", "Vanilla");
    private final SliderSetting fireHeight = new SliderSetting("Fire height (%)", 40.0, 0.0, 100.0, 5.0);
    private final MultiSetting remove = new MultiSetting("Remove",
            List.of(NAUSEA, HURT_CAM, BLINDNESS, OVERLAYS, FLUID_OVERLAY, TOTEM),
            NAUSEA, HURT_CAM, BLINDNESS, OVERLAYS, FLUID_OVERLAY);

    public Removals() {
        super("Removals", "Removes fire, nausea and other view obstructions", Category.RENDER);
        instance = this;
        with(this.fire, this.fireHeight, this.remove);
    }

    public static Removals instance() {
        return instance;
    }

    private static boolean on(String option) {
        return instance != null && instance.isEnabled() && instance.remove.isSelected(option);
    }

    public static boolean removesFire() {
        return instance != null && instance.isEnabled() && instance.fire.is("Remove");
    }

    /** Vertical offset (blocks, negative = down) applied to the fire overlay in "Lower" mode. */
    public static float fireOffset() {
        if (instance == null || !instance.isEnabled() || !instance.fire.is("Lower")) {
            return 0.0f;
        }
        return -0.5f * (1.0f - (float) instance.fireHeight.value() / 100.0f);
    }

    public static boolean removesNausea() {
        return on(NAUSEA);
    }

    public static boolean removesHurtCam() {
        return on(HURT_CAM);
    }

    public static boolean removesBlindness() {
        return on(BLINDNESS);
    }

    public static boolean removesOverlays() {
        return on(OVERLAYS);
    }

    public static boolean removesFluidOverlay() {
        return on(FLUID_OVERLAY);
    }

    public static boolean removesTotem() {
        return on(TOTEM);
    }
}
