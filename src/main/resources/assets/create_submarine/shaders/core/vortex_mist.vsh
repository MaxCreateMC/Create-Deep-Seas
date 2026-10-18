#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform sampler2D LightData;
uniform sampler3D ShadowGrid;
uniform int LightCount;
uniform vec3 CameraFract;
uniform ivec3 GridOffset;
uniform float Shadows;

out vec2 texCoord;
out vec4 vertexColor;
out vec3 worldPos;
out float shade[8];

ivec3 cellOf(vec3 point) {
    return ivec3(floor(point + CameraFract));
}

bool solid(ivec3 cell) {
    ivec3 grid = cell + GridOffset;
    if (any(lessThan(grid, ivec3(0))) || any(greaterThanEqual(grid, ivec3(128))))
        return false;
    return texelFetch(ShadowGrid, grid, 0).r > 0.5;
}

float visibility(vec3 from, vec3 to) {
    ivec3 start = cellOf(from);
    ivec3 source = cellOf(to);
    vec3 path = to - from;
    int count = int(min(length(path) * 2.0, 16.0));

    for (int i = 1; i < count; i++) {
        ivec3 cell = cellOf(from + path * (float(i) / float(count)));
        if (cell == source)
            break;
        if (cell != start && solid(cell))
            return 0.0;
    }

    return 1.0;
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord = UV0;
    vertexColor = Color;
    worldPos = Position;

    vec3 center = Position + vec3(UV1, UV2.x) / 1000.0;
    for (int i = 0; i < 8; i++) {
        shade[i] = 1.0;
        if (i < LightCount && Shadows > 0.5)
            shade[i] = visibility(center, texelFetch(LightData, ivec2(i, 0), 0).xyz);
    }
}
