package com.maxenonyme.highseas.sail;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FurlState {
    private FurlState() {
    }

    private static final Map<UUID, Map<Long, Float>> REEF = new ConcurrentHashMap<>();

    public static float amount(UUID sub, long key) {
        Map<Long, Float> r = REEF.get(sub);
        return r == null ? 0.0f : r.getOrDefault(key, 0.0f);
    }

    public static float amount(UUID sub, BlockPos groupMin) {
        return amount(sub, groupMin.asLong());
    }

    public static float amount(UUID sub, int minX, int minY, int minZ) {
        return amount(sub, BlockPos.asLong(minX, minY, minZ));
    }

    public static boolean onHalyard(UUID sub, BlockPos groupMin) {
        Map<Long, Float> r = REEF.get(sub);
        return r != null && r.containsKey(groupMin.asLong());
    }

    public static void setReef(UUID sub, long key, float value) {
        REEF.computeIfAbsent(sub, k -> new ConcurrentHashMap<>()).put(key, value);
    }

    public static void dropReef(UUID sub, long key) {
        Map<Long, Float> r = REEF.get(sub);
        if (r != null)
            r.remove(key);
    }

    public static Map<Long, Float> reefs(UUID sub) {
        Map<Long, Float> r = REEF.get(sub);
        return r == null ? Map.of() : Map.copyOf(r);
    }

    public static Set<UUID> reefSubs() {
        return REEF.keySet();
    }

    public static void applyReefClient(UUID sub, List<Long> keys, List<Float> values) {
        Map<Long, Float> r = new ConcurrentHashMap<>();
        for (int i = 0; i < Math.min(keys.size(), values.size()); i++)
            r.put(keys.get(i), values.get(i));
        REEF.put(sub, r);
    }

    public static void clearAll() {
        REEF.clear();
    }
}
