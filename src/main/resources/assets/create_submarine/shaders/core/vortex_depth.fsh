#version 150

uniform sampler2D DepthSampler;

out vec4 fragColor;

void main() {
    fragColor = vec4(texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r, 0.0, 0.0, 1.0);
}
