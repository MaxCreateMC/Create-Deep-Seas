package com.maxenonyme.createsubmarine.submarine.stress;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

public class RealisticHull {
    public static final float UNBREAKABLE = 1.0e6f;
    private static final double REFERENCE_DEPTH_PER_PASCAL = 3.85e-6;

    private static final ForkJoinPool POOL = new ForkJoinPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
            pool -> {
                ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                t.setName("Deep Seas hull solver " + t.getPoolIndex());
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            }, null, false);

    public record Material(double stiffness, double yield, double nu, double volume, Direction.Axis grainAxis,
            double[] grain) {
    }

    public static Material material(BlockState state) {
        double[] props = MaterialProperties.of(state);
        double volume = 1.0;
        try {
            VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (!shape.isEmpty()) {
                AABB box = shape.bounds();
                volume = Math.min(1.0, Math.max(0.01, box.getXsize() * box.getYsize() * box.getZsize()));
            }
        } catch (RuntimeException ignored) {
        }
        Direction.Axis axis = state.hasProperty(BlockStateProperties.AXIS) ? state.getValue(BlockStateProperties.AXIS) : null;
        double[] grain = axis == null ? null : MaterialProperties.grain(state);
        return new Material(Math.max(1.0, props[0]), props[1], props[2], volume, axis, grain);
    }

    public static float[] crushDepths(int n, int[] x, int[] y, int[] z, Material[] materials, Long2IntOpenHashMap index,
            boolean[][] wet, double thickness) {
        return POOL.submit(() -> solve(n, x, y, z, materials, index, wet, thickness)).join();
    }

    private static float[] solve(int n, int[] x, int[] y, int[] z, Material[] materials, Long2IntOpenHashMap index,
            boolean[][] wet, double thickness) {
        int[][] neighbors = new int[n][RigidLatticeSolver.NEIGHBORS];
        double[][] stiffness = new double[n][RigidLatticeSolver.NEIGHBORS];
        double[] yield = new double[n];
        double[] nu = new double[n];
        double[] volume = new double[n];
        for (int i = 0; i < n; i++) {
            Material a = materials[i];
            nu[i] = a.nu();
            volume[i] = a.volume();
            double grainSum = 0;
            int faces = 0;
            for (int d = 0; d < RigidLatticeSolver.NEIGHBORS; d++) {
                int dx = RigidLatticeSolver.DX[d], dy = RigidLatticeSolver.DY[d], dz = RigidLatticeSolver.DZ[d];
                int j = index.get(BlockPos.asLong(x[i] + dx, y[i] + dy, z[i] + dz));
                neighbors[i][d] = j;
                if (j < 0) {
                    stiffness[i][d] = 0;
                    continue;
                }
                Material b = materials[j];
                double ea = a.stiffness() * grain(a, dx, dy, dz);
                double eb = b.stiffness() * grain(b, -dx, -dy, -dz);
                double joint = 2.0 * ea * eb / (ea + eb + 1e-30);
                stiffness[i][d] = joint * 0.5 * (a.volume() + b.volume()) * RigidLatticeSolver.INV_DIST[d];
                if (d < 6) {
                    grainSum += grain(a, dx, dy, dz);
                    faces++;
                }
            }
            yield[i] = a.yield() * Math.max(faces > 0 ? grainSum / faces : 1.0, 0.05);
        }
        RigidLatticeSolver solver = new RigidLatticeSolver(n, x, y, z, yield, nu, volume, neighbors, stiffness, wet);
        double[] crush = solver.crushDepths();
        float[] out = new float[n];
        double scale = thickness * thickness;
        for (int i = 0; i < n; i++) {
            double c = crush == null ? Double.POSITIVE_INFINITY : crush[i];
            double reference = yield[i] * REFERENCE_DEPTH_PER_PASCAL;
            out[i] = (float) Math.min(UNBREAKABLE, Math.sqrt(c * reference) * scale);
        }
        return out;
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
