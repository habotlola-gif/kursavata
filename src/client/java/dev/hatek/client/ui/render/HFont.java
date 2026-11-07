package dev.hatek.client.ui.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HFont {
    private static final int ATLAS = 512;
    private static final int PAD = 1;
    private static final String PREBAKE = buildPrebakeSet();

    private final Font awt;
    private final FontRenderContext frc;
    private final Identifier textureId;
    private final NativeImage image;
    private final DynamicTexture texture;

    private final Map<Character, Glyph> glyphs = new HashMap<>();
    private final Map<String, Line> lines = new HashMap<>();

    private int penX = PAD;
    private int penY = PAD;
    private int shelfHeight;
    private boolean dirty;

    private final float ascent;
    private final float descent;
    private final float lineHeight;

    HFont(Font base, float size, String cacheKey) {
        Map<TextAttribute, Object> attributes = new HashMap<>();
        attributes.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
        this.awt = base.deriveFont(size).deriveFont(attributes);
        this.frc = new FontRenderContext(new AffineTransform(), true, true);

        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = probe.createGraphics();
        FontMetrics fm = g.getFontMetrics(this.awt);
        this.ascent = fm.getAscent();
        this.descent = fm.getDescent();
        this.lineHeight = fm.getHeight();
        g.dispose();

        this.image = new NativeImage(NativeImage.Format.RGBA, ATLAS, ATLAS, false);
        this.image.fillRect(0, 0, ATLAS, ATLAS, 0);
        this.textureId = Identifier.fromNamespaceAndPath("hatek_client",
                "font/" + cacheKey.toLowerCase(Locale.ROOT));
        this.texture = new DynamicTexture(() -> "hatek/font/" + cacheKey, this.image);
        Minecraft.getInstance().getTextureManager().register(this.textureId, this.texture);

        for (int i = 0; i < PREBAKE.length(); i++) {
            glyph(PREBAKE.charAt(i));
        }
        flush();
    }

    public float ascent() {
        return this.ascent;
    }

    public float descent() {
        return this.descent;
    }

    public float lineHeight() {
        return this.lineHeight;
    }

    public float width(String text) {
        return text == null || text.isEmpty() ? 0.0f : line(text).width;
    }

    public float centeredBaseline(float y, float h) {
        return y + (h + this.ascent - this.descent) / 2.0f;
    }

    public float draw(GuiGraphicsExtractor gg, String text, float x, float baselineY, int argb) {
        if (text == null || text.isEmpty() || (argb >>> 24) == 0) {
            return x;
        }
        Line line = line(text);
        for (char c : line.chars) {
            glyph(c);
        }
        flush();
        int originX = Math.round(x);
        int originY = Math.round(baselineY);
        for (int i = 0; i < line.chars.length; i++) {
            Glyph glyph = this.glyphs.get(line.chars[i]);
            if (glyph == null || glyph.w() == 0) {
                continue;
            }
            Render2D.blit(gg, this.textureId,
                    originX + Math.round(line.offsets[i]) + glyph.offX(), originY + glyph.offY(),
                    glyph.u(), glyph.v(), glyph.w(), glyph.h(), ATLAS, ATLAS, argb);
        }
        return x + line.width;
    }

    public float drawCentered(GuiGraphicsExtractor gg, String text, float cx, float baselineY, int argb) {
        return draw(gg, text, cx - width(text) / 2.0f, baselineY, argb);
    }

    public float drawRight(GuiGraphicsExtractor gg, String text, float right, float baselineY, int argb) {
        return draw(gg, text, right - width(text), baselineY, argb);
    }

    public List<String> wrap(String text, float maxWidth) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (current.length() == 0 || width(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
            } else {
                out.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
        return out;
    }

    public String trim(String text, float maxWidth) {
        if (text == null || width(text) <= maxWidth) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (width(sb.toString() + text.charAt(i) + "...") > maxWidth) {
                break;
            }
            sb.append(text.charAt(i));
        }
        return sb + "...";
    }

    private record Glyph(int u, int v, int w, int h, int offX, int offY) {
    }

    private record Line(char[] chars, float[] offsets, float width) {
    }

    private Line line(String text) {
        Line cached = this.lines.get(text);
        if (cached != null) {
            return cached;
        }
        char[] source = text.toCharArray();
        GlyphVector gv = this.awt.layoutGlyphVector(this.frc, source, 0, source.length,
                Font.LAYOUT_LEFT_TO_RIGHT);
        int count = gv.getNumGlyphs();
        char[] chars = new char[count];
        float[] offsets = new float[count];
        for (int i = 0; i < count; i++) {
            int index = gv.getGlyphCharIndex(i);
            chars[i] = source[Math.min(Math.max(index, 0), source.length - 1)];
            offsets[i] = (float) gv.getGlyphPosition(i).getX();
        }
        Line line = new Line(chars, offsets, (float) gv.getGlyphPosition(count).getX());
        if (this.lines.size() < 4096) {
            this.lines.put(text, line);
        }
        return line;
    }

    private Glyph glyph(char c) {
        Glyph cached = this.glyphs.get(c);
        if (cached != null) {
            return cached;
        }
        Glyph baked = bake(c);
        this.glyphs.put(c, baked);
        return baked;
    }

    private Glyph bake(char c) {
        GlyphVector gv = this.awt.createGlyphVector(this.frc, new char[]{c});
        Shape outline = gv.getGlyphOutline(0);
        Rectangle2D b = outline.getBounds2D();
        if (b.getWidth() <= 0.0 || b.getHeight() <= 0.0) {
            return new Glyph(0, 0, 0, 0, 0, 0);
        }
        int offX = (int) Math.floor(b.getX()) - 1;
        int offY = (int) Math.floor(b.getY()) - 1;
        int w = (int) Math.ceil(b.getMaxX()) - offX + 1;
        int h = (int) Math.ceil(b.getMaxY()) - offY + 1;

        if (this.penX + w + PAD > ATLAS) {
            this.penX = PAD;
            this.penY += this.shelfHeight + PAD;
            this.shelfHeight = 0;
        }
        if (this.penY + h + PAD > ATLAS) {
            return new Glyph(0, 0, 0, 0, 0, 0);
        }
        int u = this.penX;
        int v = this.penY;
        this.penX += w + PAD;
        this.shelfHeight = Math.max(this.shelfHeight, h);

        BufferedImage cell = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = cell.createGraphics();
        TexCache.applyQuality(g);
        g.translate(-offX, -offY);
        g.setColor(Color.WHITE);
        g.fill(outline);
        g.dispose();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                this.image.setPixel(u + x, v + y, cell.getRGB(x, y));
            }
        }
        this.dirty = true;
        return new Glyph(u, v, w, h, offX, offY);
    }

    private void flush() {
        if (this.dirty) {
            this.texture.upload();
            this.dirty = false;
        }
    }

    void close() {
        Minecraft.getInstance().getTextureManager().release(this.textureId);
        this.texture.close();
    }

    private static String buildPrebakeSet() {
        StringBuilder sb = new StringBuilder();
        for (char c = 0x20; c <= 0x7E; c++) {
            sb.append(c);
        }
        for (char c = 0xA0; c <= 0xFF; c++) {
            sb.append(c);
        }
        for (char c = 0x400; c <= 0x45F; c++) {
            sb.append(c);
        }
        sb.append("–—‘’“”•…→·");
        return sb.toString();
    }
}
