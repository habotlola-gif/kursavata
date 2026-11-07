package dev.hatek.client.module;

import dev.hatek.client.ui.Style;
import dev.hatek.client.module.impl.combat.AttackAura;
import dev.hatek.client.module.impl.movement.AutoSprint;
import dev.hatek.client.module.impl.misc.VoiceControl;
import dev.hatek.client.module.impl.player.NoJumpDelay;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ButtonSetting;
import dev.hatek.client.module.setting.ColorSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.MultiSetting;
import dev.hatek.client.module.setting.SliderSetting;
import dev.hatek.client.module.setting.TextSetting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class ModuleManager {
    private static final List<Module> MODULES = new ArrayList<>();

    private ModuleManager() {
    }

    public static List<Module> all() {
        if (MODULES.isEmpty()) {
            register();
        }
        return MODULES;
    }

    public static List<Module> of(Category category) {
        List<Module> out = new ArrayList<>();
        for (Module module : all()) {
            if (module.category() == category) {
                out.add(module);
            }
        }
        return out;
    }

    public static void tickAll() {
        for (Module module : all()) {
            if (module.isEnabled()) {
                module.onClientTick();
            }
        }
    }

    public static boolean onKey(int key) {
        boolean handled = false;
        for (Module module : all()) {
            if (module.keyCode() == key) {
                module.toggle();
                handled = true;
            }
        }
        return handled;
    }

    private static void register() {
        MODULES.add(new AttackAura().expanded(true));

        MODULES.add(new AutoSprint().enabled(true).expanded(true));

        MODULES.add(new NoJumpDelay());

        MODULES.add(new VoiceControl());

    }
}
