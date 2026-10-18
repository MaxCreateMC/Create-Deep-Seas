package com.maxenonyme.vortexengine.light;

import com.maxenonyme.highseas.compat.iris.IrisCompat;
import com.maxenonyme.vortexengine.VortexConfig;
import com.maxenonyme.vortexengine.VortexEngine;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class VortexLights {

    private static final Set<PointLight> MOVING = new LinkedHashSet<>();

    public static boolean usable() {
        return VortexEngine.enabled() && VortexConfig.LIGHTS.get() && !IrisCompat.isShaderPackActive();
    }

    public static PointLight add(PointLight light) {
        MOVING.add(light);
        return light;
    }

    public static void remove(Object light) {
        if (light instanceof PointLight point)
            MOVING.remove(point);
    }

    static Collection<PointLight> moving() {
        return MOVING;
    }

    static void clear() {
        MOVING.clear();
    }
}
