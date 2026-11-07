package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.ModeSetting;
import net.minecraft.client.renderer.state.LightmapRenderState;

/** Full brightness everywhere: the lightmap is driven like the Night Vision effect (or the maximum Brightness setting). */
public final class Fullbright extends Module {
    private static Fullbright instance;

    private final ModeSetting mode = new ModeSetting("Mode", 0, "Night vision", "Gamma");

    public Fullbright() {
        super("Fullbright", "Everything is fully lit, also in caves and at night", Category.RENDER);
        instance = this;
        with(this.mode);
    }

    public static Fullbright instance() {
        return instance;
    }

    /** Called after the lightmap state is extracted. */
    public static void apply(LightmapRenderState state) {
        Fullbright self = instance;
        if (self == null || !self.isEnabled()) {
            return;
        }
        state.brightness = 1.0f;
        if (self.mode.is("Night vision")) {
            state.nightVisionEffectIntensity = 1.0f;
        }
    }
}
