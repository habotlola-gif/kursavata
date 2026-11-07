package dev.hatek.client.ui.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2f;

public final class Render2D {
    private static final float[] ALPHA_STACK = new float[16];
    private static int alphaDepth;
    private static float alpha = 1.0f;

    private Render2D() {
    }

    public static float guiScale() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }

    public static int screenWidth() {
        return Minecraft.getInstance().getWindow().getWidth();
    }

    public static int screenHeight() {
        return Minecraft.getInstance().getWindow().getHeight();
    }

    public static double mouseX() {
        Minecraft mc = Minecraft.getInstance();
        int screen = Math.max(1, mc.getWindow().getScreenWidth());
        return mc.mouseHandler.xpos() * mc.getWindow().getWidth() / screen;
    }

    public static double mouseY() {
        Minecraft mc = Minecraft.getInstance();
        int screen = Math.max(1, mc.getWindow().getScreenHeight());
        return mc.mouseHandler.ypos() * mc.getWindow().getHeight() / screen;
    }

    public static void begin(GuiGraphicsExtractor gg) {
        float inv = 1.0f / guiScale();
        gg.pose().pushMatrix();
        gg.pose().scale(inv, inv);
        alphaDepth = 0;
        alpha = 1.0f;
    }

    public static void end(GuiGraphicsExtractor gg) {
        gg.pose().popMatrix();
        alphaDepth = 0;
        alpha = 1.0f;
    }

    public static void pushScale(GuiGraphicsExtractor gg, float pivotX, float pivotY, float scale) {
        gg.pose().pushMatrix();
        gg.pose().translate(pivotX, pivotY);
        gg.pose().scale(scale, scale);
        gg.pose().translate(-pivotX, -pivotY);
    }

    public static void pushTranslate(GuiGraphicsExtractor gg, float dx, float dy) {
        gg.pose().pushMatrix();
        gg.pose().translate(dx, dy);
    }

    public static void popTransform(GuiGraphicsExtractor gg) {
        gg.pose().popMatrix();
    }

    public static void pushAlpha(float factor) {
        if (alphaDepth < ALPHA_STACK.length) {
            ALPHA_STACK[alphaDepth++] = alpha;
        }
        alpha = Mth.clamp(alpha * factor, 0.0f, 1.0f);
    }

    public static void popAlpha() {
        if (alphaDepth > 0) {
            alpha = ALPHA_STACK[--alphaDepth];
        } else {
            alpha = 1.0f;
        }
    }

    public static void pushClip(GuiGraphicsExtractor gg, float x, float y, float w, float h) {
        Matrix3x2fStack pose = gg.pose();
        Vector2f min = pose.transformPosition(x, y, new Vector2f());
        Vector2f max = pose.transformPosition(x + w, y + h, new Vector2f());
        pose.pushMatrix();
        pose.identity();
        gg.enableScissor(Mth.floor(Math.min(min.x, max.x)), Mth.floor(Math.min(min.y, max.y)),
                Mth.ceil(Math.max(min.x, max.x)), Mth.ceil(Math.max(min.y, max.y)));
        pose.popMatrix();
    }

    public static void popClip(GuiGraphicsExtractor gg) {
        gg.disableScissor();
    }

    public static void rect(GuiGraphicsExtractor gg, float x, float y, float w, float h, int argb) {
        int color = tint(argb);
        if ((color >>> 24) == 0 || w <= 0.0f || h <= 0.0f) {
            return;
        }
        int x1 = Math.round(x);
        int y1 = Math.round(y);
        gg.fill(x1, y1, x1 + Math.max(1, Math.round(w)), y1 + Math.max(1, Math.round(h)), color);
    }

    public static void round(GuiGraphicsExtractor gg, float x, float y, float w, float h, float r, int argb) {
        int color = tint(argb);
        if ((color >>> 24) == 0 || w <= 0.0f || h <= 0.0f) {
            return;
        }
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.max(1, Math.round(w));
        int ih = Math.max(1, Math.round(h));
        float radius = Math.min(r, Math.min(iw, ih) / 2.0f);
        if (radius < 0.5f) {
            int x1 = ix;
            int y1 = iy;
            gg.fill(x1, y1, x1 + iw, y1 + ih, color);
            return;
        }
        int ir = (int) radius;
        boolean sliceable = Math.abs(radius - ir) < 0.01f && ir * 2 <= iw && ir * 2 <= ih;
        if (!sliceable) {
            rawTexture(gg, TexCache.roundRect(iw, ih, radius), ix, iy, iw, ih, color);
            return;
        }
        Identifier disc = TexCache.corner(ir);
        int d = ir * 2;
        blitRaw(gg, disc, ix, iy, 0.0f, 0.0f, ir, ir, d, d, color);
        blitRaw(gg, disc, ix + iw - ir, iy, ir, 0.0f, ir, ir, d, d, color);
        blitRaw(gg, disc, ix, iy + ih - ir, 0.0f, ir, ir, ir, d, d, color);
        blitRaw(gg, disc, ix + iw - ir, iy + ih - ir, ir, ir, ir, ir, d, d, color);
        if (iw - d > 0) {
            gg.fill(ix + ir, iy, ix + iw - ir, iy + ir, color);
            gg.fill(ix + ir, iy + ih - ir, ix + iw - ir, iy + ih, color);
        }
        if (ih - d > 0) {
            gg.fill(ix, iy + ir, ix + iw, iy + ih - ir, color);
        }
    }

    public static void circle(GuiGraphicsExtractor gg, float x, float y, float d, int argb) {
        int id = Math.max(1, Math.round(d));
        texture(gg, TexCache.circle(id), Math.round(x), Math.round(y), id, id, argb);
    }

    public static void icon(GuiGraphicsExtractor gg, String name, float x, float y, float w, float h, int argb) {
        icon(gg, name, x, y, w, h, argb, false);
    }

    public static void icon(GuiGraphicsExtractor gg, String name, float x, float y,
                            float w, float h, int argb, boolean flipY) {
        int iw = Math.max(1, Math.round(w));
        int ih = Math.max(1, Math.round(h));
        rawTexture(gg, TexCache.icon(name, iw, ih, flipY), Math.round(x), Math.round(y), iw, ih, tint(argb));
    }

    public static void texture(GuiGraphicsExtractor gg, Identifier id, float x, float y, float w, float h, int argb) {
        rawTexture(gg, id, Math.round(x), Math.round(y), Math.max(1, Math.round(w)),
                Math.max(1, Math.round(h)), tint(argb));
    }

    private static void rawTexture(GuiGraphicsExtractor gg, Identifier id, int x, int y, int w, int h, int color) {
        if ((color >>> 24) == 0) {
            return;
        }
        blitRaw(gg, id, x, y, 0.0f, 0.0f, w, h, w, h, color);
    }

    public static void blit(GuiGraphicsExtractor gg, Identifier id, int x, int y,
                            float u, float v, int w, int h, int texW, int texH, int argb) {
        blitRaw(gg, id, x, y, u, v, w, h, texW, texH, tint(argb));
    }

    private static void blitRaw(GuiGraphicsExtractor gg, Identifier id, int x, int y,
                                float u, float v, int w, int h, int texW, int texH, int color) {
        gg.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, texW, texH, color);
    }

    private static int tint(int argb) {
        if (alpha >= 1.0f) {
            return argb;
        }
        int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        return (Mth.clamp(a, 0, 255) << 24) | (argb & 0x00FFFFFF);
    }

    public static int withAlpha(int argb, float factor) {
        int a = Mth.clamp(Math.round(((argb >>> 24) & 0xFF) * factor), 0, 255);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    public static int lerp(int from, int to, float t) {
        float k = Mth.clamp(t, 0.0f, 1.0f);
        int a = Math.round(Mth.lerp(k, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF));
        int r = Math.round(Mth.lerp(k, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(k, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int b = Math.round(Mth.lerp(k, from & 0xFF, to & 0xFF));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static boolean hovered(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
