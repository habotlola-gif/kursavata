package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.MultiSetting;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
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

    public static final String LEFT_HAND = "Left hand";
    public static final String RIGHT_HAND = "Right hand";
    public static final String ARMOR = "Armor";

    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final MultiSetting items = new MultiSetting("Items above tag", List.of(LEFT_HAND, RIGHT_HAND, ARMOR),
            LEFT_HAND, RIGHT_HAND, ARMOR);
    public static final String INFO_HEALTH = "Health";
    public static final String INFO_SCORE = "Server score (below name)";
    public static final String INFO_DISTANCE = "Distance";
    public static final String INFO_PING = "Ping";

    private final MultiSetting info = new MultiSetting("Info below the nick",
            List.of(INFO_HEALTH, INFO_SCORE, INFO_DISTANCE, INFO_PING),
            INFO_HEALTH, INFO_SCORE, INFO_DISTANCE, INFO_PING);
    private final BoolSetting privilege = new BoolSetting("Show rank", true);
    private final BoolSetting throughWalls = new BoolSetting("Through walls", true);
    private final ModeSetting style = new ModeSetting("Style", 0, "2D (fixed size)", "3D (shrinks with distance)");
    private final SliderSetting worldHeight = new SliderSetting("3D: card height (blocks)", 0.22, 0.1, 0.6, 0.01);
    private final SliderSetting scale = new SliderSetting("Scale (%)", 100.0, 40.0, 200.0, 5.0);
    private final SliderSetting range = new SliderSetting("Range (blocks)", 128.0, 16.0, 256.0, 8.0);

    /** The vanilla "below name" texts of the last frame, by entity id (the vanilla line itself is dropped). */
    private static final java.util.Map<Integer, net.minecraft.network.chat.Component> SCORES = new java.util.HashMap<>();
    private static final java.util.Map<Integer, Long> SCORE_SEEN = new java.util.HashMap<>();

    /** Called by the vanilla name tag extraction: remembers the score text before the vanilla line is dropped. */
    public static void captureScore(Entity entity, net.minecraft.network.chat.Component text) {
        if (text == null) {
            SCORES.remove(entity.getId());
            return;
        }
        SCORES.put(entity.getId(), text);
        SCORE_SEEN.put(entity.getId(), System.currentTimeMillis());
    }

    private static net.minecraft.network.chat.Component scoreOf(Entity entity) {
        Long seen = SCORE_SEEN.get(entity.getId());
        return seen != null && System.currentTimeMillis() - seen < 1500L ? SCORES.get(entity.getId()) : null;
    }

    private record Tag(AbstractClientPlayer player, double distance, float x, float y) {}

    public NameTags() {
        super("NameTags", "Name cards with face, rank and nick above players", Category.RENDER);
        instance = this;
        with(this.items, this.info, this.privilege, this.throughWalls, this.style, this.worldHeight, this.scale, this.range);
        this.worldHeight.visibleIf(() -> this.style.value().startsWith("3D"));
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
        boolean world = this.style.value().startsWith("3D");
        double fov = Math.toRadians(mc.options.fov().get());
        double pixelsPerBlock = Render2D.screenHeight() / (2.0 * Math.tan(fov / 2.0));
        // in the 3D style the whole card is laid out at its real size (fonts are made at that size, not stretched),
        // so the text stays sharp; the size never goes below what a font of 8 px can still show
        float minFactor = 8.0f / (15.0f * s);
        Render2D.begin(gg);
        try {
            for (Tag tag : tags) {
                float ts = s;
                if (world) {
                    float factor = (float) Math.max(minFactor, Math.min(3.5,
                            pixelsPerBlock * this.worldHeight.value() / Math.max(0.5, tag.distance()) / (40.0 * s)));
                    ts = s * factor;
                }
                draw(gg, tag, ts);
            }
        } finally {
            Render2D.end(gg);
        }
    }

    private record Segment(String text, int color) {}

    private List<Segment> infoOf(AbstractClientPlayer player, double distance) {
        List<Segment> out = new ArrayList<>();
        net.minecraft.network.chat.Component score = this.info.isSelected(INFO_SCORE) ? scoreOf(player) : null;
        float health = player.getHealth();
        float absorption = player.getAbsorptionAmount();
        if (score != null) {
            Integer number = leadingNumber(score.getString());
            net.minecraft.network.chat.TextColor styled = score.getStyle().getColor();
            if (styled == null && !score.getSiblings().isEmpty()) {
                styled = score.getSiblings().get(score.getSiblings().size() - 1).getStyle().getColor();
            }
            out.add(new Segment(score.getString(), styled != null ? 0xFF000000 | styled.getValue() : 0xFFFF7A7A));
            if (number != null && number == Math.round(health + absorption)) {
                health = -1.0f;         // the server line already tells the health
            }
        }
        if (this.info.isSelected(INFO_HEALTH) && health >= 0.0f) {
            float max = Math.max(1.0f, player.getMaxHealth());
            float ratio = health / max;
            int color = ratio >= 0.66f ? 0xFF6EE06E : ratio >= 0.33f ? 0xFFE8C23A : 0xFFFF5555;
            String text = "HP " + trim(health) + (absorption > 0.0f ? " +" + trim(absorption) : "");
            out.add(0, new Segment(text, color));
        }
        if (this.info.isSelected(INFO_DISTANCE)) {
            out.add(new Segment(String.format(java.util.Locale.ROOT, "%.1f m", distance), 0xFFC9C6D4));
        }
        if (this.info.isSelected(INFO_PING)) {
            net.minecraft.client.multiplayer.ClientPacketListener connection = Minecraft.getInstance().getConnection();
            net.minecraft.client.multiplayer.PlayerInfo entry = connection == null ? null : connection.getPlayerInfo(player.getUUID());
            if (entry != null) {
                int ms = entry.getLatency();
                out.add(new Segment(ms + " ms", ms < 80 ? 0xFF6EE06E : ms < 160 ? 0xFFE8C23A : 0xFFFF5555));
            }
        }
        return out;
    }

    private static Integer leadingNumber(String text) {
        int end = 0;
        while (end < text.length() && Character.isDigit(text.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return null;
        }
        try {
            return Integer.parseInt(text.substring(0, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trim(float value) {
        return Math.abs(value - Math.round(value)) < 0.05f ? Integer.toString(Math.round(value))
                : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private void draw(GuiGraphicsExtractor gg, Tag tag, float s) {
        HFont rankFont = Fonts.of(Fonts.INTER_BOLD, Math.max(8, Math.round(15.0f * s)));
        HFont nickFont = Fonts.of(Fonts.INTER_SEMIBOLD, Math.max(8, Math.round(15.0f * s)));
        HFont infoFont = Fonts.of(Fonts.INTER_SEMIBOLD, Math.max(8, Math.round(12.5f * s)));
        String nick = tag.player().getGameProfile().name();
        Privilege.Info rank = this.privilege.value() ? Privilege.of(tag.player()) : null;
        List<Segment> info = infoOf(tag.player(), tag.distance());

        float nickWidth = nickFont.width(nick);
        float rankWidth = rank != null ? rankFont.width(rank.text()) : 0.0f;
        float rowOne = rankWidth + (rank != null ? 10.0f * s : 0.0f) + nickWidth;
        float gap = 9.0f * s;
        float rowTwo = 0.0f;
        for (int i = 0; i < info.size(); i++) {
            rowTwo += infoFont.width(info.get(i).text()) + (i > 0 ? gap : 0.0f);
        }
        boolean twoRows = !info.isEmpty();
        float width = (6.0f + 28.0f + 10.0f) * s + Math.max(rowOne, rowTwo) + 14.0f * s;
        float height = (twoRows ? 46.0f : 40.0f) * s;
        float x = tag.x() - width / 2.0f;
        float y = tag.y() - height;

        Render2D.round(gg, x, y, width, height, 10.0f * s, BACKGROUND);
        float face = Math.max(8, Math.round(28.0f * s));
        PlayerFaceExtractor.extractRenderState(gg, tag.player().getSkin(),
                Math.round(x + 6.0f * s), Math.round(y + (height - face) / 2.0f), Math.round(face));

        float textX = x + 44.0f * s;
        float baseline = twoRows ? y + 19.0f * s : y + 25.5f * s;
        float cursor = textX;
        if (rank != null) {
            rankFont.draw(gg, rank.text(), cursor, baseline, rank.argb());
            cursor += rankWidth + 10.0f * s;
        }
        nickFont.draw(gg, nick, cursor, baseline, 0xFFFFFFFF);

        if (twoRows) {
            float infoBaseline = y + 37.0f * s;
            float ix = textX;
            for (Segment segment : info) {
                ix = infoFont.draw(gg, segment.text(), ix, infoBaseline, segment.color()) + gap;
            }
        }

        drawItems(gg, tag.player(), tag.x(), y, s);
    }

    /** Held items and armor in a small dark strip above the card: left hand, armor (head to feet), right hand. */
    private void drawItems(GuiGraphicsExtractor gg, AbstractClientPlayer player, float centerX, float cardTop, float s) {
        List<ItemStack> stacks = new ArrayList<>();
        if (this.items.isSelected(LEFT_HAND)) {
            addIfPresent(stacks, player.getItemBySlot(EquipmentSlot.OFFHAND));
        }
        if (this.items.isSelected(ARMOR)) {
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                addIfPresent(stacks, player.getItemBySlot(slot));
            }
        }
        if (this.items.isSelected(RIGHT_HAND)) {
            addIfPresent(stacks, player.getItemBySlot(EquipmentSlot.MAINHAND));
        }
        if (stacks.isEmpty()) {
            return;
        }
        float size = 22.0f * s;
        float gap = 4.0f * s;
        float pad = 6.0f * s;
        float rowW = pad * 2.0f + stacks.size() * size + (stacks.size() - 1) * gap;
        float rowH = size + pad * 2.0f;
        float rowX = centerX - rowW / 2.0f;
        float rowY = cardTop - rowH - 4.0f * s;
        Render2D.round(gg, rowX, rowY, rowW, rowH, 10.0f * s, BACKGROUND);

        Minecraft mc = Minecraft.getInstance();
        float factor = size / 16.0f;
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            float ix = rowX + pad + i * (size + gap);
            float iy = rowY + pad;
            gg.pose().pushMatrix();
            gg.pose().translate(Math.round(ix), Math.round(iy));
            gg.pose().scale(factor, factor);
            gg.item(stack, 0, 0);
            gg.itemDecorations(mc.font, stack, 0, 0);
            gg.pose().popMatrix();
        }
    }

    private static void addIfPresent(List<ItemStack> out, ItemStack stack) {
        if (!stack.isEmpty()) {
            out.add(stack);
        }
    }
}
