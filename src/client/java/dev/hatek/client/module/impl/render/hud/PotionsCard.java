package dev.hatek.client.module.impl.render.hud;

import dev.hatek.client.module.impl.render.Interface;
import dev.hatek.client.module.setting.PositionSetting;

import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Card listing the active potion effects with name, level and remaining time (design: potions-hud.svg). */
public final class PotionsCard extends HudCard<PotionsCard.Effect> {
    private static final int MAX_ROWS = 8;
    private static final int ACCENT = 0xFFB3A8F0;
    private static final int ACCENT_LIGHT = 0xFFE4DCFF;
    private static final int LEVEL = 0xFFA5A3B5;

    public record Effect(String name, String level, String time) implements HudCard.Row {
        @Override
        public String id() {
            return this.name;
        }
    }

    public PotionsCard(Interface owner, PositionSetting position) {
        super(owner, Interface.POTIONS, position);
    }

    @Override
    protected float cardWidth() {
        return 230.0f;
    }

    @Override
    protected float firstBaseline() {
        return 62.5f;
    }

    @Override
    protected List<Effect> currentRows() {
        List<Effect> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return out;
        }
        List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
        effects.sort(Comparator.comparingInt(MobEffectInstance::getDuration).reversed());
        for (MobEffectInstance effect : effects) {
            String name = effect.getEffect().value().getDisplayName().getString();
            String level = effect.getAmplifier() >= 1 ? Integer.toString(effect.getAmplifier() + 1) : "";
            out.add(new Effect(name, level, time(effect)));
            if (out.size() >= MAX_ROWS) {
                break;
            }
        }
        return out;
    }

    @Override
    protected List<Effect> sampleRows() {
        return List.of(new Effect("Weakness", "", "00:28"), new Effect("Night Vision", "2", "8333:19"),
                new Effect("Absorption", "", "01:45"));
    }

    private static String time(MobEffectInstance effect) {
        if (effect.isInfiniteDuration()) {
            return "--:--";
        }
        int seconds = Math.max(0, effect.getDuration() / 20);
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    protected void drawHeader(GuiGraphicsExtractor gg, float x, float y, float s) {
        title(s).draw(gg, "Potions", x + 14.0f * s, y + 28.5f * s, 0xFFFFFFFF);
        // the small potion bottle, tilted 40 degrees
        gg.pose().pushMatrix();
        gg.pose().translate(x + 198.0f * s + 9.0f * s, y + 14.0f * s + 9.0f * s);
        gg.pose().rotate((float) Math.toRadians(40.0));
        gg.pose().translate(-9.0f * s, -9.0f * s);
        Render2D.round(gg, 6.0f * s, 1.0f * s, 6.0f * s, 2.0f * s, 1.0f * s, ACCENT);
        Render2D.rect(gg, 6.5f * s, 3.5f * s, 5.0f * s, 7.0f * s, ACCENT);
        Render2D.round(gg, 6.5f * s, 8.0f * s, 5.0f * s, 5.0f * s, 2.5f * s, ACCENT);
        Render2D.round(gg, 7.5f * s, 9.0f * s, 3.0f * s, 4.0f * s, 1.5f * s, ACCENT_LIGHT);
        gg.pose().popMatrix();
    }

    @Override
    protected void drawRow(GuiGraphicsExtractor gg, float x, float baseline, float s, Effect r) {
        HFont nameFont = row(s);
        HFont levelFont = Fonts.of(Fonts.INTER_MEDIUM, Math.max(8, Math.round(15.0f * s)));
        float timeWidth = nameFont.width(r.time());
        float maxName = 230.0f * s - 28.0f * s - timeWidth - 12.0f * s;
        String name = nameFont.trim(r.name(), maxName);
        float nameWidth = nameFont.draw(gg, name, x + 14.0f * s, baseline, 0xFFFFFFFF);
        if (!r.level().isEmpty()) {
            levelFont.draw(gg, r.level(), x + 14.0f * s + nameWidth + 5.0f * s, baseline, LEVEL);
        }
        nameFont.drawRight(gg, r.time(), x + 216.0f * s, baseline, ACCENT);
    }
}
