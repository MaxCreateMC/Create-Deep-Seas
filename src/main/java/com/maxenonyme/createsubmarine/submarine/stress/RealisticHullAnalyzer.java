package com.maxenonyme.createsubmarine.submarine.stress;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RealisticHullAnalyzer {
    private RealisticHullAnalyzer() {
    }

    private record Snapshot(Set<BlockPos> solid, int version, double thickness, Long2FloatOpenHashMap depths) {
    }

    private static final Map<UUID, Snapshot> DONE = new ConcurrentHashMap<>();
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Deep Seas realistic hull");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    public static float depth(UUID id, BlockPos plotPos) {
        Long2FloatOpenHashMap depths = depths(id);
        return depths == null ? -1f : depths.get(plotPos.asLong());
    }

    public static Long2FloatOpenHashMap depths(UUID id) {
        if (!SubmarineConfig.realisticPressure())
            return null;
        Set<BlockPos> solid = CompartmentTracker.solidBlocks(id);
        if (solid.isEmpty() || solid.size() > SubmarineConfig.REALISTIC_MAX_BLOCKS.get())
            return null;
        double thickness = SubmarineConfig.REALISTIC_WALL_THICKNESS.get();
        int version = CompartmentTracker.structureVersion(id);
        Snapshot snap = DONE.get(id);
        if (snap == null || stale(snap, solid, version, thickness))
            schedule(id, solid, version, thickness);
        return snap == null ? null : snap.depths();
    }

    private static boolean stale(Snapshot snap, Set<BlockPos> solid, int version, double thickness) {
        if (snap.thickness() != thickness)
            return true;
        if (snap.solid() == solid)
            return false;
        return snap.version() != version || snap.solid().size() != solid.size();
    }

    public static void forget(UUID id) {
        DONE.remove(id);
    }

    public static void clear() {
        DONE.clear();
    }

    private static void schedule(UUID id, Set<BlockPos> solid, int version, double thickness) {
        if (PENDING.contains(id))
            return;
        Level plot = SubLevelRegistry.getLevel(id);
        if (plot == null)
            return;
        int n = solid.size();
        int[] x = new int[n], y = new int[n], z = new int[n];
        RealisticHull.Material[] materials = new RealisticHull.Material[n];
        Long2IntOpenHashMap index = new Long2IntOpenHashMap(n);
        index.defaultReturnValue(-1);
        int i = 0;
        for (BlockPos p : solid) {
            BlockState state = plot.getBlockState(p);
            x[i] = p.getX();
            y[i] = p.getY();
            z[i] = p.getZ();
            materials[i] = RealisticHull.material(state);
            index.put(p.asLong(), i);
            i++;
        }
        boolean[][] wet = new boolean[n][6];
        boolean[] exterior = new boolean[n];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (i = 0; i < n; i++) {
            for (int d = 0; d < 6; d++) {
                m.set(x[i] + RigidLatticeSolver.DX[d], y[i] + RigidLatticeSolver.DY[d], z[i] + RigidLatticeSolver.DZ[d]);
                wet[i][d] = index.get(m.asLong()) < 0 && !CompartmentTracker.isWithinShip(id, m);
                exterior[i] |= wet[i][d];
            }
        }
        PENDING.add(id);
        WORKER.execute(() -> {
            try {
                float[] crush = RealisticHull.crushDepths(n, x, y, z, materials, index, wet, thickness);
                Long2FloatOpenHashMap depths = new Long2FloatOpenHashMap();
                depths.defaultReturnValue(-1f);
                for (int k = 0; k < n; k++)
                    if (exterior[k])
                        depths.put(BlockPos.asLong(x[k], y[k], z[k]), crush[k]);
                if (CompartmentTracker.solidBlocks(id) == solid)
                    DONE.put(id, new Snapshot(solid, version, thickness, depths));
            } catch (RuntimeException e) {
                CreateSubmarine.LOGGER.warn("Realistic hull analysis failed for {}", id, e);
            } finally {
                PENDING.remove(id);
            }
        });
    }
}
