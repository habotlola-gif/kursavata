package dev.hatek.client.module.setting;

import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Anim;
import dev.hatek.client.ui.render.Render2D;
import dev.hatek.client.ui.render.TexCache;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

public final class BoolSetting extends Setting {
    private static final int GLYPH = 6;

    private boolean value;
    private final Anim checkAnim = new Anim(0.0f, 15.0f);
    private final Anim hoverAnim = new Anim(0.0f, 18.0f);

    public BoolSetting(String name, boolean value) {
        super(name);
        this.value = value;
        this.checkAnim.snap(value ? 1.0f : 0.0f);
    }

    public boolean value() {
        return this.value;
    }

    public void value(boolean v) {
        this.value = v;
    }

    @Override
    public float bottomOffset() {
        return Style.CHECKBOX - 11.0f;
    }

    @Override
    public void render(GuiGraphicsExtractor gg, float x, float baseline, double mx, double my) {
        drawLabel(gg, x, baseline);
        float boxX = x + Style.ROW_W - Style.CHECKBOX;
        float boxY = baseline - 11.0f;

        this.hoverAnim.to(Render2D.hovered(mx, my, x, boxY, Style.ROW_W, Style.CHECKBOX));
        this.checkAnim.to(this.value);
        float k = this.checkAnim.get();
        float hover = this.hoverAnim.get();

        Render2D.round(gg, boxX, boxY, Style.CHECKBOX, Style.CHECKBOX,
                Style.CHECKBOX_R,
                Render2D.lerp(Render2D.lerp(Style.WHITE_04, Style.white(0.09f), hover),
                        Style.accent(), k));

        float cx = boxX + Style.CHECKBOX / 2.0f;
        float cy = boxY + Style.CHECKBOX / 2.0f;
        if (k < 0.999f) {
            Render2D.icon(gg, "cross", cx - GLYPH / 2.0f, cy - GLYPH / 2.0f, GLYPH, GLYPH,
                    Render2D.withAlpha(Style.WHITE_45, 1.0f - k));
        }
        if (k > 0.001f) {
            int size = GLYPH + 1;
            Render2D.texture(gg, TexCache.check(size), cx - size / 2.0f, cy - size / 2.0f,
                    size, size, Render2D.withAlpha(Style.PANEL, k));
        }
    }

    @Override
    public boolean mouseClicked(float x, float baseline, double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        if (!Render2D.hovered(mx, my, x, baseline - 11.0f, Style.ROW_W, Style.CHECKBOX)) {
            return false;
        }
        this.value = !this.value;
        return true;
    }

    @Override
    public String serialize() {
        return String.valueOf(this.value);
    }

    @Override
    public void deserialize(String raw) {
        this.value = Boolean.parseBoolean(raw);
        this.checkAnim.snap(this.value ? 1.0f : 0.0f);
    }
}
