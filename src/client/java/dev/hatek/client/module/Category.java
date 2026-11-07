package dev.hatek.client.module;

public enum Category {
    COMBAT("Combat", "combat", 14, 14),
    MOVEMENT("Movement", "movement", 14, 14),
    RENDER("Render", "render", 14, 11),
    PLAYER("Player", "player", 11, 14),
    MISC("Misc", "misc", 14, 14);

    private final String displayName;
    private final String icon;
    private final int iconWidth;
    private final int iconHeight;

    Category(String displayName, String icon, int iconWidth, int iconHeight) {
        this.displayName = displayName;
        this.icon = icon;
        this.iconWidth = iconWidth;
        this.iconHeight = iconHeight;
    }

    public String displayName() {
        return this.displayName;
    }

    public String icon() {
        return this.icon;
    }

    public int iconWidth() {
        return this.iconWidth;
    }

    public int iconHeight() {
        return this.iconHeight;
    }
}
