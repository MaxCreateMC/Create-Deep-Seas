layout (vertices = 4) out;

in vec2 texCoord0[];
in vec4 vertexColor[];
in float vertexDistance[];
in vec3 normalPass[];

out vec2 texCoord0_tc[];
out vec4 vertexColor_tc[];
out float vertexDistance_tc[];
out vec3 normalPass_tc[];

uniform float tessLevel;

void main(void) {
    if (gl_InvocationID == 0) {
        float t = max(tessLevel, 1.0);
        gl_TessLevelInner[0] = t;
        gl_TessLevelInner[1] = t;
        gl_TessLevelOuter[0] = t;
        gl_TessLevelOuter[1] = t;
        gl_TessLevelOuter[2] = t;
        gl_TessLevelOuter[3] = t;
    }

    gl_out[gl_InvocationID].gl_Position = gl_in[gl_InvocationID].gl_Position;
    texCoord0_tc[gl_InvocationID] = texCoord0[gl_InvocationID];
    vertexColor_tc[gl_InvocationID] = vertexColor[gl_InvocationID];
    vertexDistance_tc[gl_InvocationID] = vertexDistance[gl_InvocationID];
    normalPass_tc[gl_InvocationID] = normalPass[gl_InvocationID];
}
