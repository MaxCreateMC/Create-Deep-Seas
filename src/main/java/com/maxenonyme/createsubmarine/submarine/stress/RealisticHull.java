package com.maxenonyme.createsubmarine.submarine.stress;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Merge stub: the experimental rigid-lattice "realistic" solver is not ported.
 * REALISTIC currently behaves exactly like CLASSIC. Material lookup reuses the
 * cogfly {@link DefaultMaterialProperties} table; crush depths report unknown
 * so callers fall back to per-block limits.
 */
public class RealisticHull {

    public record Material(double stiffness, double yield, double nu, double volume,
            Direction.Axis grainAxis, double[] grain) {
    }

    public static Material material(BlockState state) {
        double[] props = DefaultMaterialProperties.getProperties(state);
        double stiffness = props != null ? props[0] : 5.0e9;
        double yield = props != null ? props[1] : 4.0e7;
        double nu = props != null ? props[2] : 0.25;
        double volume = 1.0;
        try {
            VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (!shape.isEmpty()) {
                AABB box = shape.bounds();
                volume = Math.min(1.0, Math.max(0.01, box.getXsize() * box.getYsize() * box.getZsize()));
            }
        } catch (RuntimeException ignored) {
        }
        return new Material(Math.max(1.0, stiffness), yield, nu, volume, null, null);
    }

    public static float[] crushDepths(int n, int[] x, int[] y, int[] z, Material[] materials,
            Long2IntOpenHashMap index, boolean[][] wet, double thickness) {
        float[] out = new float[n];
        java.util.Arrays.fill(out, Float.POSITIVE_INFINITY);
        return out;
    }
}
