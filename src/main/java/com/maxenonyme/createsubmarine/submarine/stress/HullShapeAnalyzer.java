package com.maxenonyme.createsubmarine.submarine.stress;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * Merge stub: the experimental per-shape hull factor analysis is not ported yet.
 * HYBRID currently behaves exactly like CLASSIC (neutral factor 1.0).
 * Every method here is a pure pass-through so callers compile and the game
 * falls back to each block's own maxWaterDepth.
 */
public class HullShapeAnalyzer {

    public static double factor(UUID id, BlockPos plotPos) {
        return 1.0;
    }

    public static Long2FloatOpenHashMap exterior(UUID id) {
        return null;
    }

    public static Long2FloatOpenHashMap factors(int n, int[] x, int[] y, int[] z,
            Long2IntOpenHashMap index, boolean[][] exterior, int span, double min, double max) {
        Long2FloatOpenHashMap out = new Long2FloatOpenHashMap();
        out.defaultReturnValue(1.0f);
        return out;
    }

    public static void submit(Runnable task) {
        task.run();
    }

    public static void forget(UUID id) {
    }

    public static void clear() {
    }
}
