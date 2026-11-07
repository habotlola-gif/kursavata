package dev.hatek.client.ui.screen;

public enum Tab {
    CONFIGS("nav_config", 12, 14),
    THEMES("nav_theme", 14, 14),
    MODULES("combat", 14, 14);

    private final String icon;
    private final int iconWidth;
    private final int iconHeight;

    Tab(String icon, int iconWidth, int iconHeight) {
        this.icon = icon;
        this.iconWidth = iconWidth;
        this.iconHeight = iconHeight;
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
