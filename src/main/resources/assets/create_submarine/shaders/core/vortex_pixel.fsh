#version 150

uniform sampler2D DepthSampler;
uniform sampler2D OpaqueDepth;
uniform sampler2D LightData;
uniform isampler2D Tiles;
uniform isampler2D Indices;
uniform sampler3D ShadowGrid;
uniform mat4 InvProjMat;
uniform mat4 InvViewMat;
uniform vec2 ScreenSize;
uniform float LightScale;
uniform vec2 Jitter;
uniform vec3 CameraFract;
uniform ivec3 GridOffset;
uniform int TileSize;
uniform float Shadows;
uniform float Pixelated;
uniform float Strength;
uniform float GameTime;
uniform float Translucent;

out vec4 fragColor;

float hash(vec3 point) {
    return fract(sin(dot(point, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

vec3 hueOf(vec3 hue) {
    return hue / max(max(hue.r, hue.g), max(hue.b, 0.001));
}

vec4 gridAt(ivec3 cell) {
    ivec3 grid = cell + GridOffset;
    if (any(lessThan(grid, ivec3(0))) || any(greaterThanEqual(grid, ivec3(128))))
        return vec4(0.0);
    return texelFetch(ShadowGrid, grid, 0);
}

bool covered(vec4 grid, vec3 local) {
    int code = int(grid.a * 255.0 + 0.5);
    if (code == 255)
        return true;
    if (code != 1)
        return false;

    int bit = int(local.x >= 0.5) + int(local.y >= 0.5) * 2 + int(local.z >= 0.5) * 4;
    return (int(grid.r * 255.0 + 0.5) >> bit & 1) != 0;
}

vec3 passing(vec3 from, vec3 to) {
    vec3 start = from + CameraFract;
    ivec3 cell = ivec3(floor(start));
    ivec3 target = ivec3(floor(to + CameraFract));
    vec3 path = to - from;
    ivec3 stride = ivec3(sign(path));
    vec3 delta = 1.0 / max(abs(path), vec3(0.00001));
    vec3 inside = start - vec3(cell);
    vec3 crossing = mix(inside, 1.0 - inside, step(0.0, path)) * delta;
    vec3 through = vec3(1.0);
    float enter = 0.0;

    for (int i = 0; i < 48; i++) {
        if (cell == target)
            return through;

        float leave = min(min(crossing.x, crossing.y), min(crossing.z, 1.0));
        vec4 grid = gridAt(cell);

        if (i > 0 && grid.a > 0.002) {
            for (int k = 1; k <= 3; k++) {
                vec3 point = start + path * mix(enter, leave, float(k) * 0.25);
                if (covered(grid, point - vec3(cell)))
                    return vec3(0.0);
            }
        } else if (i > 0) {
            through *= vec3(1.0) - grid.rgb;
        }

        if (leave >= 1.0)
            return through;
        enter = leave;

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
    }

    return through;
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

vec3 worldNear(vec2 frag, vec2 offset) {
    vec2 uv = (frag + offset) / ScreenSize;
    return worldAt(uv, texture(DepthSampler, uv).r);
}

vec3 normalAt(vec2 frag, vec3 world) {
    vec3 left = world - worldNear(frag, vec2(-1.0, 0.0));
    vec3 right = worldNear(frag, vec2(1.0, 0.0)) - world;
    vec3 down = world - worldNear(frag, vec2(0.0, -1.0));
    vec3 up = worldNear(frag, vec2(0.0, 1.0)) - world;

    vec3 across = dot(right, right) < dot(left, left) ? right : left;
    vec3 along = dot(up, up) < dot(down, down) ? up : down;
    float footprint = 16.0 * length(world) * InvProjMat[1][1] / ScreenSize.y;
    if (max(dot(across, across), dot(along, along)) > footprint * footprint)
        return -normalize(world);

    vec3 normal = normalize(cross(across, along));
    return dot(normal, world) > 0.0 ? -normal : normal;
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
    vec2 frag = floor(gl_FragCoord.xy) * LightScale + 0.5 + Jitter;
    vec2 uv = frag / ScreenSize;
    float depth = texture(DepthSampler, uv).r;
    if (depth >= 1.0 || Translucent > 0.5 && depth >= texelFetch(OpaqueDepth, ivec2(frag), 0).r)
        discard;

    vec3 world = worldAt(uv, depth);
    vec3 normal = axis(normalAt(frag, world));

    vec3 point = world;
    if (Pixelated > 0.5)
        point = (floor((world + CameraFract) * 16.0) + 0.5) / 16.0 - CameraFract;
    point += normal * 0.05;

    ivec2 tile = ivec2(frag) / TileSize;
    int tileWord = texelFetch(Tiles, tile, 0).r;
    int count = tileWord & 63;
    int offset = tileWord >> 6;
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

        vec3 through = Shadows > 0.5 ? passing(point, place.xyz) : vec3(1.0);
        if (max(through.r, max(through.g, through.b)) <= 0.0)
            continue;

        vec4 tint = texelFetch(LightData, ivec2(index, 1), 0);
        float seed = texelFetch(LightData, ivec2(index, 2), 0).x;
        float stepped = Pixelated > 0.5 ? floor(level) : level;
        float bright = stepped / 15.0;
        bright = bright / (2.2 - 1.2 * bright);

        light = max(light, hueOf(tint.rgb) * through * bright * rhythm(int(tint.a + 0.5), seed));
    }

    light *= 1.0 - smoothstep(48.0, 64.0, length(world));
    if (max(light.r, max(light.g, light.b)) <= 0.001)
        discard;

    fragColor = vec4(light * Strength, 1.0);
}
