package dev.hatek;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hatek.client.ui.screen.AccountsScreen;
import dev.hatek.client.ui.screen.ClickGuiScreen;
import dev.hatek.mixin.accessor.ScreenInvoker;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.ModuleManager;
import dev.hatek.client.module.impl.misc.VoiceControl;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

public class HatekClient implements ClientModInitializer {
    public static final String MOD_ID = "hatek_client";

    private static KeyMapping openClickGui;
    private final Set<Integer> heldKeys = new HashSet<>();

    @Override
    public void onInitializeClient() {
        openClickGui = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key." + MOD_ID + ".clickgui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyMapping.Category.MISC));

        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath(MOD_ID, "target_esp"),
                (graphics, delta) -> {
                    dev.hatek.client.module.impl.render.TargetEsp esp = dev.hatek.client.module.impl.render.TargetEsp.instance();
                    if (esp != null) {
                        esp.render(graphics, delta);
                    }
                });

        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath(MOD_ID, "interface"),
                (graphics, delta) -> {
                    dev.hatek.client.module.impl.render.hud.DragHandle.beginFrame();
                    dev.hatek.client.module.impl.render.Interface hud = dev.hatek.client.module.impl.render.Interface.instance();
                    if (hud != null) {
                        hud.render(graphics, delta);
                    }
                    dev.hatek.client.module.impl.render.ViewModel viewModel = dev.hatek.client.module.impl.render.ViewModel.instance();
                    if (viewModel != null) {
                        viewModel.renderEditor(graphics);
                    }
                });

        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath(MOD_ID, "name_tags"),
                (graphics, delta) -> {
                    dev.hatek.client.module.impl.render.NameTags tags = dev.hatek.client.module.impl.render.NameTags.instance();
                    if (tags != null) {
                        tags.render(graphics, delta);
                    }
                });

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        ScreenEvents.AFTER_INIT.register(this::onScreenInit);
    }

    private void onScreenInit(Minecraft mc, net.minecraft.client.gui.screens.Screen screen,
                              int width, int height) {
        if (!(screen instanceof TitleScreen)) {
            return;
        }
        ((ScreenInvoker) screen).hatek$addRenderableWidget(Button
                .builder(Component.literal("Accounts"),
                        button -> mc.gui.setScreen(new AccountsScreen(screen)))
                .bounds(6, height - 26, 84, 20)
                .build());
    }

    private void onTick(Minecraft mc) {
        VoiceControl voice = VoiceControl.instance();
        if (voice != null) {
            voice.onTick();
        }

        ModuleManager.tickAll();

        while (openClickGui.consumeClick()) {
            mc.gui.setScreen(new ClickGuiScreen());
        }
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof TitleScreen)) {
            this.heldKeys.clear();
            return;
        }
        Set<Integer> down = new HashSet<>();
        for (Module module : ModuleManager.all()) {
            int key = module.keyCode();
            if (key != GLFW.GLFW_KEY_UNKNOWN && InputConstants.isKeyDown(mc.getWindow(), key)) {
                down.add(key);
            }
        }
        for (int key : down) {
            if (!this.heldKeys.contains(key)) {
                ModuleManager.onKey(key);
            }
        }
        this.heldKeys.clear();
        this.heldKeys.addAll(down);
    }
}
