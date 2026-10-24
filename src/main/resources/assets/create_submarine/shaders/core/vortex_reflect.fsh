#version 150

uniform sampler2D SurfaceDepth;
uniform sampler2D OpaqueDepth;
uniform sampler2D SceneDepth;
uniform sampler2D SceneSampler;
uniform mat4 Projection;
uniform mat4 InvProjMat;
uniform mat4 InvViewMat;
uniform vec2 ScreenSize;
uniform vec3 SkyColor;
uniform float SkyLight;

out vec4 fragColor;

const int STEPS = 32;
const float REACH = 48.0;

float hash(vec3 point) {
    return fract(sin(dot(point, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

vec3 viewOf(vec2 uv, float depth) {
    vec4 view = InvProjMat * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return view.xyz / view.w;
}

vec3 surfaceAt(ivec2 pixel) {
    pixel = clamp(pixel, ivec2(0), ivec2(ScreenSize) - 1);
    return viewOf((vec2(pixel) + 0.5) / ScreenSize, texelFetch(SurfaceDepth, pixel, 0).r);
}

vec3 normalAt(ivec2 pixel, vec3 center) {
    vec3 left = surfaceAt(pixel - ivec2(1, 0));
    vec3 right = surfaceAt(pixel + ivec2(1, 0));
    vec3 down = surfaceAt(pixel - ivec2(0, 1));
    vec3 up = surfaceAt(pixel + ivec2(0, 1));

    vec3 across = abs(right.z - center.z) < abs(center.z - left.z) ? right - center : center - left;
    vec3 along = abs(up.z - center.z) < abs(center.z - down.z) ? up - center : center - down;
    vec3 normal = normalize(cross(across, along));
    return dot(normal, center) > 0.0 ? -normal : normal;
}

vec2 project(vec3 point) {
    vec4 clip = Projection * vec4(point, 1.0);
    return clip.xy / clip.w * 0.5 + 0.5;
}

bool outside(vec2 uv) {
    return any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)));
}

float gapAt(vec3 probe) {
    vec2 uv = project(probe);
    return viewOf(uv, texture(SceneDepth, uv).r).z - probe.z;
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float depth = texelFetch(SurfaceDepth, pixel, 0).r;
    if (depth >= 1.0 || depth >= texelFetch(OpaqueDepth, pixel, 0).r)
        discard;

    vec3 point = surfaceAt(pixel);
    vec3 normal = normalAt(pixel, point);
    vec3 eye = normalize(point);
    vec3 ray = reflect(eye, normal);
    float facing = clamp(dot(-eye, normal), 0.0, 1.0);
    float fresnel = 0.02 + 0.98 * pow(1.0 - facing, 5.0);

    float travel = 0.1 + hash(vec3(pixel, 0.0)) * 0.25;
    float before = 0.0;
    bool hit = false;

    for (int i = 0; i < STEPS; i++) {
        vec3 probe = point + ray * travel;
        if (probe.z > -0.05 || travel > REACH || outside(project(probe)))
            break;

        float gap = gapAt(probe);
        float stride = 0.25 + travel * 0.12;
        if (gap > 0.0 && gap < stride * 2.0 + 0.2) {
            hit = true;
            break;
        }

        before = travel;
        travel += stride;
    }

    vec3 color = SkyColor;
    float weight = smoothstep(0.0, 0.2, (mat3(InvViewMat) * ray).y) * SkyLight;

    if (hit) {
        for (int i = 0; i < 4; i++) {
            float middle = (before + travel) * 0.5;
            if (gapAt(point + ray * middle) > 0.0)
                travel = middle;
            else
                before = middle;
        }

        vec2 uv = project(point + ray * travel);
        vec2 border = min(uv, 1.0 - uv);
        float fade = smoothstep(0.0, 0.08, min(border.x, border.y)) * (1.0 - travel / REACH);
        color = mix(color, texture(SceneSampler, uv).rgb, fade);
        weight = mix(weight, 1.0, fade);
    }

    float alpha = fresnel * weight;
    if (alpha <= 0.002)
        discard;

    fragColor = vec4(color, alpha);
}
