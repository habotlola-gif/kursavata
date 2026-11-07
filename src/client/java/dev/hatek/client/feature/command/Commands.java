package dev.hatek.client.feature.command;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.hatek.client.module.impl.combat.SuperSpear;
import dev.hatek.client.module.impl.movement.VoidTeleport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Chat commands that start with a dot. They are handled on the client and never sent to the server; the chat box
 * suggests the command names and the nicknames (nearest first) while you type.
 *
 *   .tp ник          — VoidTeleport to a player (says so if the player is not loaded or stands behind blocks)
 *   .tp x y z        — VoidTeleport to coordinates
 *   .friend ник      — add / remove a friend (toggle)
 *   .friend add|remove ник, .friend list, .friend clear
 */
public final class Commands {
    private static final List<String> NAMES = List.of(".friend", ".tp");
    private static final List<String> FRIEND_ACTIONS = List.of("add", "remove", "list", "clear");

    private Commands() {
    }

    private static void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§bHatek: §f" + text));
        }
    }

    /** True when the line was a client command (the chat must not send it). */
    public static boolean handle(String line) {
        String text = line.trim();
        if (!text.startsWith(".")) {
            return false;
        }
        String[] parts = text.substring(1).split("\\s+");
        String name = parts[0].toLowerCase(Locale.ROOT);
        switch (name) {
            case "tp" -> teleport(parts);
            case "friend" -> friend(parts);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------------ nicknames

    /** Loaded players, nearest first (excluding yourself). */
    private static List<Player> loadedByDistance() {
        Minecraft mc = Minecraft.getInstance();
        List<Player> out = new ArrayList<>();
        if (mc.level == null || mc.player == null) {
            return out;
        }
        for (Player other : mc.level.players()) {
            if (other != mc.player) {
                out.add(other);
            }
        }
        out.sort(Comparator.comparingDouble(p -> p.distanceToSqr(mc.player)));
        return out;
    }

    /** Nicknames for the chat suggestions: loaded players nearest first, then everyone else from the tab list. */
    private static List<String> nicknames() {
        Minecraft mc = Minecraft.getInstance();
        Set<String> names = new LinkedHashSet<>();
        for (Player player : loadedByDistance()) {
            names.add(player.getGameProfile().name());
        }
        if (mc.getConnection() != null) {
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                String name = info.getProfile().name();
                if (mc.player == null || !name.equalsIgnoreCase(mc.player.getGameProfile().name())) {
                    names.add(name);
                }
            }
        }
        return new ArrayList<>(names);
    }

    private static boolean online(String nick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return false;
        }
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (info.getProfile().name().equalsIgnoreCase(nick)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------ suggestions

    /** Suggestions for a chat line that starts with a dot, in the order they should be shown. */
    public static Suggestions suggestions(SuggestionsBuilder builder) {
        String input = builder.getInput();
        String[] words = input.split(" ", -1);
        int wordIndex = words.length - 1;
        String current = words[wordIndex].toLowerCase(Locale.ROOT);
        List<String> candidates = new ArrayList<>();
        if (wordIndex == 0) {
            candidates.addAll(NAMES);
        } else {
            String command = words[0].toLowerCase(Locale.ROOT);
            if (command.equals(".tp") && wordIndex == 1) {
                candidates.addAll(nicknames());
            } else if (command.equals(".friend")) {
                String sub = words[1].toLowerCase(Locale.ROOT);
                if (wordIndex == 1) {
                    candidates.addAll(FRIEND_ACTIONS);
                    candidates.addAll(nicknames());
                } else if (wordIndex == 2 && sub.equals("add")) {
                    for (String nick : nicknames()) {
                        if (!Friends.is(nick)) {
                            candidates.add(nick);
                        }
                    }
                } else if (wordIndex == 2 && sub.equals("remove")) {
                    candidates.addAll(Friends.list());
                }
            }
        }
        StringRange range = StringRange.between(builder.getStart(), input.length());
        List<Suggestion> list = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(current)) {
                list.add(new Suggestion(range, candidate));
            }
        }
        return new Suggestions(range, list);
    }

    // ------------------------------------------------------------------------------------------------ .friend

    private static void friend(String[] parts) {
        if (parts.length < 2) {
            say("Использование: .friend ник  |  .friend add/remove ник  |  .friend list  |  .friend clear");
            return;
        }
        String sub = parts[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                List<String> names = Friends.list();
                say(names.isEmpty() ? "Список друзей пуст." : "Друзья (" + names.size() + "): " + String.join(", ", names));
            }
            case "clear" -> {
                Friends.clear();
                say("Список друзей очищен.");
            }
            case "add", "remove" -> {
                if (parts.length < 3) {
                    say("Укажи ник: .friend " + sub + " ник");
                    return;
                }
                if (sub.equals("add")) {
                    say(Friends.add(parts[2]) ? parts[2] + " добавлен в друзья." : parts[2] + " уже в друзьях.");
                } else {
                    say(Friends.remove(parts[2]) ? parts[2] + " убран из друзей." : parts[2] + " не был в друзьях.");
                }
            }
            default -> {
                // ".friend ник" toggles
                if (Friends.is(parts[1])) {
                    Friends.remove(parts[1]);
                    say(parts[1] + " убран из друзей.");
                } else {
                    Friends.add(parts[1]);
                    say(parts[1] + " добавлен в друзья.");
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ .tp

    private static void teleport(String[] parts) {
        Minecraft mc = Minecraft.getInstance();
        VoidTeleport module = VoidTeleport.instance();
        if (mc.player == null || mc.level == null || module == null) {
            return;
        }
        if (parts.length == 2) {
            String nick = parts[1];
            Player found = null;
            for (Player other : loadedByDistance()) {
                if (other.getGameProfile().name().equalsIgnoreCase(nick)) {
                    found = other;
                    break;
                }
            }
            if (found == null) {
                if (online(nick)) {
                    say("Игрок " + nick + " не прогружен (слишком далеко от тебя), его координат нет. Телепорт невозможен.");
                } else {
                    say("Игрок " + nick + " не найден на сервере.");
                }
                return;
            }
            String shown = found.getGameProfile().name();
            if (!SuperSpear.canSee(mc.player, found)) {
                say("Игрок " + shown + " загражден блоками. Телепорт пойдёт на ближайшее свободное место над ним.");
            } else {
                say("Телепорт к " + shown + "...");
            }
            module.teleportTo(found.position());
        } else if (parts.length == 4) {
            try {
                Vec3 point = new Vec3(Double.parseDouble(parts[1].replace(',', '.')),
                        Double.parseDouble(parts[2].replace(',', '.')), Double.parseDouble(parts[3].replace(',', '.')));
                module.teleportTo(point);
            } catch (NumberFormatException e) {
                say("Координаты должны быть числами: .tp x y z");
            }
        } else {
            say("Использование: .tp ник  или  .tp x y z");
        }
    }
}
