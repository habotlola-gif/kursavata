package dev.hatek.client.feature.config;

import java.nio.file.Path;

public record ConfigEntry(String name, String author, String date, Path file, boolean favourite) {

    public ConfigEntry withFavourite(boolean value) {
        return new ConfigEntry(this.name, this.author, this.date, this.file, value);
    }
}
