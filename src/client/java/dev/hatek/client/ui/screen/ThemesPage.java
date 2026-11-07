package dev.hatek.client.ui.screen;

import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Anim;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.Render2D;
import dev.hatek.client.feature.theme.ThemeEntry;
import dev.hatek.client.feature.theme.ThemeManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ThemesPage extends Page {
    private final Map<String, Anim> hover = new HashMap<>();
    private final Map<String, Anim> applied = new HashMap<>();

    private Anim hover(String name) {
        return this.hover.computeIfAbsent(name, k -> new Anim(0.0f, 17.0f));
    }

    private Anim applied(String name) {
        return this.applied.computeIfAbsent(name, k -> new Anim(0.0f, 14.0f));
    }

    @Override
    public float contentHeight() {
        int rows = (ThemeManager.all().size() + Style.COLUMNS - 1) / Style.COLUMNS;
        return Math.max(0, rows * Style.ENTRY_PITCH - Style.CARD_GAP);
    }

    @Override
    public void render(GuiGraphicsExtractor gg, float x, float y, float scroll, double mx, double my) {
        List<ThemeEntry> themes = ThemeManager.all();
        float viewport = Style.PANEL_H - Style.CONTENT_TOP;
        for (int i = 0; i < themes.size(); i++) {
            ThemeEntry theme = themes.get(i);
            float cx = x + (i % Style.COLUMNS) * Style.COLUMN_PITCH;
            float cy = y + (i / Style.COLUMNS) * Style.ENTRY_PITCH - scroll;
            if (cy > y + viewport || cy + Style.ENTRY_H < y) {
                continue;
            }
            renderCard(gg, theme, cx, cy, mx, my);
        }
    }

    private void renderCard(GuiGraphicsExtractor gg, ThemeEntry theme, float x, float y,
                            double mx, double my) {
        Anim hoverAnim = hover(theme.name());
        Anim appliedAnim = applied(theme.name());
        hoverAnim.to(Render2D.hovered(mx, my, x, y, Style.CARD_W, Style.ENTRY_H));
        appliedAnim.to(ThemeManager.isApplied(theme));
        float hot = hoverAnim.get();
        float on = appliedAnim.get();

        Render2D.round(gg, x, y, Style.CARD_W, Style.ENTRY_H, Style.CARD_R,
                Render2D.lerp(Style.WHITE_02, Style.white(0.05f), Math.max(hot, on * 0.6f)));

        Render2D.pushClip(gg, x, y, Style.CARD_W, Style.ENTRY_H);
        Render2D.icon(gg, "swoosh", x + Style.CARD_W - Style.SWOOSH_W,
                y + Style.ENTRY_H - Style.SWOOSH_H,
                Style.SWOOSH_W, Style.SWOOSH_H, theme.color());
        Render2D.popClip(gg);

        EntryCard.header(gg, x, y, theme.author(), theme.date(), theme.name(), theme.color());

        if (hot > 0.01f) {
            Fonts.body().draw(gg, ThemeManager.hex(theme.color()), x + Style.ENTRY_NAME_X,
                    y + Style.ENTRY_NAME_BASELINE + 15.0f,
                    Render2D.withAlpha(Style.WHITE_45, hot));
        }
        if (on > 0.01f) {
            Render2D.circle(gg, x + Style.ENTRY_NAME_X + Fonts.title().width(theme.name()) + 7.0f,
                    y + Style.ENTRY_NAME_BASELINE - 7.0f, 6.0f,
                    Render2D.withAlpha(theme.color(), on));
        }
    }

    @Override
    public boolean mouseClicked(float x, float y, float scroll, double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        List<ThemeEntry> themes = ThemeManager.all();
        for (int i = 0; i < themes.size(); i++) {
            float cx = x + (i % Style.COLUMNS) * Style.COLUMN_PITCH;
            float cy = y + (i / Style.COLUMNS) * Style.ENTRY_PITCH - scroll;
            if (Render2D.hovered(mx, my, cx, cy, Style.CARD_W, Style.ENTRY_H)) {
                ThemeManager.apply(themes.get(i));
                return true;
            }
        }
        return false;
    }
}
