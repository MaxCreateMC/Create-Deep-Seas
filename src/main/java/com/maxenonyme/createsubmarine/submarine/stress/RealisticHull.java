package com.maxenonyme.createsubmarine.submarine.stress;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

public class RealisticHull {
    public static final float UNBREAKABLE = 1.0e6f;
    private static final double REFERENCE_DEPTH_PER_PASCAL = 5.06e-6;
    private static final Map<BlockState, Material> MATERIALS = new ConcurrentHashMap<>();

    private static final ForkJoinPool POOL = new ForkJoinPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
            pool -> {
                ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                t.setName("Deep Seas hull solver " + t.getPoolIndex());
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            }, null, false);

    public record Geometry(AABB bounds, double volume, double[] faceArea, double[] faceCenter, double[] centroid) {
    }

    public record Material(double stiffness, double yield, double tensile, boolean brittle, double nu,
            Direction.Axis grainAxis, double[] grain, Geometry geometry) {
    }

    public static Material material(BlockState state) {
        return MATERIALS.computeIfAbsent(state, RealisticHull::resolve);
    }

    private static Material resolve(BlockState state) {
        double[] props = MaterialProperties.of(state);
        Direction.Axis axis = null;
        if (state.hasProperty(BlockStateProperties.AXIS))
            axis = state.getValue(BlockStateProperties.AXIS);

        double[] grain = axis == null ? null : MaterialProperties.grain(state);
        return new Material(Math.max(1.0, props[0]), props[1], MaterialProperties.tensile(state),
                MaterialProperties.brittle(state), props[2], axis, grain, geometry(state));
    }

    private static Geometry geometry(BlockState state) {
        double[] area = {1, 1, 1, 1, 1, 1};
        double[] center = new double[18];
        double[] centroid = {0.5, 0.5, 0.5};

        for (int d = 0; d < 6; d++) {
            center[3 * d] = 0.5 + 0.5 * RigidLatticeSolver.DX[d];
            center[3 * d + 1] = 0.5 + 0.5 * RigidLatticeSolver.DY[d];
            center[3 * d + 2] = 0.5 + 0.5 * RigidLatticeSolver.DZ[d];
        }

        VoxelShape shape;
        try {
            shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        } catch (RuntimeException e) {
            return new Geometry(null, 1.0, area, center, centroid);
        }
        if (shape.isEmpty())
            return new Geometry(null, 1.0, area, center, centroid);

        AABB bounds = shape.bounds();
        double[] min = {bounds.minX, bounds.minY, bounds.minZ};
        double[] max = {bounds.maxX, bounds.maxY, bounds.maxZ};

        double[] mass = new double[4];
        double[] faceArea = new double[6];
        double[] faceSum = new double[18];
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            double[] lo = {x1, y1, z1};
            double[] hi = {x2, y2, z2};
            double volume = (x2 - x1) * (y2 - y1) * (z2 - z1);
            for (int k = 0; k < 3; k++)
                mass[k] += volume * (lo[k] + hi[k]) * 0.5;
            mass[3] += volume;

            for (int d = 0; d < 6; d++) {
                int axis = d / 2;
                double plane = d % 2 == 0 ? hi[axis] : lo[axis];
                double edge = d % 2 == 0 ? max[axis] : min[axis];
                if (Math.abs(plane - edge) > 1e-7)
                    continue;

                int u = (axis + 1) % 3, v = (axis + 2) % 3;
                double a = (hi[u] - lo[u]) * (hi[v] - lo[v]);

                faceArea[d] += a;
                faceSum[3 * d + axis] += a * plane;
                faceSum[3 * d + u] += a * (lo[u] + hi[u]) * 0.5;
                faceSum[3 * d + v] += a * (lo[v] + hi[v]) * 0.5;
            }
        });

        if (mass[3] > 1e-9)
            for (int k = 0; k < 3; k++)
                centroid[k] = mass[k] / mass[3];

        for (int d = 0; d < 6; d++) {
            if (faceArea[d] <= 1e-9)
                continue;

            area[d] = Math.min(1.0, faceArea[d]);
            for (int k = 0; k < 3; k++)
                center[3 * d + k] = faceSum[3 * d + k] / faceArea[d];
        }
        return new Geometry(bounds, Mth.clamp(mass[3], 0.01, 1.0), area, center, centroid);
    }

    public static float[] crushDepths(int n, int[] x, int[] y, int[] z, Material[] materials, Long2IntOpenHashMap index,
            boolean[][] wet, double thickness) {
        return POOL.submit(() -> solve(n, x, y, z, materials, index, wet, thickness)).join();
    }

    private static float[] solve(int n, int[] x, int[] y, int[] z, Material[] materials, Long2IntOpenHashMap index,
            boolean[][] wet, double thickness) {
        int[][] neighbors = new int[n][RigidLatticeSolver.NEIGHBORS];
        double[][] stiffness = new double[n][RigidLatticeSolver.NEIGHBORS];
        double[] limit = new double[n];

        for (int i = 0; i < n; i++) {
            Material a = materials[i];
            double grainSum = 0;
            int faces = 0;
            for (int d = 0; d < RigidLatticeSolver.NEIGHBORS; d++) {
                int dx = RigidLatticeSolver.DX[d], dy = RigidLatticeSolver.DY[d], dz = RigidLatticeSolver.DZ[d];
                int j = index.get(BlockPos.asLong(x[i] + dx, y[i] + dy, z[i] + dz));
                if (j >= 0 && !touches(a, materials[j], d))
                    j = -1;

                neighbors[i][d] = j;
                if (j < 0)
                    continue;

                Material b = materials[j];
                double ea = a.stiffness() * grain(a, dx, dy, dz);
                double eb = b.stiffness() * grain(b, -dx, -dy, -dz);
                double joint = 2.0 * ea * eb / (ea + eb + 1e-30);

                double volume = 0.5 * (a.geometry().volume() + b.geometry().volume());
                stiffness[i][d] = joint * volume * RigidLatticeSolver.INV_DIST[d];
                if (d < 6) {
                    grainSum += grain(a, dx, dy, dz);
                    faces++;
                }
            }

            double grainFactor = faces > 0 ? grainSum / faces : 1.0;
            limit[i] = a.brittle() ? a.tensile() : a.yield() * Math.max(grainFactor, 0.05);
        }

        double[] crush = new RigidLatticeSolver(n, x, y, z, materials, limit, neighbors, stiffness, wet).crushDepths();

        float[] out = new float[n];
        double scale = thickness * thickness;
        for (int i = 0; i < n; i++) {
            double c = crush == null ? Double.POSITIVE_INFINITY : crush[i];
            out[i] = (float) Math.min(UNBREAKABLE, Math.sqrt(c * limit[i] * REFERENCE_DEPTH_PER_PASCAL) * scale);
        }
        return out;
    }

    private static boolean touches(Material a, Material b, int d) {
        AABB from = a.geometry().bounds(), to = b.geometry().bounds();
        if (d >= 6 || from == null || to == null)
            return true;

        Direction.Axis axis = Direction.Axis.VALUES[d / 2];

        if (d % 2 == 0)
            return from.max(axis) + 1e-9 >= 1.0 + to.min(axis);
        return from.min(axis) - 1e-9 <= to.max(axis) - 1.0;
    }

    private static double grain(Material mat, int dx, int dy, int dz) {
        if (mat.grainAxis() == null || mat.grain() == null)
            return 1.0;

        double along = switch (mat.grainAxis()) {
            case X -> Math.abs(dx);
            case Y -> Math.abs(dy);
            case Z -> Math.abs(dz);
        };
        double weight = along / Math.sqrt(dx * dx + dy * dy + dz * dz);
        return mat.grain()[0] * weight + mat.grain()[1] * (1.0 - weight);
    }
}
