package com.maxenonyme.createsubmarine.submarine.stress;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * Merge stub: no background realistic analysis runs yet, so every lookup
 * reports unknown (-1) and callers use CLASSIC depths. See {@link RealisticHull}.
 */
public class RealisticHullAnalyzer {

    public static float depth(UUID id, BlockPos plotPos) {
        return -1f;
    }

    public static Long2FloatOpenHashMap depths(UUID id) {
        return null;
    }

    public static void forget(UUID id) {
    }

    public static void clear() {
    }
}
