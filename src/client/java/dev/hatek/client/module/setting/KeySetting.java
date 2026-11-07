package dev.hatek.client.module.setting;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Anim;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** A key bound to something inside a module (separate from the module's own toggle key). Click, press a key; Esc clears. */
public final class KeySetting extends Setting {
    /** Mouse buttons are stored as MOUSE_BASE - button (so button 3, "mouse 4", is -103). */
    public static final int MOUSE_BASE = -100;

    private int key;
    private boolean binding;
    private final boolean[] mouseWas = new boolean[8];

    private final Anim hoverAnim = new Anim(0.0f, 18.0f);
    private final Anim bindAnim = new Anim(0.0f, 14.0f);

    public KeySetting(String name, int key) {
        super(name);
        this.key = key;
    }

    public int key() {
        return this.key;
    }

    public boolean isBinding() {
        return this.binding;
    }

    /** Takes a mouse button / wheel code while the setting waits for a key. */
    public void assign(int code) {
        this.key = code;
        this.binding = false;
    }

    public boolean isSet() {
        return this.key >= 0 || this.key <= MOUSE_BASE;
    }

    public boolean isMouse() {
        return this.key <= MOUSE_BASE;
    }

    /** True while the bound key is held and no screen is open. */
    public boolean isDown() {
        Minecraft mc = Minecraft.getInstance();
        if (!isSet() || mc.gui.screen() != null) {
            return false;
        }
        if (isMouse()) {
            int button = MOUSE_BASE - this.key;
            if (dev.hatek.client.module.Wheel.isWheel(button)) {
                return dev.hatek.client.module.Wheel.active(button);
            }
            return GLFW.glfwGetMouseButton(mc.getWindow().handle(), button) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(mc.getWindow(), this.key);
    }

    @Override
    public float bottomOffset() {
        return Style.LABEL_TO_CONTROL + Style.ROW_H;
    }

    @Override
    public boolean capturesInput() {
        return this.binding;
    }

    @Override
    public void closePopups() {
        this.binding = false;
    }

    private String keyLabel() {
        if (!isSet()) {
            return "None";
        }
        if (isMouse()) {
            int button = MOUSE_BASE - this.key;
            return dev.hatek.client.module.Wheel.isWheel(button) ? dev.hatek.client.module.Wheel.label(button)
                    : "MOUSE " + (button + 1);
        }
        String name = InputConstants.Type.KEYSYM.getOrCreate(this.key).getDisplayName().getString();
        return name.toUpperCase(Locale.ROOT);
    }

    @Override
    public void render(GuiGraphicsExtractor gg, float x, float baseline, double mx, double my) {
        drawLabel(gg, x, baseline);
        pollMouseBinding();
        float rowY = controlTop(baseline);
        this.hoverAnim.to(Render2D.hovered(mx, my, x, rowY, Style.ROW_W, Style.ROW_H));
        this.bindAnim.to(this.binding);
        float hover = this.hoverAnim.get();
        float bind = this.bindAnim.get();

        Render2D.round(gg, x, rowY, Style.ROW_W, Style.ROW_H, Style.ROW_R,
                Render2D.lerp(Render2D.lerp(Style.WHITE_04, Style.white(0.07f), hover), Style.accent(), bind));

        int tint = Render2D.lerp(Style.WHITE_45, Style.WHITE, Math.max(hover * 0.6f, bind));
        Render2D.icon(gg, "keyboard", x + Style.FIELD_ICON_X, rowY + (Style.ROW_H - Style.FIELD_ICON) / 2.0f,
                Style.FIELD_ICON, Style.FIELD_ICON, tint);
        String text = this.binding ? ((System.currentTimeMillis() / 350L) % 2L == 0L ? "..." : "Press a key") : keyLabel();
        Fonts.body().draw(gg, text, x + 24.0f, rowY + Style.FIELD_TEXT_BASELINE, tint);
    }

    /** While binding, a middle or side mouse button (buttons 3 to 8) can be the bound key too. */
    private void pollMouseBinding() {
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().handle();
        for (int button = 2; button < this.mouseWas.length; button++) {
            boolean down = GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
            if (this.binding && down && !this.mouseWas[button]) {
                this.key = MOUSE_BASE - button;
                this.binding = false;
            }
            this.mouseWas[button] = down;
        }
    }

    @Override
    public boolean mouseClicked(float x, float baseline, double mx, double my, int button) {
        boolean inside = Render2D.hovered(mx, my, x, controlTop(baseline), Style.ROW_W, Style.ROW_H);
        if (inside && button == 0) {
            this.binding = true;
            return true;
        }
        if (inside && button == 1) {
            this.key = GLFW.GLFW_KEY_UNKNOWN;
            this.binding = false;
            return true;
        }
        if (this.binding) {
            this.binding = false;
        }
        return inside;
    }

    @Override
    public boolean keyPressed(int pressed) {
        if (!this.binding) {
            return false;
        }
        this.key = pressed == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : pressed;
        this.binding = false;
        return true;
    }

    @Override
    public String serialize() {
        return Integer.toString(this.key);
    }

    @Override
    public void deserialize(String raw) {
        try {
            this.key = Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            this.key = GLFW.GLFW_KEY_UNKNOWN;
        }
    }
}
