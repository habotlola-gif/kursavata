package dev.hatek.client.module.impl.render;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.ModuleManager;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Card listing the modules that have a key bound (design: keybinds-hud.svg). Disabled modules are dimmed. */
public final class KeybindsHud extends PanelHud {
    private static final int MAX_ROWS = 12;
    private static final int ACCENT = 0xFFB3A8F0;
    private static final int KEY = 0xFF9D93E6;
    private static final int BAR = 0xFF3A3456;

    private static KeybindsHud instance;

    private record Row(String name, String key, boolean on) {}

    public KeybindsHud() {
        super("KeybindsHUD", "Card listing the modules with a bound key", 1.5, 30.0);
        instance = this;
    }

    public static KeybindsHud instance() {
        return instance;
    }

    @Override
    protected float cardWidth() {
        return 210.0f;
    }

    private List<Row> rows() {
        List<Row> out = new ArrayList<>();
        for (Module module : ModuleManager.all()) {
            int key = module.keyCode();
            if (key == GLFW.GLFW_KEY_UNKNOWN || key < 0 || module == this) {
                continue;
            }
            String label = InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString().toUpperCase(Locale.ROOT);
            out.add(new Row(spaced(module.name()), label, module.isEnabled()));
            if (out.size() >= MAX_ROWS) {
                break;
            }
        }
        return out;
    }

    /** "AirStuck" -> "Air Stuck", "TargetHUD" -> "Target HUD". */
    private static String spaced(String name) {
        return name.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ").replaceAll("(?<=[A-Z])(?=[A-Z][a-z])", " ");
    }

    @Override
    protected int rowCount() {
        return rows().size();
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor gg, float x, float y, float s, int count, boolean preview) {
        List<Row> rows = preview
                ? List.of(new Row("Full Bright", "K", true), new Row("Packet Criticals", "U", true), new Row("Pearl Target", "I", true))
                : rows();

        title(s).draw(gg, "Keybinds", x + 14.0f * s, y + 28.5f * s, 0xFFFFFFFF);
        // keyboard badge in the header
        float bx = x + 174.0f * s;
        float by = y + 14.0f * s;
        Render2D.round(gg, bx, by, 22.0f * s, 18.0f * s, 5.0f * s, ACCENT);
        Render2D.round(gg, bx + 5.0f * s, by + 6.0f * s, 12.0f * s, 2.0f * s, 1.0f * s, BAR);
        Render2D.round(gg, bx + 5.0f * s, by + 10.5f * s, 12.0f * s, 2.0f * s, 1.0f * s, BAR);

        HFont nameFont = row(s);
        HFont keyFont = Fonts.of(Fonts.INTER_BOLD, Math.max(8, Math.round(15.0f * s)));
        for (int i = 0; i < Math.min(count, rows.size()); i++) {
            Row r = rows.get(i);
            float base = y + (65.5f + 29.0f * i) * s;
            float keyWidth = keyFont.width(r.key());
            String name = nameFont.trim(r.name(), 210.0f * s - 28.0f * s - keyWidth - 12.0f * s);
            int color = r.on() ? 0xFFFFFFFF : Render2D.withAlpha(0xFFFFFFFF, 0.55f);
            nameFont.draw(gg, name, x + 14.0f * s, base, color);
            keyFont.drawRight(gg, r.key(), x + 194.0f * s, base, KEY);
        }
    }
}
