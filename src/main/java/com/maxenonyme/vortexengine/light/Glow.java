package com.maxenonyme.vortexengine.light;

public record Glow(float red, float green, float blue, float radius, float intensity, float pulse, float flicker) {

    Glow scaled(float scale) {
        return new Glow(red, green, blue, radius * scale, intensity * scale, pulse, flicker);
    }

    Light at(double x, double y, double z) {
        return Light.omni(x, y, z, red, green, blue, radius, intensity, behavior());
    }

    int behavior() {
        return Math.round(pulse * 15) << 4 | Math.round(flicker * 15);
    }
}
