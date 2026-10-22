#version 150

uniform sampler2D DepthSampler;
uniform sampler2D Heights;
uniform sampler2D MistData;
uniform sampler2D BeamData;
uniform sampler3D ShadowGrid;
uniform sampler3D Field;
uniform mat4 InvProjMat;
uniform mat4 InvViewMat;
uniform vec2 TargetSize;
uniform vec3 CameraFract;
uniform vec3 WorldWrap;
uniform ivec3 GridOffset;
uniform float FieldMode;
uniform float GlowFog;
uniform float Shafts;
uniform vec3 SunDir;
uniform vec3 SunColor;
uniform float SurfaceY;
uniform int MistCount;
uniform float MistScatter;
uniform float Motes;
uniform float Blocky;
uniform int BeamCount;
uniform float FieldReady;
uniform float MoteDensity;
uniform float GameTime;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

out vec4 fragColor;

const float FAINT = 0.0001;
const float REACH = 32.0;

float hash(vec3 point) {
    return fract(sin(dot(point, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

float noise(vec3 point) {
    vec3 cell = floor(point);
    vec3 t = point - cell;
    t = t * t * (3.0 - 2.0 * t);

    float a = mix(hash(cell), hash(cell + vec3(1, 0, 0)), t.x);
    float b = mix(hash(cell + vec3(0, 1, 0)), hash(cell + vec3(1, 1, 0)), t.x);
    float c = mix(hash(cell + vec3(0, 0, 1)), hash(cell + vec3(1, 0, 1)), t.x);
    float d = mix(hash(cell + vec3(0, 1, 1)), hash(cell + vec3(1, 1, 1)), t.x);
    return mix(mix(a, b, t.y), mix(c, d, t.y), t.z);
}

bool solid(ivec3 cell) {
    ivec3 grid = cell + GridOffset;
    if (any(lessThan(grid, ivec3(0))) || any(greaterThanEqual(grid, ivec3(128))))
        return false;
    return texelFetch(ShadowGrid, grid, 0).a > 0.5;
}

vec3 hueOf(vec3 hue) {
    return hue / max(max(hue.r, hue.g), max(hue.b, 0.001));
}

vec3 glowAt(vec3 point) {
    if (FieldReady < 0.5)
        return vec3(0.0);

    vec3 grid = point + CameraFract + vec3(GridOffset);
    if (Blocky > 0.5)
        grid = floor(grid) + 0.5;
    vec4 field = texture(Field, grid / 128.0);

    if (FieldMode < 1.5) {
        if (field.a <= FAINT)
            return vec3(0.0);
        float level = clamp(log(field.a / FAINT) / 10.2, 0.0, 1.0);
        return hueOf(field.rgb / field.a) * level;
    }

    return hueOf(field.rgb) * field.a;
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

float beams(vec2 point, float time) {
    vec2 snapped = floor(point * 4.0) / 4.0;
    float a = sin(snapped.x * 0.9 + time * 0.7) * sin(snapped.y * 0.7 - time * 0.5);
    float b = sin((snapped.x + snapped.y) * 0.45 + time * 0.4);
    float c = sin((snapped.x - snapped.y) * 1.3 - time * 0.9);
    float wave = (a + b * 0.6 + c * 0.4) * 0.5;
    return pow(max(wave, 0.0), 3.0) * 2.5;
}

vec3 sunAt(vec3 point, float time) {
    float below = SurfaceY - point.y;
    if (Shafts <= 0.0 || below <= 0.0 || !sunlit(point))
        return vec3(0.0);

    vec2 hit = point.xz + CameraFract.xz + WorldWrap.xz + SunDir.xz / SunDir.y * below;
    return SunColor * beams(hit, time) * exp(-below / 22.0);
}

float groundBelow(ivec3 cell) {
    for (int i = 0; i < 5; i++) {
        if (solid(cell - ivec3(0, i + 1, 0)))
            return float(i);
    }

    return -1.0;
}

float mistAt(ivec3 cell, float time, out vec3 tint) {
    tint = vec3(0.0);
    if (solid(cell))
        return 0.0;

    float height = groundBelow(cell);
    if (height < 0.0)
        return 0.0;

    vec3 center = vec3(cell) + 0.5 - CameraFract;
    float density = 0.0;

    for (int i = 0; i < 8; i++) {
        if (i >= MistCount)
            break;

        vec4 shape = texelFetch(MistData, ivec2(i, 0), 0);
        vec4 tone = texelFetch(MistData, ivec2(i, 1), 0);
        vec3 offset = center - shape.xyz;
        float edge = 1.0 - dot(offset.xz, offset.xz) / (shape.w * shape.w);
        if (edge <= 0.0 || offset.y > 8.0 || offset.y < -32.0)
            continue;

        float layer = exp(-height / (0.6 + shape.w * 0.03));
        vec3 world = vec3(cell) + WorldWrap;
        float billow = noise(world * 0.23 + vec3(time * 0.05, time * 0.01, time * 0.03)) * 0.65
                + noise(world * 0.51 - vec3(time * 0.04)) * 0.35;
        float amount = tone.a * edge * layer * smoothstep(0.32, 0.72, billow);

        density += amount;
        tint += tone.rgb * amount;
    }

    if (density > 0.0)
        tint /= density;
    return density;
}

vec3 moteAt(ivec3 cell, vec3 eye, vec3 ray, float limit, float spread, float time) {
    vec3 world = vec3(cell) + WorldWrap;
    float seed = hash(world + 17.0);
    float plankton = Motes < 1.5 ? 1.0 : 0.0;
    if (seed > mix(0.035, 0.09, plankton) * MoteDensity)
        return vec3(0.0);

    float fall = fract(time * 0.035 + seed * 7.0);
    vec3 drift = plankton > 0.5
            ? vec3(sin(time * 0.5 + seed * 40.0), sin(time * 0.3 + seed * 70.0), cos(time * 0.4 + seed * 55.0)) * 0.3
            : vec3(sin(time * 0.2 + seed * 30.0) * 0.2, 0.45 - fall * 0.9, cos(time * 0.17 + seed * 50.0) * 0.2);
    vec3 mote = vec3(cell) + 0.5 + drift - CameraFract;
    vec3 toward = mote - eye;

    float along = dot(toward, ray);
    vec3 offset = abs(ray * along - toward);
    float size = max(along * spread * 0.6, Blocky > 0.5 ? 1.0 / 16.0 : 1.0 / 32.0);
    if (along <= 0.3 || along >= limit || max(offset.x, max(offset.y, offset.z)) >= size)
        return vec3(0.0);

    float fade = 1.0 - smoothstep(10.0, 22.0, along);
    vec3 glow = glowAt(mote);

    if (plankton > 0.5) {
        float twinkle = 0.55 + 0.45 * sin(time * 4.0 + seed * 90.0);
        return (glow * 2.6 + vec3(0.02, 0.05, 0.06) * MoteDensity) * twinkle * fade;
    }

    float ends = sin(fall * 3.14159);
    return (FogColor.rgb + glow * 1.4 + sunAt(mote, time) * 2.0 + 0.025) * ends * fade;
}

float viewDistance(vec2 uv) {
    float depth = texture(DepthSampler, uv).r;
    if (depth >= 1.0)
        return 1.0e6;

    vec4 view = InvProjMat * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return length(view.xyz / view.w);
}

vec3 beamsAt(vec2 uv) {
    vec3 sum = vec3(0.0);
    float aspect = TargetSize.x / TargetSize.y;

    for (int i = 0; i < 4; i++) {
        if (i >= BeamCount)
            break;

        vec4 place = texelFetch(BeamData, ivec2(i, 0), 0);
        vec4 tint = texelFetch(BeamData, ivec2(i, 1), 0);
        vec2 toLight = place.xy - uv;
        float reach = clamp(place.w * 1.6 / (place.z * InvProjMat[1][1] * 2.0), 0.05, 0.7);
        float screen = length(toLight * vec2(aspect, 1.0));
        if (screen >= reach)
            continue;

        float open = 0.0;
        for (int s = 1; s <= 16; s++) {
            vec2 at = uv + toLight * (float(s) / 16.0);
            bool outside = any(lessThan(at, vec2(0.0))) || any(greaterThan(at, vec2(1.0)));
            open += outside || viewDistance(at) > place.z - 1.0 ? 1.0 : 0.0;
        }

        float fade = 1.0 - screen / reach;
        sum += tint.rgb * fade * fade * floor(open) / 16.0;
    }

    return sum;
}

vec3 worldAt(vec2 uv, float depth) {
    vec4 view = InvProjMat * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return (InvViewMat * vec4(view.xyz / view.w, 1.0)).xyz;
}

void main() {
    vec2 uv = gl_FragCoord.xy / TargetSize;
    float depth = texture(DepthSampler, uv).r;
    vec3 eye = worldAt(uv, 0.0);
    vec3 ray = normalize(worldAt(uv, 0.5) - eye);
    float scene = depth >= 1.0 ? 1.0e6 : length(worldAt(uv, depth) - eye);
    float limit = min(scene, REACH);
    float time = GameTime * 1200.0;

    vec3 start = CameraFract + eye;
    vec3 corner = floor(start);
    vec3 inside = start - corner;
    vec3 delta = 1.0 / max(abs(ray), vec3(0.00001));
    vec3 toEdge = mix(inside, 1.0 - inside, step(0.0, ray));
    vec3 crossing = toEdge * delta;
    ivec3 cell = ivec3(corner);
    ivec3 stride = ivec3(sign(ray));

    vec3 scatter = vec3(0.0);
    float transmit = 1.0;
    float travelled = 0.0;
    float spread = 2.0 * InvProjMat[1][1] / TargetSize.y;

    for (int i = 0; i < 64; i++) {
        float next = min(min(crossing.x, crossing.y), crossing.z);
        float end = min(next, limit);
        float span = end - travelled;

        if (span > 0.0) {
            vec3 point = eye + ray * ((travelled + end) * 0.5);
            float fogged = 1.0 - smoothstep(FogStart, FogEnd, travelled);
            vec3 glow = GlowFog > 0.0 || MistCount > 0 ? glowAt(point) : vec3(0.0);
            vec3 sun = sunAt(point, time);
            vec3 halo = Blocky > 0.5 ? glow : glow * glow * glow;
            vec3 light = (halo * GlowFog + sun * Shafts) * fogged;

            vec3 tint;
            float mist = MistCount > 0 ? mistAt(cell, time, tint) : 0.0;
            float absorbed = 1.0 - exp(-mist * 0.9 * span);

            scatter += transmit * light * span;
            if (absorbed > 0.0) {
                vec3 lit = tint * 0.75 + glow * 0.9 + sun * 0.5 + FogColor.rgb * MistScatter;
                scatter += transmit * lit * absorbed * fogged;
                transmit *= 1.0 - absorbed;
            }

            if (Motes > 0.5 && travelled < 22.0)
                scatter += transmit * moteAt(cell, eye, ray, limit, spread, time);
        }

        travelled = end;
        if (travelled >= limit || transmit < 0.02)
            break;

        if (crossing.x <= crossing.y && crossing.x <= crossing.z) {
            crossing.x += delta.x;
            cell.x += stride.x;
        } else if (crossing.y <= crossing.z) {
            crossing.y += delta.y;
            cell.y += stride.y;
        } else {
            crossing.z += delta.z;
            cell.z += stride.z;
        }
    }

    if (BeamCount > 0) {
        vec2 cell = (floor(gl_FragCoord.xy / 2.0) + 0.5) * 2.0 / TargetSize;
        scatter += beamsAt(cell) * transmit;
    }

    fragColor = vec4(scatter, transmit);
}
