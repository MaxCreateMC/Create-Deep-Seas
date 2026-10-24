#version 150

uniform sampler2D LightSampler;
uniform sampler2D DepthSampler;
uniform mat4 InvProjMat;
uniform vec2 ScreenSize;
uniform float LightScale;
uniform float Strength;
uniform float Bloom;

out vec4 fragColor;

vec3 tone(vec3 light) {
    return vec3(1.0) - exp(-light * 2.0);
}

float distanceAt(ivec2 pixel) {
    vec2 uv = (vec2(pixel) + 0.5) / ScreenSize;
    float depth = texelFetch(DepthSampler, pixel, 0).r;
    vec4 view = InvProjMat * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return length(view.xyz / view.w);
}

vec3 lightAt(vec2 frag) {
    if (LightScale < 1.5)
        return texelFetch(LightSampler, ivec2(frag), 0).rgb;
    if (texelFetch(DepthSampler, ivec2(frag), 0).r >= 1.0)
        return vec3(0.0);

    ivec2 size = textureSize(LightSampler, 0);
    vec2 cell = (frag - 0.5) / LightScale;
    vec2 base = floor(cell);
    vec2 blend = cell - base;
    float here = distanceAt(ivec2(frag));

    vec3 sum = vec3(0.0);
    float total = 0.0;

    for (int i = 0; i < 4; i++) {
        ivec2 offset = ivec2(i & 1, i >> 1);
        ivec2 low = clamp(ivec2(base) + offset, ivec2(0), size - 1);
        float there = distanceAt(ivec2(vec2(low) * LightScale));

        vec2 near = mix(1.0 - blend, blend, vec2(offset));
        float edge = 1.0 - smoothstep(0.06, 0.15, abs(there - here) / max(here, 0.5));
        float weight = near.x * near.y * (edge + 0.001);
        sum += texelFetch(LightSampler, low, 0).rgb * weight;
        total += weight;
    }

    return total > 0.0 ? sum / total : vec3(0.0);
}

vec3 blurred(vec2 uv, float level) {
    vec2 texel = exp2(level) / ScreenSize;
    float lod = max(level - log2(LightScale), 0.0);
    vec3 sum = vec3(0.0);

    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            float weight = (2.0 - abs(float(x))) * (2.0 - abs(float(y)));
            sum += textureLod(LightSampler, uv + vec2(x, y) * texel, lod).rgb * weight;
        }
    }

    return sum / 16.0;
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec3 light = tone(lightAt(gl_FragCoord.xy)) * Strength;

    if (Bloom > 0.0) {
        vec3 wide = blurred(uv, 3.0) * 0.6 + blurred(uv, 5.0) * 0.4;
        light += tone(wide) * Bloom;
    }

    fragColor = vec4(light, 1.0);
}
