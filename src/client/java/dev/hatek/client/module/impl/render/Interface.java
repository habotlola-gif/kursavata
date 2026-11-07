package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.impl.render.hud.KeybindsCard;
import dev.hatek.client.module.impl.render.hud.PotionsCard;
import dev.hatek.client.module.impl.render.hud.TargetCard;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.MultiSetting;
import dev.hatek.client.module.setting.PositionSetting;
import dev.hatek.client.module.setting.SliderSetting;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * The on-screen interface: Target HUD, Potions and Keybinds cards in one module. Pick which cards are shown;
 * while the chat is open every shown card can be dragged with the mouse (positions are saved with configs).
 */
public final class Interface extends Module {
    public static final String TARGET = "Target HUD";
    public static final String POTIONS = "Potions";
    public static final String KEYBINDS = "Keybinds";

    private static Interface instance;

    private final MultiSetting elements = new MultiSetting("Elements", List.of(TARGET, POTIONS, KEYBINDS),
            TARGET, POTIONS, KEYBINDS);
    private final SliderSetting scale = new SliderSetting("Scale (%)", 100.0, 50.0, 200.0, 5.0);
    private final ModeSetting barColor = new ModeSetting("Target bar color", 0, "Fixed", "By health");
    private final ModeSetting keybindsShow = new ModeSetting("Keybinds show", 0, "All bound", "Only enabled");

    // positions are hidden settings: saved with configs, not shown in the menu
    private final PositionSetting targetPos = new PositionSetting("Target position", 62.0, 55.0);
    private final PositionSetting potionsPos = new PositionSetting("Potions position", 1.5, 4.0);
    private final PositionSetting keybindsPos = new PositionSetting("Keybinds position", 1.5, 30.0);

    private final TargetCard target = new TargetCard(this, this.targetPos);
    private final PotionsCard potions = new PotionsCard(this, this.potionsPos);
    private final KeybindsCard keybinds = new KeybindsCard(this, this.keybindsPos);

    public Interface() {
        super("Interface", "On-screen cards: target, potions, keybinds (drag them in chat)", Category.RENDER);
        instance = this;
        with(this.elements, this.scale, this.barColor, this.keybindsShow, this.targetPos, this.potionsPos, this.keybindsPos);
    }

    public static Interface instance() {
        return instance;
    }

    public boolean shows(String element) {
        return isEnabled() && this.elements.isSelected(element);
    }

    /** Pixels per design pixel: follows the screen height and the Scale setting. */
    public float pixelScale() {
        return Render2D.screenHeight() / 1080.0f * (float) this.scale.value() / 100.0f;
    }

    public boolean barByHealth() {
        return this.barColor.is("By health");
    }

    public boolean onlyEnabledBinds() {
        return this.keybindsShow.is("Only enabled");
    }

    /** HUD element: called every frame. */
    public void render(GuiGraphicsExtractor gg, DeltaTracker delta) {
        this.target.render(gg, delta);
        this.potions.render(gg, delta);
        this.keybinds.render(gg, delta);
    }
}
