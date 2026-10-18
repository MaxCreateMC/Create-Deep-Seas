#version 150

uniform sampler2D DepthSampler;
uniform sampler2D LightData;
uniform isampler2D Tiles;
uniform isampler2D Indices;
uniform sampler3D ShadowGrid;
uniform mat4 InvProjMat;
uniform mat4 InvViewMat;
uniform vec2 ScreenSize;
uniform vec3 CameraFract;
uniform ivec3 GridOffset;
uniform int TileSize;
uniform float Shadows;
uniform float Pixelated;
uniform float Strength;
uniform float GameTime;

out vec4 fragColor;

float hash(vec3 point) {
    return fract(sin(dot(point, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

vec3 vivid(vec3 hue) {
    hue /= max(max(hue.r, hue.g), max(hue.b, 0.001));
    return pow(hue, vec3(1.5));
}

bool solid(ivec3 cell) {
    ivec3 grid = cell + GridOffset;
    if (any(lessThan(grid, ivec3(0))) || any(greaterThanEqual(grid, ivec3(128))))
        return false;
    return texelFetch(ShadowGrid, grid, 0).r > 0.5;
}

bool blocked(vec3 from, vec3 to) {
    vec3 start = from + CameraFract;
    ivec3 cell = ivec3(floor(start));
    ivec3 target = ivec3(floor(to + CameraFract));
    vec3 path = to - from;
    ivec3 stride = ivec3(sign(path));
    vec3 delta = 1.0 / max(abs(path), vec3(0.00001));
    vec3 inside = start - vec3(cell);
    vec3 crossing = mix(inside, 1.0 - inside, step(0.0, path)) * delta;

    for (int i = 0; i < 48; i++) {
        if (cell == target)
            return false;

        if (crossing.x <= crossing.y && crossing.x <= crossing.z) {
            cell.x += stride.x;
            crossing.x += delta.x;
        } else if (crossing.y <= crossing.z) {
            cell.y += stride.y;
            crossing.y += delta.y;
        } else {
            cell.z += stride.z;
            crossing.z += delta.z;
        }

        if (cell == target)
            return false;
        if (solid(cell))
            return true;
    }

    return false;
}

float rhythm(int code, float seed) {
    float time = GameTime * 1200.0;
    float pulse = (code & 1) != 0 ? 1.0 - 0.45 * (0.5 - 0.5 * cos(time * 0.9 + seed * 6.2832)) : 1.0;
    float beat = time * 6.0 + seed * 17.0;
    float jolt = mix(hash(vec3(floor(beat), seed, 3.0)), hash(vec3(floor(beat) + 1.0, seed, 3.0)),
            smoothstep(0.0, 1.0, fract(beat)));
    float flicker = (code & 2) != 0 ? 1.0 - 0.3 * jolt : 1.0;
    return pulse * flicker;
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

    vec3 point = world;
    if (Pixelated > 0.5)
        point = (floor((world + CameraFract) * 16.0) + 0.5) / 16.0 - CameraFract;
    point += normal * 0.05;

    ivec2 tile = ivec2(gl_FragCoord.xy) / TileSize;
    int packed = texelFetch(Tiles, tile, 0).r;
    int count = packed & 63;
    int offset = packed >> 6;
    vec3 light = vec3(0.0);

    for (int i = 0; i < 32; i++) {
        if (i >= count)
            break;

        int slot = offset + i;
        int index = texelFetch(Indices, ivec2(slot & 255, slot >> 8), 0).r;
        vec4 place = texelFetch(LightData, ivec2(index, 0), 0);
        vec3 toLight = place.xyz - point;
        vec3 span = abs(toLight);
        float level = place.w + 0.5 - (span.x + span.y + span.z);
        if (level < 1.0 || dot(toLight, normal) < -0.25)
            continue;
        if (Shadows > 0.5 && blocked(point, place.xyz))
            continue;

        vec4 tint = texelFetch(LightData, ivec2(index, 1), 0);
        float seed = texelFetch(LightData, ivec2(index, 2), 0).x;
        float stepped = Pixelated > 0.5 ? floor(level) : level;
        float bright = stepped / 15.0;
        bright = bright / (2.2 - 1.2 * bright);

        light = max(light, vivid(tint.rgb) * bright * rhythm(int(tint.a + 0.5), seed));
    }

    light *= 1.0 - smoothstep(48.0, 64.0, length(world));
    if (max(light.r, max(light.g, light.b)) <= 0.001)
        discard;

    fragColor = vec4(light * Strength, 1.0);
}
