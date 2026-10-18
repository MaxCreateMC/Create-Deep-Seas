#version 150

uniform sampler2D LightSampler;
uniform vec2 ScreenSize;
uniform float Strength;
uniform float Bloom;

out vec4 fragColor;

vec3 tone(vec3 light) {
    return vec3(1.0) - exp(-light * 2.0);
}

vec3 blurred(vec2 uv, float level) {
    vec2 texel = exp2(level) / ScreenSize;
    vec3 sum = vec3(0.0);

    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            float weight = (2.0 - abs(float(x))) * (2.0 - abs(float(y)));
            sum += textureLod(LightSampler, uv + vec2(x, y) * texel, level).rgb * weight;
        }
    }

    return sum / 16.0;
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec3 light = tone(textureLod(LightSampler, uv, 0.0).rgb) * Strength;

    if (Bloom > 0.0) {
        vec3 wide = blurred(uv, 3.0) * 0.6 + blurred(uv, 5.0) * 0.4;
        light += tone(wide) * Bloom;
    }

    fragColor = vec4(light, 1.0);
}
