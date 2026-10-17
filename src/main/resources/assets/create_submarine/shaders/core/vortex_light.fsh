#version 150

uniform sampler2D DepthSampler;
uniform sampler3D ShadowGrid;
uniform mat4 InvProjMat;
uniform mat4 InvViewMat;
uniform vec2 ScreenSize;
uniform vec3 CameraFract;
uniform vec3 WorldWrap;
uniform ivec3 GridOffset;
uniform float Pixelated;
uniform float Shadows;
uniform float Haze;
uniform float Murk;
uniform float GameTime;
uniform float Strength;
uniform float HaloScale;
uniform float HaloCap;
uniform float HaloGain;
uniform float FogStart;
uniform float FogEnd;

flat in vec3 lightCenter;
flat in vec3 lightColor;
flat in vec3 lightDirection;
flat in float lightRadius;
flat in float lightIntensity;
flat in float lightCone;
flat in float lightSeed;

out vec4 fragColor;

const float BAYER[16] = float[](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);

float hash(vec3 point) {
    return fract(sin(dot(point, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

vec3 absorb(float travel) {
    if (Murk < 0.5)
        return vec3(1.0);
    return exp(-vec3(0.06, 0.025, 0.015) * travel);
}

float bayer(ivec2 cell) {
    return (BAYER[(cell.x & 3) + (cell.y & 3) * 4] + 0.5) / 16.0;
}

float pixelsPerBlock(float range) {
    return ScreenSize.y / (2.0 * InvProjMat[1][1] * max(range, 0.1));
}

float steps(float value, float count, float dither) {
    if (Pixelated < 0.5)
        return value;
    return floor(value * count + dither) / count;
}

float fogFade(float range) {
    if (range <= FogStart)
        return 1.0;
    if (range >= FogEnd)
        return 0.0;
    return smoothstep(FogEnd, FogStart, range);
}

float spotOf(vec3 fromLight) {
    if (lightCone < -0.99 || length(fromLight) < 0.001)
        return 1.0;
    return smoothstep(lightCone, lightCone + 0.15, dot(normalize(fromLight), lightDirection));
}

vec3 hot(vec3 hue, float strength) {
    return mix(hue, vec3(1.0), clamp(strength - 0.7, 0.0, 0.6));
}

ivec3 cellOf(vec3 point) {
    return ivec3(floor(point + CameraFract));
}

bool solid(ivec3 cell) {
    ivec3 grid = cell + GridOffset;
    if (any(lessThan(grid, ivec3(0))) || any(greaterThanEqual(grid, ivec3(128))))
        return false;
    return texelFetch(ShadowGrid, grid, 0).r > 0.5;
}

float shadow(vec3 from) {
    ivec3 source = cellOf(lightCenter);
    vec3 path = lightCenter - from;
    int count = int(min(length(path) * 4.0, 64.0));

    for (int i = 1; i < count; i++) {
        ivec3 cell = cellOf(from + path * (float(i) / float(count)));
        if (cell == source)
            break;
        if (solid(cell))
            return 0.0;
    }

    return 1.0;
}

float caustic(vec3 point, float time) {
    float wave = sin(point.x * 1.7 + time * 1.3) * sin(point.z * 1.3 - time)
            + sin((point.x + point.z) * 0.9 + point.y * 0.6 + time * 1.7) * 0.5;
    return 0.8 + 0.4 * clamp(wave, 0.0, 1.0);
}

vec3 surfaceLight(vec3 world, vec3 normal, vec3 hue, float time) {
    vec3 surface = world - normal * 0.01;
    vec3 texel = floor((surface + CameraFract) * 16.0);
    if (Pixelated > 0.5)
        surface = (texel + 0.5) / 16.0 - CameraFract;

    vec3 toLight = lightCenter - surface;
    float dist = length(toLight);
    if (dist >= lightRadius)
        return vec3(0.0);

    vec3 axis = abs(normal);
    ivec2 face = axis.y > axis.x && axis.y > axis.z ? ivec2(texel.xz)
            : axis.x > axis.z ? ivec2(texel.zy) : ivec2(texel.xy);

    float texelPixels = pixelsPerBlock(length(world)) / 16.0;
    float dither = mix(0.5, bayer(face), clamp(texelPixels - 1.0, 0.0, 1.0));
    float falloff = 1.0 - dist / lightRadius;
    falloff = steps(falloff * falloff, 8.0, dither);

    float facing = dot(normal, toLight / max(dist, 0.001)) * 0.5 + 0.5;
    float strength = lightIntensity * falloff * spotOf(-toLight) * mix(0.4, 1.0, facing);
    if (strength <= 0.0)
        return vec3(0.0);

    if (Murk > 0.5)
        strength *= caustic(surface + CameraFract + WorldWrap, time);
    if (Shadows > 0.5 && shadow(surface + normal * 0.1) <= 0.0)
        return vec3(0.0);

    return hot(hue, strength) * strength * absorb(length(world) + dist);
}

vec3 worldAt(vec2 uv, float depth) {
    vec4 view = InvProjMat * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return (InvViewMat * vec4(view.xyz / view.w, 1.0)).xyz;
}

float motes(vec3 eye, vec3 ray, float enter, float leave, float reach, float spread, float time) {
    float sparkle = 0.0;
    int count = int(min(leave - enter, 16.0));

    for (int i = 0; i < count; i++) {
        vec3 cell = floor(eye + ray * (enter + float(i) + 0.5) + CameraFract + WorldWrap);
        float seed = hash(cell);
        if (seed > 0.25)
            continue;

        vec3 drift = vec3(sin(time * 0.5 + seed * 40.0), sin(time * 0.3 + seed * 70.0), cos(time * 0.4 + seed * 55.0));
        vec3 mote = cell + 0.5 + drift * 0.3 - CameraFract - WorldWrap;
        vec3 toward = mote - eye;
        float along = dot(toward, ray);
        vec3 offset = abs(ray * along - toward);
        float size = along * spread * 0.5;
        if (along <= enter || along >= leave || max(offset.x, max(offset.y, offset.z)) >= size)
            continue;

        float closeness = max(1.0 - length(mote - lightCenter) / reach, 0.0);
        sparkle += closeness * (0.6 + 0.4 * sin(time * 4.0 + seed * 90.0));
    }

    return sparkle;
}

vec3 hazeLight(vec3 eye, vec3 ray, float limit, float spread, vec3 hue, float time) {
    float reach = min(lightRadius * HaloScale, HaloCap);
    vec3 center = lightCenter - eye;
    float along = dot(center, ray);
    vec3 closest = ray * along - center;
    float miss = length(closest);
    if (miss >= reach)
        return vec3(0.0);

    float chord = sqrt(reach * reach - miss * miss);
    float enter = max(along - chord, 0.0);
    float leave = min(along + chord, limit);
    if (leave <= enter)
        return vec3(0.0);

    float height = max(miss, 0.3);
    float edge = 1.0 - miss / reach;
    float glow = (atan((leave - along) / height) - atan((enter - along) / height)) / 3.14159 * edge * edge * edge;
    glow *= lightIntensity * Murk * spotOf(closest) * fogFade(clamp(along, enter, leave));
    glow = steps(glow, 8.0, 0.5);

    vec3 light = hot(hue, glow * 1.2 * HaloGain) * glow * 1.2 * HaloGain;
    if (Murk > 0.5)
        light += hot(hue, 1.0) * motes(eye, ray, enter, leave, reach, spread, time) * lightIntensity * 0.6;

    return light * absorb(along);
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float depth = texture(DepthSampler, uv).r;

    vec3 world = worldAt(uv, depth);

    vec3 normal = normalize(cross(dFdx(world), dFdy(world)));
    if (dot(normal, world) > 0.0)
        normal = -normal;

    float viewDistance = length(world);
    float time = GameTime * 1200.0;
    vec3 hue = lightColor / max(max(lightColor.r, lightColor.g), max(lightColor.b, 0.001));
    hue = pow(hue, vec3(1.5));

    vec3 light = vec3(0.0);
    if (depth < 1.0)
        light += surfaceLight(world, normal, hue, time) * fogFade(viewDistance);
    if (Haze > 0.5) {
        float widest = max(2.0, floor(ScreenSize.y / 270.0 + 0.5));
        float texelPixels = floor(pixelsPerBlock(length(lightCenter)) / 16.0);
        float cell = Pixelated > 0.5 ? clamp(texelPixels, 1.0, widest) : 1.0;
        vec2 cellUv = (floor(gl_FragCoord.xy / cell) + 0.5) * cell / ScreenSize;
        float cellDepth = texture(DepthSampler, cellUv).r;
        vec3 eye = worldAt(cellUv, 0.0);
        vec3 cellWorld = worldAt(cellUv, cellDepth) - eye;
        float cellDistance = length(cellWorld);
        float spread = 2.0 * InvProjMat[1][1] / ScreenSize.y * cell;

        light += hazeLight(eye, cellWorld / max(cellDistance, 0.0001), cellDepth < 1.0 ? cellDistance : 1.0e6,
                spread, hue, time);
    }

    if (max(light.r, max(light.g, light.b)) <= 0.0)
        discard;

    fragColor = vec4(light * 0.5 * Strength, 1.0);
}
