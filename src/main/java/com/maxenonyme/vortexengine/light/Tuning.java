package com.maxenonyme.vortexengine.light;

import com.maxenonyme.vortexengine.VortexConfig;
import net.minecraft.util.Mth;

import java.util.Objects;

class Tuning {

    private static int applied;

    static float reach() {
        return VortexConfig.REACH.get() / 100f;
    }

    static float saturate(float channel, float top) {
        float saturation = VortexConfig.SATURATION.get() / 100f;
        return Mth.clamp(top + (channel - top) * saturation, 0, top);
    }

    static int behavior(int behavior) {
        return VortexConfig.ANIMATED_LIGHTS.get() ? behavior : 0;
    }

    static void refresh() {
        int current = Objects.hash(VortexConfig.REACH.get(), VortexConfig.SATURATION.get(),
                VortexConfig.ANIMATED_LIGHTS.get());
        if (current == applied)
            return;

        boolean first = applied == 0;
        applied = current;
        if (!first)
            LightIndex.rescanAll();
    }
}
