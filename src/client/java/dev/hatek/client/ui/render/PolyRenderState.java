package dev.hatek.client.ui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/** A filled single-colour triangle drawn through the stock GUI pipeline (as a degenerate quad). */
public record PolyRenderState(Matrix3x2fc pose, float x1, float y1, float x2, float y2, float x3, float y3, int color,
                              @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
        implements GuiElementRenderState {

    public static PolyRenderState create(Matrix3x2fc pose, float x1, float y1, float x2, float y2, float x3, float y3,
                                         int color, @Nullable ScreenRectangle scissor) {
        int minX = (int) Math.floor(Math.min(x1, Math.min(x2, x3))) - 1;
        int minY = (int) Math.floor(Math.min(y1, Math.min(y2, y3))) - 1;
        int maxX = (int) Math.ceil(Math.max(x1, Math.max(x2, x3))) + 1;
        int maxY = (int) Math.ceil(Math.max(y1, Math.max(y2, y3))) + 1;
        ScreenRectangle area = new ScreenRectangle(minX, minY, maxX - minX, maxY - minY).transformMaxBounds(pose);
        ScreenRectangle bounds = scissor != null ? scissor.intersection(area) : area;
        return new PolyRenderState(pose, x1, y1, x2, y2, x3, y3, color, scissor, bounds);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        consumer.addVertexWith2DPose(this.pose, this.x1, this.y1).setColor(this.color);
        consumer.addVertexWith2DPose(this.pose, this.x2, this.y2).setColor(this.color);
        consumer.addVertexWith2DPose(this.pose, this.x3, this.y3).setColor(this.color);
        consumer.addVertexWith2DPose(this.pose, this.x3, this.y3).setColor(this.color);
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }
}
