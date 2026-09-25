package com.maxenonyme.highseas.client;

import java.lang.reflect.Method;

public final class IrisCompat {
    private IrisCompat() {
    }

    private static Object nonePhase;
    private static boolean noneResolved;

    private static Object irisApi;
    private static Method shaderPackInUse;
    private static boolean apiResolved;

    public static Object nonePhase() {
        if (!noneResolved) {
            noneResolved = true;
            try {
                nonePhase = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPhase")
                        .getField("NONE").get(null);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return nonePhase;
    }

    public static boolean isShaderPackActive() {
        if (!apiResolved) {
            apiResolved = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                shaderPackInUse = api.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException ignored) {
            }
        }
        if (irisApi == null || shaderPackInUse == null) {
            return false;
        }
        try {
            return (boolean) shaderPackInUse.invoke(irisApi);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
