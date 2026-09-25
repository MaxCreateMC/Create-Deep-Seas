package com.maxenonyme.highseas.sail;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;

public final class FurlState {
    private FurlState() {
    }

    private static final Map<UUID, Set<Long>> FURLED = new ConcurrentHashMap<>();

    public static boolean isFurled(UUID sub, BlockPos groupMin) {
        Set<Long> s = FURLED.get(sub);
        return s != null && s.contains(groupMin.asLong());
    }

    public static boolean isFurled(UUID sub, int minX, int minY, int minZ) {
        Set<Long> s = FURLED.get(sub);
        return s != null && s.contains(BlockPos.asLong(minX, minY, minZ));
    }

    public static void setFurled(UUID sub, BlockPos groupMin, boolean furled) {
        Set<Long> s = FURLED.computeIfAbsent(sub, k -> ConcurrentHashMap.newKeySet());
        if (furled) {
            s.add(groupMin.asLong());
        } else {
            s.remove(groupMin.asLong());
        }
    }

    public static List<Long> get(UUID sub) {
        Set<Long> s = FURLED.get(sub);
        return s == null ? List.of() : new ArrayList<>(s);
    }

    public static Set<UUID> subs() {
        return FURLED.keySet();
    }

    public static void applyClient(UUID sub, List<Long> keys) {
        Set<Long> s = ConcurrentHashMap.newKeySet();
        s.addAll(keys);
        FURLED.put(sub, s);
    }

    public static void clearAll() {
        FURLED.clear();
    }

    public static void loadFromTag(CompoundTag tag) {
        FURLED.clear();
        for (String key : tag.getAllKeys()) {
            try {
                UUID sub = UUID.fromString(key);
                Set<Long> s = ConcurrentHashMap.newKeySet();
                for (long l : tag.getLongArray(key)) {
                    s.add(l);
                }
                if (!s.isEmpty()) {
                    FURLED.put(sub, s);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public static void saveToTag(CompoundTag tag) {
        for (Map.Entry<UUID, Set<Long>> e : FURLED.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            long[] arr = new long[e.getValue().size()];
            int i = 0;
            for (long l : e.getValue()) {
                arr[i++] = l;
            }
            tag.putLongArray(e.getKey().toString(), arr);
        }
    }
}
