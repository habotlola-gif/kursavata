package dev.hatek.client.module.setting;

import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Anim;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public final class ModeSetting extends Setting {
    private static final int OPTION_H = 24;
    private static final int LIST_GAP = 4;

    private final List<String> options;
    private int index;

    private boolean open;
    private final Anim openAnim = new Anim(0.0f, 15.0f);
    private final Anim hoverAnim = new Anim(0.0f, 18.0f);
    private final Anim chevronAnim = new Anim(0.0f, 16.0f);
    private final List<Anim> optionAnims = new ArrayList<>();

    public ModeSetting(String name, int index, String... options) {
        super(name);
        this.options = List.of(options);
        this.index = Math.floorMod(index, Math.max(1, this.options.size()));
        for (int i = 0; i < this.options.size(); i++) {
            this.optionAnims.add(new Anim(0.0f, 18.0f));
        }
    }

    public String value() {
        return this.options.get(this.index);
    }

    public boolean is(String option) {
        return value().equalsIgnoreCase(option);
    }

    public void value(String option) {
        int found = this.options.indexOf(option);
        if (found >= 0) {
            this.index = found;
        }
    }

    public List<String> options() {
        return this.options;
    }

    private float listHeight() {
        return LIST_GAP + this.options.size() * OPTION_H;
    }

    @Override
    public float bottomOffset() {
        this.openAnim.to(this.open);
        return Style.LABEL_TO_CONTROL + Style.ROW_H
                + listHeight() * Anim.easeInOut(this.openAnim.get());
    }

    @Override
    public void closePopups() {
        this.open = false;
    }

    @Override
    public void render(GuiGraphicsExtractor gg, float x, float baseline, double mx, double my) {
        drawLabel(gg, x, baseline);
        float rowY = controlTop(baseline);
        boolean hover = Render2D.hovered(mx, my, x, rowY, Style.ROW_W, Style.ROW_H);
        this.hoverAnim.to(hover);
        this.chevronAnim.to(this.open);
        this.openAnim.to(this.open);
        float k = this.hoverAnim.get();
        float openness = Anim.easeInOut(this.openAnim.get());

        Render2D.round(gg, x, rowY, Style.ROW_W, Style.ROW_H, Style.ROW_R,
                Render2D.lerp(Render2D.lerp(Style.WHITE_04, Style.white(0.075f), k),
                        Style.white(0.10f), openness));

        Render2D.icon(gg, "list", x + Style.FIELD_ICON_X + 1.0f,
                rowY + (Style.ROW_H - 6.0f) / 2.0f, 8.0f, 6.0f,
                Render2D.lerp(Style.WHITE_45, Style.WHITE, k * 0.5f));

        Fonts.body().draw(gg, value(), x + 24.0f, rowY + Style.FIELD_TEXT_BASELINE,
                Render2D.lerp(Style.WHITE_45, Style.WHITE, Math.max(k * 0.45f, openness)));

        float flip = Anim.easeInOut(this.chevronAnim.get());
        float chevronY = rowY + Style.CHEVRON_Y;
        int chevronTint = Render2D.lerp(
                Render2D.lerp(Style.WHITE_45, Style.WHITE, k * 0.5f),
                Style.accent(), flip);
        if (flip < 0.999f) {
            Render2D.icon(gg, "chevron", x + Style.CHEVRON_X, chevronY + flip * 4.0f, 6.0f, 3.0f,
                    Render2D.withAlpha(chevronTint, 1.0f - flip));
        }
        if (flip > 0.001f) {
            Render2D.icon(gg, "chevron", x + Style.CHEVRON_X,
                    chevronY - (1.0f - flip) * 4.0f, 6.0f, 3.0f,
                    Render2D.withAlpha(chevronTint, flip), true);
        }

        if (openness <= 0.002f) {
            return;
        }
        float listTop = rowY + Style.ROW_H + LIST_GAP;
        Render2D.pushClip(gg, x - 2.0f, rowY + Style.ROW_H,
                Style.ROW_W + 4.0f, listHeight() * openness);
        Render2D.pushAlpha(Mth.clamp((openness - 0.15f) / 0.6f, 0.0f, 1.0f));
        for (int i = 0; i < this.options.size(); i++) {
            float oy = listTop + i * OPTION_H;
            boolean selected = i == this.index;
            Anim anim = this.optionAnims.get(i);
            anim.to(Render2D.hovered(mx, my, x, oy, Style.ROW_W, OPTION_H) || selected);
            float hot = anim.get();

            Render2D.round(gg, x, oy, Style.ROW_W, OPTION_H - 2.0f, 6.0f,
                    selected ? Render2D.withAlpha(Style.accent(), 0.16f)
                            : Render2D.lerp(0x00FFFFFF, Style.white(0.06f), hot));
            if (selected) {
                Render2D.round(gg, x + 6.0f, oy + (OPTION_H - 2.0f) / 2.0f - 4.0f, 2.0f, 8.0f, 1.0f,
                        Style.accent());
            }
            Fonts.body().draw(gg, this.options.get(i), x + 16.0f, oy + 15.0f,
                    selected ? Style.accent()
                            : Render2D.lerp(Style.WHITE_45, Style.WHITE, hot));
        }
        Render2D.popAlpha();
        Render2D.popClip(gg);
    }

    @Override
    public boolean mouseClicked(float x, float baseline, double mx, double my, int button) {
        float rowY = controlTop(baseline);
        if (Render2D.hovered(mx, my, x, rowY, Style.ROW_W, Style.ROW_H)) {
            if (button == 0) {
                this.open = !this.open;
                return true;
            }
            if (button == 1) {
                this.index = Math.floorMod(this.index - 1, this.options.size());
                return true;
            }
            return false;
        }
        if (!this.open) {
            return false;
        }
        float listTop = rowY + Style.ROW_H + LIST_GAP;
        for (int i = 0; i < this.options.size(); i++) {
            if (Render2D.hovered(mx, my, x, listTop + i * OPTION_H, Style.ROW_W, OPTION_H)) {
                this.index = i;
                this.open = false;
                return true;
            }
        }
        return false;
    }

    @Override
    public String serialize() {
        return value();
    }

    @Override
    public void deserialize(String raw) {
        value(raw);
    }
}
