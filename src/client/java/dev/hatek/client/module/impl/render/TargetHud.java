package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import dev.hatek.client.render.TargetTracker;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import dev.hatek.client.ui.Style;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * Card with the target's face, name, health and a health bar. Layout follows the design file
 * (276x84 card: face 62x62 at 10,10; name and "Health" text at x=98; bar 166x10 at 98,61).
 */
public final class TargetHud extends Module {
    private static TargetHud instance;

    // design colours
    private static final int BACKGROUND = 0xFF0D0D10;
    private static final int LABEL = 0xFF9D93E6;
    private static final int BAR_TRACK = 0xFF3A3456;
    private static final int BAR_FIXED = 0xFFE8C23A;

    private final SliderSetting scale = new SliderSetting("Scale (%)", 100.0, 50.0, 200.0, 5.0);
    private final SliderSetting posX = new SliderSetting("Position X (%)", 62.0, 0.0, 100.0, 0.1);
    private final SliderSetting posY = new SliderSetting("Position Y (%)", 55.0, 0.0, 100.0, 0.1);
    private final ModeSetting barColor = new ModeSetting("Bar color", 0, "Fixed", "By health");

    private float alpha;
    private LivingEntity shown;
    private float hpShown = -1.0f;
    private long lastFrame = System.nanoTime();
    private boolean editing;
    private boolean dragging;
    private boolean mouseWasDown;
    private double grabX;
    private double grabY;

    public TargetHud() {
        super("TargetHUD", "Card with the target's face, name and health", Category.RENDER);
        instance = this;
        with(this.scale, this.posX, this.posY, this.barColor);
    }

    public static TargetHud instance() {
        return instance;
    }

    /** HUD element: called every frame. */
    public void render(GuiGraphicsExtractor gg, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || mc.player == null || mc.level == null) {
            this.alpha = 0.0f;
            this.shown = null;
            return;
        }
        long now = System.nanoTime();
        float dt = Math.min((now - this.lastFrame) / 1.0e9f, 0.1f);
        this.lastFrame = now;

        // with the chat open the card is shown even without a target and can be dragged
        this.editing = mc.gui.screen() instanceof ChatScreen;
        LivingEntity target = TargetTracker.current();
        if (this.editing && target == null) {
            target = mc.player;
        }
        if (!this.editing) {
            this.dragging = false;
            this.mouseWasDown = false;
        }
        if (target != null) {
            if (target != this.shown) {
                this.hpShown = -1.0f;
            }
            this.shown = target;
        }
        this.alpha += ((target != null ? 1.0f : 0.0f) - this.alpha) * (1.0f - (float) Math.exp(-dt * 10.0f));
        if (this.alpha < 0.01f || this.shown == null) {
            if (this.alpha < 0.01f) {
                this.shown = null;
            }
            return;
        }

        LivingEntity entity = this.shown;
        float maxHp = Math.max(1.0f, entity.getMaxHealth());
        float hp = Math.max(0.0f, entity.getHealth());
        if (this.hpShown < 0.0f) {
            this.hpShown = hp;
        }
        this.hpShown += (hp - this.hpShown) * (1.0f - (float) Math.exp(-dt * 9.0f));
        float frac = Mth.clamp(this.hpShown / maxHp, 0.0f, 1.0f);

        Render2D.begin(gg);
        try {
            draw(gg, entity, hp + entity.getAbsorptionAmount(), frac);
        } finally {
            Render2D.end(gg);
        }
    }

    private void draw(GuiGraphicsExtractor gg, LivingEntity entity, float hpText, float frac) {
        float s = Render2D.screenHeight() / 1080.0f * (float) this.scale.value() / 100.0f;
        float cardW = 276.0f * s;
        float cardH = 84.0f * s;
        float x = (Render2D.screenWidth() - cardW) * (float) this.posX.value() / 100.0f;
        float y = (Render2D.screenHeight() - cardH) * (float) this.posY.value() / 100.0f;

        if (this.editing) {
            handleDrag(x, y, cardW, cardH);
            x = (Render2D.screenWidth() - cardW) * (float) this.posX.value() / 100.0f;
            y = (Render2D.screenHeight() - cardH) * (float) this.posY.value() / 100.0f;
        }

        Render2D.pushAlpha(this.alpha);
        if (this.editing) {
            // frame and hint while the card can be moved
            int frame = Render2D.withAlpha(Style.accent(), this.dragging ? 0.9f : 0.55f);
            Render2D.round(gg, x - 3.0f * s, y - 3.0f * s, cardW + 6.0f * s, cardH + 6.0f * s, 16.0f * s, frame);
            HFont hint = Fonts.of(Fonts.INTER_MEDIUM, Math.max(8, Math.round(12.0f * s)));
            hint.draw(gg, "Drag to move", x, y - 8.0f * s, 0xFFFFFFFF);
        }
        Render2D.round(gg, x, y, cardW, cardH, 13.0f * s, BACKGROUND);

        // face
        int faceX = Math.round(x + 10.0f * s);
        int faceY = Math.round(y + 10.0f * s);
        int faceSize = Math.max(8, Math.round(62.0f * s));
        if (entity instanceof AbstractClientPlayer player) {
            int white = (Mth.clamp(Math.round(this.alpha * 255.0f), 0, 255) << 24) | 0xFFFFFF;
            PlayerFaceExtractor.extractRenderState(gg, player.getSkin(), faceX, faceY, faceSize, white);
        } else {
            genericFace(gg, faceX, faceY, faceSize);
        }

        // texts (font sizes are whole pixels so the glyph atlases are reused)
        HFont nameFont = Fonts.of(Fonts.INTER_BOLD, Math.max(8, Math.round(16.0f * s)));
        HFont infoFont = Fonts.of(Fonts.INTER_SEMIBOLD, Math.max(8, Math.round(15.0f * s)));
        float textX = x + 98.0f * s;
        String name = nameFont.trim(entity.getName().getString(), 166.0f * s);
        nameFont.draw(gg, name, textX, y + 25.5f * s, Render2D.withAlpha(0xFFFFFFFF, 1.0f));
        float labelWidth = infoFont.draw(gg, "Health:", textX, y + 49.5f * s, LABEL);
        infoFont.draw(gg, String.format(Locale.ROOT, "%.1f", hpText), textX + labelWidth + 4.0f * s, y + 49.5f * s, 0xFFFFFFFF);

        // bar
        float barX = x + 98.0f * s;
        float barY = y + 61.0f * s;
        float barW = 166.0f * s;
        float barH = 10.0f * s;
        Render2D.round(gg, barX, barY, barW, barH, 4.0f * s, BAR_TRACK);
        float fillW = Math.max(barH, barW * frac);
        if (frac > 0.001f) {
            Render2D.round(gg, barX, barY, fillW, barH, 4.0f * s, barColorFor(frac));
        }
        Render2D.popAlpha();
    }

    /** Left mouse button drags the card; the position is stored in the Position X/Y settings. */
    private void handleDrag(float x, float y, float cardW, float cardH) {
        Minecraft mc = Minecraft.getInstance();
        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();
        boolean down = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (down && !this.mouseWasDown && Render2D.hovered(mx, my, x, y, cardW, cardH)) {
            this.dragging = true;
            this.grabX = mx - x;
            this.grabY = my - y;
        }
        if (!down) {
            this.dragging = false;
        }
        if (this.dragging) {
            double freeW = Render2D.screenWidth() - cardW;
            double freeH = Render2D.screenHeight() - cardH;
            if (freeW > 1.0) {
                this.posX.value((mx - this.grabX) / freeW * 100.0);
            }
            if (freeH > 1.0) {
                this.posY.value((my - this.grabY) / freeH * 100.0);
            }
        }
        this.mouseWasDown = down;
    }

    private int barColorFor(float frac) {
        if (this.barColor.is("Fixed")) {
            return BAR_FIXED;
        }
        // red -> yellow -> green
        int red = 0xFFE5484D;
        int green = 0xFF46C46B;
        return frac < 0.5f ? Render2D.lerp(red, BAR_FIXED, frac * 2.0f) : Render2D.lerp(BAR_FIXED, green, (frac - 0.5f) * 2.0f);
    }

    /** The pixel face from the design, for entities that have no player skin. */
    private static void genericFace(GuiGraphicsExtractor gg, int x, int y, int size) {
        int[][] cells = {
                {0, 0, 8, 8, 0xC98552}, {0, 0, 8, 2, 0x7B4526}, {0, 2, 1, 1, 0x7B4526}, {7, 2, 1, 1, 0x7B4526},
                {1, 4, 1, 1, 0xFFFFFF}, {2, 4, 1, 1, 0x3A1C10}, {5, 4, 1, 1, 0x3A1C10}, {6, 4, 1, 1, 0xFFFFFF},
                {3, 6, 2, 1, 0x8E4F2C}};
        for (int[] c : cells) {
            int x1 = x + Math.round(c[0] * size / 8.0f);
            int y1 = y + Math.round(c[1] * size / 8.0f);
            int x2 = x + Math.round((c[0] + c[2]) * size / 8.0f);
            int y2 = y + Math.round((c[1] + c[3]) * size / 8.0f);
            Render2D.rect(gg, x1, y1, x2 - x1, y2 - y1, 0xFF000000 | c[4]);
        }
    }
}
