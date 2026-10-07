package com.maxenonyme.createsubmarine.submarine.stress;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HullShapeAnalyzer {
    private HullShapeAnalyzer() {
    }

    private record Params(int span, double min, double max) {
    }

    private record Snapshot(Set<BlockPos> solid, int version, Params params, Long2FloatOpenHashMap factors) {
    }

    private static final Map<UUID, Snapshot> DONE = new ConcurrentHashMap<>();
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Deep Seas hull shape");
        t.setDaemon(true);
        return t;
    });

    public static double factor(UUID id, BlockPos plotPos) {
        if (!SubmarineConfig.hybridPressure())
            return 1.0;
        Long2FloatOpenHashMap factors = exterior(id);
        return factors == null ? 1.0 : factors.get(plotPos.asLong());
    }

    public static Long2FloatOpenHashMap exterior(UUID id) {
        if (!SubmarineConfig.SERVER_SPEC.isLoaded())
            return null;
        Set<BlockPos> solid = CompartmentTracker.solidBlocks(id);
        if (solid.isEmpty() || solid.size() > SubmarineConfig.SHAPE_MAX_BLOCKS.get())
            return null;
        Params params = new Params(SubmarineConfig.SHAPE_REFERENCE_SPAN.get(), SubmarineConfig.SHAPE_FACTOR_MIN.get(),
                SubmarineConfig.SHAPE_FACTOR_MAX.get());
        int version = CompartmentTracker.structureVersion(id);
        Snapshot snap = DONE.get(id);
        if (snap == null || stale(snap, solid, version, params))
            schedule(id, solid, version, params);
        return snap == null ? null : snap.factors();
    }

    private static boolean stale(Snapshot snap, Set<BlockPos> solid, int version, Params params) {
        if (!snap.params().equals(params))
            return true;
        if (snap.solid() == solid)
            return false;
        return snap.version() != version || snap.solid().size() != solid.size();
    }

    public static void submit(Runnable task) {
        WORKER.execute(task);
    }

    public static void forget(UUID id) {
        DONE.remove(id);
    }

    public static void clear() {
        DONE.clear();
    }

    private static void schedule(UUID id, Set<BlockPos> solid, int version, Params params) {
        if (!PENDING.add(id))
            return;
        WORKER.execute(() -> {
            try {
                Long2FloatOpenHashMap factors = analyse(id, solid, params);
                if (CompartmentTracker.solidBlocks(id) == solid)
                    DONE.put(id, new Snapshot(solid, version, params, factors));
            } catch (RuntimeException e) {
                CreateSubmarine.LOGGER.warn("Hull shape analysis failed for {}", id, e);
            } finally {
                PENDING.remove(id);
            }
        });
    }

    private static Long2FloatOpenHashMap analyse(UUID id, Set<BlockPos> solid, Params params) {
        int n = solid.size();
        int[] x = new int[n], y = new int[n], z = new int[n];
        Long2IntOpenHashMap index = new Long2IntOpenHashMap(n);
        index.defaultReturnValue(-1);
        int i = 0;
        for (BlockPos p : solid) {
            x[i] = p.getX();
            y[i] = p.getY();
            z[i] = p.getZ();
            index.put(p.asLong(), i);
            i++;
        }

        boolean[][] exterior = new boolean[n][6];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (i = 0; i < n; i++) {
            for (int d = 0; d < 6; d++) {
                m.set(x[i] + SolverCore.DX[d], y[i] + SolverCore.DY[d], z[i] + SolverCore.DZ[d]);
                exterior[i][d] = index.get(m.asLong()) < 0 && !CompartmentTracker.isWithinShip(id, m);
            }
        }
        return factors(n, x, y, z, index, exterior, params.span(), params.min(), params.max());
    }

    public static Long2FloatOpenHashMap factors(int n, int[] x, int[] y, int[] z, Long2IntOpenHashMap index,
            boolean[][] exterior, int span, double min, double max) {
        int[][] neighbors = new int[n][6];
        boolean[] facesOut = new boolean[n];
        int i;
        for (i = 0; i < n; i++) {
            for (int d = 0; d < 6; d++) {
                neighbors[i][d] = index.get(BlockPos.asLong(x[i] + SolverCore.DX[d], y[i] + SolverCore.DY[d],
                        z[i] + SolverCore.DZ[d]));
                facesOut[i] |= exterior[i][d];
            }
        }

        double[] ones = new double[n];
        Arrays.fill(ones, 1.0);
        SolverCore core = new SolverCore(n, x, y, z, ones, ones, neighbors, null, null, null, null, 0, ones, null,
                ones);
        core.exteriorFaces = exterior;
        double[] f = HullShapeFactor.compute(core, span, min, max);

        Long2FloatOpenHashMap factors = new Long2FloatOpenHashMap();
        factors.defaultReturnValue(1f);
        for (i = 0; i < n; i++) {
            if (facesOut[i])
                factors.put(BlockPos.asLong(x[i], y[i], z[i]), (float) f[i]);
        }
        return factors;
    }
}
