#version 150

uniform sampler2D Sampler0;
uniform sampler2D DepthSampler;
uniform sampler2D LightData;
uniform sampler3D Field;
uniform mat4 InvProjMat;
uniform vec2 ScreenSize;
uniform vec3 CameraFract;
uniform ivec3 GridOffset;
uniform float FieldMode;
uniform int LightCount;
uniform float Pixelated;
uniform float Scatter;
uniform float GameTime;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in vec2 texCoord;
in vec4 vertexColor;
in vec3 worldPos;
in float shade[8];

out vec4 fragColor;

float hash(vec3 point) {
    return fract(sin(dot(point, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

float rhythm(float behavior, float seed) {
    int code = int(behavior + 0.5);
    float pulse = float(code / 16) / 15.0;
    float flicker = float(code - code / 16 * 16) / 15.0;
    float time = GameTime * 1200.0;

    float breathing = 0.5 - 0.5 * cos(time * (0.8 + seed * 0.6) + seed * 6.2832);
    float beat = time * 6.0 + seed * 17.0;
    float jolt = mix(hash(vec3(floor(beat), seed, 3.0)), hash(vec3(floor(beat) + 1.0, seed, 3.0)),
            smoothstep(0.0, 1.0, fract(beat)));
    return (1.0 - pulse * 0.6 * breathing) * (1.0 - flicker * 0.35 * jolt);
}

vec3 hueOf(vec3 hue) {
    return hue / max(max(hue.r, hue.g), max(hue.b, 0.001));
}

vec3 fieldAt(vec3 point) {
    vec4 field = texture(Field, (point + CameraFract + vec3(GridOffset)) / 128.0);

    if (FieldMode < 1.5) {
        if (field.a <= 0.0001)
            return vec3(0.0);
        float level = clamp(log(field.a / 0.0001) / 10.2, 0.0, 1.0);
        return hueOf(field.rgb / field.a) * level;
    }

    return hueOf(field.rgb) * field.a;
}

vec3 lightAt(vec3 point, vec3 view) {
    vec3 glow = vec3(0.0);

    for (int i = 0; i < 8; i++) {
        if (i >= LightCount)
            break;

        vec4 place = texelFetch(LightData, ivec2(i, 0), 0);
        vec4 tint = texelFetch(LightData, ivec2(i, 1), 0);
        vec4 beam = texelFetch(LightData, ivec2(i, 2), 0);
        vec4 rhythmData = texelFetch(LightData, ivec2(i, 3), 0);

        vec3 toLight = place.xyz - point;
        float dist = length(toLight);
        if (dist >= place.w)
            continue;

        vec3 direction = toLight / max(dist, 0.001);
        float falloff = 1.0 - dist / place.w;
        falloff *= falloff;
        if (Pixelated > 0.5)
            falloff = floor(falloff * 8.0 + 0.5) / 8.0;

        float spot = 1.0;
        if (beam.w > -0.99)
            spot = smoothstep(beam.w, beam.w + 0.15, dot(-direction, beam.xyz));

        float forward = 0.5 + 1.5 * pow(max(dot(view, direction), 0.0), 4.0);
        vec3 hue = tint.rgb / max(max(tint.r, tint.g), max(tint.b, 0.001));

        glow += hue * tint.a * rhythm(rhythmData.x, rhythmData.y) * falloff * spot * forward * shade[i];
    }

    return glow;
}

void main() {
    vec4 smoke = texture(Sampler0, texCoord);
    float alpha = smoke.a * vertexColor.a;
    if (alpha <= 0.002)
        discard;

    float eyeDistance = length(worldPos);
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float depth = texture(DepthSampler, uv).r;
    vec4 view = InvProjMat * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    float scene = depth >= 1.0 ? 1.0e6 : length(view.xyz / view.w);

    alpha *= clamp(scene - eyeDistance, 0.0, 1.0);
    alpha *= smoothstep(0.5, 3.0, eyeDistance);
    if (alpha <= 0.002)
        discard;

    vec3 glow = lightAt(worldPos, worldPos / max(eyeDistance, 0.001));
    if (FieldMode > 0.5)
        glow += fieldAt(worldPos) * 1.4;
    float lit = min(max(glow.r, max(glow.g, glow.b)), 1.0);

    vec3 color = smoke.rgb * (vertexColor.rgb * (1.0 - lit * 0.6) + glow * 1.3) + FogColor.rgb * Scatter;
    float fog = eyeDistance <= FogStart ? 0.0 : smoothstep(FogStart, FogEnd, eyeDistance);
    fragColor = vec4(mix(color, FogColor.rgb, fog * FogColor.a), min(alpha * (1.0 + lit), 1.0));
}
