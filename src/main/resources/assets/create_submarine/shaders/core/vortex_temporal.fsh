#version 150

uniform sampler2D LightSampler;
uniform sampler2D DepthSampler;
uniform sampler2D HistorySampler;
uniform mat4 InvProjMat;
uniform mat4 InvViewMat;
uniform mat4 PreviousMat;
uniform vec3 CameraMotion;
uniform vec2 ScreenSize;
uniform float LightScale;
uniform vec2 Jitter;
uniform float HistoryValid;

out vec4 fragColor;

vec3 worldAt(vec2 uv, float depth) {
    vec4 view = InvProjMat * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return (InvViewMat * vec4(view.xyz / view.w, 1.0)).xyz;
}

float distanceAt(ivec2 pixel) {
    vec2 uv = (vec2(pixel) + 0.5) / ScreenSize;
    float depth = texelFetch(DepthSampler, pixel, 0).r;
    vec4 view = InvProjMat * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return length(view.xyz / view.w);
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    ivec2 size = textureSize(LightSampler, 0);
    vec2 cell = (gl_FragCoord.xy - 0.5 - Jitter) / LightScale;
    vec2 base = floor(cell);
    vec2 blend = cell - base;
    float here = distanceAt(pixel);

    vec3 sum = vec3(0.0);
    float total = 0.0;

    for (int i = 0; i < 4; i++) {
        ivec2 offset = ivec2(i & 1, i >> 1);
        ivec2 texel = clamp(ivec2(base) + offset, ivec2(0), size - 1);
        ivec2 source = min(ivec2(vec2(texel) * LightScale + Jitter), ivec2(ScreenSize) - 1);
        float there = distanceAt(source);

        vec2 near = mix(1.0 - blend, blend, vec2(offset));
        float edge = 1.0 - smoothstep(0.06, 0.15, abs(there - here) / max(here, 0.5));
        float weight = near.x * near.y * (edge + 0.001);
        sum += texelFetch(LightSampler, texel, 0).rgb * weight;
        total += weight;
    }

    vec3 current = total > 0.0 ? sum / total : vec3(0.0);
    vec3 low = current;
    vec3 high = current;
    ivec2 middle = ivec2(floor(cell + 0.5));

    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            ivec2 texel = clamp(middle + ivec2(x, y), ivec2(0), size - 1);
            ivec2 source = min(ivec2(vec2(texel) * LightScale + Jitter), ivec2(ScreenSize) - 1);
            if (abs(distanceAt(source) - here) > here * 0.15 + 0.1)
                continue;

            vec3 light = texelFetch(LightSampler, texel, 0).rgb;
            low = min(low, light);
            high = max(high, light);
        }
    }

    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float depth = texelFetch(DepthSampler, pixel, 0).r;
    if (depth >= 1.0 || HistoryValid < 0.5) {
        fragColor = vec4(current, depth >= 1.0 ? 0.0 : here);
        return;
    }

    vec3 world = worldAt(uv, depth) + CameraMotion;
    vec4 previous = PreviousMat * vec4(world, 1.0);
    vec2 back = previous.xy / previous.w * 0.5 + 0.5;
    if (previous.w <= 0.0 || any(lessThan(back, vec2(0.0))) || any(greaterThan(back, vec2(1.0)))) {
        fragColor = vec4(current, here);
        return;
    }

    vec4 history = textureLod(HistorySampler, back, 0.0);
    float expected = length(world);
    if (abs(history.a - expected) > expected * 0.1 + 0.1) {
        fragColor = vec4(current, here);
        return;
    }

    fragColor = vec4(mix(current, clamp(history.rgb, low, high), 0.9), here);
}
