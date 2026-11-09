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
    private final ColorSetting horizon = new ColorSetting("Gradient: horizon", 0xFF1A2A5E);
    private final ColorSetting zenith = new ColorSetting("Gradient: zenith", 0xFF03040F);
    private final SliderSetting gradientStrength = new SliderSetting("Gradient strength (%)", 80.0, 10.0, 100.0, 5.0);
    private final BoolSetting stars = new BoolSetting("Starry sky", true);
    private final SliderSetting density = new SliderSetting("Stars: amount", 3500.0, 500.0, 9000.0, 250.0);
    private final SliderSetting starBrightness = new SliderSetting("Stars: brightness (%)", 100.0, 20.0, 150.0, 5.0);
    private final BoolSetting twinkle = new BoolSetting("Stars: twinkle", true);
    private final BoolSetting hideVanillaStars = new BoolSetting("Hide the vanilla stars", true);
    private final BoolSetting aurora = new BoolSetting("Aurora", true);
    private final ColorSetting auroraLow = new ColorSetting("Aurora: lower color", 0xFF3CFF9A);
    private final ColorSetting auroraHigh = new ColorSetting("Aurora: upper color", 0xFFA45CFF);
    private final SliderSetting auroraIntensity = new SliderSetting("Aurora: intensity (%)", 100.0, 10.0, 200.0, 5.0);
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
                    visible * (float) self.gradientStrength.value() / 100.0f, false);
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
                    self.draw(target, pose, self.starBuffers[g], self.starIndexCounts[g], visible * level * flicker, true);
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

    /**
     * Draws a mesh. Additive meshes (stars, aurora) glow on the dark sky: their colours are already multiplied by
     * their brightness and the overall fade goes through the colour of the modulator; the translucent dome fades
     * through the alpha of the modulator.
     */
    private void draw(RenderTarget target, PoseStack pose, GpuBuffer buffer, int indexCount, float alpha, boolean additive) {
        if (buffer == null || indexCount <= 0 || alpha <= 0.002f) {
            return;
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(pose.last().pose());
        try {
            float fade = Mth.clamp(alpha, 0.0f, 1.0f);
            GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView),
                    additive ? new Vector4f(fade, fade, fade, 1.0f) : new Vector4f(1.0f, 1.0f, 1.0f, fade));
            RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "hatek custom sky", target.getColorTextureView(), Optional.empty(),
                    target.getDepthTextureView(), OptionalDouble.empty())) {
                pass.setPipeline(additive ? RenderPipelines.GUI_TEXT_HIGHLIGHT : RenderPipelines.GUI);
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
            draw(target, pose, buffer, counts[0], visible, true);
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
                    quads[at + 3] = r * brightness;
                    quads[at + 4] = gg * brightness;
                    quads[at + 5] = b * brightness;
                    quads[at + 6] = 1.0f;
                }
            }
            int[] counts = new int[1];
            this.starBuffers[g] = upload(quads, counts);
            this.starIndexCounts[g] = counts[0];
        }
        this.builtStarCount = count;
    }

    // ------------------------------------------------------------------------------------------------ aurora

    /**
     * The aurora as a list of quads (7 floats per vertex, premultiplied colour for the additive pipeline), rebuilt
     * every frame. Two curtains of vertical rays: a bright lower edge with a pink fringe that fades up into a long
     * soft tail, rays whose brightness and height drift along the curtain, a folded path that is brighter where it is
     * seen edge-on. The shape was tuned against an offline preview of the same formulas.
     */
    private static final class AuroraMesh {
        private static final int CURTAINS = 2;
        private static final int COLUMNS = 140;
        private static final int ROWS = 14;
        private static final float[] FRINGE = {1.0f, 0.24f, 0.55f};

        private static double hash(double k, int seed) {
            double v = Math.sin(k * 127.1 + seed * 311.7) * 43758.5453;
            return v - Math.floor(v);
        }

        /** Smooth value noise along one axis. */
        private static double noise(double x, int seed) {
            double i = Math.floor(x);
            double f = x - i;
            double a = hash(i, seed);
            double b = hash(i + 1.0, seed);
            double s = f * f * (3.0 - 2.0 * f);
            return a + (b - a) * s;
        }

        static float[] build(double t, int lowColor, int highColor, float intensity, float height, float directionDeg) {
            float[] low = {((lowColor >> 16) & 0xFF) / 255.0f, ((lowColor >> 8) & 0xFF) / 255.0f, (lowColor & 0xFF) / 255.0f};
            float[] high = {((highColor >> 16) & 0xFF) / 255.0f, ((highColor >> 8) & 0xFF) / 255.0f, (highColor & 0xFF) / 255.0f};
            float[] out = new float[CURTAINS * COLUMNS * ROWS * 28];
            int at = 0;
            double[] vs = new double[ROWS + 1];
            for (int j = 0; j <= ROWS; j++) {
                vs[j] = Math.pow((double) j / ROWS, 1.7);
            }
            int stride = ROWS + 1;
            float[] grid = new float[(COLUMNS + 1) * stride * 6];
            for (int c = 0; c < CURTAINS; c++) {
                double heading = directionDeg + (c - 0.5 * (CURTAINS - 1)) * 46.0 + Math.sin(t * 0.035 + c * 1.7) * 14.0;
                double half = 58.0 - c * 8.0;
                double weight = c == 0 ? 1.0 : 0.62;
                double radiusBase = 90.0 - c * 3.0;
                for (int i = 0; i <= COLUMNS; i++) {
                    double u = (double) i / COLUMNS;
                    double wave = 7.0 * Math.sin(u * 5.3 + t * 0.30 + c) + 3.5 * Math.sin(u * 11.7 - t * 0.45 + c * 2.0)
                            + 1.6 * Math.sin(u * 23.0 - t * 0.8);
                    double azimuth = Math.toRadians(heading + (u - 0.5) * 2.0 * half + wave);
                    double radius = radiusBase * (1.0 + 0.07 * Math.sin(u * 9.1 + t * 0.22 + c * 1.3) + 0.04 * Math.sin(u * 21.0 - t * 0.5));
                    double ray = Math.pow(0.08 + 0.92 * noise(u * 52.0 + t * 0.55, c + 3), 1.6);
                    double ray2 = noise(u * 13.0 - t * 0.25, c + 9);
                    double level = Math.min(1.0, ray * (0.45 + 0.9 * ray2) * 1.5) * (0.88 + 0.12 * Math.sin(t * 1.3 + u * 40.0 + c));
                    double top = height * (0.5 + 0.5 * noise(u * 7.0 + t * 0.12, c + 21)) * (0.45 + 0.8 * level);
                    double base = 13.0 + 5.0 * Math.sin(u * 4.0 + t * 0.18 + c) + 2.0 * Math.sin(u * 13.0 - t * 0.3);
                    double envelope = Math.pow(Math.max(0.0, Math.sin(Math.PI * u)), 0.55);
                    double fold = 1.0 + 0.8 * Math.min(1.0, Math.abs(0.07 * 9.1 * Math.cos(u * 9.1 + t * 0.22 + c * 1.3)));
                    double column = intensity * weight * envelope * level * fold;
                    float x = (float) (radius * Math.sin(azimuth));
                    float z = (float) (-radius * Math.cos(azimuth));
                    for (int j = 0; j <= ROWS; j++) {
                        double v = vs[j];
                        double profile = (Math.exp(-v * 2.6) * 0.85 + 0.15 * (1.0 - v) * (1.0 - v))
                                * Math.pow(Math.min(1.0, v / 0.05), 0.8) * Math.pow(1.0 - v, 0.9);
                        double edge = Math.exp(-(v / 0.05) * (v / 0.05));
                        double b = column * profile;
                        float cr;
                        float cg;
                        float cb;
                        if (v < 0.09) {
                            double k = v / 0.09;
                            cr = (float) ((FRINGE[0] * 0.6 + low[0] * 0.4) * (1.0 - k) + low[0] * k);
                            cg = (float) ((FRINGE[1] * 0.6 + low[1] * 0.4) * (1.0 - k) + low[1] * k);
                            cb = (float) ((FRINGE[2] * 0.6 + low[2] * 0.4) * (1.0 - k) + low[2] * k);
                        } else {
                            double m = Math.pow(Math.min(1.0, (v - 0.09) / 0.8), 0.9);
                            cr = (float) (low[0] * (1.0 - m) + high[0] * m);
                            cg = (float) (low[1] * (1.0 - m) + high[1] * m);
                            cb = (float) (low[2] * (1.0 - m) + high[2] * m);
                        }
                        float glow = (float) (edge * b * 0.12);
                        int g = (i * stride + j) * 6;
                        grid[g] = x;
                        grid[g + 1] = (float) (base + v * top);
                        grid[g + 2] = z;
                        grid[g + 3] = (float) (cr * b * 1.15) + glow;
                        grid[g + 4] = (float) (cg * b * 1.15) + glow;
                        grid[g + 5] = (float) (cb * b * 1.15) + glow;
                    }
                }
                for (int i = 0; i < COLUMNS; i++) {
                    for (int j = 0; j < ROWS; j++) {
                        at = put(out, at, grid, i, j, stride);
                        at = put(out, at, grid, i + 1, j, stride);
                        at = put(out, at, grid, i + 1, j + 1, stride);
                        at = put(out, at, grid, i, j + 1, stride);
                    }
                }
            }
            return out;
        }

        private static int put(float[] out, int at, float[] grid, int i, int j, int stride) {
            int g = (i * stride + j) * 6;
            out[at] = grid[g];
            out[at + 1] = grid[g + 1];
            out[at + 2] = grid[g + 2];
            out[at + 3] = grid[g + 3];
            out[at + 4] = grid[g + 4];
            out[at + 5] = grid[g + 5];
            out[at + 6] = 1.0f;
            return at + 7;
        }
    }
}
