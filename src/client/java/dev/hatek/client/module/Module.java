package dev.hatek.client.module;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hatek.client.ui.Style;
import dev.hatek.client.module.setting.Setting;
import dev.hatek.client.ui.render.Anim;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import dev.hatek.client.ui.render.TexCache;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Module {
    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting> settings = new ArrayList<>();

    private boolean enabled;
    private boolean expanded;
    private int keyCode = GLFW.GLFW_KEY_UNKNOWN;
    private boolean binding;

    private final Anim toggleAnim = new Anim(0.0f, 16.0f);
    private final Anim expandAnim = new Anim(0.0f, 13.0f);
    private final Anim hoverAnim = new Anim(0.0f, 16.0f);
    private final Anim badgeAnim = new Anim(0.0f, 18.0f);
    private final Anim iconAnim = new Anim(0.0f, 14.0f);

    public Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public Module with(Setting... values) {
        this.settings.addAll(List.of(values));
        return this;
    }

    public Module keybind(int key) {
        this.keyCode = key;
        return this;
    }

    public Module expanded(boolean value) {
        this.expanded = value;
        this.expandAnim.snap(value ? 1.0f : 0.0f);
        return this;
    }

    public Module enabled(boolean value) {
        this.enabled = value;
        this.toggleAnim.snap(value ? 1.0f : 0.0f);
        return this;
    }

    public String name() {
        return this.name;
    }

    public String description() {
        return this.description;
    }

    public Category category() {
        return this.category;
    }

    public List<Setting> settings() {
        return this.settings;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public boolean isExpanded() {
        return this.expanded;
    }

    public int keyCode() {
        return this.keyCode;
    }

    public boolean isBinding() {
        return this.binding;
    }

    public void toggle() {
        this.enabled = !this.enabled;
        if (this.enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    public void setEnabled(boolean value) {
        if (this.enabled != value) {
            toggle();
        }
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    public void onClientTick() {
    }

    public int expandedHeight() {
        if (this.settings.isEmpty()) {
            return Style.COLLAPSED_H;
        }
        int height = Style.HEADER_H;
        for (Setting setting : this.settings) {
            height += Style.SETTING_GAP + setting.bottomOffset();
        }
        return height + Style.CARD_BOTTOM_PAD;
    }

    public float height() {
        this.expandAnim.to(this.expanded);
        return Mth.lerp(Anim.easeInOut(this.expandAnim.get()),
                Style.COLLAPSED_H, expandedHeight());
    }

    public void render(GuiGraphicsExtractor gg, float x, float y, double mx, double my) {
        float height = height();
        this.hoverAnim.to(Render2D.hovered(mx, my, x, y, Style.CARD_W, height));
        Render2D.round(gg, x, y, Style.CARD_W, height, Style.CARD_R,
                Render2D.lerp(Style.WHITE_02, Style.white(0.035f), this.hoverAnim.get()));

        renderHeader(gg, x, y, mx, my);

        float progress = Anim.easeInOut(this.expandAnim.get());
        if (progress <= 0.002f) {
            return;
        }
        Render2D.pushClip(gg, x, y + Style.HEADER_H - 2.0f,
                Style.CARD_W, Math.max(0.0f, height - Style.HEADER_H + 2.0f));
        Render2D.pushAlpha(Mth.clamp((progress - 0.25f) / 0.6f, 0.0f, 1.0f));
        float cursor = y + Style.HEADER_H;
        for (Setting setting : this.settings) {
            float baseline = cursor + Style.SETTING_GAP;
            setting.render(gg, x + Style.ROW_X, baseline, mx, my);
            cursor = baseline + setting.bottomOffset();
        }
        Render2D.popAlpha();
        Render2D.popClip(gg);
    }

    private void renderHeader(GuiGraphicsExtractor gg, float x, float y, double mx, double my) {
        String icon = this.category.icon();
        int pad = TexCache.shadowPad(Style.SHADOW_BLUR);
        Render2D.texture(gg,
                TexCache.iconShadow(icon, Style.ICON_SIZE, Style.ICON_SIZE, Style.SHADOW_BLUR),
                x + Style.ICON_X - pad, y + Style.ICON_Y - pad + Style.SHADOW_DY,
                Style.ICON_SIZE + pad * 2, Style.ICON_SIZE + pad * 2, Style.SHADOW);

        this.iconAnim.to(this.enabled);
        Render2D.icon(gg, icon, x + Style.ICON_X, y + Style.ICON_Y,
                Style.ICON_SIZE, Style.ICON_SIZE,
                Render2D.lerp(Render2D.withAlpha(Style.accent(), 0.7f), Style.accent(),
                        this.iconAnim.get()));

        Fonts.title().draw(gg, this.name, x + Style.TITLE_X, y + Style.TITLE_BASELINE,
                Style.WHITE);

        HFont body = Fonts.body();
        List<String> lines = body.wrap(this.description, Style.DESC_MAX_W);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            body.draw(gg, lines.get(i), x + Style.DESC_X,
                    y + Style.DESC_BASELINE + i * Style.DESC_LINE, Style.WHITE_25);
        }

        renderBadge(gg, x, y, mx, my);
        renderToggle(gg, x, y, mx, my);
    }

    private void renderBadge(GuiGraphicsExtractor gg, float x, float y, double mx, double my) {
        float bx = x + Style.BADGE_X;
        float by = y + Style.BADGE_Y;
        this.badgeAnim.to(this.binding
                || Render2D.hovered(mx, my, bx, by, Style.BADGE_W, Style.BADGE_H));
        float k = this.badgeAnim.get();
        int background = this.binding
                ? Render2D.lerp(Style.WHITE_04, Style.accent(), k)
                : Render2D.lerp(Style.WHITE_04, Style.white(0.09f), k);
        Render2D.round(gg, bx, by, Style.BADGE_W, Style.BADGE_H, Style.BADGE_R, background);

        int tint = this.binding
                ? Render2D.lerp(Style.WHITE_25, Style.PANEL, k)
                : Render2D.lerp(Style.WHITE_25, Style.WHITE_45, k);
        String label = this.binding && (System.currentTimeMillis() / 350L) % 2L == 0L ? "..." : keyLabel();
        Fonts.body().draw(gg, label, bx + Style.BADGE_KEY_X,
                by + Style.BADGE_KEY_BASELINE, tint);
        Render2D.icon(gg, "keyboard", bx + Style.BADGE_ICON_X, by + Style.BADGE_ICON_Y,
                Style.BADGE_ICON_W, Style.BADGE_ICON_H, tint);
    }

    private void renderToggle(GuiGraphicsExtractor gg, float x, float y, double mx, double my) {
        this.toggleAnim.to(this.enabled);
        float k = Anim.easeInOut(this.toggleAnim.get());

        float tx = x + Style.TOGGLE_X;
        float ty = y + Style.TOGGLE_Y;
        boolean hover = Render2D.hovered(mx, my, tx, ty, Style.TOGGLE_W, Style.TOGGLE_H);
        Render2D.round(gg, tx, ty, Style.TOGGLE_W, Style.TOGGLE_H, Style.TOGGLE_R,
                Render2D.lerp(Style.WHITE_04, Style.accent(), k));

        float travel = Style.TOGGLE_W - Style.KNOB - Style.KNOB_INSET * 2;
        Render2D.circle(gg, tx + Style.KNOB_INSET + travel * k, ty + Style.KNOB_INSET,
                Style.KNOB, hover ? Style.WHITE : Style.white(0.94f));
    }

    public String keyLabel() {
        if (this.keyCode == GLFW.GLFW_KEY_UNKNOWN) {
            return "-";
        }
        String name = InputConstants.Type.KEYSYM.getOrCreate(this.keyCode).getDisplayName().getString();
        return name.length() > 3 ? name.substring(0, 3).toUpperCase(Locale.ROOT)
                : name.toUpperCase(Locale.ROOT);
    }

    public boolean mouseClicked(float x, float y, double mx, double my, int button) {
        if (Render2D.hovered(mx, my, x + Style.TOGGLE_X, y + Style.TOGGLE_Y,
                Style.TOGGLE_W, Style.TOGGLE_H)) {
            if (button == 0) {
                toggle();
                return true;
            }
        }
        if (Render2D.hovered(mx, my, x + Style.BADGE_X, y + Style.BADGE_Y,
                Style.BADGE_W, Style.BADGE_H)) {
            if (button == 0) {
                this.binding = true;
                return true;
            }
            if (button == 1) {
                this.keyCode = GLFW.GLFW_KEY_UNKNOWN;
                return true;
            }
        }
        if (this.expanded && this.expandAnim.get() > 0.5f) {
            float cursor = y + Style.HEADER_H;
            for (Setting setting : this.settings) {
                float baseline = cursor + Style.SETTING_GAP;
                if (setting.mouseClicked(x + Style.ROW_X, baseline, mx, my, button)) {
                    return true;
                }
                cursor = baseline + setting.bottomOffset();
            }
        }
        if (Render2D.hovered(mx, my, x, y, Style.CARD_W, Style.COLLAPSED_H)
                && (button == 0 || button == 1)) {
            this.expanded = !this.expanded;
            if (!this.expanded) {
                this.settings.forEach(Setting::closePopups);
            }
            return true;
        }
        return false;
    }

    public void mouseDragged(float x, float y, double mx, double my) {
        if (!this.expanded) {
            return;
        }
        float cursor = y + Style.HEADER_H;
        for (Setting setting : this.settings) {
            float baseline = cursor + Style.SETTING_GAP;
            setting.mouseDragged(x + Style.ROW_X, baseline, mx, my);
            cursor = baseline + setting.bottomOffset();
        }
    }

    public void mouseReleased() {
        this.settings.forEach(Setting::mouseReleased);
    }

    public boolean consumeBinding(int key) {
        if (!this.binding) {
            return false;
        }
        this.binding = false;
        this.keyCode = key == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : key;
        return true;
    }

    public boolean keyPressed(int key) {
        for (Setting setting : this.settings) {
            if (setting.capturesInput() && setting.keyPressed(key)) {
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(char c) {
        for (Setting setting : this.settings) {
            if (setting.capturesInput() && setting.charTyped(c)) {
                return true;
            }
        }
        return false;
    }

    public boolean capturesInput() {
        for (Setting setting : this.settings) {
            if (setting.capturesInput()) {
                return true;
            }
        }
        return false;
    }
}
