package dev.hatek.client.ui.screen;

import dev.hatek.client.ui.Style;
import dev.hatek.client.feature.config.ConfigEntry;
import dev.hatek.client.feature.config.ConfigManager;
import dev.hatek.client.ui.render.Anim;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConfigsPage extends Page {
    private final Map<String, Anim> hover = new HashMap<>();
    private final Map<String, Anim> heart = new HashMap<>();
    private final Map<String, Anim> trash = new HashMap<>();
    private final Map<String, Anim> copy = new HashMap<>();

    private Anim anim(Map<String, Anim> map, String key, float speed) {
        return map.computeIfAbsent(key, k -> new Anim(0.0f, speed));
    }

    @Override
    public void onShow() {
        ConfigManager.refresh();
    }

    @Override
    public float contentHeight() {
        int count = Math.max(1, ConfigManager.all().size());
        int rows = (count + Style.COLUMNS - 1) / Style.COLUMNS;
        return Math.max(0, rows * Style.ENTRY_PITCH - Style.CARD_GAP);
    }

    @Override
    public void render(GuiGraphicsExtractor gg, float x, float y, float scroll, double mx, double my) {
        List<ConfigEntry> configs = ConfigManager.all();
        if (configs.isEmpty()) {
            Fonts.body().draw(gg, "No configs yet - use the + on the left to save one", x + 2.0f,
                    y + 24.0f, Style.WHITE_25);
            return;
        }
        float viewport = Style.PANEL_H - Style.CONTENT_TOP;
        for (int i = 0; i < configs.size(); i++) {
            float cx = x + (i % Style.COLUMNS) * Style.COLUMN_PITCH;
            float cy = y + (i / Style.COLUMNS) * Style.ENTRY_PITCH - scroll;
            if (cy > y + viewport || cy + Style.ENTRY_H < y) {
                continue;
            }
            renderCard(gg, configs.get(i), cx, cy, mx, my);
        }
    }

    private void renderCard(GuiGraphicsExtractor gg, ConfigEntry config, float x, float y,
                            double mx, double my) {
        String key = config.name();
        Anim hoverAnim = anim(this.hover, key, 17.0f);
        Anim heartAnim = anim(this.heart, key, 15.0f);
        hoverAnim.to(Render2D.hovered(mx, my, x, y, Style.CARD_W, Style.ENTRY_H));
        heartAnim.to(config.favourite());
        float hot = hoverAnim.get();
        float fav = heartAnim.get();
        boolean loaded = key.equals(ConfigManager.loadedName());

        Render2D.round(gg, x, y, Style.CARD_W, Style.ENTRY_H, Style.CARD_R,
                Render2D.lerp(Style.WHITE_02, Style.white(0.05f), hot));
        if (loaded) {
            Render2D.round(gg, x + 1.0f, y + Style.CARD_R,
                    2.0f, Style.ENTRY_H - Style.CARD_R * 2.0f, 1.0f, Style.accent());
        }

        EntryCard.header(gg, x, y, config.author(), config.date(), config.name(),
                Style.accent());

        Render2D.icon(gg, "heart", x + Style.HEART_X, y + Style.HEART_Y,
                Style.HEART_W, Style.HEART_H,
                Render2D.lerp(Render2D.lerp(Style.WHITE_25, Style.WHITE_45, hot),
                        Style.FAVOURITE, fav));

        Anim trashAnim = anim(this.trash, key, 18.0f);
        Anim copyAnim = anim(this.copy, key, 18.0f);
        float trashX = x + Style.ACTION_X;
        float copyX = x + Style.ACTION_X + Style.ACTION_STEP;
        float actionY = y + Style.ACTION_Y;
        trashAnim.to(Render2D.hovered(mx, my, trashX - 4.0f, actionY - 3.0f, 18.0f, 18.0f));
        copyAnim.to(Render2D.hovered(mx, my, copyX - 4.0f, actionY - 3.0f, 18.0f, 18.0f));

        action(gg, "trash", trashX, actionY, trashAnim.get(), Style.DANGER);
        action(gg, "copy", copyX, actionY, copyAnim.get(), Style.WHITE_25);
    }

    private static void action(GuiGraphicsExtractor gg, String icon, float x, float y,
                               float hot, int idle) {
        Render2D.icon(gg, icon, x, y, Style.ACTION_W, Style.ACTION_H,
                Render2D.lerp(idle, Style.WHITE, hot * 0.45f));
    }

    @Override
    public boolean mouseClicked(float x, float y, float scroll, double mx, double my, int button) {
        List<ConfigEntry> configs = ConfigManager.all();
        for (int i = 0; i < configs.size(); i++) {
            ConfigEntry config = configs.get(i);
            float cx = x + (i % Style.COLUMNS) * Style.COLUMN_PITCH;
            float cy = y + (i / Style.COLUMNS) * Style.ENTRY_PITCH - scroll;
            if (!Render2D.hovered(mx, my, cx, cy, Style.CARD_W, Style.ENTRY_H)) {
                continue;
            }
            if (button != 0) {
                return true;
            }
            if (Render2D.hovered(mx, my, cx + Style.HEART_X - 4.0f,
                    cy + Style.HEART_Y - 4.0f, 20.0f, 19.0f)) {
                ConfigManager.toggleFavourite(config);
                return true;
            }
            float actionY = cy + Style.ACTION_Y;
            if (Render2D.hovered(mx, my, cx + Style.ACTION_X - 4.0f, actionY - 3.0f, 18.0f, 18.0f)) {
                ConfigManager.delete(config);
                return true;
            }
            if (Render2D.hovered(mx, my, cx + Style.ACTION_X + Style.ACTION_STEP - 4.0f,
                    actionY - 3.0f, 18.0f, 18.0f)) {
                ConfigManager.duplicate(config);
                return true;
            }
            ConfigManager.load(config);
            return true;
        }
        return false;
    }
}
