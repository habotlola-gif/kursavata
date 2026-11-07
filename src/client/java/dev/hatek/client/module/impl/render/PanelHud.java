package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.SliderSetting;
import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;

/**
 * Base for list-style HUD cards (Potions, Keybinds): a dark rounded card whose height grows with the number of
 * rows (55 + 29 * rows design pixels). It fades in only when there is something to show, scales with the screen
 * height, and can be dragged with the mouse while the chat is open (position kept in the Position X/Y settings).
 */
public abstract class PanelHud extends Module {
    protected static final int BACKGROUND = 0xFF0D0D10;

    protected final SliderSetting scale = new SliderSetting("Scale (%)", 100.0, 50.0, 200.0, 5.0);
    protected final SliderSetting posX;
    protected final SliderSetting posY;

    private float alpha;
    private int shownRows;
    private boolean editing;
    private boolean dragging;
    private boolean mouseWasDown;
    private double grabX;
    private double grabY;
    private long lastFrame = System.nanoTime();

    protected PanelHud(String name, String description, double defaultX, double defaultY) {
        super(name, description, Category.RENDER);
        this.posX = new SliderSetting("Position X (%)", defaultX, 0.0, 100.0, 0.1);
        this.posY = new SliderSetting("Position Y (%)", defaultY, 0.0, 100.0, 0.1);
        with(this.scale, this.posX, this.posY);
    }

    /** Card width in design pixels. */
    protected abstract float cardWidth();

    /** Rows with real content right now (0 hides the card unless the chat is open). */
    protected abstract int rowCount();

    /** Draw the card contents. {@code preview} is true when there is no real data and sample rows should be shown. */
    protected abstract void drawContent(GuiGraphicsExtractor gg, float x, float y, float s, int rows, boolean preview);

    /** Sample rows shown while the chat is open and there is no real content. */
    protected int previewRows() {
        return 3;
    }

    protected static float designHeight(int rows) {
        return 55.0f + 29.0f * rows;
    }

    public final void render(GuiGraphicsExtractor gg, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || mc.player == null || mc.level == null) {
            this.alpha = 0.0f;
            return;
        }
        long now = System.nanoTime();
        float dt = Math.min((now - this.lastFrame) / 1.0e9f, 0.1f);
        this.lastFrame = now;

        this.editing = mc.gui.screen() instanceof ChatScreen;
        if (!this.editing) {
            this.dragging = false;
            this.mouseWasDown = false;
        }
        int rows = rowCount();
        boolean preview = rows == 0 && this.editing;
        if (preview) {
            rows = previewRows();
        }
        boolean visible = rows > 0;
        if (visible) {
            this.shownRows = rows;
        }
        this.alpha += ((visible ? 1.0f : 0.0f) - this.alpha) * (1.0f - (float) Math.exp(-dt * 10.0f));
        if (this.alpha < 0.01f) {
            return;
        }
        int drawRows = visible ? rows : this.shownRows;

        Render2D.begin(gg);
        try {
            float s = Render2D.screenHeight() / 1080.0f * (float) this.scale.value() / 100.0f;
            float w = cardWidth() * s;
            float h = designHeight(drawRows) * s;
            float x = (Render2D.screenWidth() - w) * (float) this.posX.value() / 100.0f;
            float y = (Render2D.screenHeight() - h) * (float) this.posY.value() / 100.0f;
            if (this.editing) {
                handleDrag(x, y, w, h);
                x = (Render2D.screenWidth() - w) * (float) this.posX.value() / 100.0f;
                y = (Render2D.screenHeight() - h) * (float) this.posY.value() / 100.0f;
            }
            Render2D.pushAlpha(this.alpha);
            if (this.editing) {
                int frame = Render2D.withAlpha(Style.accent(), this.dragging ? 0.9f : 0.55f);
                Render2D.round(gg, x - 3.0f * s, y - 3.0f * s, w + 6.0f * s, h + 6.0f * s, 16.0f * s, frame);
                HFont hint = Fonts.of(Fonts.INTER_MEDIUM, Math.max(8, Math.round(12.0f * s)));
                hint.draw(gg, "Drag to move", x, y - 8.0f * s, 0xFFFFFFFF);
            }
            Render2D.round(gg, x, y, w, h, 13.0f * s, BACKGROUND);
            drawContent(gg, x, y, s, drawRows, preview);
            Render2D.popAlpha();
        } finally {
            Render2D.end(gg);
        }
    }

    private void handleDrag(float x, float y, float w, float h) {
        Minecraft mc = Minecraft.getInstance();
        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();
        boolean down = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (down && !this.mouseWasDown && Render2D.hovered(mx, my, x, y, w, h)) {
            this.dragging = true;
            this.grabX = mx - x;
            this.grabY = my - y;
        }
        if (!down) {
            this.dragging = false;
        }
        if (this.dragging) {
            double freeW = Render2D.screenWidth() - w;
            double freeH = Render2D.screenHeight() - h;
            if (freeW > 1.0) {
                this.posX.value((mx - this.grabX) / freeW * 100.0);
            }
            if (freeH > 1.0) {
                this.posY.value((my - this.grabY) / freeH * 100.0);
            }
        }
        this.mouseWasDown = down;
    }

    protected static HFont title(float s) {
        return Fonts.of(Fonts.INTER_BOLD, Math.max(8, Math.round(16.0f * s)));
    }

    protected static HFont row(float s) {
        return Fonts.of(Fonts.INTER_SEMIBOLD, Math.max(8, Math.round(15.0f * s)));
    }
}
