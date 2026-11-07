package dev.hatek.client.ui.render;

import dev.hatek.mixin.accessor.GuiGraphicsExtractorAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/** Shapes Render2D lacks: filled triangles and rotated lines. Coordinates are framebuffer pixels (inside Render2D.begin). */
public final class Draw {
    private Draw() {
    }

    public static void triangle(GuiGraphicsExtractor gg, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        int color = Render2D.tint(argb);
        if ((color >>> 24) == 0) {
            return;
        }
        ((GuiGraphicsExtractorAccessor) gg).hatek$guiRenderState().addGuiElement(
                PolyRenderState.create(new Matrix3x2f((Matrix3x2fc) gg.pose()), x1, y1, x2, y2, x3, y3, color, null));
    }

    public static void line(GuiGraphicsExtractor gg, float x1, float y1, float x2, float y2, float thickness, int argb) {
        float length = (float) Math.hypot(x2 - x1, y2 - y1);
        if (length < 0.5f) {
            return;
        }
        gg.pose().pushMatrix();
        gg.pose().translate(x1, y1);
        gg.pose().rotate((float) Math.atan2(y2 - y1, x2 - x1));
        Render2D.rect(gg, 0.0f, -thickness / 2.0f, length, thickness, argb);
        gg.pose().popMatrix();
    }
}
