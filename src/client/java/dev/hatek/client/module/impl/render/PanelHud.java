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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Base for list-style HUD cards (Potions, Keybinds): a dark rounded card whose height follows the number of rows
 * (55 + 29 * rows design pixels). Rows fade in/out and slide into place, and the card smoothly grows and shrinks.
 * The card scales with the screen height and can be dragged with the mouse while the chat is open
 * (position kept in the Position X/Y settings).
 */
public abstract class PanelHud<R extends PanelHud.Row> extends Module {
    protected static final int BACKGROUND = 0xFF0D0D10;
    private static final float ROW_PITCH = 29.0f;

    /** A row of a card; {@link #id()} identifies it between frames so it can be animated. */
    public interface Row {
        String id();
    }

    private static final class Entry<R> {
        final String id;
        R data;
        float presence;
        float pos;
        boolean present;

        Entry(String id, R data, float pos) {
            this.id = id;
            this.data = data;
            this.pos = pos;
        }
    }

    protected final SliderSetting scale = new SliderSetting("Scale (%)", 100.0, 50.0, 200.0, 5.0);
    protected final SliderSetting posX;
    protected final SliderSetting posY;

    private final List<Entry<R>> entries = new ArrayList<>();
    private float alpha;
    private float animRows;
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

    /** Design y of the first row's text baseline. */
    protected abstract float firstBaseline();

    /** Rows to show right now, in order. */
    protected abstract List<R> currentRows();

    /** Sample rows shown while the chat is open and there is nothing real to show. */
    protected abstract List<R> sampleRows();

    protected abstract void drawHeader(GuiGraphicsExtractor gg, float x, float y, float s);

    protected abstract void drawRow(GuiGraphicsExtractor gg, float x, float baseline, float s, R row);

    public final void render(GuiGraphicsExtractor gg, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || mc.player == null || mc.level == null) {
            this.alpha = 0.0f;
            this.entries.clear();
            this.animRows = 0.0f;
            return;
        }
        long now = System.nanoTime();
        float dt = Math.min((now - this.lastFrame) / 1.0e9f, 0.1f);
        this.lastFrame = now;
        float k = 1.0f - (float) Math.exp(-dt * 12.0f);

        this.editing = mc.gui.screen() instanceof ChatScreen;
        if (!this.editing) {
            this.dragging = false;
            this.mouseWasDown = false;
        }
        List<R> rows = currentRows();
        if (rows.isEmpty() && this.editing) {
            rows = sampleRows();
        }
        updateEntries(rows, k);

        this.animRows += (rows.size() - this.animRows) * k;
        boolean visible = !rows.isEmpty();
        this.alpha += ((visible ? 1.0f : 0.0f) - this.alpha) * (1.0f - (float) Math.exp(-dt * 10.0f));
        if (this.alpha < 0.01f) {
            return;
        }

        Render2D.begin(gg);
        try {
            float s = Render2D.screenHeight() / 1080.0f * (float) this.scale.value() / 100.0f;
            float w = cardWidth() * s;
            float h = (55.0f + ROW_PITCH * this.animRows) * s;
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
            drawHeader(gg, x, y, s);

            // rows are clipped to the card so a row that is sliding out never pokes outside the animated card
            Render2D.pushClip(gg, x, y + 44.0f * s, w, Math.max(0.0f, h - 44.0f * s));
            for (Entry<R> entry : this.entries) {
                if (entry.presence < 0.02f) {
                    continue;
                }
                Render2D.pushAlpha(entry.presence);
                drawRow(gg, x, y + (firstBaseline() + ROW_PITCH * entry.pos) * s, s, entry.data);
                Render2D.popAlpha();
            }
            Render2D.popClip(gg);
            Render2D.popAlpha();
        } finally {
            Render2D.end(gg);
        }
    }

    /** Matches rows to entries by id: new rows fade in, missing rows fade out, the rest slide to their slot. */
    private void updateEntries(List<R> rows, float k) {
        for (Entry<R> entry : this.entries) {
            entry.present = false;
        }
        for (int i = 0; i < rows.size(); i++) {
            R row = rows.get(i);
            Entry<R> found = null;
            for (Entry<R> entry : this.entries) {
                if (entry.id.equals(row.id())) {
                    found = entry;
                    break;
                }
            }
            if (found == null) {
                found = new Entry<>(row.id(), row, i);
                this.entries.add(found);
            }
            found.data = row;
            found.present = true;
            found.pos += (i - found.pos) * k;
            found.presence += (1.0f - found.presence) * k;
        }
        Iterator<Entry<R>> it = this.entries.iterator();
        while (it.hasNext()) {
            Entry<R> entry = it.next();
            if (!entry.present) {
                entry.presence += (0.0f - entry.presence) * k;
                if (entry.presence < 0.02f) {
                    it.remove();
                }
            }
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
