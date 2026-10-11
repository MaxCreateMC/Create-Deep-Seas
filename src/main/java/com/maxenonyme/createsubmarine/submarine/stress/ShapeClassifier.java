package com.maxenonyme.createsubmarine.submarine.stress;

import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.joml.Vector3d;

import java.util.*;

public class ShapeClassifier {

    public record HullBlock(BlockPos pos, double[] rawNormals, double weight) {}
    public record ClassificationResult(
        Map<BlockPos, Vector3d> smoothedNormals,
        double coherence,
        double minCurvatureRadius,
        int hullBlockCount,
        double rawExposedFaceAvg,
        Map<BlockPos, Integer> effectiveFaceCounts
    ) {}

    /**
     * Classifies the hull into smoothed surface normals and effective face counts.
     *
     * <p>{@code exposedFaces} maps block position to a 6-bit mask of exposed face
     * directions (bit d = face d). Iterating the bits in ascending order reproduces the
     * order a {@code HashSet<Integer>} of the same small integers would have produced, so
     * the result is unchanged by the container swap.</p>
     */
    public static ClassificationResult classify(
        final Map<BlockPos, Integer> exposedFaces,
        final double kernelRadius
    ) {
        if (exposedFaces.isEmpty()) {
            return new ClassificationResult(Map.of(), 1.0, Double.MAX_VALUE, 0, 0, Map.of());
        }

        double kr = kernelRadius > 0 ? kernelRadius : (SubmarineConfig.KERNEL_RADIUS != null ? SubmarineConfig.KERNEL_RADIUS.get() : 1.5);
        double krSq = kr * kr;

        Map<BlockPos, Vector3d> rawNormals = new HashMap<>();
        Map<BlockPos, Double> rawFaceCounts = new HashMap<>();
        for (var entry : exposedFaces.entrySet()) {
            BlockPos pos = entry.getKey();
            int faces = entry.getValue();
            double nx = 0, ny = 0, nz = 0;
            int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
            for (int f = 0; f < 6; f++) {
                if ((faces & (1 << f)) == 0) continue;
                nx += dirs[f][0];
                ny += dirs[f][1];
                nz += dirs[f][2];
            }
            double len = Math.sqrt(nx*nx + ny*ny + nz*nz);
            if (len > 1e-10) {
                rawNormals.put(pos, new Vector3d(nx/len, ny/len, nz/len));
                rawFaceCounts.put(pos, (double) Integer.bitCount(faces));
            }
        }

        if (rawNormals.isEmpty()) {
            return new ClassificationResult(Map.of(), 1.0, Double.MAX_VALUE, 0, 0, Map.of());
        }

        List<BlockPos> hullPositions = new ArrayList<>(rawNormals.keySet());
        Map<BlockPos, Vector3d> smoothedNormals = new HashMap<>();
        Map<BlockPos, Integer> effectiveFaceCounts = new HashMap<>();

        // The normal-smoothing and the curvature estimate below both scan the hull with a
        // bounded kernel (radius <= 3 blocks), so a positional index lets each block visit
        // only the hull blocks that can possibly be inside the kernel instead of the whole
        // hull: O(h^2) -> O(h * 343). Candidate indices are collected and sorted ascending,
        // which reproduces the original full-scan iteration order exactly, so the sums are
        // bit-for-bit unchanged.
        final HashMap<Long, Integer> hullIndex = new HashMap<>(hullPositions.size() * 2);
        for (int i = 0; i < hullPositions.size(); i++) {
            hullIndex.put(posKey(hullPositions.get(i)), i);
        }
        final int cellRange = (int) Math.ceil(kr);   // max coordinate offset inside the kernel
        // the curvature scan below uses a fixed 3-block radius, so size for the larger of the two
        final int scanRange = Math.max(cellRange, 3);
        final int[] scratch = new int[(2 * scanRange + 1) * (2 * scanRange + 1) * (2 * scanRange + 1)];

        for (BlockPos pos : hullPositions) {
            Vector3d rn = rawNormals.get(pos);
            double sx = rn.x * 0.6, sy = rn.y * 0.6, sz = rn.z * 0.6;
            double totalW = 0.6;

            final int found = collectInRadius(hullIndex, scratch, pos, posKey(pos), krSq, cellRange);
            for (int k = 0; k < found; k++) {
                final int j = scratch[k];
                final BlockPos neighbor = hullPositions.get(j);
                final double dx = neighbor.getX() - pos.getX();
                final double dy = neighbor.getY() - pos.getY();
                final double dz = neighbor.getZ() - pos.getZ();
                final double dSq = dx * dx + dy * dy + dz * dz;

                Vector3d nn = rawNormals.get(neighbor);
                double w = 1.0 / (1.0 + Math.sqrt(dSq));
                sx += nn.x * w;
                sy += nn.y * w;
                sz += nn.z * w;
                totalW += w;
            }

            if (totalW > 0) {
                double len = Math.sqrt(sx*sx + sy*sy + sz*sz);
                if (len > 1e-10) {
                    smoothedNormals.put(pos, new Vector3d(sx/len, sy/len, sz/len));
                } else {
                    smoothedNormals.put(pos, new Vector3d(rn));
                }
            } else {
                smoothedNormals.put(pos, new Vector3d(rn));
            }

            Vector3d sn = smoothedNormals.get(pos);
            double effFaces = 0;
            int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
            int faces = exposedFaces.getOrDefault(pos, 0);
            for (int f = 0; f < 6; f++) {
                if ((faces & (1 << f)) == 0) continue;
                double dot = sn.x * dirs[f][0] + sn.y * dirs[f][1] + sn.z * dirs[f][2];
                if (dot > 0.3) effFaces += dot;
            }
            effectiveFaceCounts.put(pos, Math.max(1, (int) Math.round(effFaces)));
        }

        double totalAngle = 0;
        for (BlockPos pos : hullPositions) {
            Vector3d rn = rawNormals.get(pos);
            Vector3d sn = smoothedNormals.get(pos);
            double dot = rn.x * sn.x + rn.y * sn.y + rn.z * sn.z;
            dot = Math.max(-1.0, Math.min(1.0, dot));
            double angleRad = Math.acos(dot);
            totalAngle += angleRad;
        }
        double avgAngleDeg = Math.toDegrees(totalAngle / hullPositions.size());
        double coherence = 1.0 - avgAngleDeg / 90.0;

        final double gaussianErrorThreshold = SubmarineConfig.GAUSSIAN_MAX_ERROR != null
            ? SubmarineConfig.GAUSSIAN_MAX_ERROR.get() : 0.05;
        if (coherence < 1.0 - gaussianErrorThreshold) {
            effectiveFaceCounts.clear();
            for (BlockPos pos : hullPositions) {
                effectiveFaceCounts.put(pos, Math.max(1, rawFaceCounts.getOrDefault(pos, 1.0).intValue()));
            }
        }

        double maxAngleVar = 0;
        for (BlockPos pos : hullPositions) {
            Vector3d sn = smoothedNormals.get(pos);
            // same bounded-kernel scan as the smoothing above (dist in [0.5, 3])
            final int found = collectInRadiusBox(hullIndex, scratch, posKey(pos), 3);
            for (int k = 0; k < found; k++) {
                final BlockPos neighbor = hullPositions.get(scratch[k]);
                if (neighbor.equals(pos)) continue;
                double dx = neighbor.getX() - pos.getX();
                double dy = neighbor.getY() - pos.getY();
                double dz = neighbor.getZ() - pos.getZ();
                double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                if (dist < 0.5 || dist > 3) continue;

                Vector3d nn = smoothedNormals.get(neighbor);
                double dot = sn.x * nn.x + sn.y * nn.y + sn.z * nn.z;
                dot = Math.max(-1.0, Math.min(1.0, dot));
                double angleRad = Math.acos(dot);
                double curvature = angleRad / dist;
                if (curvature > maxAngleVar) maxAngleVar = curvature;
            }
        }
        double minRadius = maxAngleVar > 1e-10 ? (1.0 / maxAngleVar) : Double.MAX_VALUE;

        double avgFaces = rawFaceCounts.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);

        return new ClassificationResult(
            Collections.unmodifiableMap(smoothedNormals),
            coherence,
            minRadius,
            hullPositions.size(),
            avgFaces,
            Collections.unmodifiableMap(effectiveFaceCounts)
        );
    }

    /** packed key for a block position (same encoding as SolverCore.pack) */
    private static long posKey(final BlockPos p) {
        return ((long) p.getX() & 0x1FFFFFL)
             | (((long) p.getY() & 0x1FFFFFL) << 21)
             | (((long) p.getZ() & 0x1FFFFFL) << 42);
    }

    /**
     * Collects the indices (in {@code index}) of all hull positions within Euclidean
     * distance {@code maxDist} of {@code pos}, into {@code sink}, sorted ascending.
     * Ascending index order is exactly the order the original full scan visited, so the
     * accumulation order - and therefore the floating point results - are preserved.
     *
     * @return the number of indices written
     */
    private static int collectInRadius(final HashMap<Long, Integer> index, final int[] sink,
                                       final BlockPos pos, final long posKey,
                                       final double maxDistSq, final int range) {
        int count = 0;
        final int px = pos.getX(), py = pos.getY(), pz = pos.getZ();
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    final double dSq = (double) dx * dx + (double) dy * dy + (double) dz * dz;
                    if (dSq > maxDistSq) continue;          // cheap rejection before the hash probe
                    final Integer j = index.get(packKey(px + dx, py + dy, pz + dz));
                    if (j == null) continue;
                    sink[count++] = j;
                }
            }
        }
        java.util.Arrays.sort(sink, 0, count);
        return count;
    }

    /** as collectInRadius, but with an explicit box radius (the curvature scan uses [0.5, 3]) */
    private static int collectInRadiusBox(final HashMap<Long, Integer> index, final int[] sink,
                                          final long posKey, final int range) {
        int count = 0;
        final int px = (int) (posKey & 0x1FFFFFL);
        final int py = (int) ((posKey >>> 21) & 0x1FFFFFL);
        final int pz = (int) ((posKey >>> 42) & 0x1FFFFFL);
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    final Integer j = index.get(packKey(px + dx, py + dy, pz + dz));
                    if (j == null) continue;
                    sink[count++] = j;
                }
            }
        }
        java.util.Arrays.sort(sink, 0, count);
        return count;
    }

    private static long packKey(final int x, final int y, final int z) {
        return ((long) x & 0x1FFFFFL) | (((long) y & 0x1FFFFFL) << 21) | (((long) z & 0x1FFFFFL) << 42);
    }

    public static boolean useAnalytical(double coherence, double minRadius, int minApproxRadius) {
        if (minRadius < minApproxRadius) return false;
        double thresh = SubmarineConfig.COHERENCE_THRESHOLD_ANALYTICAL != null
            ? SubmarineConfig.COHERENCE_THRESHOLD_ANALYTICAL.get() : 0.85;
        return coherence >= thresh;
    }

    public static boolean useCorrected(double coherence) {
        double thresh = SubmarineConfig.COHERENCE_THRESHOLD_CORRECTED != null
            ? SubmarineConfig.COHERENCE_THRESHOLD_CORRECTED.get() : 0.60;
        return coherence >= thresh;
    }

    public static double roughnessPenalty(double coherence) {
        if (useAnalytical(coherence, Double.MAX_VALUE, 0)) return 0;
        if (!useCorrected(coherence)) return 0;
        double aThresh = SubmarineConfig.COHERENCE_THRESHOLD_ANALYTICAL != null
            ? SubmarineConfig.COHERENCE_THRESHOLD_ANALYTICAL.get() : 0.85;
        double cThresh = SubmarineConfig.COHERENCE_THRESHOLD_CORRECTED != null
            ? SubmarineConfig.COHERENCE_THRESHOLD_CORRECTED.get() : 0.60;
        double penalty = SubmarineConfig.ROUGHNESS_PENALTY != null
            ? SubmarineConfig.ROUGHNESS_PENALTY.get() : 0.05;
        double t = (aThresh - coherence) / (aThresh - cThresh);
        return penalty * Math.max(0, Math.min(1, t));
    }
}
