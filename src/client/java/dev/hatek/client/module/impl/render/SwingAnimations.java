package dev.hatek.client.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.util.Mth;
import org.joml.Quaternionfc;

/** First-person swing animations and swing speed. */
public final class SwingAnimations extends Module {
    private static SwingAnimations instance;

    private final ModeSetting mode = new ModeSetting("Animation", 4, "Vanilla", "Smooth", "Block 1.7", "Spin", "Chop", "Stab", "Slam", "Swipe",
            "Whip", "Flip", "Roll", "Wave", "Pendulum", "Punch", "Drop", "Shake", "Cross", "Corkscrew", "Hammer",
            "Fan");
    private final SliderSetting speed = new SliderSetting("Swing speed (%)", 100.0, 50.0, 200.0, 5.0);
    private final BoolSetting noEquip = new BoolSetting("No equip dip", false);

    public SwingAnimations() {
        super("SwingAnimations", "Custom first-person swing animations and swing speed", Category.RENDER);
        instance = this;
        with(this.mode, this.speed, this.noEquip);
    }

    public static SwingAnimations instance() {
        return instance;
    }

    public int adjustDuration(int duration) {
        if (!isEnabled()) {
            return duration;
        }
        return Math.max(1, Math.round(duration * 100.0f / (float) this.speed.value()));
    }

    /** True when the vanilla swing is replaced (any mode but Vanilla). */
    public boolean replacesSwing() {
        return isEnabled() && !this.mode.is("Vanilla");
    }

    public boolean skipEquipDip() {
        return isEnabled() && this.noEquip.value();
    }

    public void applySwing(PoseStack pose, float progress, int side) {
        float arc = Mth.sin(progress * (float) Math.PI);
        float snap = Mth.sin(Mth.sqrt(progress) * (float) Math.PI);
        float eased = progress * progress * (3.0f - 2.0f * progress);
        switch (this.mode.value()) {
            case "Smooth" -> {
                pose.translate(side * -0.12f * arc, 0.04f * arc, -0.12f * arc);
                pose.mulPose((Quaternionfc) Axis.YP.rotationDegrees(side * -12.0f * arc));
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(-55.0f * arc));
            }
            case "Block 1.7" -> {
                pose.translate(side * -0.14f * snap, 0.08f * snap, 0.14f * snap);
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(-102.25f * snap));
                pose.mulPose((Quaternionfc) Axis.YP.rotationDegrees(side * 13.365f * snap));
                pose.mulPose((Quaternionfc) Axis.ZP.rotationDegrees(side * 78.05f * snap));
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(-25.0f * arc));
            }
            case "Spin" -> {
                pose.translate(side * -0.1f * arc, 0.05f * arc, -0.1f * arc);
                pose.mulPose((Quaternionfc) Axis.YP.rotationDegrees(side * 360.0f * eased));
            }
            case "Chop" -> {
                float angle;
                if (progress < 0.25f) {
                    float t = progress / 0.25f;
                    angle = 35.0f * t * (2.0f - t);
                } else if (progress < 0.55f) {
                    float t = (progress - 0.25f) / 0.3f;
                    angle = Mth.lerp(t * t, 35.0f, -60.0f);
                } else {
                    float t = (progress - 0.55f) / 0.45f;
                    angle = -60.0f * (1.0f - t * t * (3.0f - 2.0f * t));
                }
                pose.translate(side * -0.06f * arc, 0.08f * arc, -0.16f * arc);
                pose.mulPose((Quaternionfc) Axis.ZP.rotationDegrees(side * 12.0f * arc));
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(angle));
            }
            case "Stab" -> {
                pose.translate(side * -0.05f * arc, 0.02f * arc, -0.45f * arc);
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(-10.0f * arc));
            }
            case "Slam" -> {
                // wind up above the head, then hit down hard
                float wind = smooth(0.0f, 0.3f, progress) * (1.0f - smooth(0.3f, 0.5f, progress));
                float hit = smooth(0.3f, 0.5f, progress) * (1.0f - smooth(0.55f, 1.0f, progress));
                pose.translate(side * -0.04f * hit, 0.12f * wind - 0.05f * hit, -0.22f * hit);
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(70.0f * wind - 95.0f * hit));
            }
            case "Swipe" -> {
                // a diagonal slash from the upper outside to the lower inside
                float sweep = eased * 2.0f - 1.0f;
                pose.translate(side * (0.18f * -sweep) * arc, 0.12f * -sweep * arc, -0.15f * arc);
                pose.mulPose((Quaternionfc) Axis.ZP.rotationDegrees(side * (-50.0f * sweep) * arc));
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(-35.0f * arc));
                pose.mulPose((Quaternionfc) Axis.YP.rotationDegrees(side * 25.0f * sweep * arc));
            }
            case "Whip" -> {
                pose.translate(side * -0.3f * snap, 0.03f * arc, -0.1f * arc);
                pose.mulPose((Quaternionfc) Axis.YP.rotationDegrees(side * -75.0f * snap));
                pose.mulPose((Quaternionfc) Axis.ZP.rotationDegrees(side * 22.0f * arc));
            }
            case "Flip" -> {
                pose.translate(0.0f, 0.1f * arc, -0.2f * arc);
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(-360.0f * eased));
            }
            case "Roll" -> {
                pose.translate(side * -0.06f * arc, 0.04f * arc, -0.1f * arc);
                pose.mulPose((Quaternionfc) Axis.ZP.rotationDegrees(side * 360.0f * eased));
            }
            case "Wave" -> {
                float wave = Mth.sin(progress * (float) Math.PI * 4.0f);
                pose.translate(side * -0.08f * wave * arc, 0.04f * arc, -0.1f * arc);
                pose.mulPose((Quaternionfc) Axis.ZP.rotationDegrees(side * 28.0f * wave * arc));
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(-30.0f * arc));
            }
            case "Pendulum" -> {
                float swing = Mth.sin(progress * (float) Math.PI * 2.0f);
                pose.translate(side * 0.2f * swing * arc, -0.05f * arc, -0.12f * arc);
                pose.mulPose((Quaternionfc) Axis.YP.rotationDegrees(side * -45.0f * swing * arc));
                pose.mulPose((Quaternionfc) Axis.ZP.rotationDegrees(side * 18.0f * swing * arc));
            }
            case "Punch" -> {
                pose.translate(side * -0.02f * snap, 0.04f * snap, -0.6f * snap);
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(-6.0f * snap));
            }
            case "Drop" -> {
                pose.translate(0.0f, -0.4f * arc, 0.05f * arc);
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(28.0f * arc));
            }
            case "Shake" -> {
                float shake = Mth.sin(progress * (float) Math.PI * 12.0f);
                pose.translate(side * 0.03f * shake * arc, 0.02f * shake * arc, -0.12f * arc);
                pose.mulPose((Quaternionfc) Axis.ZP.rotationDegrees(side * 10.0f * shake * arc));
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(-20.0f * arc));
            }
            case "Cross" -> {
                pose.translate(side * 0.5f * snap, 0.05f * arc, -0.15f * arc);
                pose.mulPose((Quaternionfc) Axis.YP.rotationDegrees(side * 60.0f * snap));
                pose.mulPose((Quaternionfc) Axis.ZP.rotationDegrees(side * -18.0f * snap));
            }
            case "Corkscrew" -> {
                pose.translate(side * -0.04f * arc, 0.02f * arc, -0.42f * arc);
                pose.mulPose((Quaternionfc) Axis.ZP.rotationDegrees(side * 540.0f * eased));
            }
            case "Hammer" -> {
                // lifts the hand straight up and drops it onto the target
                float lift = smooth(0.0f, 0.4f, progress) * (1.0f - smooth(0.4f, 0.55f, progress));
                float fall = smooth(0.4f, 0.55f, progress) * (1.0f - smooth(0.6f, 1.0f, progress));
                pose.translate(0.0f, 0.3f * lift - 0.12f * fall, -0.1f * lift - 0.28f * fall);
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(40.0f * lift - 80.0f * fall));
            }
            case "Fan" -> {
                // the hand opens like a fan: a wide arc left to right and back
                float arcAngle = Mth.sin(progress * (float) Math.PI * 2.0f);
                pose.translate(side * -0.1f * arcAngle * arc, 0.06f * arc, -0.2f * arc);
                pose.mulPose((Quaternionfc) Axis.YP.rotationDegrees(side * 55.0f * arcAngle * arc));
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(-40.0f * arc));
            }
            default -> {
            }
        }
    }

    private static float smooth(float from, float to, float value) {
        float t = Mth.clamp((value - from) / (to - from), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
