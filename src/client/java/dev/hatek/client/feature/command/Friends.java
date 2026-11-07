package dev.hatek.client.feature.command;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** The friends list (.friend). Friends are never targeted by SuperSpear and AttackAura. Saved in config/hatek/friends.txt. */
public final class Friends {
    private static final Set<String> NAMES = new TreeSet<>();
    private static boolean loaded;

    private Friends() {
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("hatek").resolve("friends.txt");
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Path file = file();
            if (Files.exists(file)) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String name = line.trim().toLowerCase(Locale.ROOT);
                    if (!name.isEmpty()) {
                        NAMES.add(name);
                    }
                }
            }
        } catch (IOException ignored) {
            // an unreadable list is just an empty one
        }
    }

    private static void save() {
        try {
            Path file = file();
            Files.createDirectories(file.getParent());
            Files.write(file, new ArrayList<>(NAMES), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // the list stays in memory for this session
        }
    }

    public static boolean is(String name) {
        ensureLoaded();
        return name != null && NAMES.contains(name.toLowerCase(Locale.ROOT));
    }

    public static boolean is(Entity entity) {
        return entity instanceof Player player && is(player.getGameProfile().name());
    }

    public static boolean add(String name) {
        ensureLoaded();
        boolean changed = NAMES.add(name.toLowerCase(Locale.ROOT));
        if (changed) {
            save();
        }
        return changed;
    }

    public static boolean remove(String name) {
        ensureLoaded();
        boolean changed = NAMES.remove(name.toLowerCase(Locale.ROOT));
        if (changed) {
            save();
        }
        return changed;
    }

    public static void clear() {
        ensureLoaded();
        NAMES.clear();
        save();
    }

    public static List<String> list() {
        ensureLoaded();
        return new ArrayList<>(NAMES);
    }
}
