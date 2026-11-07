package dev.hatek.client.module.impl.render.hud;

import dev.hatek.client.module.impl.render.Interface;
import dev.hatek.client.module.setting.PositionSetting;
import dev.hatek.client.render.TargetTracker;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * Card with the target's face, name, health and a health bar. Layout follows the design file
 * (276x84 card: face 62x62 at 10,10; name and "Health" text at x=98; bar 166x10 at 98,61).
 */
public final class TargetCard {
    private static final int BACKGROUND = 0xFF0D0D10;
    private static final int LABEL = 0xFF9D93E6;
    private static final int BAR_TRACK = 0xFF3A3456;
    private static final int BAR_FIXED = 0xFFE8C23A;

    private final Interface owner;
    private final PositionSetting position;
    private final DragHandle drag = new DragHandle();

    private float alpha;
    private LivingEntity shown;
    private float hpShown = -1.0f;
    private long lastFrame = System.nanoTime();

    public TargetCard(Interface owner, PositionSetting position) {
        this.owner = owner;
        this.position = position;
    }

    public void render(GuiGraphicsExtractor gg, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!this.owner.shows(Interface.TARGET) || mc.player == null || mc.level == null) {
            this.alpha = 0.0f;
            this.shown = null;
            this.drag.reset();
            return;
        }
        long now = System.nanoTime();
        float dt = Math.min((now - this.lastFrame) / 1.0e9f, 0.1f);
        this.lastFrame = now;

        // with the chat open the card is shown even without a target and can be dragged
        boolean editing = mc.gui.screen() instanceof ChatScreen;
        LivingEntity target = TargetTracker.current();
        if (editing && target == null) {
            target = mc.player;
        }
        if (!editing) {
            this.drag.reset();
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
            draw(gg, entity, hp + entity.getAbsorptionAmount(), frac, editing);
        } finally {
            Render2D.end(gg);
        }
    }

    private void draw(GuiGraphicsExtractor gg, LivingEntity entity, float hpText, float frac, boolean editing) {
        float s = this.owner.pixelScale();
        float cardW = 276.0f * s;
        float cardH = 84.0f * s;
        float x = DragHandle.posX(this.position, cardW);
        float y = DragHandle.posY(this.position, cardH);
        if (editing) {
            this.drag.update(this.position, x, y, cardW, cardH);
            x = DragHandle.posX(this.position, cardW);
            y = DragHandle.posY(this.position, cardH);
        }

        Render2D.pushAlpha(this.alpha);
        if (editing) {
            this.drag.drawFrame(gg, x, y, cardW, cardH, s);
        }
        Render2D.round(gg, x, y, cardW, cardH, 13.0f * s, BACKGROUND);

        int faceX = Math.round(x + 10.0f * s);
        int faceY = Math.round(y + 10.0f * s);
        int faceSize = Math.max(8, Math.round(62.0f * s));
        if (entity instanceof AbstractClientPlayer player) {
            int white = (Mth.clamp(Math.round(this.alpha * 255.0f), 0, 255) << 24) | 0xFFFFFF;
            PlayerFaceExtractor.extractRenderState(gg, player.getSkin(), faceX, faceY, faceSize, white);
        } else {
            genericFace(gg, faceX, faceY, faceSize);
        }

        HFont nameFont = Fonts.of(Fonts.INTER_BOLD, Math.max(8, Math.round(16.0f * s)));
        HFont infoFont = Fonts.of(Fonts.INTER_SEMIBOLD, Math.max(8, Math.round(15.0f * s)));
        float textX = x + 98.0f * s;
        String name = nameFont.trim(entity.getName().getString(), 166.0f * s);
        nameFont.draw(gg, name, textX, y + 25.5f * s, 0xFFFFFFFF);
        float labelWidth = infoFont.draw(gg, "Health:", textX, y + 49.5f * s, LABEL);
        infoFont.draw(gg, String.format(Locale.ROOT, "%.1f", hpText), textX + labelWidth + 4.0f * s, y + 49.5f * s, 0xFFFFFFFF);

        float barX = x + 98.0f * s;
        float barY = y + 61.0f * s;
        float barW = 166.0f * s;
        float barH = 10.0f * s;
        Render2D.round(gg, barX, barY, barW, barH, 4.0f * s, BAR_TRACK);
        if (frac > 0.001f) {
            Render2D.round(gg, barX, barY, Math.max(barH, barW * frac), barH, 4.0f * s, barColorFor(frac));
        }
        Render2D.popAlpha();
    }

    private int barColorFor(float frac) {
        if (!this.owner.barByHealth()) {
            return BAR_FIXED;
        }
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
