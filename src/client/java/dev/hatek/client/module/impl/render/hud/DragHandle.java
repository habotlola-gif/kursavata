package dev.hatek.client.module.impl.render.hud;

import dev.hatek.client.module.setting.PositionSetting;
import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

/** Drag-with-the-mouse behaviour shared by the HUD cards; active only while the chat is open. */
public final class DragHandle {
    private static boolean cardHover;
    private static boolean cardDragging;

    /** Call once per HUD frame before the cards are drawn. */
    public static void beginFrame() {
        cardHover = false;
        cardDragging = false;
    }

    /** True when a card is under the mouse or being dragged this frame (other mouse tools must stay out of the way). */
    public static boolean cardsBusy() {
        return cardHover || cardDragging;
    }

    private boolean dragging;
    private boolean mouseWasDown;
    private double grabX;
    private double grabY;

    void reset() {
        this.dragging = false;
        this.mouseWasDown = false;
    }

    boolean isDragging() {
        return this.dragging;
    }

    /** Position of a card of the given size (pixels) from its percentage setting. */
    static float posX(PositionSetting pos, float w) {
        return (Render2D.screenWidth() - w) * (float) pos.x() / 100.0f;
    }

    static float posY(PositionSetting pos, float h) {
        return (Render2D.screenHeight() - h) * (float) pos.y() / 100.0f;
    }

    /** Handles the left mouse button; writes the new position into {@code pos}. */
    void update(PositionSetting pos, float x, float y, float w, float h) {
        Minecraft mc = Minecraft.getInstance();
        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();
        boolean down = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (Render2D.hovered(mx, my, x, y, w, h)) {
            cardHover = true;
        }
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
            double nx = freeW > 1.0 ? (mx - this.grabX) / freeW * 100.0 : pos.x();
            double ny = freeH > 1.0 ? (my - this.grabY) / freeH * 100.0 : pos.y();
            pos.set(nx, ny);
        }
        if (this.dragging) {
            cardDragging = true;
        }
        this.mouseWasDown = down;
    }

    /** Frame around the card and a hint while it can be moved. */
    void drawFrame(GuiGraphicsExtractor gg, float x, float y, float w, float h, float s) {
        int frame = Render2D.withAlpha(Style.accent(), this.dragging ? 0.9f : 0.55f);
        Render2D.round(gg, x - 3.0f * s, y - 3.0f * s, w + 6.0f * s, h + 6.0f * s, 16.0f * s, frame);
        HFont hint = Fonts.of(Fonts.INTER_MEDIUM, Math.max(8, Math.round(12.0f * s)));
        hint.draw(gg, "Drag to move", x, y - 8.0f * s, 0xFFFFFFFF);
    }
}
