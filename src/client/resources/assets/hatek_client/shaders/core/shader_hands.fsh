#version 330

#moj_import <minecraft:globals.glsl>

// Руки отрисованы в отдельный буфер: alpha > 0 там, где рука/предмет, 0 — пусто.
uniform sampler2D InSampler;

// Параметры из настроек модуля ShaderHands (см. ShaderHandsRenderer).
layout(std140) uniform HandsInfo {
    vec4 ColorA;
    vec4 ColorB;
    float Fill;        // 0..1 — насколько заливка перекрывает текстуру
    float GlowRadius;  // радиус свечения в пикселях при 1080p
    float Phase;       // фаза анимации градиента, радианы
    int Mode;          // 0 — заливка + свечение, 1 — только заливка, 2 — только свечение
    float Trail;       // яркость огненного шлейфа, 0 — выключен
    int TrailPalette;  // 0 — настоящий огонь, 1 — цвета рук
    float Shadow;      // режим Bloom: сила мягкой тени под предметом, 0..1
};

// Жар шлейфа (см. hands_trail.fsh), красный канал: 0 — холодно, 1 — у самых рук.
uniform sampler2D TrailSampler;

in vec2 texCoord;

out vec4 fragColor;

const int GLOW_RINGS = 6;
const int GLOW_DIRECTIONS = 14;
const float TAU = 6.2831853;

vec3 gradient(vec2 uv) {
    float phase = (uv.x * 1.6 + uv.y) * 3.0 - Phase;
    return mix(ColorA.rgb, ColorB.rgb, 0.5 + 0.5 * sin(phase));
}

// Цвет пламени по жару: кончики тёмные, ближе к рукам — ярче и почти белые.
vec3 flame(float heat, vec3 grad) {
    if (TrailPalette == 0) {
        vec3 c = mix(vec3(0.55, 0.04, 0.0), vec3(1.0, 0.38, 0.03), smoothstep(0.0, 0.45, heat));
        c = mix(c, vec3(1.0, 0.78, 0.25), smoothstep(0.4, 0.75, heat));
        return mix(c, vec3(1.0, 0.95, 0.78), smoothstep(0.75, 1.0, heat));
    }
    vec3 c = mix(ColorB.rgb * 0.6, grad, smoothstep(0.0, 0.55, heat));
    return mix(c, vec3(1.0), smoothstep(0.7, 1.0, heat) * 0.6);
}

// Режим Bloom: яркая полоса, которая бежит вдоль предмета (переливание), 0..1.
float sweep(vec2 uv) {
    float s = 0.5 + 0.5 * sin((uv.x * 1.3 - uv.y * 0.8) * 4.5 - Phase * 4.0);
    return s * s * (3.0 - 2.0 * s);
}

// Доля предмета в кольцах вокруг точки (для свечения и тени).
float coverageAround(vec2 uv, float radius) {
    vec2 texel = 1.0 / ScreenSize;
    float coverage = 0.0;
    float total = 0.0;
    for (int ring = 1; ring <= GLOW_RINGS; ring++) {
        float dist = radius * float(ring) / float(GLOW_RINGS);
        float weight = 1.0 - float(ring - 1) / float(GLOW_RINGS);
        for (int i = 0; i < GLOW_DIRECTIONS; i++) {
            float angle = TAU * (float(i) + 0.5 * float(ring)) / float(GLOW_DIRECTIONS);
            vec2 offset = vec2(cos(angle), sin(angle)) * dist * texel;
            coverage += step(0.01, texture(InSampler, uv + offset).a) * weight;
            total += weight;
        }
    }
    return coverage / total;
}

void bloomMode(vec4 hand) {
    float band = sweep(texCoord);
    vec3 light = mix(ColorB.rgb, ColorA.rgb, band);
    float scale = max(ScreenSize.y / 1080.0, 1.0);
    if (hand.a > 0.01) {
        // the item is washed toward the light colour and overexposed: bright pixels go white, the texture still
        // shows through as a slightly darker pattern; the running band is the brightest part
        float lum = dot(hand.rgb, vec3(0.299, 0.587, 0.114));
        vec3 lifted = mix(hand.rgb, light, clamp(Fill * 0.72, 0.0, 1.0));
        vec3 lit = lifted + light * Fill * (0.32 + 0.75 * band) * (0.5 + 0.5 * lum);
        // the glow bleeds over the edges of the item
        float inner = 1.0 - coverageAround(texCoord, 5.0 * scale);
        lit += light * inner * 0.35 * Fill;
        fragColor = vec4(min(lit, vec3(1.0)), hand.a);
        return;
    }
    // a wide soft halo with a brighter core near the item; it breathes with the band
    float wide = coverageAround(texCoord, GlowRadius * 2.0 * scale);
    float near = coverageAround(texCoord, GlowRadius * 0.7 * scale);
    float glow = clamp(pow(wide, 0.85) * 2.3 + near * 0.9, 0.0, 1.0) * (0.6 + 0.45 * band);
    glow = clamp(glow, 0.0, 1.0);
    // soft shadow under the item: the item above this point darkens it
    float shade = 0.0;
    if (Shadow > 0.0) {
        vec2 lift = vec2(-4.0, 12.0) * scale / ScreenSize;
        shade = coverageAround(texCoord + lift, 14.0 * scale) * Shadow * 0.8 * (1.0 - glow);
    }
    float alpha = glow + shade * (1.0 - glow);
    if (alpha <= 0.003) {
        discard;
    }
    vec3 rgb = (mix(light, vec3(1.0), 0.35) * glow) / alpha;
    fragColor = vec4(rgb, alpha);
}

void main() {
    vec4 hand = texture(InSampler, texCoord);
    if (Mode == 3) {
        bloomMode(hand);
        return;
    }
    vec3 grad = gradient(texCoord);

    if (hand.a > 0.01) {
        // Заливка сохраняет объём: яркость исходной текстуры модулирует градиент.
        float fill = Mode == 2 ? 0.0 : Fill;
        float lum = dot(hand.rgb, vec3(0.299, 0.587, 0.114));
        vec3 tinted = grad * (0.45 + 0.75 * lum);
        fragColor = vec4(mix(hand.rgb, tinted, fill), hand.a);
        return;
    }

    float glow = 0.0;
    if (Mode != 1) {
        vec2 texel = 1.0 / ScreenSize;
        float radius = GlowRadius * max(ScreenSize.y / 1080.0, 1.0);
        float coverage = 0.0;
        float total = 0.0;
        for (int ring = 1; ring <= GLOW_RINGS; ring++) {
            float dist = radius * float(ring) / float(GLOW_RINGS);
            float weight = 1.0 - float(ring - 1) / float(GLOW_RINGS);
            for (int i = 0; i < GLOW_DIRECTIONS; i++) {
                float angle = TAU * (float(i) + 0.5 * float(ring)) / float(GLOW_DIRECTIONS);
                vec2 offset = vec2(cos(angle), sin(angle)) * dist * texel;
                coverage += step(0.01, texture(InSampler, texCoord + offset).a) * weight;
                total += weight;
            }
        }
        glow = smoothstep(0.0, 0.3, coverage / total);
    }
    vec3 glowColor = mix(grad, vec3(1.0), 0.15);

    // огненный шлейф поверх свечения
    float fireAlpha = 0.0;
    vec3 fireColor = vec3(0.0);
    if (Trail > 0.0) {
        float heat = clamp(texture(TrailSampler, texCoord).r * Trail, 0.0, 1.0);
        fireAlpha = smoothstep(0.03, 0.5, heat);
        fireColor = flame(heat, grad);
    }

    float alpha = fireAlpha + glow * (1.0 - fireAlpha);
    if (alpha <= 0.003) {
        discard;
    }
    vec3 rgb = (fireColor * fireAlpha + glowColor * glow * (1.0 - fireAlpha)) / alpha;
    fragColor = vec4(rgb, alpha);
}
