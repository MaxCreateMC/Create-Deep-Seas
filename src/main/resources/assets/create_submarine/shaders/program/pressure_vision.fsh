#version 150

uniform sampler2D DiffuseSampler;
uniform float Strength;
uniform float Grey;
uniform float Tunnel;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

void main() {
    vec2 centered = texCoord - 0.5;
    centered.x *= oneTexel.y / oneTexel.x;
    float dist = length(centered);

    float inner = mix(0.62, 0.14, Strength) - 0.12 * Tunnel;
    float band = mix(0.42, 0.32, Strength) - 0.1 * Tunnel;
    float edge = smoothstep(inner, inner + band, dist);
    float radius = edge * Strength * 4.0;

    vec3 sum = vec3(0.0);
    float weight = 0.0;
    for (int i = -4; i <= 4; i++) {
        for (int j = -4; j <= 4; j++) {
            float w = exp(-float(i * i + j * j) / 9.0);
            sum += texture(DiffuseSampler, texCoord + vec2(i, j) * radius * oneTexel).rgb * w;
            weight += w;
        }
    }
    vec3 color = sum / weight;

    if (Grey > 0.0) {
        float luma = dot(color, vec3(0.299, 0.587, 0.114));
        float peak = max(color.r, max(color.g, color.b));
        float lamp = smoothstep(0.6, 0.95, peak) * 0.45;
        vec3 lit = mix(vec3(luma), color, lamp);
        color = mix(color, lit, Grey);
    }

    float dark = smoothstep(inner, inner + band + 0.35, dist) * (0.25 + 0.5 * Strength);
    fragColor = vec4(color * (1.0 - dark), 1.0);
}
