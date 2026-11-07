package dev.hatek.client.module.setting;

import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Anim;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.Render2D;
import dev.hatek.client.ui.render.TexCache;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.awt.Color;

public final class ColorSetting extends Setting {
    private static final int SV_H = 70;
    private static final int BAR_H = 8;
    private static final int GAP = 6;
    private static final int PICKER_H = GAP + SV_H + GAP + BAR_H + GAP + BAR_H;

    private float hue;
    private float saturation;
    private float brightness;
    private float alpha;

    private boolean open;
    private int dragTarget = -1;

    private final Anim openAnim = new Anim(0.0f, 13.0f);
    private final Anim hoverAnim = new Anim(0.0f, 18.0f);

    public ColorSetting(String name, int argb) {
        super(name);
        set(argb);
    }

    public int value() {
        int rgb = Color.HSBtoRGB(this.hue, this.saturation, this.brightness) & 0x00FFFFFF;
        return (Math.round(this.alpha * 255.0f) << 24) | rgb;
    }

    public void set(int argb) {
        this.alpha = ((argb >>> 24) & 0xFF) / 255.0f;
        float[] hsb = Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }

    @Override
    public float bottomOffset() {
        this.openAnim.to(this.open);
        return Style.LABEL_TO_CONTROL + Style.ROW_H
                + PICKER_H * Anim.easeInOut(this.openAnim.get());
    }

    @Override
    public void closePopups() {
        this.open = false;
    }

    @Override
    public void render(GuiGraphicsExtractor gg, float x, float baseline, double mx, double my) {
        drawLabel(gg, x, baseline);
        float rowY = controlTop(baseline);
        this.hoverAnim.to(Render2D.hovered(mx, my, x, rowY, Style.ROW_W, Style.ROW_H));
        this.openAnim.to(this.open);
        float hover = this.hoverAnim.get();
        float openness = Anim.easeInOut(this.openAnim.get());

        Render2D.round(gg, x, rowY, Style.ROW_W, Style.ROW_H, Style.ROW_R,
                Render2D.lerp(Render2D.lerp(Style.WHITE_04, Style.white(0.075f), hover),
                        Style.white(0.10f), openness));

        Render2D.icon(gg, "paint", x + Style.FIELD_ICON_X,
                rowY + (Style.ROW_H - Style.FIELD_ICON) / 2.0f,
                Style.FIELD_ICON, Style.FIELD_ICON,
                Render2D.lerp(Style.WHITE_45, Style.WHITE, hover * 0.5f));

        float captionX = x + Style.ROW_W / 2.0f - 4.0f;
        float captionY = rowY + Style.FIELD_TEXT_BASELINE;
        if (openness < 0.999f) {
            Fonts.body().drawCentered(gg, "Open", captionX, captionY - 9.0f * openness,
                    Render2D.withAlpha(Style.WHITE_45, 1.0f - openness));
        }
        if (openness > 0.001f) {
            Fonts.body().drawCentered(gg, "Close", captionX, captionY + 9.0f * (1.0f - openness),
                    Render2D.withAlpha(Style.WHITE_45, openness));
        }

        Render2D.round(gg, x + Style.SWATCH_X, rowY + Style.SWATCH_Y,
                Style.SWATCH, Style.SWATCH, Style.SWATCH_R, value() | 0xFF000000);

        if (openness > 0.002f) {
            float top = rowY + Style.ROW_H + GAP;
            Render2D.pushClip(gg, x - 2.0f, top - GAP, Style.ROW_W + 4.0f,
                    PICKER_H * openness + GAP);
            Render2D.pushAlpha(Mth.clamp((openness - 0.2f) / 0.6f, 0.0f, 1.0f));
            renderPicker(gg, x, top);
            Render2D.popAlpha();
            Render2D.popClip(gg);
        }
    }

    private void renderPicker(GuiGraphicsExtractor gg, float x, float y) {
        int w = Style.ROW_W;
        int pureHue = Color.HSBtoRGB(this.hue, 1.0f, 1.0f) | 0xFF000000;

        Render2D.round(gg, x, y, w, SV_H, Style.ROW_R, pureHue);
        Render2D.texture(gg, TexCache.saturationRamp(w, SV_H, Style.ROW_R),
                x, y, w, SV_H, Style.WHITE);
        Render2D.texture(gg, TexCache.valueRamp(w, SV_H, Style.ROW_R),
                x, y, w, SV_H, Style.WHITE);
        marker(gg, x + this.saturation * w, y + (1.0f - this.brightness) * SV_H);

        float hueY = y + SV_H + GAP;
        Render2D.texture(gg, TexCache.hueRamp(w, BAR_H, BAR_H / 2.0f),
                x, hueY, w, BAR_H, Style.WHITE);
        marker(gg, x + this.hue * w, hueY + BAR_H / 2.0f);

        float alphaY = hueY + BAR_H + GAP;
        Render2D.round(gg, x, alphaY, w, BAR_H, BAR_H / 2.0f, Style.WHITE_04);
        Render2D.texture(gg, TexCache.alphaRamp(w, BAR_H, BAR_H / 2.0f),
                x, alphaY, w, BAR_H, value() | 0xFF000000);
        marker(gg, x + this.alpha * w, alphaY + BAR_H / 2.0f);
    }

    private static void marker(GuiGraphicsExtractor gg, float cx, float cy) {
        Render2D.circle(gg, cx - 4.0f, cy - 4.0f, 8.0f, Style.WHITE);
        Render2D.circle(gg, cx - 3.0f, cy - 3.0f, 6.0f, 0xFF000000);
        Render2D.circle(gg, cx - 2.0f, cy - 2.0f, 4.0f, Style.WHITE);
    }

    @Override
    public boolean mouseClicked(float x, float baseline, double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        float rowY = controlTop(baseline);
        if (Render2D.hovered(mx, my, x, rowY, Style.ROW_W, Style.ROW_H)) {
            this.open = !this.open;
            return true;
        }
        if (!this.open) {
            return false;
        }
        float y = rowY + Style.ROW_H + GAP;
        int w = Style.ROW_W;
        if (Render2D.hovered(mx, my, x, y, w, SV_H)) {
            this.dragTarget = 0;
        } else if (Render2D.hovered(mx, my, x, y + SV_H + GAP - 4.0f, w, BAR_H + 8.0f)) {
            this.dragTarget = 1;
        } else if (Render2D.hovered(mx, my, x, y + SV_H + GAP + BAR_H + GAP - 4.0f, w, BAR_H + 8.0f)) {
            this.dragTarget = 2;
        } else {
            return false;
        }
        apply(x, rowY, mx, my);
        return true;
    }

    @Override
    public void mouseDragged(float x, float baseline, double mx, double my) {
        if (this.dragTarget >= 0) {
            apply(x, controlTop(baseline), mx, my);
        }
    }

    @Override
    public void mouseReleased() {
        this.dragTarget = -1;
    }

    private void apply(float x, float rowY, double mx, double my) {
        int w = Style.ROW_W;
        float y = rowY + Style.ROW_H + GAP;
        float fx = Mth.clamp((float) (mx - x) / w, 0.0f, 1.0f);
        switch (this.dragTarget) {
            case 0 -> {
                this.saturation = fx;
                this.brightness = 1.0f - Mth.clamp((float) (my - y) / SV_H, 0.0f, 1.0f);
            }
            case 1 -> this.hue = fx;
            case 2 -> this.alpha = fx;
            default -> {
            }
        }
    }

    @Override
    public String serialize() {
        return String.valueOf(value());
    }

    @Override
    public void deserialize(String raw) {
        try {
            set((int) Long.parseLong(raw));
        } catch (NumberFormatException ignored) {
        }
    }
}
