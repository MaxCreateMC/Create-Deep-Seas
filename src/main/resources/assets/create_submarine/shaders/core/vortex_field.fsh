#version 150

uniform sampler2D DepthSampler;
uniform sampler2D Heights;
uniform sampler3D ShadowGrid;
uniform sampler3D Field;
uniform mat4 InvProjMat;
uniform mat4 InvViewMat;
uniform vec2 ScreenSize;
uniform vec3 CameraFract;
uniform vec3 WorldWrap;
uniform ivec3 GridOffset;
uniform float FieldMode;
uniform float Pixelated;
uniform float Gain;
uniform float FieldLight;
uniform float Murk;
uniform float Caustics;
uniform vec3 SunDir;
uniform vec3 SunColor;
uniform float SurfaceY;
uniform float GameTime;

out vec4 fragColor;

const float FAINT = 0.0001;

vec4 fieldAt(vec3 point) {
    vec3 grid = (point + CameraFract + vec3(GridOffset)) / 128.0;
    float open = 1.0 - texture(ShadowGrid, grid).r;
    if (open < 0.02)
        return vec4(0.0);
    return texture(Field, grid) / open;
}

vec3 vivid(vec3 hue) {
    hue /= max(max(hue.r, hue.g), max(hue.b, 0.001));
    return pow(hue, vec3(1.5));
}

vec3 glowOf(vec4 field) {
    if (FieldMode < 1.5) {
        if (field.a <= FAINT)
            return vec3(0.0);
        float level = clamp(log(field.a / FAINT) / 10.2, 0.0, 1.0);
        return vivid(field.rgb / field.a) * pow(level, 1.5);
    }

    float level = floor(field.a * 15.0 + 0.5) / 15.0;
    return vivid(field.rgb) * level * 0.8;
}

float facing(vec3 point, vec3 normal) {
    vec3 grid = (point + CameraFract + vec3(GridOffset)) / 128.0;
    float nudge = 0.5 / 128.0;
    vec3 slope = vec3(
            texture(Field, grid + vec3(nudge, 0.0, 0.0)).a - texture(Field, grid - vec3(nudge, 0.0, 0.0)).a,
            texture(Field, grid + vec3(0.0, nudge, 0.0)).a - texture(Field, grid - vec3(0.0, nudge, 0.0)).a,
            texture(Field, grid + vec3(0.0, 0.0, nudge)).a - texture(Field, grid - vec3(0.0, 0.0, nudge)).a);

    float size = length(slope);
    if (size < 0.000001)
        return 1.0;
    return 0.7 + 0.3 * dot(normal, slope / size);
}

vec2 hash2(vec2 point) {
    return fract(sin(vec2(dot(point, vec2(127.1, 311.7)), dot(point, vec2(269.5, 183.3)))) * 43758.5453);
}

float web(vec2 point, float time) {
    vec2 cell = floor(point);
    vec2 local = point - cell;
    float first = 8.0;
    float second = 8.0;

    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 offset = vec2(x, y);
            vec2 seed = hash2(cell + offset);
            vec2 site = offset + 0.5 + 0.4 * sin(time * 0.6 + seed * 6.2832);
            float d = length(site - local);

            if (d < first) {
                second = first;
                first = d;
            } else if (d < second) {
                second = d;
            }
        }
    }

    return 1.0 - smoothstep(0.0, 0.24, second - first);
}

float caustic(vec2 point, float time) {
    vec2 snapped = floor(point * 16.0) / 16.0;
    float wide = web(snapped * 0.3 + vec2(time * 0.02, 0.0), time);
    float fine = web(snapped * 0.55 - vec2(0.0, time * 0.03), time * 1.3);
    float swell = 0.5 + 0.5 * sin(snapped.x * 0.21 + time * 0.15) * sin(snapped.y * 0.17 - time * 0.1);
    return (wide * fine * 1.6 + wide * 0.2) * (0.3 + 1.4 * swell);
}

float topAt(vec2 point) {
    ivec2 column = ivec2(floor(point + CameraFract.xz)) + GridOffset.xz;
    if (any(lessThan(column, ivec2(0))) || any(greaterThanEqual(column, ivec2(128))))
        return -1.0e6;

    float height = texelFetch(Heights, column, 0).r * 255.0;
    if (height < 0.5)
        return -1.0e6;
    return floor(height + 0.5) - float(GridOffset.y) - CameraFract.y;
}

bool sunlit(vec3 point) {
    float top = topAt(point.xz);
    vec2 shifted = point.xz + SunDir.xz / SunDir.y * max(top - point.y, 0.0);
    return point.y >= topAt(shifted) - 0.05;
}

vec3 worldAt(vec2 uv, float depth) {
    vec4 view = InvProjMat * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return (InvViewMat * vec4(view.xyz / view.w, 1.0)).xyz;
}

vec3 axis(vec3 normal) {
    vec3 size = abs(normal);
    if (size.x >= size.y && size.x >= size.z)
        return vec3(sign(normal.x), 0.0, 0.0);
    if (size.y >= size.z)
        return vec3(0.0, sign(normal.y), 0.0);
    return vec3(0.0, 0.0, sign(normal.z));
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float depth = texture(DepthSampler, uv).r;
    if (depth >= 1.0)
        discard;

    vec3 world = worldAt(uv, depth);
    vec3 normal = normalize(cross(dFdx(world), dFdy(world)));
    if (dot(normal, world) > 0.0)
        normal = -normal;
    normal = axis(normal);

    float range = length(world);
    vec3 point = world;
    if (Pixelated > 0.5)
        point = (floor((world + CameraFract) * 16.0) + 0.5) / 16.0 - CameraFract;

    vec3 probe = point + normal * 0.5;
    vec3 light = FieldLight > 0.5 ? glowOf(fieldAt(probe)) * facing(probe, normal) * Gain : vec3(0.0);

    if (Caustics > 0.0 && Murk > 0.5) {
        float below = SurfaceY - world.y;
        if (below > 0.0 && normal.y > 0.5 && sunlit(world)) {
            float time = GameTime * 1200.0;
            vec2 hit = world.xz + CameraFract.xz + WorldWrap.xz + SunDir.xz / SunDir.y * below;
            float fade = exp(-below / 22.0) * (1.0 - smoothstep(24.0, 56.0, range));
            light += SunColor * caustic(hit, time) * Caustics * fade;
        }
    }

    light *= 1.0 - smoothstep(48.0, 60.0, range);
    if (max(light.r, max(light.g, light.b)) <= 0.001)
        discard;

    fragColor = vec4(light, 1.0);
}
