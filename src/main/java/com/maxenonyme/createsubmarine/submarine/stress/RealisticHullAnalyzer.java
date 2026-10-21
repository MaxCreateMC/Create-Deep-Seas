package com.maxenonyme.createsubmarine.submarine.stress;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import com.maxenonyme.createsubmarine.submarine.system.SubmarinePressureSystem;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.joml.Matrix4d;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RealisticHullAnalyzer {

    private record Snapshot(Set<BlockPos> solid, int version, double thickness, Long2FloatOpenHashMap depths,
            RigidLatticeSolver solver, byte[] faceClass, double surfaceY, double centerX, double centerY,
            double centerZ, double oX, double oY, double oZ, double oW) {

        int depthAtSolve() {
            return (int) Math.round(surfaceY - centerY);
        }

        boolean poseMoved(Vector3dc center, Quaterniondc orientation) {
            if (center == null || orientation == null)
                return false;
            if (Math.abs(center.x() - centerX) > 4 || Math.abs(center.y() - centerY) > 4
                    || Math.abs(center.z() - centerZ) > 4)
                return true;
            double dot = orientation.x() * oX + orientation.y() * oY + orientation.z() * oZ + orientation.w() * oW;
            return Math.abs(dot) < 0.9999;
        }
    }

    private static final long REFRESH_COOLDOWN_TICKS = 200;
    private static final double DEPTH_DRIFT_TRIGGER = 4.0;
    private static final double TILT_DOT_TRIGGER = 0.9999;

    private static final Map<UUID, Snapshot> DONE = new ConcurrentHashMap<>();
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> LAST_REFRESH = new ConcurrentHashMap<>();
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
        if (snap == null || stale(snap, solid, version, thickness)) {
            if (!CompartmentTracker.isRescanning(id))
                schedule(id, solid, version, thickness, null);
            return snap == null ? null : snap.depths();
        }
        maybeWarmRefresh(id, snap);
        return snap.depths();
    }

    private static boolean stale(Snapshot snap, Set<BlockPos> solid, int version, double thickness) {
        if (snap.thickness() != thickness)
            return true;
        if (snap.solid() == solid)
            return false;
        return snap.version() != version || snap.solid().size() != solid.size();
    }

    private static void maybeWarmRefresh(UUID id, Snapshot snap) {
        if (PENDING.contains(id))
            return;
        Level plot = SubLevelRegistry.getLevel(id);
        if (plot == null)
            return;
        long now = plot.getGameTime();
        Long last = LAST_REFRESH.get(id);
        if (last != null && now - last < REFRESH_COOLDOWN_TICKS)
            return;
        SubLevelAccess subLevel = SubLevelRegistry.getAll().get(id);
        if (subLevel == null)
            return;
        int depth = SubmarinePressureSystem.getCachedDepth(id);
        if (!snap.poseMoved(subLevel.logicalPose().position(), subLevel.logicalPose().orientation())
                && Math.abs(depth - snap.depthAtSolve()) <= DEPTH_DRIFT_TRIGGER)
            return;
        LAST_REFRESH.put(id, now);
        schedule(id, snap.solid(), snap.version(), snap.thickness(), snap);
    }

    public static void forget(UUID id) {
        DONE.remove(id);
        LAST_REFRESH.remove(id);
    }

    public static void clear() {
        DONE.clear();
        LAST_REFRESH.clear();
    }

    private static void schedule(UUID id, Set<BlockPos> solid, int version, double thickness, Snapshot warm) {
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
        for (BlockPos blockPos : solid) {
            BlockState state = plot.getBlockState(blockPos);
            x[i] = blockPos.getX();
            y[i] = blockPos.getY();
            z[i] = blockPos.getZ();
            materials[i] = RealisticHull.material(state);
            index.put(blockPos.asLong(), i);
            i++;
        }

        SubLevelAccess subLevel = SubLevelRegistry.getAll().get(id);
        Matrix4d poseMatrix = subLevel == null ? null : subLevel.logicalPose().bakeIntoMatrix(new Matrix4d());
        Vector3dc center = subLevel == null ? null : subLevel.logicalPose().position();
        Quaterniondc orientation = subLevel == null ? null : subLevel.logicalPose().orientation();
        double surfaceY = seaSurfaceY(subLevel, plot);
        if (!Double.isFinite(surfaceY))
            return;

        byte[] faceClass = buildFaceClass(id, n, x, y, z, index, plot);

        RigidLatticeSolver reuse = warm != null ? warm.solver() : null;
        double centerX = center != null ? center.x() : Double.NaN;
        double centerY = center != null ? center.y() : Double.NaN;
        double centerZ = center != null ? center.z() : Double.NaN;
        double oX = orientation != null ? orientation.x() : Double.NaN;
        double oY = orientation != null ? orientation.y() : Double.NaN;
        double oZ = orientation != null ? orientation.z() : Double.NaN;
        double oW = orientation != null ? orientation.w() : Double.NaN;

        PENDING.add(id);
        WORKER.execute(() -> {
            try {
                RealisticHull.SolveResult result = RealisticHull.solveHull(n, x, y, z, materials, index,
                        faceClass, surfaceY, poseMatrix, thickness, reuse);
                Long2FloatOpenHashMap depths = new Long2FloatOpenHashMap();
                depths.defaultReturnValue(-1f);
                for (int k = 0; k < n; k++)
                    if (isExterior(faceClass, 6 * k))
                        depths.put(BlockPos.asLong(x[k], y[k], z[k]), result.depths()[k]);
                if (!CompartmentTracker.solidBlocks(id).isEmpty() && CompartmentTracker.structureVersion(id) == version)
                    DONE.put(id, new Snapshot(solid, version, thickness, depths, result.solver(), faceClass,
                            surfaceY, centerX, centerY, centerZ, oX, oY, oZ, oW));
            } catch (RuntimeException e) {
                CreateSubmarine.LOGGER.warn("Realistic hull analysis failed for {}", id, e);
            } finally {
                PENDING.remove(id);
            }
        });
    }

    private static byte[] buildFaceClass(UUID id, int n, int[] x, int[] y, int[] z, Long2IntOpenHashMap index,
            Level plot) {
        byte[] faceClass = new byte[6 * n];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < n; i++) {
            for (int d = 0; d < 6; d++) {
                pos.set(x[i] + RigidLatticeSolver.DX[d], y[i] + RigidLatticeSolver.DY[d],
                        z[i] + RigidLatticeSolver.DZ[d]);
                if (index.get(pos.asLong()) >= 0)
                    continue;
                FluidState fluid = CompartmentTracker.realFluidState(plot, pos);
                if (!fluid.isEmpty() && (fluid.is(FluidTags.WATER) || fluid.is(FluidTags.LAVA))) {
                    faceClass[6 * i + d] = RigidLatticeSolver.WET_INTERNAL;
                    continue;
                }
                if (CompartmentTracker.isWithinShip(id, pos))
                    continue;
                faceClass[6 * i + d] = RigidLatticeSolver.WET_EXTERNAL;
            }
        }
        return faceClass;
    }

    private static boolean isExterior(byte[] faceClass, int faceBase) {
        for (int d = 0; d < 6; d++)
            if (faceClass[faceBase + d] != RigidLatticeSolver.WET_DRY)
                return true;
        return false;
    }

    private static double seaSurfaceY(SubLevelAccess subLevel, Level plot) {
        Level oceanLevel = subLevel instanceof SubLevel sl ? sl.getLevel() : plot;
        Vector3dc center = subLevel == null ? null : subLevel.logicalPose().position();
        if (oceanLevel == null || center == null)
            return Double.NaN;
        int surfaceY = SubmarinePressureSystem.measureSurfaceY(oceanLevel, center);
        return surfaceY == Integer.MIN_VALUE ? Double.NaN : surfaceY;
    }
}
