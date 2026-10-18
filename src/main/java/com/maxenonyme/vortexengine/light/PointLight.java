package com.maxenonyme.vortexengine.light;

import net.minecraft.util.Mth;

public class PointLight {

    double x, y, z;
    float red = 1, green = 1, blue = 1;
    float radius = 4;
    float intensity = 1;
    float dirX, dirY, dirZ;
    float cone = -1;

    public PointLight setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public PointLight setColor(float red, float green, float blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        return this;
    }

    public PointLight setRadius(float radius) {
        this.radius = radius;
        return this;
    }

    public PointLight setIntensity(float intensity) {
        this.intensity = intensity;
        return this;
    }

    public PointLight setBeam(double x, double y, double z, float halfAngle) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0e-6)
            return this;

        dirX = (float) (x / length);
        dirY = (float) (y / length);
        dirZ = (float) (z / length);
        cone = Mth.cos(halfAngle * Mth.DEG_TO_RAD);
        return this;
    }

    Light snapshot() {
        return new Light(x, y, z, red, green, blue, radius, intensity, dirX, dirY, dirZ, cone, 0, 0.5f);
    }
}
