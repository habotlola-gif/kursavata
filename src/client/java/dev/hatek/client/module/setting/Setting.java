package dev.hatek.client.module.setting;

import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Fonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public abstract class Setting {
    private final String name;

    protected Setting(String name) {
        this.name = name;
    }

    public String name() {
        return this.name;
    }

    private java.util.function.BooleanSupplier visibleWhen;

    /** Shows this setting only while the condition holds (it stays saved with configs either way). */
    public Setting visibleIf(java.util.function.BooleanSupplier condition) {
        this.visibleWhen = condition;
        return this;
    }

    /** Hidden settings are saved with configs but take no space in the menu. */
    public boolean hidden() {
        return this.visibleWhen != null && !this.visibleWhen.getAsBoolean();
    }

    public abstract float bottomOffset();

    public abstract void render(GuiGraphicsExtractor gg, float x, float baseline, double mx, double my);

    public boolean mouseClicked(float x, float baseline, double mx, double my, int button) {
        return false;
    }

    public void mouseReleased() {
    }

    public void mouseDragged(float x, float baseline, double mx, double my) {
    }

    public boolean capturesInput() {
        return false;
    }

    public boolean keyPressed(int key) {
        return false;
    }

    public boolean charTyped(char c) {
        return false;
    }

    public void closePopups() {
    }

    public String serialize() {
        return null;
    }

    public void deserialize(String raw) {
    }

    protected void drawLabel(GuiGraphicsExtractor gg, float x, float baseline) {
        Fonts.label().draw(gg, this.name, x, baseline, Style.WHITE);
    }

    protected static float controlTop(float baseline) {
        return baseline + Style.LABEL_TO_CONTROL;
    }
}
