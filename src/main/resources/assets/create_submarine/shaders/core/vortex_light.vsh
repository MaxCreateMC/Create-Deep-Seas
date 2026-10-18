#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 AnchorOffset;
uniform float GameTime;
uniform float FadeRange;

flat out vec3 lightCenter;
flat out vec3 lightColor;
flat out vec3 lightDirection;
flat out float lightRadius;
flat out float lightIntensity;
flat out float lightCone;
flat out float lightSeed;

float hash(vec3 point) {
    return fract(sin(dot(point, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

float shimmer(float time, float seed) {
    float beat = time * 6.0 + seed * 17.0;
    float from = hash(vec3(floor(beat), seed, 3.0));
    float to = hash(vec3(floor(beat) + 1.0, seed, 3.0));
    return mix(from, to, smoothstep(0.0, 1.0, fract(beat)));
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    lightRadius = UV0.x;
    lightColor = Color.rgb;
    lightCenter = Position - sign(Normal) * UV0.x + AnchorOffset;
    lightDirection = vec3(UV1, UV2.x) / 32767.0;
    lightCone = float(UV2.y) / 32767.0;
    lightSeed = (abs(Normal.x) - 0.25) / 0.75;

    int behavior = int(Color.a * 255.0 + 0.5);
    float pulse = float(behavior / 16) / 15.0;
    float flicker = float(behavior - behavior / 16 * 16) / 15.0;
    float time = GameTime * 1200.0;

    float breathing = 0.5 - 0.5 * cos(time * (0.8 + lightSeed * 0.6) + lightSeed * 6.2832);
    float jolt = shimmer(time, lightSeed);
    float fade = 1.0 - smoothstep(FadeRange - 8.0, FadeRange, length(lightCenter));
    lightIntensity = UV0.y * (1.0 - pulse * 0.6 * breathing) * (1.0 - flicker * 0.35 * jolt) * fade;
}
