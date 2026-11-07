package dev.hatek.client.module.impl.render.hud;

import dev.hatek.client.module.impl.render.Interface;
import dev.hatek.client.module.setting.PositionSetting;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Base for list-style cards (Potions, Keybinds): a dark rounded card whose height follows the number of rows
 * (55 + 29 * rows design pixels). Rows fade in/out and slide into place, and the card smoothly grows and shrinks.
 * Dragged with the mouse while the chat is open.
 */
public abstract class HudCard<R extends HudCard.Row> {
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

    protected final Interface owner;
    private final String kind;
    private final PositionSetting position;
    private final DragHandle drag = new DragHandle();
    private final List<Entry<R>> entries = new ArrayList<>();
    private float alpha;
    private float animRows;
    private long lastFrame = System.nanoTime();

    protected HudCard(Interface owner, String kind, PositionSetting position) {
        this.owner = owner;
        this.kind = kind;
        this.position = position;
    }

    protected abstract float cardWidth();

    protected abstract float firstBaseline();

    protected abstract List<R> currentRows();

    protected abstract List<R> sampleRows();

    protected abstract void drawHeader(GuiGraphicsExtractor gg, float x, float y, float s);

    protected abstract void drawRow(GuiGraphicsExtractor gg, float x, float baseline, float s, R row);

    public final void render(GuiGraphicsExtractor gg, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!this.owner.shows(this.kind) || mc.player == null || mc.level == null) {
            this.alpha = 0.0f;
            this.entries.clear();
            this.animRows = 0.0f;
            this.drag.reset();
            return;
        }
        long now = System.nanoTime();
        float dt = Math.min((now - this.lastFrame) / 1.0e9f, 0.1f);
        this.lastFrame = now;
        float k = 1.0f - (float) Math.exp(-dt * 12.0f);

        boolean editing = mc.gui.screen() instanceof ChatScreen;
        if (!editing) {
            this.drag.reset();
        }
        List<R> rows = currentRows();
        if (rows.isEmpty() && editing) {
            rows = sampleRows();
        }
        updateEntries(rows, k);

        this.animRows += (rows.size() - this.animRows) * k;
        this.alpha += ((!rows.isEmpty() ? 1.0f : 0.0f) - this.alpha) * (1.0f - (float) Math.exp(-dt * 10.0f));
        if (this.alpha < 0.01f) {
            return;
        }

        Render2D.begin(gg);
        try {
            float s = this.owner.pixelScale();
            float w = cardWidth() * s;
            float h = (55.0f + ROW_PITCH * this.animRows) * s;
            float x = DragHandle.posX(this.position, w);
            float y = DragHandle.posY(this.position, h);
            if (editing) {
                this.drag.update(this.position, x, y, w, h);
                x = DragHandle.posX(this.position, w);
                y = DragHandle.posY(this.position, h);
            }
            Render2D.pushAlpha(this.alpha);
            if (editing) {
                this.drag.drawFrame(gg, x, y, w, h, s);
            }
            Render2D.round(gg, x, y, w, h, 13.0f * s, BACKGROUND);
            drawHeader(gg, x, y, s);

            // rows are clipped to the card so a row sliding out never pokes outside the animated card
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

    protected static HFont title(float s) {
        return Fonts.of(Fonts.INTER_BOLD, Math.max(8, Math.round(16.0f * s)));
    }

    protected static HFont row(float s) {
        return Fonts.of(Fonts.INTER_SEMIBOLD, Math.max(8, Math.round(15.0f * s)));
    }
}
