package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.ColorSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.MultiSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/** Highlights entities through walls with an animated gradient fill, outline and glow (custom shader). */
public final class ShaderEsp extends Module {
    public static final String PLAYERS = "Players";
    public static final String MONSTERS = "Monsters";
    public static final String ANIMALS = "Animals";
    public static final String ITEMS = "Items";

    private static ShaderEsp instance;

    private final MultiSetting targets = new MultiSetting("Targets",
            List.of(PLAYERS, MONSTERS, ANIMALS, ITEMS), PLAYERS, MONSTERS);
    private final ModeSetting colorMode = new ModeSetting("Color", 0, "Gradient", "Team color");
    private final ColorSetting colorA = new ColorSetting("Color 1", 0xFF7C4DFF);
    private final ColorSetting colorB = new ColorSetting("Color 2", 0xFF18D2E8);
    private final SliderSetting fill = new SliderSetting("Fill (%)", 25.0, 0.0, 100.0, 5.0);
    private final SliderSetting glow = new SliderSetting("Glow (px)", 10.0, 0.0, 24.0, 1.0);
    private final SliderSetting speed = new SliderSetting("Speed", 1.0, 0.0, 3.0, 0.1);

    public ShaderEsp() {
        super("ShaderESP", "Highlights entities through walls", Category.RENDER);
        instance = this;
        with(this.targets, this.colorMode, this.colorA, this.colorB, this.fill, this.glow, this.speed);
    }

    public static ShaderEsp instance() {
        return instance;
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    public int colorModeIndex() {
        return this.colorMode.is("Gradient") ? 0 : 1;
    }

    public int colorA() {
        return this.colorA.value();
    }

    public int colorB() {
        return this.colorB.value();
    }

    public float fill() {
        return (float) this.fill.value() / 100.0f;
    }

    public float glowPx() {
        return (float) this.glow.value();
    }

    public float speed() {
        return (float) this.speed.value();
    }

    public boolean shouldHighlight(Entity entity) {
        if (!isEnabled() || entity instanceof LocalPlayer) {
            return false;
        }
        if (entity instanceof Player player) {
            return this.targets.isSelected(PLAYERS) && !player.isSpectator();
        }
        if (entity instanceof ItemEntity) {
            return this.targets.isSelected(ITEMS);
        }
        if (!(entity instanceof LivingEntity) || entity instanceof ArmorStand) {
            return false;
        }
        return entity.getType().getCategory() == MobCategory.MONSTER
                ? this.targets.isSelected(MONSTERS) : this.targets.isSelected(ANIMALS);
    }
}
