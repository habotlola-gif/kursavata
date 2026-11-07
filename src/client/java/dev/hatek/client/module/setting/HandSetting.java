package dev.hatek.client.module.setting;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/** Offset of one hand (x, y, z in blocks; z positive = closer to the camera). Hidden: saved with configs only. */
public final class HandSetting extends Setting {
    public static final double LIMIT = 1.0;

    private double x;
    private double y;
    private double z;

    public HandSetting(String name) {
        super(name);
    }

    public float x() {
        return (float) this.x;
    }

    public float y() {
        return (float) this.y;
    }

    public float z() {
        return (float) this.z;
    }

    public void move(double dx, double dy) {
        this.x = Mth.clamp(this.x + dx, -LIMIT, LIMIT);
        this.y = Mth.clamp(this.y + dy, -LIMIT, LIMIT);
    }

    public void moveZ(double dz) {
        this.z = Mth.clamp(this.z + dz, -LIMIT, LIMIT);
    }

    public void reset() {
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
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
        return this.x + ";" + this.y + ";" + this.z;
    }

    @Override
    public void deserialize(String raw) {
        String[] parts = raw.split(";");
        if (parts.length == 3) {
            try {
                this.x = Mth.clamp(Double.parseDouble(parts[0]), -LIMIT, LIMIT);
                this.y = Mth.clamp(Double.parseDouble(parts[1]), -LIMIT, LIMIT);
                this.z = Mth.clamp(Double.parseDouble(parts[2]), -LIMIT, LIMIT);
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
