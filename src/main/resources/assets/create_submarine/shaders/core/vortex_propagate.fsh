#version 150

uniform sampler3D ShadowGrid;
uniform sampler3D Previous;
uniform sampler3D Emission;
uniform int Slice;
uniform ivec3 Shift;
uniform vec3 OriginWrap;
uniform float Mode;
uniform float GameTime;

out vec4 fragColor;

const int SIZE = 128;
const float FAINT = 0.0001;
const float STEP = 1.0 / 15.0;

float hash(vec3 point) {
    return fract(sin(dot(point, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

bool inside(ivec3 cell) {
    return all(greaterThanEqual(cell, ivec3(0))) && all(lessThan(cell, ivec3(SIZE)));
}

bool solid(ivec3 cell) {
    return texelFetch(ShadowGrid, cell, 0).a > 0.5;
}

vec4 filtered(ivec3 cell, vec4 light) {
    vec4 grid = texelFetch(ShadowGrid, cell, 0);
    vec3 through = grid.a > 0.002 ? vec3(1.0 - grid.g) : vec3(1.0) - grid.rgb;
    return vec4(light.rgb * through, light.a * max(through.r, max(through.g, through.b)));
}

vec4 previous(ivec3 cell) {
    ivec3 from = cell + Shift;
    if (!inside(cell) || !inside(from) || solid(cell))
        return vec4(0.0);
    return texelFetch(Previous, from, 0);
}

float rhythm(ivec3 cell, int code) {
    float seed = hash(vec3(cell) + OriginWrap);
    float time = GameTime * 1200.0;
    float breathing = 0.5 - 0.5 * cos(time * 0.9 + seed * 6.2832 + dot(vec3(cell) + OriginWrap, vec3(0.21, 0.13, 0.17)));
    float beat = time * 6.0 + seed * 17.0;
    float jolt = mix(hash(vec3(floor(beat), seed, 3.0)), hash(vec3(floor(beat) + 1.0, seed, 3.0)),
            smoothstep(0.0, 1.0, fract(beat)));

    float pulse = (code & 1) != 0 ? 1.0 - 0.55 * breathing : 1.0;
    float flicker = (code & 2) != 0 ? 1.0 - 0.3 * jolt : 1.0;
    return pulse * flicker;
}

vec4 source(ivec3 cell) {
    vec4 emitted = texelFetch(Emission, cell, 0);
    if (emitted.a <= 0.0)
        return vec4(0.0);

    int code = int(emitted.a * 255.0 + 0.5) - 1;
    float reach = float(code >> 2) / 62.0 * 15.0;

    if (Mode < 1.5) {
        float energy = FAINT * exp(0.61 + 0.85 * reach) * rhythm(cell, code);
        return vec4(emitted.rgb * energy, energy);
    }

    return vec4(emitted.rgb, reach * STEP);
}

vec4 soft(ivec3 cell) {
    vec4 sum = previous(cell + ivec3(1, 0, 0)) + previous(cell - ivec3(1, 0, 0))
            + previous(cell + ivec3(0, 1, 0)) + previous(cell - ivec3(0, 1, 0))
            + previous(cell + ivec3(0, 0, 1)) + previous(cell - ivec3(0, 0, 1));
    vec4 light = sum / 6.42;
    if (light.a < FAINT * 0.05)
        light = vec4(0.0);

    return max(light, source(cell));
}

void blocky(inout vec4 best, inout vec3 hue, inout float weight, vec4 neighbour) {
    float level = neighbour.a - STEP;
    if (level <= 0.0)
        return;

    best.a = max(best.a, level);
    hue += neighbour.rgb * level * level;
    weight += level * level;
}

vec4 vanilla(ivec3 cell) {
    vec4 best = vec4(0.0);
    vec3 hue = vec3(0.0);
    float weight = 0.0;

    blocky(best, hue, weight, previous(cell + ivec3(1, 0, 0)));
    blocky(best, hue, weight, previous(cell - ivec3(1, 0, 0)));
    blocky(best, hue, weight, previous(cell + ivec3(0, 1, 0)));
    blocky(best, hue, weight, previous(cell - ivec3(0, 1, 0)));
    blocky(best, hue, weight, previous(cell + ivec3(0, 0, 1)));
    blocky(best, hue, weight, previous(cell - ivec3(0, 0, 1)));

    vec4 light = weight > 0.0 ? vec4(hue / weight, best.a) : vec4(0.0);
    vec4 emitted = source(cell);
    if (emitted.a <= 0.0)
        return light;
    if (emitted.a >= light.a)
        return vec4(mix(light.rgb, emitted.rgb, light.a > 0.0 ? 0.75 : 1.0), emitted.a);
    return light;
}

void main() {
    ivec3 cell = ivec3(ivec2(gl_FragCoord.xy), Slice);
    if (solid(cell)) {
        fragColor = vec4(0.0);
        return;
    }

    fragColor = filtered(cell, Mode < 1.5 ? soft(cell) : vanilla(cell));
}
