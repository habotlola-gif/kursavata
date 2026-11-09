package dev.hatek.client.module.impl.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ColorSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;

/**
 * Custom sky: a night sky gradient, a denser starry sky with twinkling and a moving aurora, drawn inside the game's
 * own sky pass (SkyRendererMixin) with the plain coloured-quad pipeline. Everything is built from quads that are
 * emitted with both windings, the overall brightness goes through the colour modulator, so the sky fades in and out
 * with the time of day and the rain. If anything goes wrong in the GPU calls the module switches itself off.
 */
public final class CustomSky extends Module {
    private static CustomSky instance;

    private static final int USAGE = 40;            // the usage flags the game uses for its own sky buffers
    private static final int STAR_GROUPS = 3;

    private final BoolSetting gradient = new BoolSetting("Night gradient", true);
    private final ColorSetting horizon = new ColorSetting("Gradient: horizon", 0xFF3B2A7A);
    private final ColorSetting zenith = new ColorSetting("Gradient: zenith", 0xFF050824);
    private final SliderSetting gradientStrength = new SliderSetting("Gradient strength (%)", 80.0, 10.0, 100.0, 5.0);
    private final BoolSetting stars = new BoolSetting("Starry sky", true);
    private final SliderSetting density = new SliderSetting("Stars: amount", 3500.0, 500.0, 9000.0, 250.0);
    private final SliderSetting starBrightness = new SliderSetting("Stars: brightness (%)", 100.0, 20.0, 150.0, 5.0);
    private final BoolSetting twinkle = new BoolSetting("Stars: twinkle", true);
    private final BoolSetting hideVanillaStars = new BoolSetting("Hide the vanilla stars", true);
    private final BoolSetting aurora = new BoolSetting("Aurora", true);
    private final ColorSetting auroraLow = new ColorSetting("Aurora: lower color", 0xFF3CFF9A);
    private final ColorSetting auroraHigh = new ColorSetting("Aurora: upper color", 0xFFA45CFF);
    private final SliderSetting auroraIntensity = new SliderSetting("Aurora: intensity (%)", 90.0, 10.0, 200.0, 5.0);
    private final SliderSetting auroraHeight = new SliderSetting("Aurora: height (degrees)", 38.0, 15.0, 70.0, 1.0);
    private final SliderSetting auroraSpeed = new SliderSetting("Aurora: speed (%)", 100.0, 20.0, 300.0, 10.0);
    private final SliderSetting auroraDirection = new SliderSetting("Aurora: direction (degrees, 0 = north)", 0.0, -180.0, 180.0, 5.0);
    private final BoolSetting always = new BoolSetting("Also in the daytime", false);

    private final GpuBuffer[] starBuffers = new GpuBuffer[STAR_GROUPS];
    private final int[] starIndexCounts = new int[STAR_GROUPS];
    private int builtStarCount = -1;
    private GpuBuffer domeBuffer;
    private int domeIndexCount;
    private int builtHorizon;
    private int builtZenith;
    private boolean failed;

    public CustomSky() {
        super("CustomSky", "A custom sky: night gradient, starry sky and an aurora", Category.RENDER);
        instance = this;
        this.horizon.visibleIf(this.gradient::value);
        this.zenith.visibleIf(this.gradient::value);
        this.gradientStrength.visibleIf(this.gradient::value);
        this.density.visibleIf(this.stars::value);
        this.starBrightness.visibleIf(this.stars::value);
        this.twinkle.visibleIf(this.stars::value);
        this.hideVanillaStars.visibleIf(this.stars::value);
        for (dev.hatek.client.module.setting.Setting setting : java.util.List.of(this.auroraLow, this.auroraHigh,
                this.auroraIntensity, this.auroraHeight, this.auroraSpeed, this.auroraDirection)) {
            setting.visibleIf(this.aurora::value);
        }
        with(this.gradient, this.horizon, this.zenith, this.gradientStrength, this.stars, this.density, this.starBrightness,
                this.twinkle, this.hideVanillaStars, this.aurora, this.auroraLow, this.auroraHigh, this.auroraIntensity,
                this.auroraHeight, this.auroraSpeed, this.auroraDirection, this.always);
    }

    public static CustomSky instance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        this.failed = false;
    }

    @Override
    protected void onDisable() {
        releaseBuffers();
    }

    /** True when the vanilla stars must not be drawn (SkyRendererMixin). */
    public static boolean replacesVanillaStars() {
        CustomSky self = instance;
        return self != null && self.isEnabled() && !self.failed && self.stars.value() && self.hideVanillaStars.value();
    }

    /** How strongly the custom sky shows now: 0 in the day, 1 at night, less in the rain. */
    private float visibility(float rainClear, float vanillaStarAlpha) {
        float night = this.always.value() ? 1.0f : Mth.clamp(vanillaStarAlpha * 2.0f, 0.0f, 1.0f);
        return night * Mth.clamp(rainClear, 0.0f, 1.0f);
    }

    // ------------------------------------------------------------------------------------------------ hooks

    /** Before the sun and the moon: the gradient dome. */
    public static void drawDome(RenderTarget target, PoseStack pose, float rainClear, float starAlpha) {
        CustomSky self = instance;
        if (self == null || !self.isEnabled() || self.failed || !self.gradient.value()) {
            return;
        }
        try {
            float visible = self.visibility(rainClear, starAlpha);
            if (visible <= 0.01f) {
                return;
            }
            int h = self.horizon.value();
            int z = self.zenith.value();
            if (self.domeBuffer == null || h != self.builtHorizon || z != self.builtZenith) {
                self.buildDome(h, z);
            }
            self.draw(target, pose, self.domeBuffer, self.domeIndexCount,
                    visible * (float) self.gradientStrength.value() / 100.0f);
        } catch (Throwable t) {
            self.fail(t);
        }
    }

    /** After the sun and the moon: the stars (rotating with the sky) and the aurora (fixed to the world). */
    public static void drawStarsAndAurora(RenderTarget target, PoseStack pose, float starAngle, float rainClear, float starAlpha) {
        CustomSky self = instance;
        if (self == null || !self.isEnabled() || self.failed) {
            return;
        }
        try {
            float visible = self.visibility(rainClear, starAlpha);
            if (visible <= 0.01f) {
                return;
            }
            if (self.stars.value()) {
                int count = (int) self.density.value();
                if (count != self.builtStarCount) {
                    self.buildStars(count);
                }
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(-90.0f));
                pose.mulPose(Axis.XP.rotation(starAngle));
                double seconds = System.nanoTime() / 1.0E9;
                float level = (float) self.starBrightness.value() / 100.0f;
                for (int g = 0; g < STAR_GROUPS; g++) {
                    float flicker = self.twinkle.value() ? 0.72f + 0.28f * (float) Math.sin(seconds * (1.3 + g * 0.55) + g * 2.1) : 1.0f;
                    self.draw(target, pose, self.starBuffers[g], self.starIndexCounts[g], visible * level * flicker);
                }
                pose.popPose();
            }
            if (self.aurora.value()) {
                self.drawAurora(target, pose, visible);
            }
        } catch (Throwable t) {
            self.fail(t);
        }
    }

    private void fail(Throwable t) {
        this.failed = true;
        releaseBuffers();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§cCustomSky: ошибка отрисовки, модуль остановлен (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")"));
        }
        t.printStackTrace();
    }

    private void releaseBuffers() {
        for (int i = 0; i < STAR_GROUPS; i++) {
            if (this.starBuffers[i] != null) {
                this.starBuffers[i].close();
                this.starBuffers[i] = null;
            }
        }
        this.builtStarCount = -1;
        if (this.domeBuffer != null) {
            this.domeBuffer.close();
            this.domeBuffer = null;
        }
    }

    // ------------------------------------------------------------------------------------------------ drawing

    private void draw(RenderTarget target, PoseStack pose, GpuBuffer buffer, int indexCount, float alpha) {
        if (buffer == null || indexCount <= 0 || alpha <= 0.002f) {
            return;
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(pose.last().pose());
        try {
            GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView),
                    new Vector4f(1.0f, 1.0f, 1.0f, Mth.clamp(alpha, 0.0f, 1.0f)));
            RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "hatek custom sky", target.getColorTextureView(), Optional.empty(),
                    target.getDepthTextureView(), OptionalDouble.empty())) {
                pass.setPipeline(RenderPipelines.GUI);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transform);
                pass.setVertexBuffer(0, buffer.slice());
                pass.setIndexBuffer(indices.getBuffer(indexCount), indices.type());
                pass.drawIndexed(indexCount, 1, 0, 0, 0);
            }
        } finally {
            modelView.popMatrix();
        }
    }

    /** One frame of the aurora: a new mesh every frame (the curtains move), uploaded, drawn and released. */
    private void drawAurora(RenderTarget target, PoseStack pose, float visible) {
        float[] verts = AuroraMesh.build(System.nanoTime() / 1.0E9 * this.auroraSpeed.value() / 100.0,
                this.auroraLow.value(), this.auroraHigh.value(), (float) this.auroraIntensity.value() / 100.0f,
                (float) this.auroraHeight.value(), (float) this.auroraDirection.value());
        int[] counts = new int[1];
        GpuBuffer buffer = upload(verts, counts);
        try {
            draw(target, pose, buffer, counts[0], visible);
        } finally {
            buffer.close();
        }
    }

    // ------------------------------------------------------------------------------------------------ meshes

    /**
     * Uploads quads given as 7 floats per vertex (x, y, z, r, g, b, a in 0..1), 4 vertices per quad. Every quad is
     * emitted twice, with both windings, so it is visible from either side whatever the culling is.
     */
    private static GpuBuffer upload(float[] quads, int[] indexCountOut) {
        int quadCount = quads.length / 28;
        int vertexSize = DefaultVertexFormat.POSITION_COLOR.getVertexSize();
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(quadCount * 8 * vertexSize)) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int q = 0; q < quadCount; q++) {
                for (int i = 0; i < 4; i++) {
                    put(builder, quads, q * 28 + i * 7);
                }
                for (int i = 3; i >= 0; i--) {
                    put(builder, quads, q * 28 + i * 7);
                }
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                indexCountOut[0] = mesh.drawState().indexCount();
                return RenderSystem.getDevice().createBuffer(() -> "hatek custom sky", USAGE, mesh.vertexBuffer());
            }
        }
    }

    private static void put(BufferBuilder builder, float[] q, int at) {
        builder.addVertex(q[at], q[at + 1], q[at + 2]).setColor(
                Math.round(Mth.clamp(q[at + 3], 0.0f, 1.0f) * 255.0f), Math.round(Mth.clamp(q[at + 4], 0.0f, 1.0f) * 255.0f),
                Math.round(Mth.clamp(q[at + 5], 0.0f, 1.0f) * 255.0f), Math.round(Mth.clamp(q[at + 6], 0.0f, 1.0f) * 255.0f));
    }

    private void buildDome(int horizonColor, int zenithColor) {
        if (this.domeBuffer != null) {
            this.domeBuffer.close();
        }
        final int rings = 14;
        final int segments = 36;
        final float radius = 96.0f;
        float[] quads = new float[rings * segments * 28];
        int at = 0;
        for (int r = 0; r < rings; r++) {
            float e0 = -12.0f + (float) r / rings * 102.0f;
            float e1 = -12.0f + (float) (r + 1) / rings * 102.0f;
            for (int s = 0; s < segments; s++) {
                float a0 = (float) s / segments * Mth.TWO_PI;
                float a1 = (float) (s + 1) / segments * Mth.TWO_PI;
                at = dome(quads, at, radius, a0, e0, horizonColor, zenithColor);
                at = dome(quads, at, radius, a1, e0, horizonColor, zenithColor);
                at = dome(quads, at, radius, a1, e1, horizonColor, zenithColor);
                at = dome(quads, at, radius, a0, e1, horizonColor, zenithColor);
            }
        }
        int[] counts = new int[1];
        this.domeBuffer = upload(quads, counts);
        this.domeIndexCount = counts[0];
        this.builtHorizon = horizonColor;
        this.builtZenith = zenithColor;
    }

    private static int dome(float[] out, int at, float radius, float azimuth, float elevationDeg, int horizonColor, int zenithColor) {
        float e = (float) Math.toRadians(elevationDeg);
        float t = Mth.clamp((elevationDeg + 12.0f) / 102.0f, 0.0f, 1.0f);
        float mix = (float) Math.pow(t, 0.55);
        float alpha = Mth.lerp(mix, 1.0f, 0.97f);
        out[at] = radius * Mth.cos(e) * Mth.sin(azimuth);
        out[at + 1] = radius * Mth.sin(e);
        out[at + 2] = -radius * Mth.cos(e) * Mth.cos(azimuth);
        out[at + 3] = Mth.lerp(mix, ((horizonColor >> 16) & 0xFF) / 255.0f, ((zenithColor >> 16) & 0xFF) / 255.0f);
        out[at + 4] = Mth.lerp(mix, ((horizonColor >> 8) & 0xFF) / 255.0f, ((zenithColor >> 8) & 0xFF) / 255.0f);
        out[at + 5] = Mth.lerp(mix, (horizonColor & 0xFF) / 255.0f, (zenithColor & 0xFF) / 255.0f);
        out[at + 6] = alpha;
        return at + 7;
    }

    private void buildStars(int count) {
        for (int i = 0; i < STAR_GROUPS; i++) {
            if (this.starBuffers[i] != null) {
                this.starBuffers[i].close();
            }
        }
        Random random = new Random(20260611L);
        int perGroup = Math.max(1, count / STAR_GROUPS);
        for (int g = 0; g < STAR_GROUPS; g++) {
            float[] quads = new float[perGroup * 28];
            for (int s = 0; s < perGroup; s++) {
                // a uniform direction on the sphere
                float y = random.nextFloat() * 2.0f - 1.0f;
                float phi = random.nextFloat() * Mth.TWO_PI;
                float ring = Mth.sqrt(Math.max(0.0f, 1.0f - y * y));
                float dx = ring * Mth.cos(phi);
                float dz = ring * Mth.sin(phi);
                float radius = 99.0f;
                // two vectors across the direction
                float ux = -dz;
                float uz = dx;
                float ul = Mth.sqrt(ux * ux + uz * uz);
                if (ul < 1.0e-4f) {
                    ux = 1.0f;
                    uz = 0.0f;
                    ul = 1.0f;
                }
                ux /= ul;
                uz /= ul;
                // v = d x u
                float vxx = y * uz;
                float vyy = dz * ux - dx * uz;
                float vzz = -y * ux;
                float size = 0.22f + random.nextFloat() * random.nextFloat() * 0.7f;
                float brightness = 0.55f + random.nextFloat() * 0.45f;
                // mostly white, some blue-white and some yellow stars
                float tint = random.nextFloat();
                float r = tint < 0.22f ? 0.72f : tint > 0.86f ? 1.0f : 1.0f;
                float gg = tint < 0.22f ? 0.82f : tint > 0.86f ? 0.9f : 1.0f;
                float b = tint < 0.22f ? 1.0f : tint > 0.86f ? 0.65f : 1.0f;
                float rot = random.nextFloat() * Mth.TWO_PI;
                float cr = Mth.cos(rot) * size;
                float sr = Mth.sin(rot) * size;
                float[][] corner = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
                for (int c = 0; c < 4; c++) {
                    float a = corner[c][0];
                    float bb = corner[c][1];
                    float ox = (a * cr - bb * sr);
                    float oy = (a * sr + bb * cr);
                    int at = s * 28 + c * 7;
                    quads[at] = dx * radius + ux * ox + vxx * oy;
                    quads[at + 1] = y * radius + vyy * oy;
                    quads[at + 2] = dz * radius + uz * ox + vzz * oy;
                    quads[at + 3] = r;
                    quads[at + 4] = gg;
                    quads[at + 5] = b;
                    quads[at + 6] = brightness;
                }
            }
            int[] counts = new int[1];
            this.starBuffers[g] = upload(quads, counts);
            this.starIndexCounts[g] = counts[0];
        }
        this.builtStarCount = count;
    }

    // ------------------------------------------------------------------------------------------------ aurora

    /** The aurora curtains as a list of quads (7 floats per vertex), rebuilt every frame. */
    private static final class AuroraMesh {
        private static final int CURTAINS = 3;
        private static final int COLUMNS = 72;
        private static final int ROWS = 8;

        static float[] build(double t, int lowColor, int highColor, float intensity, float height, float directionDeg) {
            float[] out = new float[CURTAINS * COLUMNS * ROWS * 28];
            int at = 0;
            float[] low = {((lowColor >> 16) & 0xFF) / 255.0f, ((lowColor >> 8) & 0xFF) / 255.0f, (lowColor & 0xFF) / 255.0f};
            float[] high = {((highColor >> 16) & 0xFF) / 255.0f, ((highColor >> 8) & 0xFF) / 255.0f, (highColor & 0xFF) / 255.0f};
            for (int c = 0; c < CURTAINS; c++) {
                float heading = directionDeg + (c - 1) * 34.0f + (float) Math.sin(t * 0.035 + c * 1.7) * 14.0f;
                float halfSpan = 62.0f - c * 6.0f;
                float baseElevation = 7.0f + c * 5.0f;
                float weight = c == 0 ? 1.0f : c == 1 ? 0.7f : 0.5f;
                float radius = 92.0f - c * 2.0f;
                for (int i = 0; i < COLUMNS; i++) {
                    float u0 = (float) i / COLUMNS;
                    float u1 = (float) (i + 1) / COLUMNS;
                    for (int j = 0; j < ROWS; j++) {
                        float v0 = (float) j / ROWS;
                        float v1 = (float) (j + 1) / ROWS;
                        at = vertex(out, at, t, c, u0, v0, heading, halfSpan, baseElevation, height, radius, intensity * weight, low, high);
                        at = vertex(out, at, t, c, u1, v0, heading, halfSpan, baseElevation, height, radius, intensity * weight, low, high);
                        at = vertex(out, at, t, c, u1, v1, heading, halfSpan, baseElevation, height, radius, intensity * weight, low, high);
                        at = vertex(out, at, t, c, u0, v1, heading, halfSpan, baseElevation, height, radius, intensity * weight, low, high);
                    }
                }
            }
            return out;
        }

        private static int vertex(float[] out, int at, double t, int curtain, float u, float v, float heading, float halfSpan,
                                  float baseElevation, float height, float radius, float strength, float[] low, float[] high) {
            // the curtain waves sideways and its height breathes
            float wave = (float) (6.0 * Math.sin(u * 7.0 + t * 0.35 + curtain) + 3.0 * Math.sin(u * 17.0 - t * 0.6 + curtain * 2.0));
            float azimuth = (float) Math.toRadians(heading + (u - 0.5f) * 2.0f * halfSpan + wave);
            float breathe = 1.0f + 0.14f * (float) Math.sin(u * 5.0 + t * 0.21 + curtain);
            float elevation = (float) Math.toRadians(baseElevation + v * height * breathe);

            // brightness: the ends of the curtain fade out, the rays flicker along it, the lower edge is the brightest
            float envelope = (float) Math.pow(Math.max(0.0, Math.sin(Math.PI * u)), 0.7);
            float rays = 0.55f + 0.45f * (float) (Math.sin(u * 41.0 + t * 1.1 + curtain * 2.0) * Math.sin(u * 13.0 - t * 0.7));
            float rise = Math.min(1.0f, v / 0.12f);
            float fall = (float) Math.pow(Math.max(0.0f, 1.0f - v), 1.5);
            float alpha = Mth.clamp(strength * 0.55f * envelope * (0.4f + 0.6f * rays) * (0.25f + 0.75f * rise) * fall, 0.0f, 1.0f);

            float mix = (float) Math.pow(v, 0.8);
            out[at] = radius * Mth.cos(elevation) * Mth.sin(azimuth);
            out[at + 1] = radius * Mth.sin(elevation);
            out[at + 2] = -radius * Mth.cos(elevation) * Mth.cos(azimuth);
            out[at + 3] = Mth.lerp(mix, low[0], high[0]);
            out[at + 4] = Mth.lerp(mix, low[1], high[1]);
            out[at + 5] = Mth.lerp(mix, low[2], high[2]);
            out[at + 6] = alpha;
            return at + 7;
        }
    }
}
