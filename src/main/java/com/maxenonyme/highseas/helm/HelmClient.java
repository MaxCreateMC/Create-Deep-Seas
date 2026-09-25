package com.maxenonyme.highseas.helm;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HelmClient {
    private HelmClient() {
    }

    private static final Map<Long, float[]> STATE = new ConcurrentHashMap<>();

    public static void put(BlockPos pos, float throttle, float steer, int cap, long gameTime) {
        STATE.put(pos.asLong(), new float[] { throttle, steer, gameTime, cap });
    }

    public static int capFor(BlockPos pos, long gameTime) {
        float[] s = STATE.get(pos.asLong());
        if (s == null || gameTime - (long) s[2] > 100L)
            return 15;
        return (int) s[3];
    }

    public static float throttleFor(BlockPos pos, long gameTime) {
        float[] s = STATE.get(pos.asLong());
        if (s == null)
            return 0.0f;
        if (gameTime - (long) s[2] > 100L) {
            STATE.remove(pos.asLong());
            return 0.0f;
        }
        return s[0];
    }

    public static float steerFor(BlockPos pos, long gameTime) {
        float[] s = STATE.get(pos.asLong());
        if (s == null || gameTime - (long) s[2] > 100L)
            return 0.0f;
        return s[1];
    }

    public static void clear() {
        STATE.clear();
    }
}
