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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BoatClassifier {
    private BoatClassifier() {
    }

    private static final double MIN_SUBMERGED = 0.12;
    private static final int SAMPLES = 5;
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
            if (isInWater(parent, s)) {
                try {
                    for (SubLevel c : SubLevelHelper.getConnectedChain(s)) {
                        map.putIfAbsent(c.getUniqueId(), s);
                    }
                } catch (Exception e) {
                    map.putIfAbsent(s.getUniqueId(), s);
                }
            }
        }
        CACHE.put(parent, new Cached(gameTime, map));
        return map;
    }

    public static Set<UUID> boats(Level parent, Iterable<? extends SubLevel> all, long gameTime) {
        return rootMap(parent, all, gameTime).keySet();
    }

    public static SubLevel rootOf(Level parent, Iterable<? extends SubLevel> all, SubLevel sub, long gameTime) {
        return rootMap(parent, all, gameTime).get(sub.getUniqueId());
    }

    public static boolean isInWater(Level parent, SubLevel ship) {
        if (ship.getPlot() == null) {
            return false;
        }
        BoundingBox3ic bb = ship.getPlot().getBoundingBox();
        Pose3dc pose = ship.logicalPose();
        int water = 0;
        int total = 0;
        Vector3d p = new Vector3d();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < SAMPLES; i++) {
            for (int j = 0; j < SAMPLES; j++) {
                for (int k = 0; k < SAMPLES; k++) {
                    p.set(lerp(bb.minX(), bb.maxX() + 1, frac(i)),
                            lerp(bb.minY(), bb.maxY() + 1, frac(j)),
                            lerp(bb.minZ(), bb.maxZ() + 1, frac(k)));
                    pose.transformPosition(p);
                    m.set((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z));
                    if (parent.getFluidState(m).is(FluidTags.WATER)) {
                        water++;
                    }
                    total++;
                }
            }
        }
        return total > 0 && (double) water / total >= MIN_SUBMERGED;
    }

    public static boolean inAir(Level parent, SubLevel ship, Vec3 localCenter) {
        Pose3dc pose = ship.logicalPose();
        Vector3d p = new Vector3d(localCenter.x, localCenter.y, localCenter.z);
        pose.transformPosition(p);
        BlockPos bp = BlockPos.containing(p.x, p.y, p.z);
        return !parent.getFluidState(bp).is(FluidTags.WATER);
    }

    private static double frac(int i) {
        return i / (SAMPLES - 1.0);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static void clearAll() {
        CACHE.clear();
    }
}
