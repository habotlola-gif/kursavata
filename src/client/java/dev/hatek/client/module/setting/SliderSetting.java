package dev.hatek.client.module.setting;

import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Anim;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class SliderSetting extends Setting {
    private final double min;
    private final double max;
    private final double step;
    private final int decimals;
    private double value;
    private boolean dragging;

    private final Anim position = new Anim(0.0f, 22.0f);
    private final Anim knobAnim = new Anim(0.0f, 20.0f);

    public SliderSetting(String name, double value, double min, double max, double step) {
        super(name);
        this.min = min;
        this.max = max;
        this.step = step;
        this.decimals = decimalsOf(step);
        this.value = clampToStep(value);
        this.position.snap(fraction());
    }

    public double value() {
        return this.value;
    }

    public void value(double v) {
        this.value = clampToStep(v);
    }

    @Override
    public float bottomOffset() {
        return Style.LABEL_TO_CONTROL + Style.TRACK_H;
    }

    @Override
    public void render(GuiGraphicsExtractor gg, float x, float baseline, double mx, double my) {
        drawLabel(gg, x, baseline);
        Fonts.label().drawRight(gg, text(), x + Style.ROW_W - 1, baseline, Style.accent());

        float trackY = controlTop(baseline);
        boolean hot = this.dragging
                || Render2D.hovered(mx, my, x - 2.0f, trackY - 7.0f, Style.TRACK_W + 4.0f, 18.0f);
        this.knobAnim.to(hot);
        this.position.to(fraction());

        float travel = Style.TRACK_W - Style.SLIDER_KNOB;
        float knobCenter = x + Style.SLIDER_KNOB / 2.0f + travel * this.position.get();

        Render2D.round(gg, x, trackY, Style.TRACK_W, Style.TRACK_H,
                Style.TRACK_R, Style.WHITE_04);
        float filled = knobCenter - x + Style.SLIDER_KNOB / 2.0f;
        Render2D.round(gg, x, trackY, filled, Style.TRACK_H,
                Style.TRACK_R, Style.accent());

        float ring = Style.SLIDER_KNOB + 1.0f;
        float centerY = trackY + Style.TRACK_H / 2.0f;
        Render2D.circle(gg, knobCenter - ring / 2.0f, centerY - ring / 2.0f, ring,
                Render2D.lerp(Style.white(0.82f), Style.WHITE, this.knobAnim.get()));
        float dot = Style.SLIDER_KNOB - 1.0f;
        Render2D.circle(gg, knobCenter - dot / 2.0f, centerY - dot / 2.0f, dot, Style.accent());
    }

    @Override
    public boolean mouseClicked(float x, float baseline, double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        float trackY = controlTop(baseline);
        if (!Render2D.hovered(mx, my, x - 2.0f, trackY - 7.0f, Style.TRACK_W + 4.0f, 18.0f)) {
            return false;
        }
        this.dragging = true;
        apply(x, mx);
        return true;
    }

    @Override
    public void mouseDragged(float x, float baseline, double mx, double my) {
        if (this.dragging) {
            apply(x, mx);
        }
    }

    @Override
    public void mouseReleased() {
        this.dragging = false;
    }

    private void apply(float x, double mx) {
        float travel = Style.TRACK_W - Style.SLIDER_KNOB;
        double fraction = (mx - x - Style.SLIDER_KNOB / 2.0) / travel;
        this.value = clampToStep(this.min + (this.max - this.min) * Mth.clamp(fraction, 0.0, 1.0));
    }

    private float fraction() {
        return this.max - this.min <= 0.0 ? 0.0f
                : (float) Mth.clamp((this.value - this.min) / (this.max - this.min), 0.0, 1.0);
    }

    private String text() {
        return this.decimals == 0
                ? String.valueOf(Math.round(this.value))
                : String.format(Locale.ROOT, "%." + this.decimals + "f", this.value);
    }

    private double clampToStep(double raw) {
        double clamped = Mth.clamp(raw, this.min, this.max);
        if (this.step <= 0.0) {
            return clamped;
        }
        double snapped = this.min + Math.round((clamped - this.min) / this.step) * this.step;
        return BigDecimal.valueOf(Mth.clamp(snapped, this.min, this.max))
                .setScale(Math.max(this.decimals, 0), RoundingMode.HALF_UP).doubleValue();
    }

    private static int decimalsOf(double step) {
        if (step <= 0.0) {
            return 2;
        }
        String s = BigDecimal.valueOf(step).stripTrailingZeros().toPlainString();
        int dot = s.indexOf('.');
        return dot < 0 ? 0 : s.length() - dot - 1;
    }

    @Override
    public String serialize() {
        return String.valueOf(this.value);
    }

    @Override
    public void deserialize(String raw) {
        try {
            value(Double.parseDouble(raw));
            this.position.snap(fraction());
        } catch (NumberFormatException ignored) {
        }
    }
}
