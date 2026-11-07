package dev.hatek.client.ui.screen;

import dev.hatek.client.ui.Style;
import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.ModuleManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

public final class ModulesPage extends Page {
    private Category category = Category.COMBAT;
    private Module active;

    public Category category() {
        return this.category;
    }

    public void category(Category value) {
        this.category = value;
    }

    private record Placed(Module module, float x, float y) {
    }

    private float layout(List<Placed> out) {
        float[] columns = new float[Style.COLUMNS];
        for (Module module : ModuleManager.of(this.category)) {
            int shortest = 0;
            for (int i = 1; i < columns.length; i++) {
                if (columns[i] < columns[shortest] - 0.001f) {
                    shortest = i;
                }
            }
            out.add(new Placed(module, shortest * Style.COLUMN_PITCH, columns[shortest]));
            columns[shortest] += module.height() + Style.CARD_GAP;
        }
        float tallest = 0.0f;
        for (float value : columns) {
            tallest = Math.max(tallest, value);
        }
        return Math.max(0.0f, tallest - Style.CARD_GAP);
    }

    @Override
    public float contentHeight() {
        return layout(new ArrayList<>());
    }

    @Override
    public void render(GuiGraphicsExtractor gg, float x, float y, float scroll, double mx, double my) {
        List<Placed> placed = new ArrayList<>();
        layout(placed);
        float viewport = Style.PANEL_H - Style.CONTENT_TOP;
        for (Placed entry : placed) {
            float cardY = y + entry.y() - scroll;
            if (cardY > y + viewport || cardY + entry.module().height() < y) {
                continue;
            }
            entry.module().render(gg, x + entry.x(), cardY, mx, my);
        }
    }

    @Override
    public boolean mouseClicked(float x, float y, float scroll, double mx, double my, int button) {
        List<Placed> placed = new ArrayList<>();
        layout(placed);
        for (Placed entry : placed) {
            if (entry.module().mouseClicked(x + entry.x(), y + entry.y() - scroll, mx, my, button)) {
                this.active = entry.module();
                return true;
            }
        }
        return false;
    }

    @Override
    public void mouseDragged(float x, float y, float scroll, double mx, double my) {
        if (this.active == null) {
            return;
        }
        List<Placed> placed = new ArrayList<>();
        layout(placed);
        for (Placed entry : placed) {
            if (entry.module() == this.active) {
                entry.module().mouseDragged(x + entry.x(), y + entry.y() - scroll, mx, my);
                return;
            }
        }
    }

    @Override
    public void mouseReleased() {
        for (Module module : ModuleManager.all()) {
            module.mouseReleased();
        }
    }

    @Override
    public boolean keyPressed(int key) {
        for (Module module : ModuleManager.all()) {
            if (module.consumeBinding(key)) {
                return true;
            }
        }
        for (Module module : ModuleManager.all()) {
            if (module.capturesInput()) {
                return module.keyPressed(key);
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char c) {
        for (Module module : ModuleManager.all()) {
            if (module.capturesInput() && module.charTyped(c)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean capturesInput() {
        for (Module module : ModuleManager.all()) {
            if (module.capturesInput()) {
                return true;
            }
        }
        return false;
    }
}
