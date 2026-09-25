package com.maxenonyme.highseas.wind;

import net.minecraft.world.phys.Vec3;

public record WindSample(
        Vec3 vector,
        double weatherMult,
        double biomeMult,
        double altitudeMult,
        double occlusion,
        double gust) {

    public static final WindSample NONE = new WindSample(Vec3.ZERO, 1, 1, 1, 1, 1);

    public double strength() {
        return vector.length();
    }
}
