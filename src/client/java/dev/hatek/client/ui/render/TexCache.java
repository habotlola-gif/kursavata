package dev.hatek.client.ui.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class TexCache {
    private static final String NS = "hatek_client";
    private static final Map<String, Identifier> CACHE = new HashMap<>();

    private TexCache() {
    }

    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (Identifier id : CACHE.values()) {
            mc.getTextureManager().release(id);
        }
        CACHE.clear();
    }

    public static Identifier corner(int r) {
        final int d = Math.max(2, 2 * r);
        return get("corner_" + r, d, d, g -> {
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Float(0.0f, 0.0f, d, d));
        });
    }

    public static Identifier roundRect(int w, int h, float r) {
        String key = "rr_" + w + "_" + h + "_" + Math.round(r * 2.0f);
        return get(key, w, h, g -> {
            g.setColor(Color.WHITE);
            g.fill(new RoundRectangle2D.Float(0.0f, 0.0f, w, h, r * 2.0f, r * 2.0f));
        });
    }

    public static Identifier circle(int d) {
        return get("circle_" + d, d, d, g -> {
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Float(0.0f, 0.0f, d, d));
        });
    }

    public static Identifier check(int size) {
        return get("check_" + size, size, size, g -> {
            float stroke = Math.max(1.0f, size * 0.19f);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float inset = stroke * 0.6f;
            float span = size - inset * 2.0f;
            Path2D.Float p = new Path2D.Float();
            p.moveTo(inset, inset + span * 0.52f);
            p.lineTo(inset + span * 0.36f, inset + span * 0.88f);
            p.lineTo(inset + span, inset + span * 0.12f);
            g.draw(p);
        });
    }

    public static Identifier flask(int size) {
        return get("flask_" + size, size, size, g -> {
            float s = size;
            g.setColor(Color.WHITE);
            g.fill(new RoundRectangle2D.Float(s * 0.34f, 0.0f, s * 0.32f, s * 0.30f,
                    s * 0.16f, s * 0.16f));
            g.fill(new RoundRectangle2D.Float(s * 0.28f, s * 0.06f, s * 0.44f, s * 0.12f,
                    s * 0.10f, s * 0.10f));
            Path2D.Float body = new Path2D.Float();
            body.moveTo(s * 0.38f, s * 0.28f);
            body.curveTo(s * 0.38f, s * 0.46f, s * 0.08f, s * 0.52f, s * 0.08f, s * 0.72f);
            body.curveTo(s * 0.08f, s * 0.90f, s * 0.26f, s, s * 0.50f, s);
            body.curveTo(s * 0.74f, s, s * 0.92f, s * 0.90f, s * 0.92f, s * 0.72f);
            body.curveTo(s * 0.92f, s * 0.52f, s * 0.62f, s * 0.46f, s * 0.62f, s * 0.28f);
            body.closePath();
            g.fill(body);
        });
    }

    public static Identifier icon(String name, int w, int h) {
        return icon(name, w, h, false);
    }

    public static Identifier icon(String name, int w, int h, boolean flipY) {
        String key = "icon_" + name + "_" + w + "_" + h + (flipY ? "_f" : "");
        Identifier cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        return upload(key, paint(name, Math.max(1, w), Math.max(1, h), flipY));
    }

    private static BufferedImage paint(String name, int w, int h) {
        return paint(name, w, h, false);
    }

    private static final int SUPERSAMPLE = 4;

    private static BufferedImage paint(String name, int w, int h, boolean flipY) {
        Path2D.Float vector = IconShapes.shape(name, w * SUPERSAMPLE, h * SUPERSAMPLE);
        if (vector == null) {
            BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = dst.createGraphics();
            applyQuality(g);
            if (flipY) {
                g.translate(0, h);
                g.scale(1.0, -1.0);
            }
            BufferedImage src = readPng(name);
            if (src != null) {
                g.drawImage(downscale(src, w, h), 0, 0, null);
            }
            g.dispose();
            return dst;
        }
        BufferedImage big = new BufferedImage(w * SUPERSAMPLE, h * SUPERSAMPLE,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = big.createGraphics();
        applyQuality(g);
        if (flipY) {
            g.translate(0, h * SUPERSAMPLE);
            g.scale(1.0, -1.0);
        }
        g.setColor(Color.WHITE);
        g.fill(vector);
        g.dispose();
        return boxDown(big, w, h, SUPERSAMPLE);
    }

    private static BufferedImage boxDown(BufferedImage src, int w, int h, int factor) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int samples = factor * factor;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                long a = 0;
                long r = 0;
                long g = 0;
                long b = 0;
                for (int sy = 0; sy < factor; sy++) {
                    for (int sx = 0; sx < factor; sx++) {
                        int argb = src.getRGB(x * factor + sx, y * factor + sy);
                        int alpha = (argb >>> 24) & 0xFF;
                        a += alpha;
                        r += ((argb >> 16) & 0xFF) * alpha;
                        g += ((argb >> 8) & 0xFF) * alpha;
                        b += (argb & 0xFF) * alpha;
                    }
                }
                int alpha = (int) (a / samples);
                if (alpha == 0) {
                    out.setRGB(x, y, 0);
                    continue;
                }
                out.setRGB(x, y, (alpha << 24)
                        | ((int) (r / a) << 16) | ((int) (g / a) << 8) | (int) (b / a));
            }
        }
        return out;
    }

    private static BufferedImage downscale(BufferedImage src, int w, int h) {
        BufferedImage current = src;
        int cw = src.getWidth();
        int ch = src.getHeight();
        while (cw / 2 > w && ch / 2 > h) {
            cw = Math.max(w, cw / 2);
            ch = Math.max(h, ch / 2);
            BufferedImage step = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg = step.createGraphics();
            applyQuality(sg);
            sg.drawImage(current, 0, 0, cw, ch, null);
            sg.dispose();
            current = step;
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D og = out.createGraphics();
        applyQuality(og);
        og.drawImage(current, 0, 0, w, h, null);
        og.dispose();
        return out;
    }

    public static Identifier iconShadow(String name, int w, int h, float blur) {
        int pad = shadowPad(blur);
        String key = "shadow_" + name + "_" + w + "_" + h + "_" + Math.round(blur * 10.0f);
        Identifier cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        BufferedImage dst = new BufferedImage(w + pad * 2, h + pad * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        applyQuality(g);
        g.drawImage(paint(name, w, h), pad, pad, null);
        g.dispose();
        for (int y = 0; y < dst.getHeight(); y++) {
            for (int x = 0; x < dst.getWidth(); x++) {
                dst.setRGB(x, y, (dst.getRGB(x, y) & 0xFF000000) | 0x00FFFFFF);
            }
        }
        return upload(key, gaussian(dst, blur));
    }

    public static int shadowPad(float blur) {
        return (int) Math.ceil(blur * 3.0f);
    }

    public static Identifier saturationRamp(int w, int h, float r) {
        return get("sat_" + w + "_" + h + "_" + Math.round(r), w, h, g -> {
            for (int x = 0; x < w; x++) {
                int alpha = Math.round(255.0f * (1.0f - x / (float) Math.max(1, w - 1)));
                g.setColor(new Color(255, 255, 255, alpha));
                g.fillRect(x, 0, 1, h);
            }
            mask(g, w, h, r);
        });
    }

    public static Identifier valueRamp(int w, int h, float r) {
        return get("val_" + w + "_" + h + "_" + Math.round(r), w, h, g -> {
            for (int y = 0; y < h; y++) {
                int alpha = Math.round(255.0f * (y / (float) Math.max(1, h - 1)));
                g.setColor(new Color(0, 0, 0, alpha));
                g.fillRect(0, y, w, 1);
            }
            mask(g, w, h, r);
        });
    }

    public static Identifier hueRamp(int w, int h, float r) {
        return get("hue_" + w + "_" + h + "_" + Math.round(r), w, h, g -> {
            for (int x = 0; x < w; x++) {
                g.setColor(new Color(Color.HSBtoRGB(x / (float) Math.max(1, w - 1), 1.0f, 1.0f)));
                g.fillRect(x, 0, 1, h);
            }
            mask(g, w, h, r);
        });
    }

    public static Identifier alphaRamp(int w, int h, float r) {
        return get("alpha_" + w + "_" + h + "_" + Math.round(r), w, h, g -> {
            for (int x = 0; x < w; x++) {
                int alpha = Math.round(255.0f * (x / (float) Math.max(1, w - 1)));
                g.setColor(new Color(255, 255, 255, alpha));
                g.fillRect(x, 0, 1, h);
            }
            mask(g, w, h, r);
        });
    }

    private static void mask(Graphics2D g, int w, int h, float r) {
        java.awt.geom.Area outside = new java.awt.geom.Area(new java.awt.Rectangle(0, 0, w, h));
        outside.subtract(new java.awt.geom.Area(
                new RoundRectangle2D.Float(0.0f, 0.0f, w, h, r * 2.0f, r * 2.0f)));
        g.setComposite(AlphaComposite.DstOut);
        g.setColor(Color.WHITE);
        g.fill(outside);
        g.setComposite(AlphaComposite.SrcOver);
    }

    private interface Painter {
        void paint(Graphics2D g);
    }

    private static Identifier get(String key, int w, int h, Painter painter) {
        Identifier cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        applyQuality(g);
        painter.paint(g);
        g.dispose();
        return upload(key, img);
    }

    private static Identifier upload(String key, BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        NativeImage pixels = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pixels.setPixel(x, y, img.getRGB(x, y));
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath(NS, "dyn/" + key.toLowerCase(Locale.ROOT));
        Minecraft.getInstance().getTextureManager()
                .register(id, new DynamicTexture(() -> "hatek/" + key, pixels));
        CACHE.put(key, id);
        return id;
    }

    static void applyQuality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.setComposite(AlphaComposite.SrcOver);
    }

    private static BufferedImage readPng(String name) {
        try (InputStream in = TexCache.class.getResourceAsStream(
                "/assets/" + NS + "/textures/gui/" + name + ".png")) {
            return in == null ? null : ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static BufferedImage gaussian(BufferedImage src, float sigma) {
        int radius = Math.max(1, (int) Math.ceil(sigma * 3.0f));
        int size = radius * 2 + 1;
        float[] row = new float[size];
        float sum = 0.0f;
        for (int i = 0; i < size; i++) {
            float d = i - radius;
            row[i] = (float) Math.exp(-(d * d) / (2.0 * sigma * sigma));
            sum += row[i];
        }
        for (int i = 0; i < size; i++) {
            row[i] /= sum;
        }
        BufferedImage pass = new ConvolveOp(new Kernel(size, 1, row), ConvolveOp.EDGE_ZERO_FILL, null)
                .filter(src, null);
        return new ConvolveOp(new Kernel(1, size, row), ConvolveOp.EDGE_ZERO_FILL, null)
                .filter(pass, null);
    }
}
