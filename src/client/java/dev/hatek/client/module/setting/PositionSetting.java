package dev.hatek.client.module.setting;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/** A screen position as percentages (0..100). Not shown in the menu; only saved with configs. Moved by dragging in chat. */
public final class PositionSetting extends Setting {
    private double x;
    private double y;

    public PositionSetting(String name, double x, double y) {
        super(name);
        this.x = x;
        this.y = y;
    }

    public double x() {
        return this.x;
    }

    public double y() {
        return this.y;
    }

    public void set(double x, double y) {
        this.x = Mth.clamp(x, 0.0, 100.0);
        this.y = Mth.clamp(y, 0.0, 100.0);
    }

    @Override
    public boolean hidden() {
        return true;
    }

    @Override
    public float bottomOffset() {
        return 0.0f;
    }

    @Override
    public void render(GuiGraphicsExtractor gg, float x, float baseline, double mx, double my) {
    }

    @Override
    public String serialize() {
        return this.x + ";" + this.y;
    }

    @Override
    public void deserialize(String raw) {
        String[] parts = raw.split(";");
        if (parts.length == 2) {
            try {
                set(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
