package dev.hatek.client.render;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A player's server rank ("privilege"): the text that stands before the nick in the scoreboard team prefix or in the
 * tab list name, together with its colour. Cached for a second per player so it is not re-parsed every frame.
 */
public final class Privilege {
    public record Info(String text, int argb) {}

    private record Cached(@Nullable Info info, long at) {}

    private static final long CACHE_MS = 1000L;
    private static final Map<UUID, Cached> CACHE = new HashMap<>();

    private Privilege() {
    }

    public static @Nullable Info of(AbstractClientPlayer player) {
        long now = System.currentTimeMillis();
        Cached cached = CACHE.get(player.getUUID());
        if (cached != null && now - cached.at() < CACHE_MS) {
            return cached.info();
        }
        Info info = compute(player);
        if (CACHE.size() > 256) {
            CACHE.clear();
        }
        CACHE.put(player.getUUID(), new Cached(info, now));
        return info;
    }

    private static @Nullable Info compute(AbstractClientPlayer player) {
        String nick = player.getGameProfile().name();
        Component full = null;
        PlayerTeam team = player.getTeam();
        if (team != null) {
            full = PlayerTeam.formatNameForTeam(team, player.getName());
        }
        if (full == null || !full.getString().contains(nick)) {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            PlayerInfo info = connection == null ? null : connection.getPlayerInfo(player.getUUID());
            Component tab = info == null ? null : info.getTabListDisplayName();
            if (tab != null && tab.getString().contains(nick)) {
                full = tab;
            }
        }
        if (full == null) {
            return null;
        }

        record Segment(String text, Style style) {}
        List<Segment> segments = new ArrayList<>();
        full.visit((style, text) -> {
            segments.add(new Segment(text, style));
            return Optional.empty();
        }, Style.EMPTY);
        StringBuilder joined = new StringBuilder();
        for (Segment segment : segments) {
            joined.append(segment.text());
        }
        int nickAt = joined.indexOf(nick);
        if (nickAt <= 0) {
            return null;
        }

        StringBuilder prefix = new StringBuilder();
        Integer color = null;
        int position = 0;
        for (Segment segment : segments) {
            for (int i = 0; i < segment.text().length() && position < nickAt; i++, position++) {
                char c = segment.text().charAt(i);
                prefix.append(c);
                if (color == null && !Character.isWhitespace(c) && segment.style().getColor() != null) {
                    TextColor textColor = segment.style().getColor();
                    color = 0xFF000000 | textColor.getValue();
                }
            }
            if (position >= nickAt) {
                break;
            }
        }
        String text = ChatFormatting.stripFormatting(prefix.toString());
        text = text == null ? "" : text.trim();
        if (text.isEmpty()) {
            return null;
        }
        return new Info(text, color != null ? color : 0xFFFFFFFF);
    }
}
