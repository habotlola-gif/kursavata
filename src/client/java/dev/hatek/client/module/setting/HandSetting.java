package dev.hatek.client.module.setting;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/** Offset of one hand (x, y, z in blocks; z positive = closer to the camera). Hidden: saved with configs only. */
public final class HandSetting extends Setting {
    public static final double LIMIT = 1.0;

    public static final double ROT_LIMIT = 180.0;

    private double x;
    private double y;
    private double z;
    private double rotX;   // degrees: pitch (tilts the hand up / down)
    private double rotY;   // degrees: yaw (turns the hand left / right)
    private double rotZ;   // degrees: roll

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

    public float rotX() {
        return (float) this.rotX;
    }

    public float rotY() {
        return (float) this.rotY;
    }

    public float rotZ() {
        return (float) this.rotZ;
    }

    public boolean hasRotation() {
        return this.rotX != 0.0 || this.rotY != 0.0 || this.rotZ != 0.0;
    }

    /** Turns the hand: dYaw about the vertical axis, dPitch about the sideways axis (degrees). */
    public void rotate(double dYaw, double dPitch) {
        this.rotY = Mth.clamp(this.rotY + dYaw, -ROT_LIMIT, ROT_LIMIT);
        this.rotX = Mth.clamp(this.rotX + dPitch, -ROT_LIMIT, ROT_LIMIT);
    }

    public void roll(double dRoll) {
        this.rotZ = Mth.clamp(this.rotZ + dRoll, -ROT_LIMIT, ROT_LIMIT);
    }

    public void moveZ(double dz) {
        this.z = Mth.clamp(this.z + dz, -LIMIT, LIMIT);
    }

    public void reset() {
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
        this.rotX = 0.0;
        this.rotY = 0.0;
        this.rotZ = 0.0;
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
        return this.x + ";" + this.y + ";" + this.z + ";" + this.rotX + ";" + this.rotY + ";" + this.rotZ;
    }

    @Override
    public void deserialize(String raw) {
        String[] parts = raw.split(";");
        if (parts.length == 3 || parts.length == 6) {
            try {
                this.x = Mth.clamp(Double.parseDouble(parts[0]), -LIMIT, LIMIT);
                this.y = Mth.clamp(Double.parseDouble(parts[1]), -LIMIT, LIMIT);
                this.z = Mth.clamp(Double.parseDouble(parts[2]), -LIMIT, LIMIT);
                if (parts.length == 6) {
                    this.rotX = Mth.clamp(Double.parseDouble(parts[3]), -ROT_LIMIT, ROT_LIMIT);
                    this.rotY = Mth.clamp(Double.parseDouble(parts[4]), -ROT_LIMIT, ROT_LIMIT);
                    this.rotZ = Mth.clamp(Double.parseDouble(parts[5]), -ROT_LIMIT, ROT_LIMIT);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
