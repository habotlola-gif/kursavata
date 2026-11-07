package dev.hatek.client.feature.theme;

import dev.hatek.client.ui.Style;
import dev.hatek.client.feature.config.ConfigManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ThemeManager {
    private static final List<ThemeEntry> THEMES = new ArrayList<>();
    private static String appliedName;

    private ThemeManager() {
    }

    public static List<ThemeEntry> all() {
        if (THEMES.isEmpty()) {
            register();
            load();
        }
        return THEMES;
    }

    public static boolean isApplied(ThemeEntry theme) {
        return theme.name().equals(appliedName);
    }

    public static ThemeEntry applied() {
        for (ThemeEntry theme : all()) {
            if (isApplied(theme)) {
                return theme;
            }
        }
        return null;
    }

    public static void apply(ThemeEntry theme) {
        appliedName = theme.name();
        Style.accent(theme.color());
        save();
    }

    private static Path file() {
        return ConfigManager.root().resolve("theme.txt");
    }

    private static void save() {
        try {
            Files.createDirectories(ConfigManager.root());
            Files.writeString(file(), appliedName == null ? "" : appliedName, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static void load() {
        try {
            if (!Files.isRegularFile(file())) {
                return;
            }
            String saved = Files.readString(file(), StandardCharsets.UTF_8).trim();
            for (ThemeEntry theme : THEMES) {
                if (theme.name().equalsIgnoreCase(saved)) {
                    appliedName = theme.name();
                    Style.accent(theme.color());
                    return;
                }
            }
        } catch (IOException ignored) {
        }
    }

    public static String hex(int argb) {
        return String.format(Locale.ROOT, "#%06X", argb & 0x00FFFFFF);
    }

    private static void register() {
        String author = "Sap4ik555";
        String date = "31.08.2026";
        add("Japan", author, date, 0xFFFFAFE2);
        add("Cyber Lime", author, date, 0xFFB6FF00);
        add("BloodMoon", author, date, 0xFFFF3B5C);
        add("Ocean Drive", author, date, 0xFF008CFF);
        add("Neon Void", author, date, 0xFF6C63FF);
        add("Toxic Glass", author, date, 0xFF00D98B);
        add("Solar Flare", author, date, 0xFFFF7A00);
        add("Acid Noir", author, date, 0xFFD7FF00);
        add("Royal Flux", author, date, 0xFF9B5CFF);
        add("Carbon", author, date, 0xFF8B9CFF);
        add("Vanta", author, date, 0xFFD8D8D8);
        add("Ruby", author, date, 0xFFFF1744);
        add("Electric", author, date, 0xFF00F5D4);
        add("Emerald", author, date, 0xFF35E69A);
        add("Abyss", author, date, 0xFF00B8D9);
        appliedName = "Japan";
    }

    private static void add(String name, String author, String date, int color) {
        THEMES.add(new ThemeEntry(name, author, date, color));
    }
}
