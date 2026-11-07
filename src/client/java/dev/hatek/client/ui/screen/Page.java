package dev.hatek.client.ui.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public abstract class Page {
    public abstract float contentHeight();

    public abstract void render(GuiGraphicsExtractor gg, float x, float y, float scroll,
                                double mx, double my);

    public boolean mouseClicked(float x, float y, float scroll, double mx, double my, int button) {
        return false;
    }

    public void mouseDragged(float x, float y, float scroll, double mx, double my) {
    }

    public void mouseReleased() {
    }

    public boolean keyPressed(int key) {
        return false;
    }

    public boolean charTyped(char c) {
        return false;
    }

    public boolean capturesInput() {
        return false;
    }

    public void onShow() {
    }
}
