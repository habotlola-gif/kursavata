package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.SliderSetting;
import dev.hatek.client.render.Privilege;
import dev.hatek.client.render.WorldProjection;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Name tags above players as a dark card (design: nametag.svg): the player's face, the server rank in its own
 * colour and the nick. Card width = 6 + 28 + 10 + rank + 10 + nick + 14 (without a rank: 6 + 28 + 10 + nick + 14).
 * The vanilla name tag of tagged players is hidden.
 */
public final class NameTags extends Module {
    private static NameTags instance;

    private static final int BACKGROUND = 0xFF0D0D10;

    private final BoolSetting privilege = new BoolSetting("Show rank", true);
    private final BoolSetting throughWalls = new BoolSetting("Through walls", true);
    private final SliderSetting scale = new SliderSetting("Scale (%)", 100.0, 40.0, 200.0, 5.0);
    private final SliderSetting range = new SliderSetting("Range (blocks)", 128.0, 16.0, 256.0, 8.0);

    private record Tag(AbstractClientPlayer player, double distance, float x, float y) {}

    public NameTags() {
        super("NameTags", "Name cards with face, rank and nick above players", Category.RENDER);
        instance = this;
        with(this.privilege, this.throughWalls, this.scale, this.range);
    }

    public static NameTags instance() {
        return instance;
    }

    /** True when this entity gets our card instead of the vanilla name tag. */
    public static boolean replacesVanilla(Entity entity) {
        return instance != null && instance.isEnabled() && entity instanceof AbstractClientPlayer
                && entity != Minecraft.getInstance().player;
    }

    /** HUD element: called every frame. */
    public void render(GuiGraphicsExtractor gg, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || mc.player == null || mc.level == null || !WorldProjection.isValid()) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(true);
        double maxRange = this.range.value();
        List<Tag> tags = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof AbstractClientPlayer player) || player == mc.player || !player.isAlive()
                    || player.isSpectator()) {
                continue;
            }
            double distance = mc.player.distanceTo(player);
            if (distance > maxRange || (!this.throughWalls.value() && !mc.player.hasLineOfSight(player))) {
                continue;
            }
            Vec3 anchor = player.getPosition(partial).add(0.0, player.getBbHeight() + 0.35, 0.0);
            float[] screen = WorldProjection.toScreen(anchor);
            if (screen != null) {
                tags.add(new Tag(player, distance, screen[0], screen[1]));
            }
        }
        if (tags.isEmpty()) {
            return;
        }
        tags.sort(Comparator.comparingDouble(Tag::distance).reversed());

        float s = Render2D.screenHeight() / 1080.0f * (float) this.scale.value() / 100.0f;
        HFont rankFont = Fonts.of(Fonts.INTER_BOLD, Math.max(8, Math.round(15.0f * s)));
        HFont nickFont = Fonts.of(Fonts.INTER_SEMIBOLD, Math.max(8, Math.round(15.0f * s)));
        Render2D.begin(gg);
        try {
            for (Tag tag : tags) {
                draw(gg, tag, s, rankFont, nickFont);
            }
        } finally {
            Render2D.end(gg);
        }
    }

    private void draw(GuiGraphicsExtractor gg, Tag tag, float s, HFont rankFont, HFont nickFont) {
        String nick = tag.player().getGameProfile().name();
        Privilege.Info rank = this.privilege.value() ? Privilege.of(tag.player()) : null;

        float nickWidth = nickFont.width(nick);
        float rankWidth = rank != null ? rankFont.width(rank.text()) : 0.0f;
        float width = (6.0f + 28.0f + 10.0f) * s + rankWidth + (rank != null ? 10.0f * s : 0.0f) + nickWidth + 14.0f * s;
        float height = 40.0f * s;
        float x = tag.x() - width / 2.0f;
        float y = tag.y() - height;

        Render2D.round(gg, x, y, width, height, 10.0f * s, BACKGROUND);
        PlayerFaceExtractor.extractRenderState(gg, tag.player().getSkin(),
                Math.round(x + 6.0f * s), Math.round(y + 6.0f * s), Math.max(8, Math.round(28.0f * s)));

        float textX = x + 44.0f * s;
        float baseline = y + 25.5f * s;
        if (rank != null) {
            rankFont.draw(gg, rank.text(), textX, baseline, rank.argb());
            textX += rankWidth + 10.0f * s;
        }
        nickFont.draw(gg, nick, textX, baseline, 0xFFFFFFFF);
    }
}
