package com.maxenonyme.createsubmarine.submarine.util;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public class SubLevelRegistry {
    public record PlotBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        public BlockPos randomInside(Random rand) {
            int rangeX = Math.max(1, maxX - minX + 1);
            int rangeY = Math.max(1, maxY - minY + 1);
            int rangeZ = Math.max(1, maxZ - minZ + 1);
            return new BlockPos(
                minX + rand.nextInt(rangeX),
                minY + rand.nextInt(rangeY),
                minZ + rand.nextInt(rangeZ)
            );
        }
        public boolean isEmpty() {
            return maxX < minX || maxY < minY || maxZ < minZ;
        }
        public boolean isOutside(BlockPos pos) {
            return pos.getX() < minX || pos.getX() > maxX
                || pos.getY() < minY || pos.getY() > maxY
                || pos.getZ() < minZ || pos.getZ() > maxZ;
        }
    }
    private static final Map<UUID, SubLevelAccess> SUBLEVELS = new ConcurrentHashMap<>();
    private static final Map<UUID, Level> LEVELS = new ConcurrentHashMap<>();
    private static final Map<UUID, PlotBounds> BOUNDS = new ConcurrentHashMap<>();
    public static void register(UUID id, SubLevelAccess subLevel, Level level, PlotBounds bounds) {
        if (bounds.isEmpty())
            return;
        SUBLEVELS.put(id, subLevel);
        LEVELS.put(id, level);
        BOUNDS.put(id, bounds);
    }
    public static void updateBounds(UUID id, int minY, int maxY) {
        PlotBounds current = BOUNDS.get(id);
        if (current != null) {
            BOUNDS.put(id, new PlotBounds(current.minX(), current.maxX(), minY, maxY, current.minZ(), current.maxZ()));
        } else {
            BOUNDS.put(id, new PlotBounds(0, 0, minY, maxY, 0, 0));
        }
    }
    public static void unregister(UUID id) {
        SUBLEVELS.remove(id);
        LEVELS.remove(id);
        BOUNDS.remove(id);
    }

    public static void clearAll() {
        SUBLEVELS.clear();
        LEVELS.clear();
        BOUNDS.clear();
    }

    public static void clearForLevel(Level level) {
        LEVELS.entrySet().removeIf(e -> {
            if (e.getValue() != level)
                return false;
            UUID id = e.getKey();
            SUBLEVELS.remove(id);
            BOUNDS.remove(id);
            return true;
        });
    }
    public static Map<UUID, SubLevelAccess> getAll() {
        return Collections.unmodifiableMap(SUBLEVELS);
    }
    public static Level getLevel(UUID id) {
        return LEVELS.get(id);
    }
    public static PlotBounds getBounds(UUID id) {
        return BOUNDS.get(id);
    }

    public static UUID findUUID(Level level) {
        for (Map.Entry<UUID, Level> entry : LEVELS.entrySet()) {
            if (entry.getValue() == level)
                return entry.getKey();
        }
        return null;
    }


    public static UUID findUUID(Level level, BlockPos plotPos) {
        for (Map.Entry<UUID, Level> entry : LEVELS.entrySet()) {
            if (entry.getValue() != level)
                continue;
            PlotBounds bounds = BOUNDS.get(entry.getKey());
            if (bounds != null && !bounds.isOutside(plotPos))
                return entry.getKey();
        }
        return null;
    }
}
