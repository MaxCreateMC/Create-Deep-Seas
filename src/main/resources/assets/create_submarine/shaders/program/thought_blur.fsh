#version 150

uniform sampler2D DiffuseSampler;
uniform float CenterX;
uniform float CenterY;
uniform float HalfWidth;
uniform float HalfHeight;
uniform float Strength;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

void main() {
    vec2 pixel = texCoord / oneTexel;
    vec2 d = abs(pixel - vec2(CenterX, CenterY)) - vec2(HalfWidth, HalfHeight);
    float outside = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
    float mask = (1.0 - smoothstep(0.0, HalfHeight * 1.5, outside)) * Strength;

    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    if (mask > 0.001) {
        vec3 sum = vec3(0.0);
        float weight = 0.0;
        for (int i = -4; i <= 4; i++) {
            for (int j = -4; j <= 4; j++) {
                float w = exp(-float(i * i + j * j) / 9.0);
                sum += texture(DiffuseSampler, texCoord + vec2(i, j) * mask * 3.0 * oneTexel).rgb * w;
                weight += w;
            }
        }
        color = mix(color, sum / weight, mask) * (1.0 - 0.25 * mask);
    }
    fragColor = vec4(color, 1.0);
}
