package com.maxenonyme.createsubmarine.submarine.stress;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3dc;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SubLevelStressAnalyzer implements SubLevelObserver {

    public static final Object2ObjectOpenHashMap<ServerLevel, SubLevelStressAnalyzer> INSTANCES = new Object2ObjectOpenHashMap<>();
    private static final Map<UUID, Double> cachedWaterSurfaceY = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastWaterScanTick = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> cachedFluidDensityMultiplier = new ConcurrentHashMap<>();
    private static final double LAVA_DENSITY_MULTIPLIER = 3.1;

    private final ServerLevel level;
    private final Map<UUID, LatticeStressSolver> solvers = new ConcurrentHashMap<>();
    private final Map<UUID, double[]> cachedCrushDepths = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> needsRecompute = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> tickCounter = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> wasInWater = new ConcurrentHashMap<>();
    private final Map<UUID, double[]> previousU = new ConcurrentHashMap<>();

    public long totalSolveTimeNanos = 0;
    public long totalCheckTimeNanos = 0;
    public int solveCount = 0;

    private SubLevelStressAnalyzer(final ServerLevel level) {
        this.level = level;
    }

    public static SubLevelStressAnalyzer getOrCreate(final ServerLevel level) {
        SubLevelStressAnalyzer analyzer = INSTANCES.get(level);
        if (analyzer == null) {
            analyzer = new SubLevelStressAnalyzer(level);
            final SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null) {
                container.addObserver(analyzer);
                if (container instanceof final ServerSubLevelContainer sslc) {
                    for (final ServerSubLevel ssl : sslc.getAllSubLevels()) {
                        analyzer.onSubLevelAdded(ssl);
                    }
                }
            }
            INSTANCES.put(level, analyzer);
        }
        return analyzer;
    }

    @Override
    public void onSubLevelAdded(final SubLevel subLevel) {
        if (subLevel instanceof final ServerSubLevel ssl && !ssl.getLevel().isClientSide) {
            final UUID id = ssl.getUniqueId();
            synchronized (this.solvers) {
                this.needsRecompute.put(id, true);
            }
        }
    }

    @Override
    public void onSubLevelRemoved(final SubLevel subLevel, final SubLevelRemovalReason reason) {
        final UUID id = subLevel.getUniqueId();
        this.solvers.remove(id);
        this.cachedCrushDepths.remove(id);
        this.needsRecompute.remove(id);
        this.tickCounter.remove(id);
        this.wasInWater.remove(id);
        this.previousU.remove(id);
        cachedWaterSurfaceY.remove(id);
        lastWaterScanTick.remove(id);
        cachedFluidDensityMultiplier.remove(id);
    }

    public LatticeStressSolver getSolver(final ServerSubLevel subLevel) {
        return getOrCreateSolver(subLevel);
    }

    public LatticeStressSolver getSolver(final UUID subId) {
        return this.solvers.get(subId);
    }

    public boolean hasSolver(final ServerSubLevel subLevel) {
        final LatticeStressSolver s = this.solvers.get(subLevel.getUniqueId());
        return s != null && s.blockCount() > 0;
    }

    public void markDirty(final ServerSubLevel subLevel) {
        this.needsRecompute.put(subLevel.getUniqueId(), true);
    }

    public void tickRefresh() {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        if (container instanceof final ServerSubLevelContainer sslc) {
            for (final ServerSubLevel ssl : sslc.getAllSubLevels()) {
                final UUID id = ssl.getUniqueId();

                if (!isWithinRenderDistance(ssl)) {
                    this.solvers.remove(id);
                    this.cachedCrushDepths.remove(id);
                    this.needsRecompute.remove(id);
                    this.wasInWater.remove(id);
                    this.previousU.remove(id);
                    cachedWaterSurfaceY.remove(id);
                    lastWaterScanTick.remove(id);
                    cachedFluidDensityMultiplier.remove(id);
                    continue;
                }

                if (!isUnderwater(ssl)) {
                    this.solvers.remove(id);
                    this.cachedCrushDepths.remove(id);
                    this.needsRecompute.remove(id);
                    this.wasInWater.put(id, false);
                    this.previousU.remove(id);
                    continue;
                }

                final int count = this.tickCounter.merge(id, 1, (a, b) -> (a + b) % 1000);
                final boolean alreadyTracked = this.solvers.containsKey(id);

                if (!alreadyTracked) {
                    this.needsRecompute.put(id, true);
                }

                final Boolean prevWasInWater = this.wasInWater.get(id);
                if (prevWasInWater == null || !prevWasInWater) {
                    this.needsRecompute.put(id, true);
                }
                this.wasInWater.put(id, true);

                if (CompartmentTracker.isStructureDirty(id)) {
                    this.needsRecompute.put(id, true);
                }

                if (count % 600 == 0) {
                    this.needsRecompute.put(id, true);
                }

                if (this.needsRecompute.getOrDefault(id, false)) {
                    final LatticeStressSolver oldSolver = this.solvers.get(id);
                    final BoundingBox3ic bb = ssl.getPlot().getBoundingBox();
                    final boolean dirty = CompartmentTracker.isStructureDirty(id);

                    if (oldSolver != null && !dirty && alreadyTracked) {
                        this.needsRecompute.put(id, false);
                        oldSolver.refreshWaterDepths(getWaterSurfaceWorldY(ssl), ssl.logicalPose());
                        oldSolver.setFluidDensityMultiplier(getFluidDensityMultiplier(ssl));
                        oldSolver.resolve();
                        this.cachedCrushDepths.put(id, oldSolver.computeCrushDepth());
                        continue;
                    }

                    if (dirty) CompartmentTracker.clearStructureDirty(id);

                    if (oldSolver != null) {
                        this.previousU.put(id, oldSolver.getU());
                    }
                    final long t0 = System.nanoTime();

                    final double kernelRadius = SubmarineConfig.KERNEL_RADIUS != null
                        ? SubmarineConfig.KERNEL_RADIUS.get() : 1.5;
                    final Map<BlockPos, Set<Integer>> exposedFaces =
                        LatticeStressSolver.buildExposedFaceMap(ssl.getLevel(), bb);
                    final ShapeClassifier.ClassificationResult classification =
                        ShapeClassifier.classify(exposedFaces, kernelRadius);

                    final double[] warmStartU = this.previousU.remove(id);
                    final LatticeStressSolver newSolver = new LatticeStressSolver(
                        ssl.getLevel(), bb, warmStartU, classification, ssl.logicalPose().orientation(),
                        ssl.logicalPose(), getWaterSurfaceWorldY(ssl));
                    newSolver.setFluidDensityMultiplier(getFluidDensityMultiplier(ssl));
                    this.solvers.put(id, newSolver);
                    this.totalSolveTimeNanos += System.nanoTime() - t0;
                    this.solveCount++;
                    this.needsRecompute.put(id, false);
                    this.cachedCrushDepths.put(id, newSolver.computeCrushDepth());
                }

            }
        }

        if (container instanceof ServerSubLevelContainer sslcProf && this.tickCounter.values().stream().anyMatch(c -> c > 0 && c % 200 == 0)) {
            for (final ServerSubLevel sslProf : sslcProf.getAllSubLevels()) {
                final UUID pid = sslProf.getUniqueId();
                final LatticeStressSolver s = this.solvers.get(pid);
                if (s == null) continue;
                final boolean inWater = this.wasInWater.getOrDefault(pid, false);
                final double[] cd = this.cachedCrushDepths.get(pid);
                final String stressRange = cd != null ? String.format("%.1f-%.1f", cd[0], cd[s.blockCount()]) : "N/A";
                }
        }

        if (container instanceof ServerSubLevelContainer) {
            this.tickCounter.keySet().removeIf(id -> container.getSubLevel(id) == null);
            this.solvers.keySet().removeIf(id -> container.getSubLevel(id) == null);
            this.cachedCrushDepths.keySet().removeIf(id -> container.getSubLevel(id) == null);
            this.needsRecompute.keySet().removeIf(id -> container.getSubLevel(id) == null);
            this.wasInWater.keySet().removeIf(id -> container.getSubLevel(id) == null);
            this.previousU.keySet().removeIf(id -> container.getSubLevel(id) == null);
            cachedWaterSurfaceY.keySet().removeIf(id -> container.getSubLevel(id) == null);
            lastWaterScanTick.keySet().removeIf(id -> container.getSubLevel(id) == null);
            cachedFluidDensityMultiplier.keySet().removeIf(id -> container.getSubLevel(id) == null);
        }
    }

    private boolean isWithinRenderDistance(final ServerSubLevel ssl) {
        final Vector3dc worldPos = ssl.logicalPose().position();
        for (final ServerPlayer player : this.level.players()) {
            if (player.distanceToSqr(worldPos.x(), worldPos.y(), worldPos.z()) < 256.0 * 256.0) {
                return true;
            }
        }
        return false;
    }

    public static double getWaterSurfaceWorldY(final ServerSubLevel ssl) {
        return getFluidProperties(ssl)[0];
    }

    public static double getFluidDensityMultiplier(final ServerSubLevel ssl) {
        return getFluidProperties(ssl)[1];
    }

    private static double[] getFluidProperties(final ServerSubLevel ssl) {
        final UUID id = ssl.getUniqueId();
        final long tick = ssl.getLevel() != null ? ssl.getLevel().getGameTime() : 0;
        final Long lastTick = lastWaterScanTick.get(id);
        if (lastTick != null && tick - lastTick < 100) {
            return new double[]{
                cachedWaterSurfaceY.getOrDefault(id, Double.POSITIVE_INFINITY),
                cachedFluidDensityMultiplier.getOrDefault(id, 1.0)
            };
        }

        final Level level = ssl.getLevel();
        if (level == null) return new double[]{Double.POSITIVE_INFINITY, 1.0};

        final Vector3dc pos = ssl.logicalPose().position();
        final int x = (int) Math.round(pos.x());
        final int z = (int) Math.round(pos.z());

        double surfaceY = Double.POSITIVE_INFINITY;
        double densityMultiplier = 1.0;

        int startY = Math.max(level.getMinBuildHeight(), (int) Math.round(pos.y()));

        // scan upward, keep track of the top of the last fluid column;
        // a water column that ends means we found the ocean surface.
        boolean inFluid = false;
        for (int y = startY + 1; y < level.getMaxBuildHeight(); y++) {
            final FluidState fluid = level.getFluidState(new BlockPos(x, y, z));
            if (fluid.is(FluidTags.WATER) || fluid.is(FluidTags.LAVA)) {
                final boolean lava = fluid.is(FluidTags.LAVA);
                surfaceY = y + 1.0;
                densityMultiplier = lava ? LAVA_DENSITY_MULTIPLIER : 1.0;
                inFluid = true;
            } else if (inFluid) {
                // exited the fluid column -> this is the surface
                break;
            } else if (fluid.isEmpty() && y >= level.getSeaLevel()) {
                break;
            }
        }

        cachedWaterSurfaceY.put(id, surfaceY);
        cachedFluidDensityMultiplier.put(id, densityMultiplier);
        lastWaterScanTick.put(id, tick);
        return new double[]{surfaceY, densityMultiplier};
    }

    private boolean isUnderwater(final ServerSubLevel ssl) {
        return Double.isFinite(getWaterSurfaceWorldY(ssl));
    }

    private LatticeStressSolver getOrCreateSolver(final ServerSubLevel subLevel) {
        final UUID id = subLevel.getUniqueId();
        LatticeStressSolver solver = this.solvers.get(id);
        final boolean dirty = this.needsRecompute.getOrDefault(id, false);

        if (solver == null || dirty) {
            if (solver != null) {
                this.previousU.put(id, solver.getU());
            }
            final long t0 = System.nanoTime();
            final BoundingBox3ic bb = subLevel.getPlot().getBoundingBox();

            final double kernelRadius = SubmarineConfig.KERNEL_RADIUS != null
                ? SubmarineConfig.KERNEL_RADIUS.get() : 1.5;
            final Map<BlockPos, Set<Integer>> exposedFaces =
                LatticeStressSolver.buildExposedFaceMap(subLevel.getLevel(), bb);
            final ShapeClassifier.ClassificationResult classification =
                ShapeClassifier.classify(exposedFaces, kernelRadius);

            final double[] warmStartU = this.previousU.remove(id);
            final LatticeStressSolver newSolver = new LatticeStressSolver(
                subLevel.getLevel(), bb, warmStartU, classification, subLevel.logicalPose().orientation(),
                subLevel.logicalPose(), getWaterSurfaceWorldY(subLevel));
            newSolver.setFluidDensityMultiplier(getFluidDensityMultiplier(subLevel));
            this.solvers.put(id, newSolver);
            this.totalSolveTimeNanos += System.nanoTime() - t0;
            this.solveCount++;
            this.needsRecompute.put(id, false);
            this.cachedCrushDepths.put(id, newSolver.computeCrushDepth());
            return newSolver;
        }
        return solver;
    }

    public void setCrushDepths(final ServerSubLevel subLevel, final double[] crushDepths) {
        this.cachedCrushDepths.put(subLevel.getUniqueId(), crushDepths);
    }

    public double[] getCrushDepths(final ServerSubLevel subLevel) {
        final UUID id = subLevel.getUniqueId();
        getOrCreateSolver(subLevel);
        return this.cachedCrushDepths.get(id);
    }

    public String getCrushDepthResult(final ServerSubLevel subLevel) {
        final LatticeStressSolver solver = getOrCreateSolver(subLevel);
        if (solver == null || solver.blockCount() == 0) {
            return "No stress data available for this sub-level";
        }

        final double[] crushDepths = getCrushDepths(subLevel);
        if (crushDepths == null) {
            return "No stress data available for this sub-level";
        }

        final int worstBlock = (int) crushDepths[solver.blockCount()];
        final double globalDepth = worstBlock >= 0 ? crushDepths[worstBlock] : Double.POSITIVE_INFINITY;

        if (worstBlock < 0 || Double.isInfinite(globalDepth)) {
            return String.format("All %d blocks indestructible - no crush depth (solver: %s)",
                solver.blockCount(), solver.debugInfo());
        }

        final String blockName = solver.getLocalPosition(worstBlock).toShortString();
        final boolean isHull = solver.isHullBlock(worstBlock);
        final int exposedFaces = solver.getExposedFaceCount(worstBlock);

        return String.format("Crush depth: %.1f blocks  (total: %d blocks, hull: %d, worst at %s, %s, ext faces: %d, E=%.1e Pa, yield=%.1e Pa | solver: %s)",
                globalDepth, solver.blockCount(), solver.hullBlockCount(),
                blockName, isHull ? "HULL" : "interior", exposedFaces,
                solver.getYoungsModulus(worstBlock), solver.getYieldStress(worstBlock),
                solver.debugInfo());
    }

    public String getStressResult(final ServerSubLevel subLevel, final int waterDepth) {
        if (waterDepth <= 0) return "Sub-level is not underwater - no hydrostatic stress";

        final LatticeStressSolver solver = getOrCreateSolver(subLevel);
        if (solver == null || solver.blockCount() == 0) {
            return "No stress data available for this sub-level";
        }

        final double[] crushDepths = getCrushDepths(subLevel);
        if (crushDepths == null) {
            return "No stress data available for this sub-level";
        }

        final double[] stressDist = solver.getStressDistribution();
        final double[] crushDepthsFull = solver.computeCrushDepth();

        double maxFraction = 0;
        double minFraction = Double.POSITIVE_INFINITY;
        double sumFraction = 0;
        int worstIdx = -1;
        for (int i = 0; i < solver.blockCount(); i++) {
            final double f = stressDist[i];
            if (f > 0) {
                if (f > maxFraction) { maxFraction = f; worstIdx = i; }
                if (f < minFraction) minFraction = f;
                sumFraction += f;
            }
        }

        if (worstIdx < 0) return "No measurable stress";

        final double avgFraction = sumFraction / solver.blockCount();
        final int worstCrushBlock = (int) crushDepthsFull[solver.blockCount()];
        final double worstCrush = worstCrushBlock >= 0 ? crushDepthsFull[worstCrushBlock] : Double.POSITIVE_INFINITY;
        final BlockPos stressCenter = solver.getStressCenter();

        final StringBuilder sb = new StringBuilder();
        sb.append(String.format("Blocks: %d (hull: %d) | Depth: %d bl | Stress/yield: avg=%.1f%%, max=%.1f%% | ",
                solver.blockCount(), solver.hullBlockCount(), waterDepth,
                avgFraction * 100, maxFraction * 100));

        if (worstIdx >= 0) {
            final boolean isHull = solver.isHullBlock(worstIdx);
            final double blockCrush = crushDepthsFull[worstIdx];
            final int depthFromTop = solver.getDepthFromTop(worstIdx);
            sb.append(String.format("Worst: %s [%s] (yield=%.1e Pa, depthFromTop=%d, crush=%.1f bl)",
                    solver.getLocalPosition(worstIdx).toShortString(),
                    isHull ? "HULL" : "interior",
                    solver.getYieldStress(worstIdx),
                    depthFromTop, blockCrush));
            sb.append(String.format(" | Center: %s", stressCenter.toShortString()));
        }

        if (Double.isFinite(worstCrush) && worstCrush < 1.5 * waterDepth) {
            sb.append(" | WARNING: approaching crush depth!");
        }

        sb.append(String.format(" | %s", solver.debugInfo()));

        return sb.toString();
    }

    public boolean checkAndBreak(final ServerSubLevel subLevel, final int waterDepth, final Level plotLevel) {
        final long t0 = System.nanoTime();
        final UUID id = subLevel.getUniqueId();
        final LatticeStressSolver solver = getOrCreateSolver(subLevel);
        if (solver == null || solver.blockCount() <= 1) {
            this.totalCheckTimeNanos += System.nanoTime() - t0;
            return false;
        }

        final double[] crushDepths = getCrushDepths(subLevel);
        if (crushDepths == null) {
            this.totalCheckTimeNanos += System.nanoTime() - t0;
            return false;
        }

        boolean brokeAny = false;
        for (int i = 0; i < solver.blockCount(); i++) {
            final double depth = crushDepths[i];
            if (Double.isInfinite(depth)) continue;
            final double blockDepth = solver.getBlockWaterDepth(i);
            if (blockDepth < depth * 0.5) continue;

            final BlockPos pos = solver.getPosition(i);
            final BlockState state = plotLevel.getBlockState(pos);
            if (state.isAir()) continue;

            if (isFragile(state) || blockDepth >= depth * 0.8) {
                brokeAny |= breakBlock(plotLevel, pos, id, solver);
            }
        }

        this.totalCheckTimeNanos += System.nanoTime() - t0;
        return brokeAny;
    }

    private boolean breakBlock(final Level plotLevel, final BlockPos pos, final UUID id, final LatticeStressSolver solver) {
        final BlockState state = plotLevel.getBlockState(pos);
        if (state.isAir()) return false;

        final ResourceLocation rl = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (rl.equals(ResourceLocation.withDefaultNamespace("iron_block"))) {
            plotLevel.setBlock(pos, Blocks.RAW_IRON_BLOCK.defaultBlockState(), 3);
        } else if (rl.equals(ResourceLocation.withDefaultNamespace("gold_block"))) {
            plotLevel.setBlock(pos, Blocks.RAW_GOLD_BLOCK.defaultBlockState(), 3);
        } else {
            plotLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }

        this.needsRecompute.put(id, true);
        return true;
    }

    private boolean isFragile(final BlockState state) {
        return state.is(BlockTags.DIRT) ||
               state.is(BlockTags.SAND) ||
               state.is(BlockTags.WOOL) ||
               state.is(net.minecraft.world.level.block.Blocks.GRAVEL) ||
               state.is(BlockTags.LEAVES) ||
               state.is(net.minecraft.world.level.block.Blocks.GLASS) ||
               state.is(net.minecraft.world.level.block.Blocks.TINTED_GLASS) ||
               state.is(BlockTags.ICE) ||
               state.is(net.minecraft.world.level.block.Blocks.SPONGE) ||
               state.is(net.minecraft.world.level.block.Blocks.WET_SPONGE) ||
               state.is(BlockTags.FLOWERS) ||
               state.is(BlockTags.CROPS);
    }

    public String getProfilerResult() {
        return String.format("Profiler: %d solves, avg solve=%.2fms, total solve=%.2fms, total check=%.2fms",
            solveCount,
            solveCount > 0 ? totalSolveTimeNanos / 1e6 / solveCount : 0,
            totalSolveTimeNanos / 1e6,
            totalCheckTimeNanos / 1e6);
    }

    public static void clearForLevel(final ServerLevel level) {
        final SubLevelStressAnalyzer analyzer = INSTANCES.remove(level);
        if (analyzer != null) {
            analyzer.solvers.clear();
            analyzer.cachedCrushDepths.clear();
            analyzer.needsRecompute.clear();
            analyzer.tickCounter.clear();
            analyzer.wasInWater.clear();
            analyzer.previousU.clear();
            cachedWaterSurfaceY.clear();
            lastWaterScanTick.clear();
            cachedFluidDensityMultiplier.clear();
        }
    }

    public static void onGlobalServerTick(final ServerTickEvent.Post event) {
        for (final ServerLevel level : event.getServer().getAllLevels()) {
            final SubLevelStressAnalyzer analyzer = INSTANCES.get(level);
            if (analyzer == null) continue;
            analyzer.tickRefresh();
        }
    }
}
