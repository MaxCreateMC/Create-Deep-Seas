#version 150

uniform sampler2D AtmosphereSampler;
uniform vec2 ScreenSize;

out vec4 fragColor;

void main() {
    fragColor = texture(AtmosphereSampler, gl_FragCoord.xy / ScreenSize);
}
