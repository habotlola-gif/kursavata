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
    private static final int MILKY = STAR_GROUPS;           // the extra glow buffer of the Milky Way

    private final BoolSetting gradient = new BoolSetting("Night gradient", true);
    private final ColorSetting horizon = new ColorSetting("Gradient: horizon", 0xFF1A2A5E);
    private final ColorSetting zenith = new ColorSetting("Gradient: zenith", 0xFF03040F);
    private final SliderSetting gradientStrength = new SliderSetting("Gradient strength (%)", 80.0, 10.0, 100.0, 5.0);
    private final BoolSetting stars = new BoolSetting("Starry sky", true);
    private final SliderSetting density = new SliderSetting("Stars: amount", 3500.0, 500.0, 9000.0, 250.0);
    private final SliderSetting starBrightness = new SliderSetting("Stars: brightness (%)", 100.0, 20.0, 150.0, 5.0);
    private final BoolSetting twinkle = new BoolSetting("Stars: twinkle", true);
    private final BoolSetting hideVanillaStars = new BoolSetting("Hide the vanilla stars", true);
    private final BoolSetting milkyWay = new BoolSetting("Milky Way", true);
    private final BoolSetting haze = new BoolSetting("Horizon haze", true);
    private final ColorSetting hazeColor = new ColorSetting("Haze color", 0xFF2B4C9A);
    private final BoolSetting aurora = new BoolSetting("Aurora", true);
    private final ColorSetting auroraLow = new ColorSetting("Aurora: lower color", 0xFF3CFF9A);
    private final ColorSetting auroraHigh = new ColorSetting("Aurora: upper color", 0xFFA45CFF);
    private final SliderSetting auroraIntensity = new SliderSetting("Aurora: intensity (%)", 100.0, 10.0, 200.0, 5.0);
    private final SliderSetting auroraHeight = new SliderSetting("Aurora: height (degrees)", 38.0, 15.0, 70.0, 1.0);
    private final SliderSetting auroraSpeed = new SliderSetting("Aurora: speed (%)", 100.0, 20.0, 300.0, 10.0);
    private final SliderSetting auroraDirection = new SliderSetting("Aurora: direction (degrees, 0 = north)", 0.0, -180.0, 180.0, 5.0);
    private final BoolSetting always = new BoolSetting("Also in the daytime", false);

    private final GpuBuffer[] starBuffers = new GpuBuffer[STAR_GROUPS + 1];
    private final int[] starIndexCounts = new int[STAR_GROUPS + 1];
    private GpuBuffer hazeBuffer;
    private int hazeIndexCount;
    private int builtHaze;
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
        this.milkyWay.visibleIf(this.stars::value);
        this.hazeColor.visibleIf(this.haze::value);
        for (dev.hatek.client.module.setting.Setting setting : java.util.List.of(this.auroraLow, this.auroraHigh,
                this.auroraIntensity, this.auroraHeight, this.auroraSpeed, this.auroraDirection)) {
            setting.visibleIf(this.aurora::value);
        }
        with(this.gradient, this.horizon, this.zenith, this.gradientStrength, this.stars, this.density, this.starBrightness,
                this.twinkle, this.hideVanillaStars, this.milkyWay, this.haze, this.hazeColor, this.aurora, this.auroraLow, this.auroraHigh, this.auroraIntensity,
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
                if (self.milkyWay.value()) {
                    self.draw(target, pose, self.starBuffers[MILKY], self.starIndexCounts[MILKY], visible * level, true);
                }
                pose.popPose();
            }
            if (self.haze.value()) {
                // the air near the horizon: dims and tints the stars that are low in the sky
                int color = self.hazeColor.value();
                if (self.hazeBuffer == null || color != self.builtHaze) {
                    self.buildHaze(color);
                }
                self.draw(target, pose, self.hazeBuffer, self.hazeIndexCount, visible * 0.9f, false);
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
        for (int i = 0; i <= STAR_GROUPS; i++) {
            if (this.starBuffers[i] != null) {
                this.starBuffers[i].close();
                this.starBuffers[i] = null;
            }
        }
        if (this.hazeBuffer != null) {
            this.hazeBuffer.close();
            this.hazeBuffer = null;
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

    /** A growing list of quads, 4 vertices of 7 floats each (x, y, z, r, g, b, a). */
    private static final class Quads {
        float[] data = new float[28 * 1024];
        int size;

        private void grow() {
            if (size + 28 > data.length) {
                data = java.util.Arrays.copyOf(data, data.length * 2);
            }
        }

        void vertex(float[] p, float[] c) {
            data[size++] = p[0];
            data[size++] = p[1];
            data[size++] = p[2];
            data[size++] = c[0];
            data[size++] = c[1];
            data[size++] = c[2];
            data[size++] = 1.0f;
        }

        void quad(float[] pa, float[] ca, float[] pb, float[] cb, float[] pc, float[] cc, float[] pd, float[] cd) {
            grow();
            vertex(pa, ca);
            vertex(pb, cb);
            vertex(pc, cc);
            vertex(pd, cd);
        }

        /** A triangle (as a quad with a repeated corner). */
        void triangle(float[] pa, float[] ca, float[] pb, float[] cb, float[] pc, float[] cc) {
            quad(pa, ca, pb, cb, pc, cc, pc, cc);
        }

        float[] toArray() {
            return java.util.Arrays.copyOf(data, size);
        }
    }

    private static final float[] BLACK = {0.0f, 0.0f, 0.0f};

    private static float[] scaled(float[] c, float k) {
        return new float[]{c[0] * k, c[1] * k, c[2] * k};
    }

    /** Two unit vectors across the direction d. */
    private static float[][] across(float dx, float dy, float dz) {
        float rx = Math.abs(dy) < 0.95f ? 0.0f : 1.0f;
        float ry = Math.abs(dy) < 0.95f ? 1.0f : 0.0f;
        float ux = dy * 0.0f - dz * ry;
        float uy = dz * rx - dx * 0.0f;
        float uz = dx * ry - dy * rx;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        float vx = dy * uz - dz * uy;
        float vy = dz * ux - dx * uz;
        float vz = dx * uy - dy * ux;
        return new float[][]{{ux, uy, uz}, {vx, vy, vz}};
    }

    private static float[] at(float[] c, float[] u, float[] v, float cos, float sin, float r) {
        return new float[]{c[0] + (u[0] * cos + v[0] * sin) * r, c[1] + (u[1] * cos + v[1] * sin) * r,
                c[2] + (u[2] * cos + v[2] * sin) * r};
    }

    /** A small soft star: a four-pointed glow that fades to black at its tips. */
    private static void diamond(Quads q, float dx, float dy, float dz, float radius, float rotation, float[] color) {
        float[][] uv = across(dx, dy, dz);
        float[] c = {dx * 99.0f, dy * 99.0f, dz * 99.0f};
        for (int k = 0; k < 4; k++) {
            float a0 = rotation + k * Mth.HALF_PI;
            float a1 = rotation + (k + 1) * Mth.HALF_PI;
            q.triangle(c, color, at(c, uv[0], uv[1], Mth.cos(a0), Mth.sin(a0), radius), BLACK,
                    at(c, uv[0], uv[1], Mth.cos(a1), Mth.sin(a1), radius), BLACK);
        }
    }

    /** A round soft glow: an inner fan and an outer ring, brightness falling to black at the edge. */
    private static void disc(Quads q, float dx, float dy, float dz, float radius, float[] color, float innerRatio, float innerLevel) {
        float[][] uv = across(dx, dy, dz);
        float[] c = {dx * 99.0f, dy * 99.0f, dz * 99.0f};
        float[] mid = scaled(color, innerLevel);
        int segments = 8;
        for (int k = 0; k < segments; k++) {
            float a0 = Mth.TWO_PI * k / segments;
            float a1 = Mth.TWO_PI * (k + 1) / segments;
            float[] i0 = at(c, uv[0], uv[1], Mth.cos(a0), Mth.sin(a0), radius * innerRatio);
            float[] i1 = at(c, uv[0], uv[1], Mth.cos(a1), Mth.sin(a1), radius * innerRatio);
            float[] o0 = at(c, uv[0], uv[1], Mth.cos(a0), Mth.sin(a0), radius);
            float[] o1 = at(c, uv[0], uv[1], Mth.cos(a1), Mth.sin(a1), radius);
            q.triangle(c, color, i0, mid, i1, mid);
            q.quad(i0, mid, o0, BLACK, o1, BLACK, i1, mid);
        }
    }

    private static float[] tint(Random random, float brightness) {
        float t = random.nextFloat();
        float[] base = t < 0.22f ? new float[]{0.75f, 0.83f, 1.0f} : t > 0.86f ? new float[]{1.0f, 0.91f, 0.72f}
                : new float[]{1.0f, 1.0f, 1.0f};
        return scaled(base, brightness);
    }

    private void buildStars(int count) {
        for (int i = 0; i <= STAR_GROUPS; i++) {
            if (this.starBuffers[i] != null) {
                this.starBuffers[i].close();
            }
        }
        Random random = new Random(20260611L);
        Quads[] groups = new Quads[STAR_GROUPS];
        for (int g = 0; g < STAR_GROUPS; g++) {
            groups[g] = new Quads();
        }
        int bright = Math.max(20, count / 30);
        for (int i = 0; i < count; i++) {
            float[] d = randomDirection(random);
            float b = 0.25f + 0.75f * (float) Math.pow(random.nextFloat(), 2.2);
            float size = (0.22f + random.nextFloat() * random.nextFloat() * 0.5f) * 0.99f;
            diamond(groups[i % STAR_GROUPS], d[0], d[1], d[2], size, random.nextFloat() * Mth.TWO_PI, tint(random, b));
        }
        for (int i = 0; i < bright; i++) {
            // the few bright stars: a soft halo around a small sparkle
            float[] d = randomDirection(random);
            float[] color = tint(random, 0.95f);
            Quads q = groups[i % STAR_GROUPS];
            disc(q, d[0], d[1], d[2], 1.4f + random.nextFloat() * 1.2f, scaled(color, 0.9f), 0.28f, 0.22f);
            diamond(q, d[0], d[1], d[2], 0.55f, random.nextFloat() * Mth.TWO_PI, color);
        }
        for (int g = 0; g < STAR_GROUPS; g++) {
            int[] counts = new int[1];
            this.starBuffers[g] = upload(groups[g].toArray(), counts);
            this.starIndexCounts[g] = counts[0];
        }

        // the Milky Way: a faint band of soft glow with many tiny stars along a great circle
        Quads milky = new Quads();
        float[] n = {0.55f, 0.5f, 0.67f};
        float nl = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        n[0] /= nl;
        n[1] /= nl;
        n[2] /= nl;
        float[] a = {n[1] * 0.0f - n[2] * 1.0f, n[2] * 0.0f - n[0] * 0.0f, n[0] * 1.0f - n[1] * 0.0f};
        float al = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        a[0] /= al;
        a[1] /= al;
        a[2] /= al;
        float[] b = {n[1] * a[2] - n[2] * a[1], n[2] * a[0] - n[0] * a[2], n[0] * a[1] - n[1] * a[0]};
        for (int i = 0; i < 110; i++) {
            float theta = random.nextFloat() * Mth.TWO_PI;
            float off = (float) random.nextGaussian() * 0.09f;
            float[] d = bandPoint(a, b, n, theta, off);
            float warm = 0.5f + 0.5f * Mth.sin(theta * 2.0f);
            float[] color = {Mth.lerp(warm, 0.62f, 1.0f) * 0.05f, Mth.lerp(warm, 0.71f, 0.89f) * 0.05f, Mth.lerp(warm, 1.0f, 0.69f) * 0.05f};
            disc(milky, d[0], d[1], d[2], 7.0f + random.nextFloat() * 9.0f, color, 0.45f, 0.3f);
        }
        for (int i = 0; i < 900; i++) {
            float theta = random.nextFloat() * Mth.TWO_PI;
            float off = (float) random.nextGaussian() * 0.07f;
            float[] d = bandPoint(a, b, n, theta, off);
            diamond(milky, d[0], d[1], d[2], (0.15f + random.nextFloat() * 0.25f) * 0.99f, random.nextFloat() * Mth.TWO_PI,
                    scaled(new float[]{0.87f, 0.9f, 1.0f}, 0.15f + random.nextFloat() * 0.25f));
        }
        int[] counts = new int[1];
        this.starBuffers[MILKY] = upload(milky.toArray(), counts);
        this.starIndexCounts[MILKY] = counts[0];
        this.builtStarCount = count;
    }

    private static float[] bandPoint(float[] a, float[] b, float[] n, float theta, float offset) {
        float x = a[0] * Mth.cos(theta) + b[0] * Mth.sin(theta) + n[0] * offset;
        float y = a[1] * Mth.cos(theta) + b[1] * Mth.sin(theta) + n[1] * offset;
        float z = a[2] * Mth.cos(theta) + b[2] * Mth.sin(theta) + n[2] * offset;
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / l, y / l, z / l};
    }

    private static float[] randomDirection(Random random) {
        float y = random.nextFloat() * 2.0f - 1.0f;
        float phi = random.nextFloat() * Mth.TWO_PI;
        float ring = Mth.sqrt(Math.max(0.0f, 1.0f - y * y));
        return new float[]{ring * Mth.cos(phi), y, ring * Mth.sin(phi)};
    }

    /** The haze: a translucent ring of colour that is thickest at the horizon and thins out upward. */
    private void buildHaze(int argb) {
        if (this.hazeBuffer != null) {
            this.hazeBuffer.close();
        }
        float[] color = {((argb >> 16) & 0xFF) / 255.0f, ((argb >> 8) & 0xFF) / 255.0f, (argb & 0xFF) / 255.0f};
        float[][] stops = {{-8, 1.0f}, {0, 0.85f}, {4, 0.58f}, {8, 0.40f}, {13, 0.25f}, {19, 0.14f}, {27, 0.07f}, {38, 0.025f}, {52, 0.0f}};
        int segments = 48;
        Quads q = new Quads();
        for (int s = 0; s < segments; s++) {
            float a0 = Mth.TWO_PI * s / segments;
            float a1 = Mth.TWO_PI * (s + 1) / segments;
            for (int r = 0; r < stops.length - 1; r++) {
                float[] p0 = hazePoint(a0, stops[r][0]);
                float[] p1 = hazePoint(a1, stops[r][0]);
                float[] p2 = hazePoint(a1, stops[r + 1][0]);
                float[] p3 = hazePoint(a0, stops[r + 1][0]);
                q.grow();
                q.vertex(p0, color);
                q.data[q.size - 1] = stops[r][1];
                q.vertex(p1, color);
                q.data[q.size - 1] = stops[r][1];
                q.vertex(p2, color);
                q.data[q.size - 1] = stops[r + 1][1];
                q.vertex(p3, color);
                q.data[q.size - 1] = stops[r + 1][1];
            }
        }
        int[] counts = new int[1];
        this.hazeBuffer = upload(q.toArray(), counts);
        this.hazeIndexCount = counts[0];
        this.builtHaze = argb;
    }

    private static float[] hazePoint(float azimuth, float elevationDeg) {
        float e = (float) Math.toRadians(elevationDeg);
        return new float[]{97.0f * Mth.cos(e) * Mth.sin(azimuth), 97.0f * Mth.sin(e), -97.0f * Mth.cos(e) * Mth.cos(azimuth)};
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
        private static final int HALO_COLUMNS = 48;
        private static final int HALO_ROWS = 8;
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
            float[] out = new float[CURTAINS * (COLUMNS * ROWS + HALO_COLUMNS * HALO_ROWS) * 28];
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
            // the wide, faint halo behind the rays: it gives the curtains depth and a glow in the air around them
            int haloStride = HALO_ROWS + 1;
            float[] halo = new float[(HALO_COLUMNS + 1) * haloStride * 6];
            for (int c = 0; c < CURTAINS; c++) {
                double heading = directionDeg + (c - 0.5 * (CURTAINS - 1)) * 46.0 + Math.sin(t * 0.035 + c * 1.7) * 14.0;
                double half = 58.0 - c * 8.0;
                for (int i = 0; i <= HALO_COLUMNS; i++) {
                    double u = (double) i / HALO_COLUMNS;
                    double wave = 7.0 * Math.sin(u * 5.3 + t * 0.30 + c) + 3.5 * Math.sin(u * 11.7 - t * 0.45 + c * 2.0);
                    double azimuth = Math.toRadians(heading + (u - 0.5) * 2.0 * half + wave);
                    float x = (float) (94.0 * Math.sin(azimuth));
                    float z = (float) (-94.0 * Math.cos(azimuth));
                    double envelope = Math.pow(Math.max(0.0, Math.sin(Math.PI * u)), 0.8);
                    for (int j = 0; j <= HALO_ROWS; j++) {
                        double v = Math.pow((double) j / HALO_ROWS, 1.5);
                        double b = intensity * envelope * Math.exp(-v * 2.2) * Math.min(1.0, v / 0.1) * 0.20
                                * (0.8 + 0.2 * Math.sin(t * 0.7 + u * 5.0 + c));
                        int g = (i * haloStride + j) * 6;
                        halo[g] = x;
                        halo[g + 1] = (float) (10.0 + v * height * 2.1);
                        halo[g + 2] = z;
                        halo[g + 3] = (float) ((low[0] * (1.0 - v) + high[0] * v) * b);
                        halo[g + 4] = (float) ((low[1] * (1.0 - v) + high[1] * v) * b);
                        halo[g + 5] = (float) ((low[2] * (1.0 - v) + high[2] * v) * b);
                    }
                }
                for (int i = 0; i < HALO_COLUMNS; i++) {
                    for (int j = 0; j < HALO_ROWS; j++) {
                        at = put(out, at, halo, i, j, haloStride);
                        at = put(out, at, halo, i + 1, j, haloStride);
                        at = put(out, at, halo, i + 1, j + 1, haloStride);
                        at = put(out, at, halo, i, j + 1, haloStride);
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
