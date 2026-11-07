package dev.hatek.client.ui.render;

import dev.hatek.client.ui.Style;

import java.awt.Font;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class Fonts {
    public static final String ROBOTO_REGULAR = "Roboto-Regular";
    public static final String ROBOTO_MEDIUM = "Roboto-Medium";
    public static final String ROBOTO_BOLD = "Roboto-Bold";
    public static final String INTER_REGULAR = "Inter-Regular";
    public static final String INTER_MEDIUM = "Inter-Medium";
    public static final String INTER_SEMIBOLD = "Inter-SemiBold";
    public static final String INTER_BOLD = "Inter-Bold";

    private static final Map<String, Font> FACES = new HashMap<>();
    private static final Map<String, HFont> SIZED = new HashMap<>();

    private static HFont title;
    private static HFont body;
    private static HFont label;

    private Fonts() {
    }

    public static HFont title() {
        if (title == null) {
            title = of(ROBOTO_MEDIUM, Style.FONT_TITLE);
        }
        return title;
    }

    public static HFont body() {
        if (body == null) {
            body = of(ROBOTO_REGULAR, Style.FONT_BODY);
        }
        return body;
    }

    public static HFont label() {
        if (label == null) {
            label = of(INTER_MEDIUM, Style.FONT_LABEL);
        }
        return label;
    }

    public static HFont of(String face, float size) {
        String key = face + "@" + String.format(Locale.ROOT, "%.2f", size);
        HFont cached = SIZED.get(key);
        if (cached != null) {
            return cached;
        }
        HFont created = new HFont(face(face), size, key.replace('@', '_').replace('.', '_'));
        SIZED.put(key, created);
        return created;
    }

    public static void reset() {
        for (HFont font : SIZED.values()) {
            font.close();
        }
        SIZED.clear();
        title = null;
        body = null;
        label = null;
    }

    private static Font face(String name) {
        Font cached = FACES.get(name);
        if (cached != null) {
            return cached;
        }
        if (System.getProperty("java.awt.headless") == null) {
            System.setProperty("java.awt.headless", "true");
        }
        Font font;
        try (InputStream in = Fonts.class.getResourceAsStream(
                "/assets/hatek_client/fonts/" + name + ".ttf")) {
            if (in == null) {
                throw new IllegalStateException("missing bundled font " + name);
            }
            font = Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            font = new Font(Font.SANS_SERIF, name.contains("Medium") || name.contains("Bold")
                    ? Font.BOLD : Font.PLAIN, 16);
        }
        FACES.put(name, font);
        return font;
    }
}
