package com.maxenonyme.createsubmarine.submarine.stress;

import com.maxenonyme.createsubmarine.submarine.compat.CopycatsCompat;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.*;

public class LatticeStressSolver {

    private static final int MAX_NEIGHBORS = 26;
    private static final int[] DX = {
            1, -1, 0, 0, 0, 0,       
            1, 1, -1, -1, 0, 0, 0, 0, 1, 1, -1, -1,   
            1, 1, 1, 1, -1, -1, -1, -1   
    };
    private static final int[] DY = {
            0, 0, 1, -1, 0, 0,
            1, -1, 1, -1, 1, -1, 1, -1, 0, 0, 0, 0,
            1, 1, -1, -1, 1, 1, -1, -1
    };
    private static final int[] DZ = {
            0, 0, 0, 0, 1, -1,
            0, 0, 0, 0, 1, 1, -1, -1, 1, -1, 1, -1,
            1, -1, 1, -1, 1, -1, 1, -1
    };

    private static final double[] INV_DIST = new double[MAX_NEIGHBORS];
    private static final double[][] DIR_COS = new double[MAX_NEIGHBORS][3];

    private static final int[] DIR_TO_MC = {5, 4, 1, 0, 3, 2};

    static {
        for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
            final double d = Math.sqrt(DX[dir]*DX[dir] + DY[dir]*DY[dir] + DZ[dir]*DZ[dir]);
            INV_DIST[dir] = 1.0 / d;
            DIR_COS[dir][0] = DX[dir] / d;
            DIR_COS[dir][1] = DY[dir] / d;
            DIR_COS[dir][2] = DZ[dir] / d;
        }
    }

    public static double RHO_G = 10000.0;
    private double rhoG = RHO_G;
    private static final Set<Runnable> CONFIG_LOAD_HOOKS = new java.util.LinkedHashSet<>();

    public static void loadConfigValues() {
        try {
            if (SubmarineConfig.WATER_DENSITY_GRAVITY != null)
                RHO_G = SubmarineConfig.WATER_DENSITY_GRAVITY.get();
        } catch (Exception e) { /* keep default */ }
        for (Runnable h : CONFIG_LOAD_HOOKS) h.run();
        CONFIG_LOAD_HOOKS.clear();
    }

    static {
        // Support both pre- and post-config loading:
        if (SubmarineConfig.WATER_DENSITY_GRAVITY != null) {
            try { RHO_G = SubmarineConfig.WATER_DENSITY_GRAVITY.get(); } catch (Exception ignored) {}
        }
    }

    public void setFluidDensityMultiplier(double multiplier) {
        this.rhoG = RHO_G * multiplier;
    }

    public double getEffectiveRhoG() {
        return this.rhoG;
    }

    private final int n;
    private final BlockPos[] positions;
    private final double[] E;
    private final double[] yieldStress;
    private final int[][] neighbors;
    private final double[][] springK;
    private final int[] neighborCount;

    private final double[] u;
    private final double[] blockWaterDepths;
    private final int[] exposedFaceCount;
    private final boolean[] isHullBlock;
    private int hullBlockCount;
    private final int[] faceBlockCounts;

    private final BoundingBox3ic bounds;
    private final BlockGetter level;
    private Pose3dc subLevelPose;
    private double waterSurfaceWorldY;

    private final double[] volFraction;
    private final Map<BlockPos, Vector3d> smoothedNormals;
    private final Map<BlockPos, Integer> effectiveFaceCounts;
    private final double coherence;
    private Quaterniondc orientation;
    private boolean orientationEnabled;
    private static double MOON_POOL_FACTOR = 0.8;
    private static boolean ORIENTATION_ENABLED = true;
    private static boolean COPYCAT_INHERIT = false;

    private long solveTimeNanos = 0;
    private final long structureHash;
    final SolverCore solverCore;

    public LatticeStressSolver(final BlockGetter level, final BoundingBox3ic bounds) {
        this(level, bounds, null, null, null, null, Double.POSITIVE_INFINITY);
    }

    // Package-private constructor for programmatic use â€” takes pre-built arrays,
    // no BlockGetter needed. solve() is called with null (it doesn't use the param).
    LatticeStressSolver(
        final int n,
        final BlockPos[] positions,
        final double[] E,
        final double[] yieldStress,
        final int[][] neighbors,
        final double[][] springK,
        final double[] u,
        final double[] blockWaterDepths,
        final int[] exposedFaceCount,
        final boolean[] isHullBlock,
        final int hullBlockCount,
        final BoundingBox3ic bounds,
        final long structureHash
    ) {
        this.n = n;
        this.positions = positions;
        this.E = E;
        this.yieldStress = yieldStress;
        this.neighbors = neighbors;
        this.springK = springK;
        this.u = u;
        this.blockWaterDepths = blockWaterDepths;
        this.exposedFaceCount = exposedFaceCount;
        this.isHullBlock = isHullBlock;
        this.hullBlockCount = hullBlockCount;
        this.faceBlockCounts = new int[6];
        this.bounds = bounds;
        this.level = null;
        this.structureHash = structureHash;
        this.subLevelPose = null;
        this.waterSurfaceWorldY = Double.POSITIVE_INFINITY;
        this.volFraction = new double[n];
        java.util.Arrays.fill(this.volFraction, 1.0);
        this.smoothedNormals = Map.of();
        this.effectiveFaceCounts = Map.of();
        this.coherence = 1.0;
        this.orientation = new org.joml.Quaterniond();
        this.orientationEnabled = false;
        this.neighborCount = computeNeighborCounts();

        // Create SolverCore referencing our shared arrays
        final int[] sx = new int[n], sy = new int[n], sz = new int[n];
        for (int i = 0; i < n; i++) {
            sx[i] = positions[i].getX();
            sy[i] = positions[i].getY();
            sz[i] = positions[i].getZ();
        }
        this.solverCore = new SolverCore(n, sx, sy, sz,
            E, yieldStress, neighbors, springK, neighborCount,
            exposedFaceCount, isHullBlock, hullBlockCount, volFraction,
            u, blockWaterDepths);

        final long t0 = System.nanoTime();
        solve(null);
        this.solveTimeNanos = System.nanoTime() - t0;
    }

    public LatticeStressSolver(final BlockGetter level, final BoundingBox3ic bounds, final double[] previousU) {
        this(level, bounds, previousU, null, null, null, Double.POSITIVE_INFINITY);
    }

    public LatticeStressSolver(final BlockGetter level, final BoundingBox3ic bounds,
                                final double[] previousU,
                                final ShapeClassifier.ClassificationResult classification,
                                final Quaterniondc shipOrientation) {
        this(level, bounds, previousU, classification, shipOrientation, null, Double.POSITIVE_INFINITY);
    }

    public LatticeStressSolver(final BlockGetter level, final BoundingBox3ic bounds,
                                final double[] previousU,
                                final ShapeClassifier.ClassificationResult classification,
                                final Quaterniondc shipOrientation,
                                final Pose3dc subLevelPose,
                                final double waterSurfaceWorldY) {
        this.bounds = bounds;
        this.level = level;
        this.subLevelPose = subLevelPose;
        this.waterSurfaceWorldY = waterSurfaceWorldY;
        this.smoothedNormals = (classification != null) ? classification.smoothedNormals() : Map.of();
        this.effectiveFaceCounts = (classification != null) ? classification.effectiveFaceCounts() : Map.of();
        this.coherence = (classification != null) ? classification.coherence() : 1.0;
        this.orientation = (shipOrientation != null) ? shipOrientation : new org.joml.Quaterniond();
        this.orientationEnabled = ORIENTATION_ENABLED && shipOrientation != null;

        final List<BlockPos> posList = new ArrayList<>();
        final Long2IntOpenHashMap posToIdx = new Long2IntOpenHashMap();
        posToIdx.defaultReturnValue(-1);

        final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    mutable.set(x, y, z);
                    final BlockState state = level.getBlockState(mutable);
                    if (includeBlock(state, level, mutable)) {
                        final int idx = posList.size();
                        posList.add(mutable.immutable());
                        posToIdx.put(BlockPos.asLong(x, y, z), idx);
                    }
                }
            }
        }

        this.n = posList.size();
        this.positions = posList.toArray(new BlockPos[0]);

        long hash = 0;
        for (final BlockPos p : this.positions) {
            hash ^= BlockPos.asLong(p.getX(), p.getY(), p.getZ());
        }
        this.structureHash = hash;

        this.E = new double[this.n];
        this.yieldStress = new double[this.n];
        this.volFraction = new double[this.n];
        this.exposedFaceCount = new int[this.n];
        this.isHullBlock = new boolean[this.n];
        this.hullBlockCount = 0;

        for (int i = 0; i < this.n; i++) {
            final BlockPos pos = this.positions[i];
            final BlockState state = level.getBlockState(pos);

            if (COPYCAT_INHERIT && CopycatsCompat.isCopycatBlock(state) && level instanceof net.minecraft.world.level.Level lvl) {
                double[] props = CopycatsCompat.getMaterialProperties(lvl, pos, state);
                this.E[i] = props[0];
                this.yieldStress[i] = props[1];
            } else {
                this.E[i] = DefaultMaterialProperties.getYoungsModulus(state);
                if (this.E[i] <= 0) this.E[i] = 1.0;
                this.yieldStress[i] = DefaultMaterialProperties.getYieldStress(state);
            }

            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                this.volFraction[i] = 1.0;
            } else {
                double vol = shape.bounds().getXsize() * shape.bounds().getYsize() * shape.bounds().getZsize();
                this.volFraction[i] = Math.min(1.0, Math.max(0.01, vol));
            }
        }

        this.neighbors = new int[this.n][MAX_NEIGHBORS];
        this.springK = new double[this.n][MAX_NEIGHBORS];

        for (int i = 0; i < this.n; i++) {
            final BlockPos p = this.positions[i];
            final BlockState blockState = level.getBlockState(p);
            final double Ei = this.E[i];
            int exteriorFaces = 0;
            for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
                final int nx = p.getX() + DX[dir];
                final int ny = p.getY() + DY[dir];
                final int nz = p.getZ() + DZ[dir];
                final long key = BlockPos.asLong(nx, ny, nz);
                final int j = posToIdx.get(key);

                if (j >= 0) {
                    mutable.set(nx, ny, nz);
                    final BlockState neighborState = level.getBlockState(mutable);
                    if (includeBlock(neighborState, level, mutable) && facesTouch(dir, blockState, level, p, neighborState, level, mutable)) {
                        this.neighbors[i][dir] = j;
                        final double kVol = 0.5 * (this.volFraction[i] + this.volFraction[j]);
                        final double factorI = DefaultMaterialProperties.getDirectionalFactor(blockState, DX[dir], DY[dir], DZ[dir]);
                        final double factorJ = DefaultMaterialProperties.getDirectionalFactor(neighborState, -DX[dir], -DY[dir], -DZ[dir]);
                        final double EiEff = Ei * factorI;
                        final double EjEff = this.E[j] * factorJ;
                        final double axialK = (2.0 * EiEff * EjEff / (EiEff + EjEff + 1e-30)) * kVol;
                        this.springK[i][dir] = -(axialK * INV_DIST[dir]);
                    } else {
                        this.neighbors[i][dir] = -1;
                        this.springK[i][dir] = 0.0;
                        if (dir < 6) this.exposedFaceCount[i]++;
                    }
                } else {
                    this.neighbors[i][dir] = -1;
                    this.springK[i][dir] = 0.0;
                    if (dir < 6) this.exposedFaceCount[i]++;
                }

                if (dir < 6) {
                    if (nx < bounds.minX() || nx > bounds.maxX() ||
                            ny < bounds.minY() || ny > bounds.maxY() ||
                            nz < bounds.minZ() || nz > bounds.maxZ()) {
                        exteriorFaces++;
                    }
                }
            }

            if (exposedFaceCount[i] > 0) {
                this.isHullBlock[i] = true;
                this.hullBlockCount++;
            }
        }

        this.neighborCount = computeNeighborCounts();

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < this.n; i++) {
            if (!this.isHullBlock[i]) continue;
            BlockPos p = this.positions[i];
            if (p.getX() < minX) minX = p.getX();
            if (p.getX() > maxX) maxX = p.getX();
            if (p.getY() < minY) minY = p.getY();
            if (p.getY() > maxY) maxY = p.getY();
            if (p.getZ() < minZ) minZ = p.getZ();
            if (p.getZ() > maxZ) maxZ = p.getZ();
        }
        this.faceBlockCounts = new int[]{0, 0, 0, 0, 0, 0};
        for (int i = 0; i < this.n; i++) {
            if (!this.isHullBlock[i]) continue;
            BlockPos p = this.positions[i];
            if (p.getX() == maxX) this.faceBlockCounts[0]++;
            if (p.getX() == minX) this.faceBlockCounts[1]++;
            if (p.getY() == maxY) this.faceBlockCounts[2]++;
            if (p.getY() == minY) this.faceBlockCounts[3]++;
            if (p.getZ() == maxZ) this.faceBlockCounts[4]++;
            if (p.getZ() == minZ) this.faceBlockCounts[5]++;
        }

        this.u = new double[6 * this.n];
        this.blockWaterDepths = new double[this.n];
        if (previousU != null && previousU.length == 6 * this.n) {
            System.arraycopy(previousU, 0, this.u, 0, 6 * this.n);
        }

        // Create SolverCore referencing our shared arrays
        final int[] sx = new int[n], sy = new int[n], sz = new int[n];
        for (int i = 0; i < n; i++) {
            sx[i] = positions[i].getX();
            sy[i] = positions[i].getY();
            sz[i] = positions[i].getZ();
        }
        this.solverCore = new SolverCore(n, sx, sy, sz,
            E, yieldStress, neighbors, springK, neighborCount,
            exposedFaceCount, isHullBlock, hullBlockCount, volFraction,
            u, blockWaterDepths);

        // Wire smoothed normals for curvature-aware panel bending
        if (!this.smoothedNormals.isEmpty()) {
            final double[][] normals = new double[this.n][3];
            for (int i = 0; i < this.n; i++) {
                final Vector3d v = this.smoothedNormals.get(this.positions[i]);
                if (v != null) {
                    normals[i][0] = v.x;
                    normals[i][1] = v.y;
                    normals[i][2] = v.z;
                }
            }
            this.solverCore.smoothedNormals = normals;
        }

        // per-material Poisson ratios for strain/stress recovery
        final double[] nu = new double[this.n];
        for (int i = 0; i < this.n; i++) {
            nu[i] = DefaultMaterialProperties.getPoissonRatio(level.getBlockState(this.positions[i]));
        }
        this.solverCore.nuBlock = nu;

        // directionally-effective yield: wood/bone/basalt are weaker across the grain;
        // average the spring stiffness-derived factor over this block's neighbour dirs.
        final double[] ye = new double[this.n];
        for (int i = 0; i < this.n; i++) {
            final BlockState st = level.getBlockState(this.positions[i]);
            double sumF = 0;
            int cnt = 0;
            for (int dir = 0; dir < 6; dir++) {
                if (this.neighbors[i][dir] < 0) continue;
                sumF += DefaultMaterialProperties.getDirectionalFactor(st, DX[dir], DY[dir], DZ[dir]);
                cnt++;
            }
            final double avgF = cnt > 0 ? sumF / cnt : 1.0;
            ye[i] = this.yieldStress[i] * Math.max(avgF, 0.05);
        }
        this.solverCore.yieldEffective = ye;

        final long t0 = System.nanoTime();
        solve(level);
        this.solveTimeNanos = System.nanoTime() - t0;
    }

    private static boolean includeBlock(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir()) return false;
        VoxelShape shape = state.getCollisionShape(level, pos);
        return !shape.isEmpty() || state.isSolid();
    }

    public static boolean includeBlockForHash(BlockState state, BlockGetter level, BlockPos pos) {
        return includeBlock(state, level, pos);
    }

    /** Safe access to a ModConfigSpec config value â€” returns def if not loaded. */
    private static double cfgDouble(final Object val, final double def) {
        if (val == null) return def;
        try {
            if (val instanceof net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?> cv) {
                final Object v = cv.get();
                return v instanceof Number n ? n.doubleValue() : def;
            }
        } catch (Exception ignored) {}
        return def;
    }

    private static boolean facesTouch(int dir, BlockState aState, BlockGetter aLevel, BlockPos aPos,
                                       BlockState bState, BlockGetter bLevel, BlockPos bPos) {
        if (dir >= 6) return true;
        Direction face = Direction.from3DDataValue(DIR_TO_MC[dir]);
        VoxelShape aShape = aState.getCollisionShape(aLevel, aPos);
        VoxelShape bShape = bState.getCollisionShape(bLevel, bPos);
        if (aShape.isEmpty() || bShape.isEmpty()) return true;
        double aMax, bMin;
        switch (face.getAxis()) {
            case X: aMax = aShape.bounds().maxX; bMin = bShape.bounds().minX; break;
            case Y: aMax = aShape.bounds().maxY; bMin = bShape.bounds().minY; break;
            default: aMax = aShape.bounds().maxZ; bMin = bShape.bounds().minZ; break;
        }
        return aMax >= bMin;
    }

    public static Map<BlockPos, Set<Integer>> buildExposedFaceMap(
            final BlockGetter level, final BoundingBox3ic bounds) {
        final Map<BlockPos, Set<Integer>> result = new HashMap<>();
        final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        final Long2IntOpenHashMap present = new Long2IntOpenHashMap();
        present.defaultReturnValue(-1);

        int minX = bounds.maxX() + 1, maxX = bounds.minX() - 1;
        int minY = bounds.maxY() + 1, maxY = bounds.minY() - 1;
        int minZ = bounds.maxZ() + 1, maxZ = bounds.minZ() - 1;
        int found = 0;

        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    mutable.set(x, y, z);
                    BlockState state = level.getBlockState(mutable);
                    if (includeBlock(state, level, mutable)) {
                        present.put(BlockPos.asLong(x, y, z), 1);
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                        if (z < minZ) minZ = z;
                        if (z > maxZ) maxZ = z;
                        found++;
                    }
                }
            }
        }

        if (found == 0) return result;

        final int cropMinX = minX, cropMaxX = maxX;
        final int cropMinY = minY, cropMaxY = maxY;
        final int cropMinZ = minZ, cropMaxZ = maxZ;

        for (long key : present.keySet()) {
            BlockPos pos = BlockPos.of(key);
            BlockState state = level.getBlockState(pos);
            for (int dir = 0; dir < 6; dir++) {
                int nx = pos.getX() + (dir == 0 ? 1 : dir == 1 ? -1 : 0);
                int ny = pos.getY() + (dir == 2 ? 1 : dir == 3 ? -1 : 0);
                int nz = pos.getZ() + (dir == 4 ? 1 : dir == 5 ? -1 : 0);
                int neighborIdx = present.get(BlockPos.asLong(nx, ny, nz));
                if (neighborIdx < 0) {
                    result.computeIfAbsent(pos, k -> new HashSet<>()).add(dir);
                } else {
                    BlockState neighborState = level.getBlockState(new BlockPos(nx, ny, nz));
                    if (!facesTouch(dir, state, level, pos, neighborState, level, new BlockPos(nx, ny, nz))) {
                        result.computeIfAbsent(pos, k -> new HashSet<>()).add(dir);
                    }
                }
                if (nx < cropMinX || nx > cropMaxX ||
                    ny < cropMinY || ny > cropMaxY ||
                    nz < cropMinZ || nz > cropMaxZ) {
                    result.computeIfAbsent(pos, k -> new HashSet<>()).add(dir);
                }
            }
        }
        return result;
    }

    private void buildRHS(final double[] b) {
        java.util.Arrays.fill(b, 0.0);
        final Vector3d localDown = new Vector3d(0, -1, 0);
        if (this.orientationEnabled) {
            this.orientation.transform(localDown);
        }

        if (!Double.isFinite(this.waterSurfaceWorldY)) {
            solverCore.waterFace = null;
            buildRHSLegacy(b, localDown);
            return;
        }

        final Vector3d tmpVec = new Vector3d();
        final Vector3d worldPos = new Vector3d();
        boolean anyUnderwater = false;
        java.util.Arrays.fill(this.blockWaterDepths, 0.0);

        for (int i = 0; i < this.n; i++) {
            final BlockPos p = this.positions[i];
            final double worldMinY = computeBlockMinWorldY(p.getX(), p.getY(), p.getZ(), tmpVec, worldPos);
            if (worldMinY >= this.waterSurfaceWorldY) continue;
            final double waterDepth = this.waterSurfaceWorldY - worldMinY;
            this.blockWaterDepths[i] = waterDepth;
            anyUnderwater = true;
        }

        // which exposed faces are actually in contact with fluid
        final boolean[][] wf = solverCore.waterFace != null ? solverCore.waterFace : new boolean[this.n][6];
        for (int i = 0; i < this.n; i++) {
            for (int dir = 0; dir < 6; dir++) {
                if (this.neighbors[i][dir] >= 0) { wf[i][dir] = false; continue; }
                wf[i][dir] = isWetFace(i, dir);
            }
        }
        solverCore.waterFace = wf;

        for (int i = 0; i < this.n; i++) {
            if (this.blockWaterDepths[i] <= 0) continue;
            for (int dir = 0; dir < 6; dir++) {
                if (this.neighbors[i][dir] >= 0) continue;
                if (!wf[i][dir]) continue;
                final int comp = dir / 2;
                final double sign = (dir % 2 == 0) ? -1.0 : 1.0;
                double localPressure = this.rhoG * this.blockWaterDepths[i] * this.volFraction[i];
                final Direction faceDir = Direction.from3DDataValue(DIR_TO_MC[dir]);

                double faceDot = faceDir.getStepX() * localDown.x + faceDir.getStepY() * localDown.y + faceDir.getStepZ() * localDown.z;
                if (faceDot > 0.7) {
                    final BlockPos below = this.positions[i].relative(faceDir);
                    final int belowIdx = solverCore.findBlock(below.getX(), below.getY(), below.getZ());
                    if (belowIdx >= 0 && this.isHullBlock[belowIdx]) {
                        localPressure *= MOON_POOL_FACTOR;
                    }
                }

                b[6 * i + comp] += -sign * localPressure;
            }
        }

        if (!anyUnderwater) {
            java.util.Arrays.fill(b, 0.0);
        }
    }

    private boolean isWetFace(final int i, final int dir) {
        final BlockPos p = this.positions[i];
        final int nx = p.getX() + DX[dir];
        final int ny = p.getY() + DY[dir];
        final int nz = p.getZ() + DZ[dir];
        // first check the most specific signal: actual fluid in the adjacent plot cell
        // (flooded compartment, open hatch, breach)
        if (this.level != null && this.level instanceof net.minecraft.world.level.Level lv) {
            final net.minecraft.world.level.material.FluidState fs;
            try {
                fs = CompartmentTracker.realFluidState(lv, new BlockPos(nx, ny, nz));
            } catch (Exception e) {
                return false;
            }
            if (fs != null && !fs.isEmpty()
                && (fs.is(net.minecraft.tags.FluidTags.WATER) || fs.is(net.minecraft.tags.FluidTags.LAVA))) {
                return true;
            }
        }
        // next, ask the Sable world whether this particular real-world contact point is
        // wet. Using logicalPose positions this correctly: the sealed-union check is
        // world-based, and the plot chunk only ever serves the *inside* occupancy.
        if (this.level != null && this.level instanceof net.minecraft.world.level.Level lv) {
            final BlockPos wp;
            try {
                if (this.subLevelPose != null) {
                    final Vector3d w = new Vector3d();
                    this.subLevelPose.transformPosition(new Vector3d(nx + 0.5, ny + 0.5, nz + 0.5), w);
                    wp = BlockPos.containing(w.x, w.y, w.z);
                } else {
                    wp = new BlockPos(nx, ny, nz);
                }
                if (CompartmentTracker.findSealedSublevel(lv, wp) != null) {
                    return false; // dry sealed compartment interior — adjacent plot fluid is "air"
                }
            } catch (Exception ignored) {}
        }
        if (this.level == null) return true; // standalone: all exposed faces wet
        // any non-ship, non-sealed contact point on a submerged block is wet world fluid
        return this.blockWaterDepths[i] > 0;
    }

    private void buildRHSLegacy(final double[] b, final Vector3d localDown) {
        double minDepth = Double.POSITIVE_INFINITY, maxDepth = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < this.n; i++) {
            final BlockPos p = this.positions[i];
            final double d = p.getX() * localDown.x + p.getY() * localDown.y + p.getZ() * localDown.z;
            if (d < minDepth) minDepth = d;
            if (d > maxDepth) maxDepth = d;
        }
        final double height = Math.max(1, maxDepth - minDepth + 1);

        for (int i = 0; i < this.n; i++) {
            final BlockPos p = this.positions[i];
            final double depth = (p.getX() * localDown.x + p.getY() * localDown.y + p.getZ() * localDown.z) - minDepth;
            this.blockWaterDepths[i] = depth + 0.5;
            double pressure = this.rhoG * (0.5 + depth / height);

            for (int dir = 0; dir < 6; dir++) {
                if (this.neighbors[i][dir] >= 0) continue;
                final int comp = dir / 2;
                final double sign = (dir % 2 == 0) ? -1.0 : 1.0;
                double localPressure = pressure * this.volFraction[i];
                final Direction faceDir = Direction.from3DDataValue(DIR_TO_MC[dir]);

                double faceDot = faceDir.getStepX() * localDown.x + faceDir.getStepY() * localDown.y + faceDir.getStepZ() * localDown.z;
                if (faceDot > 0.7) {
                    final BlockPos below = this.positions[i].relative(faceDir);
                    final int belowIdx = solverCore.findBlock(below.getX(), below.getY(), below.getZ());
                    if (belowIdx >= 0 && this.isHullBlock[belowIdx]) {
                        localPressure *= MOON_POOL_FACTOR;
                    }
                }

                b[6 * i + comp] += -sign * localPressure;
            }
        }
    }

    private static boolean USE_MULTIGRID = true;

    public static void setUseMultigrid(boolean v) { USE_MULTIGRID = v; }

    private void solve(final BlockGetter level) {
        final double[] b = new double[6 * this.n];
        buildRHS(b);
        solverCore.poissonRatio = cfgDouble(SubmarineConfig.POISSON_RATIO, 0.3);
        solverCore.tikhonovAlphaFraction = cfgDouble(SubmarineConfig.TIKHONOV_ALPHA_FRACTION, 1e-6);
        solverCore.rhoG = this.rhoG;
        if (USE_MULTIGRID && this.n > 2000) {
            solveMultigrid(b);
        } else {
            solverCore.solveCG(b);
        }
        solverCore.removeRigidBodyMode(this.u);
    }

    private void solveCG(final double[] b) {
        solverCore.solveCG(b);
    }

    private void solveCG(final double[] b, final int maxIterOverride) {
        solverCore.solveCG(b, maxIterOverride);
    }

    private void solveMultigrid(final double[] b) {
        final int smoothIters = Math.max(10, Math.min(60, this.n / 200));

        solverCore.solveCG(b, smoothIters);

        final double avgE = n > 0 ? Arrays.stream(E).summaryStatistics().getAverage() : 0.0;
        final double tikhonovAlpha = solverCore.tikhonovAlphaFraction * avgE;

        final double[] r = new double[6 * this.n];
        solverCore.applyK(this.u, r);
        for (int k = 0; k < 6 * this.n; k++) {
            r[k] = b[k] - r[k] - tikhonovAlpha * this.u[k];
        }

        final CoarseGrid coarse = new CoarseGrid();
        final int cn = coarse.n;
        if (cn <= 0) return;

        final double[] cr = new double[6 * cn];
        restrict(r, cr, coarse);
        final double[] cu = new double[6 * cn];
        coarse.solveCG(cr, cu);

        final double[] correction = new double[6 * this.n];
        prolongate(cu, correction, coarse);
        for (int k = 0; k < 6 * this.n; k++) {
            this.u[k] += correction[k];
        }

        solverCore.solveCG(b, smoothIters);
    }

    private void restrict(final double[] fineR, final double[] coarseR, final CoarseGrid coarse) {
        java.util.Arrays.fill(coarseR, 0.0);
        for (int i = 0; i < this.n; i++) {
            final int ci = coarse.group[i];
            if (ci < 0) continue;
            coarseR[6 * ci]     += fineR[6 * i];
            coarseR[6 * ci + 1] += fineR[6 * i + 1];
            coarseR[6 * ci + 2] += fineR[6 * i + 2];
        }
    }

    private void prolongate(final double[] coarseU, final double[] fineCorrection, final CoarseGrid coarse) {
        java.util.Arrays.fill(fineCorrection, 0.0);
        for (int i = 0; i < this.n; i++) {
            final int ci = coarse.group[i];
            if (ci < 0) continue;
            fineCorrection[6 * i]     = coarseU[6 * ci];
            fineCorrection[6 * i + 1] = coarseU[6 * ci + 1];
            fineCorrection[6 * i + 2] = coarseU[6 * ci + 2];
        }
    }

    private class CoarseGrid {
        final int n;
        final int[] group;
        private final int[] coarseCX;
        private final int[] coarseCY;
        private final int[] coarseCZ;
        private final int[][] neighbors;
        private final double[][] springK;

        CoarseGrid() {
            final java.util.HashMap<Long, Integer> coarseMap = new java.util.HashMap<>();
            final int[] coarseIdx = new int[LatticeStressSolver.this.n];
            java.util.Arrays.fill(coarseIdx, -1);
            final int[][] keyToCXYZ = new int[LatticeStressSolver.this.n][];

            for (int i = 0; i < LatticeStressSolver.this.n; i++) {
                final BlockPos p = LatticeStressSolver.this.positions[i];
                final int cx = p.getX() >= 0 ? p.getX() / 2 : (p.getX() - 1) / 2;
                final int cy = p.getY() >= 0 ? p.getY() / 2 : (p.getY() - 1) / 2;
                final int cz = p.getZ() >= 0 ? p.getZ() / 2 : (p.getZ() - 1) / 2;
                final long key = BlockPos.asLong(cx, cy, cz);
                final int ci = coarseMap.computeIfAbsent(key, k -> {
                    final int idx = coarseMap.size();
                    keyToCXYZ[idx] = new int[]{cx, cy, cz};
                    return idx;
                });
                coarseIdx[i] = ci;
            }

            this.n = coarseMap.size();
            this.group = coarseIdx;
            this.coarseCX = new int[this.n];
            this.coarseCY = new int[this.n];
            this.coarseCZ = new int[this.n];
            for (int ci = 0; ci < this.n; ci++) {
                this.coarseCX[ci] = keyToCXYZ[ci][0];
                this.coarseCY[ci] = keyToCXYZ[ci][1];
                this.coarseCZ[ci] = keyToCXYZ[ci][2];
            }

            this.neighbors = new int[this.n][MAX_NEIGHBORS];
            this.springK = new double[this.n][MAX_NEIGHBORS];

            final double[] avgE = new double[this.n];
            final int[] count = new int[this.n];
            for (int i = 0; i < LatticeStressSolver.this.n; i++) {
                final int ci = coarseIdx[i];
                avgE[ci] += LatticeStressSolver.this.E[i];
                count[ci]++;
            }
            for (int ci = 0; ci < this.n; ci++) {
                avgE[ci] = count[ci] > 0 ? avgE[ci] / count[ci] : 1.0;
            }

            final double kVolCoarse = 2.0;
            for (int ci = 0; ci < this.n; ci++) {
                final int cx = this.coarseCX[ci];
                final int cy = this.coarseCY[ci];
                final int cz = this.coarseCZ[ci];
                for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
                    final long nKey = BlockPos.asLong(cx + DX[dir], cy + DY[dir], cz + DZ[dir]);
                    final Integer cj = coarseMap.get(nKey);
                    if (cj != null && cj != ci) {
                        this.neighbors[ci][dir] = cj;
                        final double axialK = 0.5 * (avgE[ci] + avgE[cj]) * kVolCoarse;
                        this.springK[ci][dir] = -(axialK * INV_DIST[dir]);
                    } else {
                        this.neighbors[ci][dir] = -1;
                        this.springK[ci][dir] = 0.0;
                    }
                }
            }
        }

        void solveCG(final double[] rhs, final double[] u) {
            java.util.Arrays.fill(u, 0.0);
            double bNorm = 0;
            for (int k = 0; k < 6 * this.n; k++) bNorm += rhs[k] * rhs[k];
            if (bNorm < 1e-30) return;

            // small Tikhonov term: the coarse operator is rigid-mode singular,
            // which otherwise makes CG crawl to the iteration cap
            double sE = 0;
            for (int i = 0; i < LatticeStressSolver.this.n; i++) sE += LatticeStressSolver.this.E[i];
            final double avgE = LatticeStressSolver.this.n > 0 ? sE / LatticeStressSolver.this.n : 0;
            final double tikhonovAlpha = 1e-6 * avgE;

            final double[] r = new double[6 * this.n];
            final double[] p = new double[6 * this.n];
            final double[] Ap = new double[6 * this.n];

            this.applyK(u, r);
            double rr = 0;
            for (int k = 0; k < 6 * this.n; k++) {
                r[k] = rhs[k] - r[k] - tikhonovAlpha * u[k];
                rr += r[k] * r[k];
            }

            System.arraycopy(r, 0, p, 0, 6 * this.n);
            double rrOld = rr;

            final int maxIter = Math.min(200, Math.max(50, 6 * this.n / 8));
            for (int iter = 0; iter < maxIter; iter++) {
                this.applyK(p, Ap);
                for (int k = 0; k < 6 * this.n; k++) Ap[k] += tikhonovAlpha * p[k];
                final double pAp = dot(p, Ap);
                if (pAp <= 0) break;

                final double alpha = rr / pAp;
                for (int k = 0; k < 6 * this.n; k++) {
                    u[k] += alpha * p[k];
                    r[k] -= alpha * Ap[k];
                }

                rr = dot(r, r);
                if (rr < 1e-12 * bNorm) break;

                final double beta = rr / rrOld;
                for (int k = 0; k < 6 * this.n; k++) p[k] = r[k] + beta * p[k];
                rrOld = rr;
            }
        }

        private void applyK(final double[] uvec, final double[] Ku) {
            java.util.Arrays.fill(Ku, 0.0);
            final double nu = solverCore.poissonRatio;
            for (int ci = 0; ci < this.n; ci++) {
                final int ci6 = 6 * ci;
                for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
                    final int cj = this.neighbors[ci][dir];
                    if (cj < 0) continue;
                    final double Kij = this.springK[ci][dir];
                    final int cj6 = 6 * cj;
                    final double k = -Kij;
                    if (!(k > 0)) continue;
                    final double nx = DIR_COS[dir][0], ny = DIR_COS[dir][1], nz = DIR_COS[dir][2];
                    final double L = 1.0 / INV_DIST[dir];
                    final double h = 0.5 * L;
                    final double uAx = uvec[ci6], uAy = uvec[ci6 + 1], uAz = uvec[ci6 + 2];
                    final double wIx = uvec[ci6 + 3], wIy = uvec[ci6 + 4], wIz = uvec[ci6 + 5];
                    final double uBx = uvec[cj6], uBy = uvec[cj6 + 1], uBz = uvec[cj6 + 2];
                    final double wJx = uvec[cj6 + 3], wJy = uvec[cj6 + 4], wJz = uvec[cj6 + 5];
                    final double UAx = uAx + wIy * (h * nz) - wIz * (h * ny);
                    final double UAy = uAy + wIz * (h * nx) - wIx * (h * nz);
                    final double UAz = uAz + wIx * (h * ny) - wIy * (h * nx);
                    final double UBx = uBx - wJy * (h * nz) + wJz * (h * ny);
                    final double UBy = uBy - wJz * (h * nx) + wJx * (h * nz);
                    final double UBz = uBz - wJx * (h * ny) + wJy * (h * nx);
                    final double relX = UBx - UAx, relY = UBy - UAy, relZ = UBz - UAz;
                    final double eN = nx * relX + ny * relY + nz * relZ;
                    double t1x = SolverCore.T1[dir][0], t1y = SolverCore.T1[dir][1], t1z = SolverCore.T1[dir][2];
                    final double t2x = SolverCore.T2[dir][0], t2y = SolverCore.T2[dir][1], t2z = SolverCore.T2[dir][2];
                    final double eS1 = t1x * relX + t1y * relY + t1z * relZ;
                    final double eS2 = t2x * relX + t2y * relY + t2z * relZ;
                    final double kS = k / (2.0 * (1.0 + nu));
                    final double kBend = k / 12.0;
                    final double kTor = k / 6.0;
                    final double fN = k * eN, fS1 = kS * eS1, fS2 = kS * eS2;
                    final double FxI = fN * nx + fS1 * t1x + fS2 * t2x;
                    final double FyI = fN * ny + fS1 * t1y + fS2 * t2y;
                    final double FzI = fN * nz + fS1 * t1z + fS2 * t2z;
                    final double wRelX = wJx - wIx, wRelY = wJy - wIy, wRelZ = wJz - wIz;
                    final double bend1 = wRelX * t1x + wRelY * t1y + wRelZ * t1z;
                    final double bend2 = wRelX * t2x + wRelY * t2y + wRelZ * t2z;
                    final double tor = wRelX * nx + wRelY * ny + wRelZ * nz;
                    final double MxI = kBend * bend1 * t1x + kBend * bend2 * t2x + kTor * tor * nx;
                    final double MyI = kBend * bend1 * t1y + kBend * bend2 * t2y + kTor * tor * ny;
                    final double MzI = kBend * bend1 * t1z + kBend * bend2 * t2z + kTor * tor * nz;
                    final double TxI = (h * ny) * FzI - (h * nz) * FyI + MxI;
                    final double TyI = (h * nz) * FxI - (h * nx) * FzI + MyI;
                    final double TzI = (h * nx) * FyI - (h * ny) * FxI + MzI;
                    Ku[ci6]     -= FxI;
                    Ku[ci6 + 1] -= FyI;
                    Ku[ci6 + 2] -= FzI;
                    Ku[ci6 + 3] -= TxI;
                    Ku[ci6 + 4] -= TyI;
                    Ku[ci6 + 5] -= TzI;
                }
            }
        }
    }

    public void resolve() {
        final long t0 = System.nanoTime();
        final double[] b = new double[6 * this.n];
        buildRHS(b);
        solverCore.poissonRatio = cfgDouble(SubmarineConfig.POISSON_RATIO, 0.3);
        solverCore.tikhonovAlphaFraction = cfgDouble(SubmarineConfig.TIKHONOV_ALPHA_FRACTION, 1e-6);
        solverCore.rhoG = this.rhoG;
        if (USE_MULTIGRID && this.n > 2000) {
            solveMultigrid(b);
        } else {
            solverCore.solveCG(b, 120);
        }
        solverCore.removeRigidBodyMode(this.u);
        this.solveTimeNanos = System.nanoTime() - t0;
    }

    private void removeRigidBodyMode(final double[] v) {
        solverCore.removeRigidBodyMode(v);
    }

    private void applyK(final double[] uvec, final double[] Ku) {
        solverCore.applyK(uvec, Ku);
    }

    private static double dot(final double[] a, final double[] b) {
        return SolverCore.dot(a, b);
    }

    public double getMaxWaterDepth() {
        double max = 0;
        for (int i = 0; i < this.n; i++) {
            if (this.blockWaterDepths[i] > max) max = this.blockWaterDepths[i];
        }
        return max;
    }

    public int blockCount() { return this.n; }
    public int hullBlockCount() { return this.hullBlockCount; }
    public int[] getFaceBlockCounts() { return this.faceBlockCounts; }
    public boolean isHullBlock(final int i) { return this.isHullBlock[i]; }
    public boolean isFaceExposed(final int i, final int dir) {
        return dir >= 0 && dir < 6 && i >= 0 && i < this.n && this.neighbors[i][dir] < 0;
    }
    public BlockPos getPosition(final int i) { return this.positions[i]; }

    public BlockPos getLocalPosition(final int i) {
        final BlockPos p = this.positions[i];
        return new BlockPos(
            p.getX() - this.bounds.minX(),
            p.getY() - this.bounds.minY(),
            p.getZ() - this.bounds.minZ()
        );
    }

    public BlockPos getWorldPosition(final int i) {
        final BlockPos p = this.positions[i];
        if (this.subLevelPose != null) {
            final Vector3d worldPos = new Vector3d();
            this.subLevelPose.transformPosition(new org.joml.Vector3d(p.getX(), p.getY(), p.getZ()), worldPos);
            return BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
        }
        if (this.orientationEnabled) {
            final Vector3d tmp = new Vector3d(p.getX(), p.getY(), p.getZ());
            this.orientation.transform(tmp);
            return BlockPos.containing(tmp.x, tmp.y, tmp.z);
        }
        return p;
    }

    public int getNeighbor(final int i, final int dir) {
        if (i < 0 || i >= this.n || dir < 0 || dir >= MAX_NEIGHBORS) return -1;
        return this.neighbors[i][dir];
    }

    public double getSpringK(final int i, final int dir) {
        if (i < 0 || i >= this.n || dir < 0 || dir >= MAX_NEIGHBORS) return 0;
        return this.springK[i][dir];
    }

    public static int getMaxNeighbors() { return MAX_NEIGHBORS; }
    public static int[] getDX() { return DX; }
    public static int[] getDY() { return DY; }
    public static int[] getDZ() { return DZ; }
    public static double[] getInvDist() { return INV_DIST; }

    public int getNeighborCount(final int i) {
        if (i < 0 || i >= this.n) return 0;
        return this.neighborCount[i];
    }

    private int[] computeNeighborCounts() {
        final int[] hullDist = new int[this.n];
        java.util.Arrays.fill(hullDist, Integer.MAX_VALUE);
        final ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < this.n; i++) {
            if (this.isHullBlock[i]) {
                hullDist[i] = 0;
                queue.addLast(i);
            }
        }
        while (!queue.isEmpty()) {
            final int i = queue.removeFirst();
            final int nd = hullDist[i] + 1;
            for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
                final int j = this.neighbors[i][dir];
                if (j >= 0 && hullDist[j] > nd) {
                    hullDist[j] = nd;
                    queue.addLast(j);
                }
            }
        }

        final int[] nc = new int[this.n];
        for (int i = 0; i < this.n; i++) {
            if (hullDist[i] <= 1) {
                nc[i] = 26;
            } else if (hullDist[i] <= 3) {
                nc[i] = 18;
            } else {
                nc[i] = 6;
            }
        }
        return nc;
    }

    public double getYoungsModulus(final int i) { return this.E[i]; }
    public double getYieldStress(final int i) { return this.yieldStress[i]; }
    public double getVonMises(final int i) { return computeVonMises(i); }
    public int getExposedFaceCount(final int i) {
        BlockPos pos = this.positions[i];
        Integer eff = this.effectiveFaceCounts.get(pos);
        return (eff != null) ? eff : this.exposedFaceCount[i];
    }
    public int getRawExposedFaceCount(final int i) { return this.exposedFaceCount[i]; }
    public double getCoherence() { return this.coherence; }
    public Map<BlockPos, Vector3d> getSmoothedNormals() { return this.smoothedNormals; }
    public double getVolumeFraction(final int i) { return this.volFraction[i]; }
    public long getSolveTimeNanos() { return this.solveTimeNanos; }
    public double[] getU() { return this.u; }
    public long getStructureHash() { return this.structureHash; }
    public BoundingBox3ic getBounds() { return this.bounds; }

    public int getDepthFromTop(final int i) {
        final Vector3d localDown = new Vector3d(0, -1, 0);
        if (this.orientationEnabled) {
            this.orientation.transform(localDown);
        }
        double minDepth = Double.POSITIVE_INFINITY, maxDepth = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < this.n; j++) {
            final BlockPos p = this.positions[j];
            final double d = p.getX() * localDown.x + p.getY() * localDown.y + p.getZ() * localDown.z;
            if (d < minDepth) minDepth = d;
            if (d > maxDepth) maxDepth = d;
        }
        final BlockPos pos = this.positions[i];
        final double depth = (pos.getX() * localDown.x + pos.getY() * localDown.y + pos.getZ() * localDown.z) - minDepth;
        return (int) Math.round(depth);
    }

    public double[] computeCrushDepth() {
        solverCore.poissonRatio = cfgDouble(SubmarineConfig.POISSON_RATIO, 0.3);
        return solverCore.computeCrushDepth();
    }

    public void refreshWaterDepths(final double newWaterSurfaceWorldY, final Pose3dc currentPose) {
        this.waterSurfaceWorldY = newWaterSurfaceWorldY;
        if (currentPose != null) {
            this.subLevelPose = currentPose;
            this.orientation = currentPose.orientation();
            this.orientationEnabled = ORIENTATION_ENABLED;
        }
        if (!Double.isFinite(this.waterSurfaceWorldY)) {
            java.util.Arrays.fill(this.blockWaterDepths, 0.0);
            return;
        }
        final Vector3d tmpVec = new Vector3d();
        final Vector3d worldPos = new Vector3d();
        java.util.Arrays.fill(this.blockWaterDepths, 0.0);
        for (int i = 0; i < this.n; i++) {
            final BlockPos p = this.positions[i];
            final double worldMinY = computeBlockMinWorldY(p.getX(), p.getY(), p.getZ(), tmpVec, worldPos);
            if (worldMinY >= this.waterSurfaceWorldY) continue;
            this.blockWaterDepths[i] = this.waterSurfaceWorldY - worldMinY;
        }
    }

    public void refreshWaterDepths(final double newWaterSurfaceWorldY) {
        refreshWaterDepths(newWaterSurfaceWorldY, null);
    }

    private double computeBlockMinWorldY(final int x, final int y, final int z, final Vector3d tmp, final Vector3d worldPos) {
        if (this.subLevelPose != null) {
            if (!this.orientationEnabled) {
                tmp.set(x, y, z);
                this.subLevelPose.transformPosition(tmp, worldPos);
                return worldPos.y;
            }
            double minY = Double.POSITIVE_INFINITY;
            for (int dx = 0; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        tmp.set(x + dx, y + dy, z + dz);
                        this.subLevelPose.transformPosition(tmp, worldPos);
                        if (worldPos.y < minY) minY = worldPos.y;
                    }
                }
            }
            return minY;
        }
        if (this.orientationEnabled) {
            double minY = Double.POSITIVE_INFINITY;
            for (int dx = 0; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        tmp.set(x + dx, y + dy, z + dz);
                        this.orientation.transform(tmp);
                        if (tmp.y < minY) minY = tmp.y;
                    }
                }
            }
            return minY;
        }
        return y;
    }

    public Map<BlockPos, Integer> getEffectiveFaceCounts() {
        return this.effectiveFaceCounts;
    }

    public double getBlockWaterDepth(final int blockIdx) {
        return this.blockWaterDepths[blockIdx];
    }

    public double[] getStressDistribution(final double waterDepth) {
        return getStressDistribution(waterDepth, computeCrushDepth());
    }

    public double[] getStressDistribution() {
        solverCore.poissonRatio = cfgDouble(SubmarineConfig.POISSON_RATIO, 0.3);
        return solverCore.getStressDistribution();
    }

    public double getStressRatio(final int i) {
        if (this.blockWaterDepths[i] <= 0) return 0;
        final double vm = computeVonMises(i);
        if (vm <= 1e-30) return 0;
        final double ye = this.solverCore.yieldEffective != null ? this.solverCore.yieldEffective[i] : this.yieldStress[i];
        return vm / ye;
    }

    public double[] getStressDistribution(final double waterDepth, final double[] crush) {
        final double[] dist = new double[this.n];
        for (int i = 0; i < this.n; i++) {
            if (Double.isInfinite(crush[i])) {
                dist[i] = 0;
            } else {
                dist[i] = Math.min(1.0, waterDepth / crush[i]);
            }
        }
        return dist;
    }

    private double computeVonMises(final int blockIdx) {
        return solverCore.computeVonMises(blockIdx);
    }

    public BlockPos getStressCenter() {
        double totalWeight = 0;
        double wx = 0, wy = 0, wz = 0;
        for (int i = 0; i < this.n; i++) {
            final double vm = computeVonMises(i);
            if (vm <= 1e-30) continue;
            final BlockPos p = this.positions[i];
            final double w = vm * vm; 
            wx += w * (p.getX() - this.bounds.minX());
            wy += w * (p.getY() - this.bounds.minY());
            wz += w * (p.getZ() - this.bounds.minZ());
            totalWeight += w;
        }
        if (totalWeight <= 0) return new BlockPos(0, 0, 0);
        return new BlockPos(
            (int) Math.round(wx / totalWeight),
            (int) Math.round(wy / totalWeight),
            (int) Math.round(wz / totalWeight)
        );
    }

    public String debugInfo() {
        double uNorm = 0;
        for (int k = 0; k < 6 * n; k++) uNorm += this.u[k] * this.u[k];
        final StringBuilder sb = new StringBuilder();
        sb.append(String.format("Solver: n=%d, hull=%d, |u|_2=%.2e, solve=%.2fms",
            n, hullBlockCount, Math.sqrt(uNorm), solveTimeNanos / 1e6));
        if (n > 0) {
            double maxStress = 0;
            int maxIdx = 0;
            for (int i = 0; i < n; i++) {
                final double vm = computeVonMises(i);
                if (vm > maxStress) { maxStress = vm; maxIdx = i; }
            }
            sb.append(String.format(", maxStress=%.2e Pa (block %d), E=%.2e Pa, yield=%.2e Pa",
                maxStress, maxIdx, E[maxIdx], yieldStress[maxIdx]));
            sb.append(String.format(", crush=%.1f m", computeCrushDepth()[maxIdx]));
        }
        return sb.toString();
    }
}
