package dev.hatek.client.module.setting;

import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Anim;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public final class TextSetting extends Setting {
    private final String placeholder;
    private String value;
    private boolean focused;
    private long lastEdit;

    private final Anim hoverAnim = new Anim(0.0f, 18.0f);
    private final Anim focusAnim = new Anim(0.0f, 14.0f);
    private final Anim caretAnim = new Anim(0.0f, 26.0f);

    public TextSetting(String name, String value, String placeholder) {
        super(name);
        this.value = value == null ? "" : value;
        this.placeholder = placeholder;
    }

    public String value() {
        return this.value;
    }

    public void value(String v) {
        this.value = v == null ? "" : v;
    }

    @Override
    public float bottomOffset() {
        return Style.LABEL_TO_CONTROL + Style.ROW_H;
    }

    @Override
    public boolean capturesInput() {
        return this.focused;
    }

    @Override
    public void closePopups() {
        this.focused = false;
    }

    @Override
    public void render(GuiGraphicsExtractor gg, float x, float baseline, double mx, double my) {
        drawLabel(gg, x, baseline);
        float rowY = controlTop(baseline);
        this.hoverAnim.to(Render2D.hovered(mx, my, x, rowY, Style.ROW_W, Style.ROW_H));
        this.focusAnim.to(this.focused);
        float hover = this.hoverAnim.get();
        float focus = this.focusAnim.get();

        Render2D.round(gg, x, rowY, Style.ROW_W, Style.ROW_H, Style.ROW_R,
                Render2D.lerp(Render2D.lerp(Style.WHITE_04, Style.white(0.07f), hover),
                        Style.white(0.10f), focus));

        Render2D.icon(gg, "typetext", x + Style.FIELD_ICON_X,
                rowY + (Style.ROW_H - Style.FIELD_ICON) / 2.0f,
                Style.FIELD_ICON, Style.FIELD_ICON,
                Render2D.lerp(Style.WHITE_45, Style.accent(), focus));

        HFont font = Fonts.body();
        float textX = x + 24.0f;
        float textBaseline = rowY + Style.FIELD_TEXT_BASELINE;
        boolean showPlaceholder = this.value.isEmpty() && !this.focused;
        String shown = showPlaceholder ? this.placeholder : this.value;
        String clipped = font.trim(shown, Style.ROW_W - 24.0f - 12.0f);
        font.draw(gg, clipped, textX, textBaseline,
                showPlaceholder ? Style.WHITE_45 : Style.WHITE);

        boolean lit = System.currentTimeMillis() - this.lastEdit < 500L
                || (System.currentTimeMillis() / 500L) % 2L == 0L;
        this.caretAnim.to(this.focused && lit ? 1.0f : 0.0f);
        float caret = this.caretAnim.get() * focus;
        if (caret > 0.01f) {
            float caretX = textX + font.width(clipped) + 1.0f;
            float height = font.ascent() + font.descent() - 3.0f;
            Render2D.rect(gg, caretX, textBaseline - font.ascent() + 2.0f, 1.0f, height,
                    Render2D.withAlpha(Style.accent(), Mth.clamp(caret, 0.0f, 1.0f)));
        }
    }

    @Override
    public boolean mouseClicked(float x, float baseline, double mx, double my, int button) {
        boolean inside = Render2D.hovered(mx, my, x, controlTop(baseline),
                Style.ROW_W, Style.ROW_H);
        if (button == 0) {
            this.focused = inside;
        }
        return inside;
    }

    @Override
    public boolean keyPressed(int key) {
        if (!this.focused) {
            return false;
        }
        this.lastEdit = System.currentTimeMillis();
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!this.value.isEmpty()) {
                    this.value = this.value.substring(0, this.value.length() - 1);
                }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_ESCAPE -> {
                this.focused = false;
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    @Override
    public boolean charTyped(char c) {
        if (!this.focused || c < 32 || c == 127) {
            return false;
        }
        this.value += c;
        this.lastEdit = System.currentTimeMillis();
        return true;
    }

    @Override
    public String serialize() {
        return this.value;
    }

    @Override
    public void deserialize(String raw) {
        value(raw);
    }
}
