package com.maxenonyme.createsubmarine.submarine.compartment;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.maxenonyme.createsubmarine.submarine.client.renderer.SubmarineWaterCullBuffer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public class CompartmentTracker {
    private static final Map<UUID, Set<BlockPos>> SEALED_UNION = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<BlockPos>> VISUAL_UNION = new ConcurrentHashMap<>();
    private static final Map<UUID, List<CompartmentDetector.Component>> COMPARTMENTS = new ConcurrentHashMap<>();
    private static final Map<UUID, SubLevelAccess> SUBS = new ConcurrentHashMap<>();
    private static final Map<UUID, AABB> WORLD_AABB = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_UPDATE_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<BlockPos>> COMPROMISED_ANCHORS = new ConcurrentHashMap<>();
    private static final Map<UUID, Vector3d> CACHED_DIMENSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, double[]> LAST_POSE = new ConcurrentHashMap<>();
    private static final Map<UUID, CompartmentDetector.IncrementalScanState> ACTIVE_SCANS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<BlockPos>> SOLID_BLOCKS = new ConcurrentHashMap<>();
    private static final Set<UUID> STRUCTURE_DIRTY = ConcurrentHashMap.newKeySet();
    private static volatile AABB globalBounds = null;

    private record SealedEntry(UUID id, SubLevelAccess access,
            ResourceKey<Level> dimension,
            LongOpenHashSet cells) {
    }

    private static volatile SealedEntry[] sealedSnapshot = new SealedEntry[0];
    private static final ThreadLocal<Vector3d> LOCAL_POS = ThreadLocal.withInitial(Vector3d::new);

    private static void rebuildSealedSnapshot() {
        ArrayList<SealedEntry> list = new ArrayList<>();
        for (Map.Entry<UUID, SubLevelAccess> e : SUBS.entrySet()) {
            Set<BlockPos> sealed = SEALED_UNION.get(e.getKey());
            if (sealed == null || sealed.isEmpty())
                continue;
            LongOpenHashSet cells = new LongOpenHashSet(
                    sealed.size());
            for (BlockPos p : sealed)
                cells.add(p.asLong());
            ResourceKey<Level> dim = null;
            if (e.getValue() instanceof SubLevel sl && sl.getLevel() != null) {
                dim = sl.getLevel().dimension();
            }
            list.add(new SealedEntry(e.getKey(), e.getValue(), dim, cells));
        }
        sealedSnapshot = list.toArray(new SealedEntry[0]);
    }

    public static void update(UUID id, SubLevelAccess sub, CompartmentDetector.Result result, long gameTick) {
        COMPARTMENTS.put(id, result.components());
        if (result.solidBlocks() != null) {
            SOLID_BLOCKS.put(id, result.solidBlocks());
        }
        SUBS.put(id, sub);
        LAST_UPDATE_TICK.put(id, gameTick);
        rebuildUnionsAndPush(id, result.components());

        if (sub instanceof SubLevel sl && sl.getPlot() != null) {
            BoundingBox3ic bounds = sl.getPlot().getBoundingBox();
            double sx = bounds.maxX() - bounds.minX() + 1;
            double sy = bounds.maxY() - bounds.minY() + 1;
            double sz = bounds.maxZ() - bounds.minZ() + 1;
            double r = Math.max(sx, Math.max(sy, sz)) * 0.75;
            Vector3dc p = sub.logicalPose().position();
            WORLD_AABB.put(id, new AABB(p.x() - r, p.y() - r, p.z() - r, p.x() + r, p.y() + r, p.z() + r));
        }
        rebuildGlobalBounds();
    }

    private static Set<BlockPos> rebuildUnionsAndPush(UUID id, List<CompartmentDetector.Component> comps) {
        Set<BlockPos> flooded = FLOODED_ANCHORS.getOrDefault(id, Set.of());
        Set<BlockPos> compromised = COMPROMISED_ANCHORS.getOrDefault(id, Set.of());
        Set<BlockPos> sealed = new HashSet<>();
        Set<BlockPos> visual = new HashSet<>();

        boolean anySealed = false;
        for (CompartmentDetector.Component c : comps) {
            if (!c.sealed() || (c.anchor() != null
                    && (compromised.contains(c.anchor()) || flooded.contains(c.anchor()))))
                continue;
            anySealed = true;
            sealed.addAll(c.internal());
            visual.addAll(c.internal());
            visual.addAll(c.hull());
        }

        if (anySealed) {
            Set<BlockPos> solid = SOLID_BLOCKS.get(id);
            if (solid != null) {
                visual.addAll(solid);
            }
        }
        SEALED_UNION.put(id, Collections.unmodifiableSet(sealed));
        VISUAL_UNION.put(id, Collections.unmodifiableSet(visual));
        rebuildSealedSnapshot();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            SubmarineWaterCullBuffer
                    .updateSubmarineOcclusion(id, visual);
        }
        return visual;
    }

    public static void remove(UUID id) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            SubmarineWaterCullBuffer
                    .updateSubmarineOcclusion(id, null);
            SubmarineWaterCullBuffer.clearSodiumPoseCache(id);
        }
        SEALED_UNION.remove(id);
        VISUAL_UNION.remove(id);
        OCCLUSION_UNION.remove(id);
        COMPARTMENTS.remove(id);
        FLOODED_ANCHORS.remove(id);
        SUBS.remove(id);
        WORLD_AABB.remove(id);
        LAST_UPDATE_TICK.remove(id);
        COMPROMISED_ANCHORS.remove(id);
        CACHED_DIMENSIONS.remove(id);
        LAST_POSE.remove(id);
        ACTIVE_SCANS.remove(id);
        SOLID_BLOCKS.remove(id);
        STRUCTURE_DIRTY.remove(id);
        rebuildSealedSnapshot();
        rebuildGlobalBounds();
    }

    public static void clearAll() {
        SEALED_UNION.clear();
        VISUAL_UNION.clear();
        OCCLUSION_UNION.clear();
        COMPARTMENTS.clear();
        FLOODED_ANCHORS.clear();
        SUBS.clear();
        WORLD_AABB.clear();
        LAST_UPDATE_TICK.clear();
        COMPROMISED_ANCHORS.clear();
        CACHED_DIMENSIONS.clear();
        LAST_POSE.clear();
        ACTIVE_SCANS.clear();
        SOLID_BLOCKS.clear();
        STRUCTURE_DIRTY.clear();
        sealedSnapshot = new SealedEntry[0];
        globalBounds = null;
    }

    public static Map<UUID, SubLevelAccess> getSubsSnapshot() {
        return new HashMap<>(SUBS);
    }

    public static SubLevelAccess getSub(UUID id) {
        return SUBS.get(id);
    }

    public static AABB getWorldAABB(UUID id) {
        return WORLD_AABB.get(id);
    }

    public static void setWorldAABB(UUID id, AABB aabb) {
        WORLD_AABB.put(id, aabb);
        rebuildGlobalBounds();
    }

    public static Vector3d getCachedDimensions(UUID id) {
        return CACHED_DIMENSIONS.get(id);
    }

    public static long lastUpdateTick(UUID id) {
        return LAST_UPDATE_TICK.getOrDefault(id, 0L);
    }

    public static void updateAABB(UUID id, Vector3dc position, Vector3dc dimensions) {
        double r = Math.max(dimensions.x(), Math.max(dimensions.y(), dimensions.z())) * 0.75;
        WORLD_AABB.put(id, new AABB(position.x() - r, position.y() - r, position.z() - r,
                position.x() + r, position.y() + r, position.z() + r));
        rebuildGlobalBounds();
    }

    public static Vector3d getOrComputeDimensions(UUID id, BoundingBox3ic bounds) {
        int sx = bounds.maxX() - bounds.minX() + 1;
        int sy = bounds.maxY() - bounds.minY() + 1;
        int sz = bounds.maxZ() - bounds.minZ() + 1;
        return CACHED_DIMENSIONS.compute(id, (k, cached) -> {
            if (cached != null && cached.x == sx && cached.y == sy && cached.z == sz)
                return cached;
            return new Vector3d(sx, sy, sz);
        });
    }

    public static boolean poseMovedEnough(UUID id, Pose3dc pose, double posEps, double rotEps) {
        double[] last = LAST_POSE.get(id);
        if (last == null)
            return true;
        Vector3dc p = pose.position();
        Quaterniondc q = pose.orientation();
        double dx = p.x() - last[0], dy = p.y() - last[1], dz = p.z() - last[2];
        if (dx * dx + dy * dy + dz * dz > posEps * posEps)
            return true;
        double dot = q.x() * last[3] + q.y() * last[4] + q.z() * last[5] + q.w() * last[6];
        return Math.abs(dot) < 1.0 - rotEps;
    }

    public static void recordPose(UUID id, Pose3dc pose) {
        Vector3dc p = pose.position();
        Quaterniondc q = pose.orientation();
        LAST_POSE.put(id, new double[] { p.x(), p.y(), p.z(), q.x(), q.y(), q.z(), q.w() });
    }

    public static boolean isScanActive(UUID id) {
        return ACTIVE_SCANS.containsKey(id);
    }

    public static boolean isStructureDirty(UUID id) {
        return STRUCTURE_DIRTY.contains(id);
    }

    private static final Map<UUID, Integer> STRUCTURE_VERSION = new ConcurrentHashMap<>();

    public static int structureVersion(UUID id) {
        return STRUCTURE_VERSION.getOrDefault(id, 0);
    }

    public static void onPlotBlockChanged(Level level, BlockPos pos) {
        for (Map.Entry<UUID, SubLevelAccess> e : SUBS.entrySet()) {
            if (!(e.getValue() instanceof SubLevel sl))
                continue;
            if (sl.getLevel() != null && sl.getLevel().dimension() != level.dimension())
                continue;
            LevelPlot plot = sl.getPlot();
            if (plot == null)
                continue;
            BoundingBox3ic b = plot.getBoundingBox();
            if (pos.getX() >= b.minX() && pos.getX() <= b.maxX()
                    && pos.getY() >= b.minY() && pos.getY() <= b.maxY()
                    && pos.getZ() >= b.minZ() && pos.getZ() <= b.maxZ()) {
                STRUCTURE_DIRTY.add(e.getKey());
                STRUCTURE_VERSION.merge(e.getKey(), 1, Integer::sum);
            }
        }
    }

    private static final Map<UUID, Set<BlockPos>> FLOODED_ANCHORS = new ConcurrentHashMap<>();

    public static Set<BlockPos> floodedAnchors(UUID id) {
        return FLOODED_ANCHORS.getOrDefault(id, Set.of());
    }

    public static void setFloodedAnchors(UUID id, Set<BlockPos> anchors) {
        Set<BlockPos> previous = FLOODED_ANCHORS.get(id);
        if (anchors == null || anchors.isEmpty()) {
            if (previous == null)
                return;
            FLOODED_ANCHORS.remove(id);
        } else {
            if (anchors.equals(previous))
                return;
            FLOODED_ANCHORS.put(id, Set.copyOf(anchors));
        }
        List<CompartmentDetector.Component> comps = COMPARTMENTS.get(id);
        if (comps != null)
            rebuildUnionsAndPush(id, comps);
    }

    private static final Map<UUID, Long> SUBMARINE_CLAIM = new ConcurrentHashMap<>();
    private static final long CLAIM_TTL = 100L;

    public static void claimSubmarine(UUID id, long gameTick) {
        SUBMARINE_CLAIM.put(id, gameTick);
    }

    public static boolean isSubmarineManaged(UUID id, long gameTick) {
        Long claimed = SUBMARINE_CLAIM.get(id);
        return claimed != null && gameTick - claimed < CLAIM_TTL;
    }

    private static final int MISSING_CHUNK_RETRIES = 3;
    private static final Map<UUID, Integer> MISSING_CHUNK_SCANS = new ConcurrentHashMap<>();

    public static void beginScanIfIdle(UUID id, SubLevelAccess sub) {
        ACTIVE_SCANS.computeIfAbsent(id, k -> {
            CompartmentDetector.IncrementalScanState st = CompartmentDetector.beginScan(sub);
            if (st != null)
                STRUCTURE_DIRTY.remove(id);
            return st;
        });
    }

    public static boolean stepScan(UUID id, SubLevelAccess sub, int budget, long gameTick) {
        SUBMARINE_CLAIM.put(id, gameTick);
        CompartmentDetector.IncrementalScanState st = ACTIVE_SCANS.get(id);
        if (st == null)
            return false;
        try {
            boolean done = CompartmentDetector.stepScan(st, budget);
            if (done) {
                int incomplete = st.chunksMissing ? MISSING_CHUNK_SCANS.merge(id, 1, Integer::sum) : 0;
                if (st.chunksMissing && incomplete < MISSING_CHUNK_RETRIES) {
                    LAST_UPDATE_TICK.put(id, gameTick);
                    STRUCTURE_DIRTY.add(id);
                } else {
                    MISSING_CHUNK_SCANS.remove(id);
                    CompartmentDetector.Result r = CompartmentDetector.finishScan(st);
                    update(id, sub, r, gameTick);
                }
                ACTIVE_SCANS.remove(id);
                return true;
            }
            return false;
        } catch (Throwable t) {
            ACTIVE_SCANS.remove(id);
            LAST_UPDATE_TICK.put(id, gameTick);
            return false;
        }
    }

    public static void abortScan(UUID id) {
        ACTIVE_SCANS.remove(id);
    }

    private static final Map<UUID, Set<BlockPos>> OCCLUSION_UNION = new ConcurrentHashMap<>();

    public static void setOcclusionBlocks(UUID id, Collection<BlockPos> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            OCCLUSION_UNION.remove(id);
        } else {
            OCCLUSION_UNION.put(id, Set.copyOf(blocks));
        }
    }

    public static boolean isOccluded(Level level, BlockPos worldPos) {
        return findContainingSub(level, worldPos, OCCLUSION_UNION) != null;
    }

    public static boolean isInSealed(Level level, BlockPos worldPos) {
        return findSealedSublevel(level, worldPos) != null;
    }

    @Nullable
    public static UUID findSealedSublevel(Level level, BlockPos worldPos) {
        AABB gb = globalBounds;
        if (gb == null)
            return null;
        double cx = worldPos.getX() + 0.5, cy = worldPos.getY() + 0.5, cz = worldPos.getZ() + 0.5;
        if (!gb.contains(cx, cy, cz))
            return null;

        SealedEntry[] snap = sealedSnapshot;
        for (SealedEntry e : snap) {
            if (e.dimension != null && e.dimension != level.dimension())
                continue;
            AABB aabb = WORLD_AABB.get(e.id);
            if (aabb == null || !aabb.contains(cx, cy, cz))
                continue;

            Vector3d local = LOCAL_POS.get();
            local.set(cx, cy, cz);
            e.access.logicalPose().transformPositionInverse(local);
            long key = BlockPos.asLong(
                    Mth.floor(local.x),
                    Mth.floor(local.y),
                    Mth.floor(local.z));
            if (e.cells.contains(key))
                return e.id;
        }
        return null;
    }

    public static boolean isOccludedExact(Level level, Vec3 exactPos) {
        return findContainingSubExact(level, exactPos, VISUAL_UNION) != null;
    }

    public static boolean isInSealedExact(Level level, Vec3 exactPos) {
        return findSealedSublevelExact(level, exactPos) != null;
    }

    @Nullable
    public static UUID findSealedSublevelExact(Level level, Vec3 exactPos) {
        AABB gb = globalBounds;
        if (gb == null || !gb.contains(exactPos.x, exactPos.y, exactPos.z))
            return null;

        for (Map.Entry<UUID, SubLevelAccess> e : SUBS.entrySet()) {
            UUID id = e.getKey();
            SubLevelAccess access = e.getValue();
            if (access instanceof SubLevel sl
                    && sl.getLevel() != null && sl.getLevel().dimension() != level.dimension())
                continue;
            AABB aabb = WORLD_AABB.get(id);
            if (aabb == null || !aabb.contains(exactPos.x, exactPos.y, exactPos.z))
                continue;
            Set<BlockPos> blocks = SEALED_UNION.get(id);
            if (blocks == null || blocks.isEmpty())
                continue;

            Pose3dc pose = (level.isClientSide && access instanceof ClientSubLevel csl)
                    ? csl.renderPose()
                    : access.logicalPose();
            Vector3d local = new Vector3d(exactPos.x, exactPos.y, exactPos.z);
            pose.transformPositionInverse(local);
            BlockPos localPos = BlockPos.containing(local.x, local.y, local.z);
            if (blocks.contains(localPos))
                return plotFluidAt(access, localPos) ? null : id;
        }
        return null;
    }

    private static boolean plotFluidAt(SubLevelAccess access, BlockPos localPos) {
        if (!(access instanceof SubLevel sl))
            return false;
        LevelPlot plot = sl.getPlot();
        if (plot == null)
            return false;
        LevelChunk chunk = plot.getChunk(
                plot.toLocal(new ChunkPos(localPos.getX() >> 4, localPos.getZ() >> 4)));
        if (chunk == null)
            return false;
        return !chunk.getBlockState(localPos).getFluidState().isEmpty();
    }

    @Nullable
    private static UUID findContainingSubExact(Level level, Vec3 exactPos,
            Map<UUID, Set<BlockPos>> blockSetPerSub) {
        AABB gb = globalBounds;
        if (gb == null)
            return null;
        if (!gb.contains(exactPos.x, exactPos.y, exactPos.z))
            return null;

        for (Map.Entry<UUID, SubLevelAccess> e : SUBS.entrySet()) {
            UUID id = e.getKey();
            SubLevelAccess access = e.getValue();
            if (access instanceof SubLevel sl
                    && sl.getLevel() != null && sl.getLevel().dimension() != level.dimension())
                continue;
            AABB aabb = WORLD_AABB.get(id);
            if (aabb == null || !aabb.contains(exactPos.x, exactPos.y, exactPos.z))
                continue;
            Set<BlockPos> blocks = blockSetPerSub.get(id);
            if (blocks == null || blocks.isEmpty())
                continue;

            Vector3d local = new Vector3d(exactPos.x, exactPos.y, exactPos.z);
            access.logicalPose().transformPositionInverse(local);
            if (blocks.contains(BlockPos.containing(local.x, local.y, local.z)))
                return id;
        }
        return null;
    }

    @Nullable
    private static UUID findContainingSub(Level level, BlockPos worldPos, Map<UUID, Set<BlockPos>> blockSetPerSub) {
        AABB gb = globalBounds;
        if (gb == null)
            return null;
        double cx = worldPos.getX() + 0.5, cy = worldPos.getY() + 0.5, cz = worldPos.getZ() + 0.5;
        if (!gb.contains(cx, cy, cz))
            return null;

        for (Map.Entry<UUID, SubLevelAccess> e : SUBS.entrySet()) {
            UUID id = e.getKey();
            SubLevelAccess access = e.getValue();
            if (access instanceof SubLevel sl
                    && sl.getLevel() != null && sl.getLevel().dimension() != level.dimension())
                continue;
            AABB aabb = WORLD_AABB.get(id);
            if (aabb == null || !aabb.contains(cx, cy, cz))
                continue;
            Set<BlockPos> blocks = blockSetPerSub.get(id);
            if (blocks == null || blocks.isEmpty())
                continue;

            Vector3d local = new Vector3d(cx, cy, cz);
            access.logicalPose().transformPositionInverse(local);
            if (blocks.contains(BlockPos.containing(local.x, local.y, local.z)))
                return id;
        }
        return null;
    }

    @Nullable
    public static BlockState getLiedBlockState(Level level, BlockPos worldPos) {
        UUID id = findSealedSublevel(level, worldPos);
        if (id == null)
            return null;
        return Blocks.AIR.defaultBlockState();
    }

    @Nullable
    public static FluidState getLiedFluidState(Level level, BlockPos worldPos) {
        return null;
    }

    public static FluidState realFluidState(Level level, BlockPos pos) {
        int y = pos.getY();
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight())
            return Fluids.EMPTY.defaultFluidState();
        ChunkAccess chunk = level.getChunk(
                pos.getX() >> 4, pos.getZ() >> 4,
                ChunkStatus.FULL, false);
        if (chunk == null)
            return Fluids.EMPTY.defaultFluidState();
        return realFluidState(chunk, pos);
    }

    public static FluidState realFluidState(ChunkAccess chunk, BlockPos pos) {
        int y = pos.getY();
        int idx = chunk.getSectionIndex(y);
        if (idx < 0 || idx >= chunk.getSections().length)
            return Fluids.EMPTY.defaultFluidState();
        LevelChunkSection section = chunk.getSection(idx);
        if (section == null || section.hasOnlyAir())
            return Fluids.EMPTY.defaultFluidState();
        return section.getBlockState(pos.getX() & 15, y & 15, pos.getZ() & 15).getFluidState();
    }

    @Nullable
    public static CompartmentDetector.Component findCompartmentAdjacent(UUID id, BlockPos plotPos) {
        if (id == null || plotPos == null)
            return null;
        List<CompartmentDetector.Component> comps = COMPARTMENTS.get(id);
        if (comps == null)
            return null;
        Set<BlockPos> compromised = COMPROMISED_ANCHORS.getOrDefault(id, Set.of());
        for (CompartmentDetector.Component c : comps) {
            if (!c.sealed() || (c.anchor() != null && compromised.contains(c.anchor())))
                continue;
            for (Direction dir : Direction.values()) {
                if (c.internal().contains(plotPos.relative(dir)))
                    return c;
            }
        }
        return null;
    }

    public static List<CompartmentDetector.Component> getCompartments(UUID id) {
        return COMPARTMENTS.getOrDefault(id, List.of());
    }

    public static boolean isWithinShip(UUID id, BlockPos plotPos) {
        Set<BlockPos> union = VISUAL_UNION.get(id);
        return union != null && union.contains(plotPos);
    }

    public static boolean isCompromised(UUID id, BlockPos anchor) {
        if (id == null || anchor == null)
            return false;
        return COMPROMISED_ANCHORS.getOrDefault(id, Set.of()).contains(anchor);
    }

    public static void markCompromised(UUID id, BlockPos anchor) {
        COMPROMISED_ANCHORS.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(anchor);
        List<CompartmentDetector.Component> comps = COMPARTMENTS.get(id);
        if (comps == null)
            return;
        rebuildUnionsAndPush(id, comps);
    }

    public static boolean hasAnySealed(UUID id) {
        Set<BlockPos> sealed = SEALED_UNION.get(id);
        return sealed != null && !sealed.isEmpty();
    }

    private static void rebuildGlobalBounds() {
        if (WORLD_AABB.isEmpty()) {
            globalBounds = null;
            return;
        }
        AABB b = null;
        for (AABB aabb : WORLD_AABB.values()) {
            b = (b == null) ? aabb : b.minmax(aabb);
        }
        globalBounds = b;
    }
}
