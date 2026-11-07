package dev.hatek.client.feature.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.ModuleManager;
import dev.hatek.client.module.setting.Setting;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault());

    private static final List<ConfigEntry> CACHE = new ArrayList<>();
    private static boolean scanned;
    private static String loadedName;

    private ConfigManager() {
    }

    public static Path root() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("hatek");
    }

    public static Path configs() {
        return root().resolve("configs");
    }

    public static String loadedName() {
        return loadedName;
    }

    public static List<ConfigEntry> all() {
        if (!scanned) {
            refresh();
        }
        return CACHE;
    }

    public static void refresh() {
        scanned = true;
        CACHE.clear();
        List<String> favourites = readFavourites();
        try {
            Files.createDirectories(configs());
            try (Stream<Path> files = Files.list(configs())) {
                files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(path -> {
                    String name = stripExtension(path);
                    String author = "unknown";
                    try {
                        JsonObject json = JsonParser.parseString(
                                Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                        if (json.has("author")) {
                            author = json.get("author").getAsString();
                        }
                    } catch (Exception ignored) {
                    }
                    String date = DATE.format(lastModified(path));
                    CACHE.add(new ConfigEntry(name, author, date, path, favourites.contains(name)));
                });
            }
        } catch (IOException ignored) {
        }
        CACHE.sort(Comparator.comparing(ConfigEntry::favourite).reversed()
                .thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT)));
    }

    public static void save(String name) {
        String safe = sanitise(name);
        if (safe.isEmpty()) {
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("author", playerName());
        root.addProperty("version", 1);
        JsonObject modules = new JsonObject();
        for (Module module : ModuleManager.all()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", module.isEnabled());
            entry.addProperty("expanded", module.isExpanded());
            entry.addProperty("key", module.keyCode());
            JsonObject settings = new JsonObject();
            for (Setting setting : module.settings()) {
                String value = setting.serialize();
                if (value != null) {
                    settings.addProperty(setting.name(), value);
                }
            }
            entry.add("settings", settings);
            modules.add(module.name(), entry);
        }
        root.add("modules", modules);

        try {
            Files.createDirectories(configs());
            Files.writeString(configs().resolve(safe + ".json"), GSON.toJson(root),
                    StandardCharsets.UTF_8);
            loadedName = safe;
        } catch (IOException ignored) {
        }
        refresh();
    }

    public static void load(ConfigEntry entry) {
        try {
            JsonObject root = JsonParser.parseString(
                    Files.readString(entry.file(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject modules = root.getAsJsonObject("modules");
            if (modules == null) {
                return;
            }
            for (Module module : ModuleManager.all()) {
                JsonElement raw = modules.get(module.name());
                if (raw == null || !raw.isJsonObject()) {
                    continue;
                }
                JsonObject node = raw.getAsJsonObject();
                if (node.has("enabled") && node.get("enabled").getAsBoolean() != module.isEnabled()) {
                    module.toggle();
                }
                if (node.has("expanded")) {
                    module.expanded(node.get("expanded").getAsBoolean());
                }
                if (node.has("key")) {
                    module.keybind(node.get("key").getAsInt());
                }
                JsonObject settings = node.getAsJsonObject("settings");
                if (settings == null) {
                    continue;
                }
                for (Setting setting : module.settings()) {
                    JsonElement value = settings.get(setting.name());
                    if (value != null && value.isJsonPrimitive()) {
                        setting.deserialize(value.getAsString());
                    }
                }
            }
            loadedName = entry.name();
        } catch (Exception ignored) {
        }
    }

    public static void delete(ConfigEntry entry) {
        try {
            Files.deleteIfExists(entry.file());
        } catch (IOException ignored) {
        }
        if (entry.name().equals(loadedName)) {
            loadedName = null;
        }
        refresh();
    }

    public static void duplicate(ConfigEntry entry) {
        try {
            String base = entry.name() + " copy";
            Path target = configs().resolve(base + ".json");
            int n = 2;
            while (Files.exists(target)) {
                target = configs().resolve(base + " " + n++ + ".json");
            }
            Files.copy(entry.file(), target);
        } catch (IOException ignored) {
        }
        refresh();
    }

    public static void toggleFavourite(ConfigEntry entry) {
        List<String> favourites = readFavourites();
        if (!favourites.remove(entry.name())) {
            favourites.add(entry.name());
        }
        try {
            Files.createDirectories(root());
            Files.write(root().resolve("favourites.txt"), favourites, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
        refresh();
    }

    public static String nextFreeName() {
        int n = 1;
        while (Files.exists(configs().resolve("Config " + n + ".json"))) {
            n++;
        }
        return "Config " + n;
    }

    public static void openFolder() {
        try {
            Files.createDirectories(configs());
            net.minecraft.util.Util.getPlatform().openPath(configs());
        } catch (IOException ignored) {
        }
    }

    private static List<String> readFavourites() {
        try {
            Path path = root().resolve("favourites.txt");
            return Files.isRegularFile(path)
                    ? new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8))
                    : new ArrayList<>();
        } catch (IOException ignored) {
            return new ArrayList<>();
        }
    }

    private static Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException ignored) {
            return Instant.now();
        }
    }

    private static String stripExtension(Path path) {
        String file = path.getFileName().toString();
        return file.substring(0, file.length() - ".json".length());
    }

    private static String sanitise(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "").trim();
    }

    private static String playerName() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getUser() == null ? "unknown" : mc.getUser().getName();
    }
}
