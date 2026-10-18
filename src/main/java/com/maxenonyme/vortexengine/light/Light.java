package com.maxenonyme.vortexengine.light;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public record Light(double x, double y, double z, float red, float green, float blue, float radius, float intensity,
        float dirX, float dirY, float dirZ, float cone, int behavior, float seed) {

    static Light omni(double x, double y, double z, float red, float green, float blue, float radius, float intensity,
            int behavior) {
        return new Light(x, y, z, red, green, blue, radius, intensity, 0, 0, 0, -1, behavior, seedOf(x, y, z));
    }

    static float seedOf(double x, double y, double z) {
        long mixed = Mth.floor(x) * 73856093L ^ Mth.floor(y) * 19349663L ^ Mth.floor(z) * 83492791L;
        return (mixed & 0xFFFF) / 65535f;
    }

    Light movedTo(double x, double y, double z) {
        return new Light(x, y, z, red, green, blue, radius, intensity, dirX, dirY, dirZ, cone, behavior, seed);
    }

    double distanceSqr(Vec3 camera) {
        double dx = x - camera.x, dy = y - camera.y, dz = z - camera.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
