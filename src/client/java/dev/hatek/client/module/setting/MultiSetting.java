package dev.hatek.client.module.setting;

import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Anim;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MultiSetting extends Setting {
    private final List<String> options;
    private final Set<String> selected = new LinkedHashSet<>();
    private final Map<String, Anim> fill = new HashMap<>();
    private final Map<String, Anim> hover = new HashMap<>();

    public MultiSetting(String name, List<String> options, String... enabled) {
        super(name);
        this.options = List.copyOf(options);
        for (String option : enabled) {
            this.selected.add(option);
        }
        for (String option : this.options) {
            this.fill.put(option, new Anim(this.selected.contains(option) ? 1.0f : 0.0f, 15.0f));
            this.hover.put(option, new Anim(0.0f, 18.0f));
        }
    }

    public boolean isSelected(String option) {
        return this.selected.contains(option);
    }

    public Set<String> selected() {
        return Set.copyOf(this.selected);
    }

    @Override
    public float bottomOffset() {
        int rows = layout(new ArrayList<>());
        return Style.LABEL_TO_CONTROL
                + (rows - 1) * Style.CHIP_ROW_STEP + Style.CHIP_H;
    }

    @Override
    public void render(GuiGraphicsExtractor gg, float x, float baseline, double mx, double my) {
        drawLabel(gg, x, baseline);
        float top = controlTop(baseline);
        HFont font = Fonts.body();
        List<Chip> chips = new ArrayList<>();
        layout(chips);
        for (Chip chip : chips) {
            float cx = x + chip.x();
            float cy = top + chip.row() * Style.CHIP_ROW_STEP;
            Anim fillAnim = this.fill.get(chip.label());
            Anim hoverAnim = this.hover.get(chip.label());
            fillAnim.to(this.selected.contains(chip.label()));
            hoverAnim.to(Render2D.hovered(mx, my, cx, cy, chip.width(), Style.CHIP_H));

            float on = fillAnim.get();
            float hot = hoverAnim.get();
            Render2D.round(gg, cx, cy, chip.width(), Style.CHIP_H, Style.CHIP_R,
                    Render2D.lerp(Render2D.lerp(Style.WHITE_04, Style.white(0.09f), hot),
                            Style.accent(), on));
            font.drawCentered(gg, chip.label(), cx + chip.width() / 2.0f,
                    cy + Style.CHIP_BASELINE,
                    Render2D.lerp(Render2D.lerp(Style.WHITE_25, Style.WHITE_45, hot),
                            Style.WHITE, on));
        }
    }

    @Override
    public boolean mouseClicked(float x, float baseline, double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        float top = controlTop(baseline);
        List<Chip> chips = new ArrayList<>();
        layout(chips);
        for (Chip chip : chips) {
            float cx = x + chip.x();
            float cy = top + chip.row() * Style.CHIP_ROW_STEP;
            if (Render2D.hovered(mx, my, cx, cy, chip.width(), Style.CHIP_H)) {
                if (!this.selected.remove(chip.label())) {
                    this.selected.add(chip.label());
                }
                return true;
            }
        }
        return false;
    }

    private record Chip(String label, float x, float width, int row) {
    }

    private int layout(List<Chip> out) {
        HFont font = Fonts.body();
        float cursor = 0.0f;
        int row = 0;
        for (String option : this.options) {
            float width = Math.round(font.width(option)) + Style.CHIP_PAD * 2;
            if (cursor > 0.0f && cursor + width > Style.ROW_W) {
                cursor = 0.0f;
                row++;
            }
            out.add(new Chip(option, cursor, width, row));
            cursor += width + Style.CHIP_GAP;
        }
        return row + 1;
    }

    @Override
    public String serialize() {
        return String.join(",", this.selected);
    }

    @Override
    public void deserialize(String raw) {
        this.selected.clear();
        if (raw != null && !raw.isBlank()) {
            for (String part : raw.split(",")) {
                String option = part.trim();
                if (this.options.contains(option)) {
                    this.selected.add(option);
                }
            }
        }
        for (String option : this.options) {
            this.fill.get(option).snap(this.selected.contains(option) ? 1.0f : 0.0f);
        }
    }
}
