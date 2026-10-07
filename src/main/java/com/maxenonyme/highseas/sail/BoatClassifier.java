package com.maxenonyme.highseas.sail;

import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BoatClassifier {
    private BoatClassifier() {
    }

    private static final int REFRESH = 20;

    private record Cached(long tick, Map<UUID, SubLevel> rootMap) {
    }

    private static final Map<Level, Cached> CACHE = new ConcurrentHashMap<>();

    private static Map<UUID, SubLevel> rootMap(Level parent, Iterable<? extends SubLevel> all, long gameTime) {
        Cached cached = CACHE.get(parent);
        if (cached != null && gameTime - cached.tick < REFRESH) {
            return cached.rootMap;
        }
        Map<UUID, SubLevel> map = new HashMap<>();
        for (SubLevel s : all) {
            if (map.containsKey(s.getUniqueId())) {
                continue;
            }
            Collection<SubLevel> chain;
            try {
                chain = SubLevelHelper.getConnectedChain(s);
            } catch (Exception e) {
                chain = List.of(s);
            }
            SubLevel hull = s;
            long biggest = volume(s);
            for (SubLevel c : chain) {
                long v = volume(c);
                if (v > biggest) {
                    biggest = v;
                    hull = c;
                }
            }
            for (SubLevel c : chain) {
                map.put(c.getUniqueId(), hull);
            }
            map.put(s.getUniqueId(), hull);
        }
        CACHE.put(parent, new Cached(gameTime, map));
        return map;
    }

    private static long volume(SubLevel sub) {
        if (sub.getPlot() == null)
            return 0L;
        BoundingBox3ic bb = sub.getPlot().getBoundingBox();
        return (long) (bb.maxX() - bb.minX() + 1) * (bb.maxY() - bb.minY() + 1) * (bb.maxZ() - bb.minZ() + 1);
    }

    public static Set<UUID> boats(Level parent, Iterable<? extends SubLevel> all, long gameTime) {
        return rootMap(parent, all, gameTime).keySet();
    }

    public static SubLevel rootOf(Level parent, Iterable<? extends SubLevel> all, SubLevel sub, long gameTime) {
        return rootMap(parent, all, gameTime).get(sub.getUniqueId());
    }

    public static boolean inAir(Level parent, SubLevel ship, Vec3 localCenter) {
        Pose3dc pose = ship.logicalPose();
        Vector3d p = new Vector3d(localCenter.x, localCenter.y, localCenter.z);
        pose.transformPosition(p);
        BlockPos bp = BlockPos.containing(p.x, p.y, p.z);
        return !parent.getFluidState(bp).is(FluidTags.WATER);
    }

    public static void clearAll() {
        CACHE.clear();
    }
}
