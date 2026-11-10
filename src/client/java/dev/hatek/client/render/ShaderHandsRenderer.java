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
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.hatek.client.module.impl.render.ShaderHands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * ShaderHands: the hand/item render is redirected into a private buffer, then composited onto the screen by a
 * shader (gradient fill, glow) and, optionally, a fire trail that is simulated in two ping-pong buffers.
 */
public final class ShaderHandsRenderer {
    private static final String NS = "hatek_client";
    private static final BindGroupLayout HANDS_LAYOUT = BindGroupLayout.builder()
            .withUniform("HandsInfo", UniformType.UNIFORM_BUFFER)
            .withSampler("InSampler")
            .withSampler("TrailSampler")
            .build();
    private static final BindGroupLayout TRAIL_LAYOUT = BindGroupLayout.builder()
            .withUniform("TrailInfo", UniformType.UNIFORM_BUFFER)
            .withSampler("HandSampler")
            .withSampler("TrailSampler")
            .build();
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(NS, "pipeline/shader_hands"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath(NS, "core/shader_hands"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(HANDS_LAYOUT)
            .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT), GpuFormat.RGBA8_UNORM,
                    ColorTargetState.WRITE_COLOR))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();
    private static final RenderPipeline TRAIL_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(NS, "pipeline/hands_trail"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath(NS, "core/hands_trail"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(TRAIL_LAYOUT)
            .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();
    private static final int UBO_SIZE = new Std140SizeCalculator()
            .putVec4().putVec4().putFloat().putFloat().putFloat().putInt().putFloat().putInt().putFloat().get();
    private static final int TRAIL_UBO_SIZE = new Std140SizeCalculator().putFloat().putFloat().putFloat().putFloat().get();
    private static final double PHASE_PER_SECOND = Math.PI / 4.0;
    private static final float TRAIL_LIFE = 0.42f;
    private static final float TRAIL_RISE = 0.5f;
    private static final int UBO_USAGE = GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM;
    private static final long START = System.nanoTime();

    private static @Nullable RenderTarget handTarget;
    private static final RenderTarget[] TRAIL_TARGETS = new RenderTarget[2];
    private static int trailCurrent;
    private static boolean trailNeedsClear = true;
    private static @Nullable MappableRingBuffer ubo;
    private static @Nullable MappableRingBuffer trailUbo;
    private static boolean redirecting;
    private static double phase;
    private static long lastFrameNanos = System.nanoTime();

    private ShaderHandsRenderer() {
    }

    /** While redirecting, everything that would draw into the main target draws into the hand buffer. */
    public static @Nullable RenderTarget redirectTarget() {
        return redirecting ? handTarget : null;
    }

    public static boolean isRedirecting() {
        return redirecting;
    }

    /** Before the hand features are rendered. */
    public static void begin() {
        if (!ShaderHands.active()) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (handTarget == null) {
            handTarget = new TextureTarget("hatek hands", main.width, main.height, true, GpuFormat.RGBA8_UNORM);
        } else if (handTarget.width != main.width || handTarget.height != main.height) {
            handTarget.resize(main.width, main.height);
        }
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                handTarget.getColorTexture(), new Vector4f(0.0f), handTarget.getDepthTexture(), 0.0);
        redirecting = true;
    }

    /** After the hand features are rendered into the hand buffer. */
    public static void end() {
        redirecting = false;
        ShaderHands settings = ShaderHands.instance();
        if (handTarget == null || settings == null) {
            return;
        }
        if (ubo == null) {
            ubo = new MappableRingBuffer(() -> "hatek hands UBO", UBO_USAGE, UBO_SIZE);
        }
        long now = System.nanoTime();
        double seconds = Math.min((now - lastFrameNanos) / 1.0e9, 0.25);
        lastFrameNanos = now;
        phase = (phase + seconds * PHASE_PER_SECOND * settings.speed()) % (Math.PI * 2.0);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        boolean trail = settings.trail();
        RenderTarget trailTarget = trail ? updateTrail(encoder, main, (float) seconds, settings) : null;
        if (!trail) {
            trailNeedsClear = true;
        }

        int a = settings.colorA();
        int b = settings.colorB();
        try (GpuBufferSlice.MappedView view = ubo.currentBuffer().map(false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putVec4((a >> 16 & 0xFF) / 255.0f, (a >> 8 & 0xFF) / 255.0f, (a & 0xFF) / 255.0f, 1.0f)
                    .putVec4((b >> 16 & 0xFF) / 255.0f, (b >> 8 & 0xFF) / 255.0f, (b & 0xFF) / 255.0f, 1.0f)
                    .putFloat(settings.fill())
                    .putFloat(settings.glowPx())
                    .putFloat((float) phase)
                    .putInt(settings.modeIndex())
                    .putFloat(trailTarget != null ? settings.trailBrightness() : 0.0f)
                    .putInt(settings.trailPaletteIndex())
                    .putFloat(settings.shadow());
        }
        try (RenderPass pass = encoder.createRenderPass(() -> "hatek shader hands", main.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("HandsInfo", ubo.currentBuffer());
            pass.bindTexture("InSampler", handTarget.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            RenderTarget trailSource = trailTarget != null ? trailTarget : handTarget;
            pass.bindTexture("TrailSampler", trailSource.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
        ubo.rotate();
    }

    /** One step of the trail simulation: previous heat rises, wobbles and cools; this frame's hands add new heat. */
    private static RenderTarget updateTrail(CommandEncoder encoder, RenderTarget main, float seconds, ShaderHands settings) {
        for (int i = 0; i < 2; i++) {
            if (TRAIL_TARGETS[i] == null) {
                TRAIL_TARGETS[i] = new TextureTarget("hatek hands trail " + i, main.width, main.height, false, GpuFormat.RGBA8_UNORM);
                trailNeedsClear = true;
            } else if (TRAIL_TARGETS[i].width != main.width || TRAIL_TARGETS[i].height != main.height) {
                TRAIL_TARGETS[i].resize(main.width, main.height);
                trailNeedsClear = true;
            }
        }
        if (trailNeedsClear) {
            encoder.clearColorTexture(TRAIL_TARGETS[0].getColorTexture(), new Vector4f(0.0f));
            encoder.clearColorTexture(TRAIL_TARGETS[1].getColorTexture(), new Vector4f(0.0f));
            trailNeedsClear = false;
        }
        if (trailUbo == null) {
            trailUbo = new MappableRingBuffer(() -> "hatek hands trail UBO", UBO_USAGE, TRAIL_UBO_SIZE);
        }
        float life = TRAIL_LIFE * settings.trailLength();
        float decay = (float) Math.exp(-3.5 * seconds / life);
        float time = (float) ((System.nanoTime() - START) / 1.0e9 % 3600.0);
        try (GpuBufferSlice.MappedView view = trailUbo.currentBuffer().map(false, true)) {
            Std140Builder.intoBuffer(view.data()).putFloat(decay).putFloat(TRAIL_RISE * seconds).putFloat(time).putFloat(1.0f);
        }
        RenderTarget previous = TRAIL_TARGETS[trailCurrent];
        RenderTarget next = TRAIL_TARGETS[1 - trailCurrent];
        try (RenderPass pass = encoder.createRenderPass(() -> "hatek hands trail", next.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(TRAIL_PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("TrailInfo", trailUbo.currentBuffer());
            pass.bindTexture("HandSampler", handTarget.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.bindTexture("TrailSampler", previous.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
        trailUbo.rotate();
        trailCurrent = 1 - trailCurrent;
        return next;
    }
}
