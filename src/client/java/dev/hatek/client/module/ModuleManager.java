package dev.hatek.client.module;

import dev.hatek.client.ui.Style;
import dev.hatek.client.module.impl.combat.AttackAura;
import dev.hatek.client.module.impl.combat.AutoSwap;
import dev.hatek.client.module.impl.combat.AutoTotem;
import dev.hatek.client.module.impl.combat.ClickAction;
import dev.hatek.client.module.impl.combat.SuperSpear;
import dev.hatek.client.module.impl.movement.AirStuck;
import dev.hatek.client.module.impl.movement.DragonFly;
import dev.hatek.client.module.impl.movement.VoidTeleport;
import dev.hatek.client.module.impl.movement.AutoSprint;
import dev.hatek.client.module.impl.misc.VoiceControl;
import dev.hatek.client.module.impl.player.NoJumpDelay;
import dev.hatek.client.module.impl.render.Freecam;
import dev.hatek.client.module.impl.render.Fullbright;
import dev.hatek.client.module.impl.render.Interface;
import dev.hatek.client.module.impl.render.NameTags;
import dev.hatek.client.module.impl.render.Removals;
import dev.hatek.client.module.impl.render.ShaderEsp;
import dev.hatek.client.module.impl.render.ShaderHands;
import dev.hatek.client.module.impl.render.SwingAnimations;
import dev.hatek.client.module.impl.render.TargetEsp;
import dev.hatek.client.module.impl.render.TimeChanger;
import dev.hatek.client.module.impl.render.ViewModel;
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

        MODULES.add(new AutoSwap());

        MODULES.add(new AutoTotem());

        MODULES.add(new SuperSpear());

        MODULES.add(new ClickAction());

        MODULES.add(new dev.hatek.client.module.impl.combat.AimRecorder());

        MODULES.add(new dev.hatek.client.module.impl.combat.FTHelper());

        MODULES.add(new AirStuck());

        MODULES.add(new VoidTeleport());

        MODULES.add(new DragonFly());

        MODULES.add(new NameTags());

        MODULES.add(new Removals());

        MODULES.add(new ShaderEsp());

        MODULES.add(new ShaderHands());

        MODULES.add(new SwingAnimations());

        MODULES.add(new TargetEsp());

        MODULES.add(new Interface());

        MODULES.add(new Fullbright());

        MODULES.add(new dev.hatek.client.module.impl.render.Particles());

        MODULES.add(new dev.hatek.client.module.impl.render.Notifications());

        MODULES.add(new dev.hatek.client.module.impl.render.ClientSound());

        MODULES.add(new Freecam());

        MODULES.add(new TimeChanger());

        MODULES.add(new ViewModel().enabled(true));



        MODULES.add(new NoJumpDelay());

        MODULES.add(new dev.hatek.client.module.impl.player.AutoTool());

        MODULES.add(new dev.hatek.client.module.impl.player.ItemScroller());

        MODULES.add(new VoiceControl());

    }
}
