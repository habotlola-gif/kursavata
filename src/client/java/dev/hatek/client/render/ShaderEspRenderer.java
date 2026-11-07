package dev.hatek.client.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.hatek.client.module.impl.render.ShaderEsp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** Composites the vanilla entity-outline silhouettes with our own fill/outline/glow shader. */
public final class ShaderEspRenderer {
    private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withUniform("EspInfo", UniformType.UNIFORM_BUFFER)
            .withSampler("InSampler")
            .build();
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("hatek_client", "pipeline/shader_esp"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath("hatek_client", "core/shader_esp"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(LAYOUT)
            .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT), GpuFormat.RGBA8_UNORM,
                    ColorTargetState.WRITE_COLOR))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();
    private static final int UBO_SIZE = new Std140SizeCalculator()
            .putVec4().putVec4().putFloat().putFloat().putFloat().putInt().get();
    private static final double PHASE_PER_SECOND = Math.PI / 4.0;

    private static @Nullable MappableRingBuffer ubo;
    private static double phase;
    private static long lastFrameNanos = System.nanoTime();

    private ShaderEspRenderer() {
    }

    public static boolean isActive() {
        return ShaderEsp.active();
    }

    public static void composite(RenderTarget outlineTarget) {
        ShaderEsp settings = ShaderEsp.instance();
        if (settings == null) {
            return;
        }
        if (ubo == null) {
            ubo = new MappableRingBuffer(() -> "hatek esp UBO", GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM, UBO_SIZE);
        }
        long now = System.nanoTime();
        double seconds = Math.min((now - lastFrameNanos) / 1.0e9, 0.25);
        lastFrameNanos = now;
        phase = (phase + seconds * PHASE_PER_SECOND * settings.speed()) % (Math.PI * 2.0);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        int a = settings.colorA();
        int b = settings.colorB();
        try (GpuBufferSlice.MappedView view = ubo.currentBuffer().map(false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putVec4((a >> 16 & 0xFF) / 255.0f, (a >> 8 & 0xFF) / 255.0f, (a & 0xFF) / 255.0f, 1.0f)
                    .putVec4((b >> 16 & 0xFF) / 255.0f, (b >> 8 & 0xFF) / 255.0f, (b & 0xFF) / 255.0f, 1.0f)
                    .putFloat(settings.fill())
                    .putFloat(settings.glowPx())
                    .putFloat((float) phase)
                    .putInt(settings.colorModeIndex());
        }
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        try (RenderPass pass = encoder.createRenderPass(() -> "hatek shader esp", main.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("EspInfo", ubo.currentBuffer());
            pass.bindTexture("InSampler", outlineTarget.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(3, 1, 0, 0);
        }
        ubo.rotate();
    }
}
