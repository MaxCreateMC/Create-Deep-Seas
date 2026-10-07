package com.maxenonyme.createsubmarine.submarine.client.renderer;

import dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionRegion;
import dev.ryanhcode.sable.util.BoundedBitVolume3i;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.fml.ModList;
import java.util.function.Predicate;

public final class SubmarineWaterCullBuffer {
    private static final double POSE_MOVE_THRESHOLD_SQ = 0.01;
    private static final double PIXEL_PERFECT_MOVE_THRESHOLD_SQ = 0.25;
    private static final double DEFAULT_RADIUS = 16.0;
    private static final int WET_SCAN_INTERVAL = 5;
    private static final boolean SODIUM = ModList.get().isLoaded("sodium");
    private static final Predicate<BlockState> HIDDEN_IN_HULL = state -> !state.isAir() && !state.is(Blocks.WATER);
    private static final Predicate<BlockState> OTHER_FLUID = state -> !state.getFluidState().isEmpty()
            && !state.getFluidState().is(FluidTags.WATER);

    private static final Map<UUID, WaterOcclusionRegion> regions = new HashMap<>();
    private static final Map<UUID, Vector3d> lastClientPose = new ConcurrentHashMap<>();
    private static final Map<UUID, Collection<BlockPos>> lastBlocks = new HashMap<>();
    private static final Map<UUID, Boolean> lastWet = new HashMap<>();
    private static final Map<UUID, Integer> lastEdits = new HashMap<>();
    private static long lastWetScanTick = -1;
    private static boolean renderingSubmarineFluid = false;
    private static volatile boolean remeshAll = false;

    private SubmarineWaterCullBuffer() {
    }

    public static boolean isRenderingSubmarineFluid() {
        return renderingSubmarineFluid;
    }

    public static void beginSubmarineFluidRender() {
        renderingSubmarineFluid = true;
    }

    public static void endSubmarineFluidRender() {
        renderingSubmarineFluid = false;
    }

    public static void clearSodiumPoseCache(UUID id) {
        lastClientPose.remove(id);
    }

    public static Map<UUID, Collection<BlockPos>> regionBlocks() {
        return lastBlocks;
    }

    public static void invalidateAllPoseCaches() {
        lastClientPose.clear();
        remeshAll = true;
    }

    public static void syncSubmarinePoses() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;
        SubLevelContainer container = SubLevelContainer.getContainer(mc.level);
        if (container == null)
            return;

        if (SodiumWaterOcclusionBridge.PIXEL_PERFECT_ACTIVE && mc.levelRenderer != null)
            repairFallbackHoles(mc);
        double threshold = SodiumWaterOcclusionBridge.PIXEL_PERFECT_ACTIVE ? PIXEL_PERFECT_MOVE_THRESHOLD_SQ
                : POSE_MOVE_THRESHOLD_SQ;
        Set<UUID> tracked = new HashSet<>(regions.keySet());
        tracked.addAll(lastBlocks.keySet());
        for (UUID id : tracked) {
            SubLevel sub = container.getSubLevel(id);
            if (sub == null) {
                forgetVanished(mc, id);
                continue;
            }
            Vector3dc p = sub.logicalPose().position();
            Vector3d last = lastClientPose.get(id);
            if (last != null && last.distanceSquared(p) < threshold)
                continue;

            double r = computeRadius(id, sub);
            AABB newAABB = new AABB(p.x() - r, p.y() - r, p.z() - r, p.x() + r, p.y() + r, p.z() + r);
            AABB oldAABB = CompartmentTracker.getWorldAABB(id);
            CompartmentTracker.setWorldAABB(id, newAABB);

            if (mc.levelRenderer != null) {
                if (oldAABB != null)
                    invalidateSections(mc, oldAABB);
                invalidateSections(mc, newAABB);
            }

            if (last == null)
                lastClientPose.put(id, new Vector3d(p));
            else
                last.set(p);
        }

        remeshAll = false;

        for (Map.Entry<UUID, Collection<BlockPos>> e : new HashMap<>(lastBlocks).entrySet()) {
            int edits = CompartmentTracker.clientEdits(e.getKey());
            Integer seen = lastEdits.get(e.getKey());
            if (seen != null && seen != edits) {
                lastEdits.put(e.getKey(), edits);
                updateSubmarineOcclusion(e.getKey(), e.getValue());
            }
        }

        long now = mc.level.getGameTime();
        if (now - lastWetScanTick >= WET_SCAN_INTERVAL) {
            lastWetScanTick = now;
            for (Map.Entry<UUID, Collection<BlockPos>> e : new HashMap<>(lastBlocks).entrySet()) {
                Boolean prev = lastWet.get(e.getKey());
                boolean wet = fluidNearby(mc.level, e.getKey());
                if (prev == null || prev != wet) {
                    updateSubmarineOcclusion(e.getKey(), e.getValue());
                }
            }
        }
    }

    private static void forgetVanished(Minecraft mc, UUID id) {
        AABB stale = CompartmentTracker.getWorldAABB(id);
        WaterOcclusionContainer<?> occlusion = WaterOcclusionContainer.getContainer(mc.level);
        WaterOcclusionRegion region = regions.remove(id);
        if (region != null && occlusion != null)
            occlusion.removeRegion(region);
        lastBlocks.remove(id);
        lastWet.remove(id);
        lastEdits.remove(id);
        lastClientPose.remove(id);
        CompartmentTracker.setOcclusionBlocks(id, null);
        if (mc.getSingleplayerServer() == null)
            CompartmentTracker.remove(id);
        if (stale != null && mc.levelRenderer != null)
            invalidateSections(mc, stale);
    }

    private static boolean fluidNearby(Level level, UUID id) {
        AABB aabb = CompartmentTracker.getWorldAABB(id);
        if (aabb == null) {
            SubLevelContainer subContainer = SubLevelContainer.getContainer(level);
            SubLevel sub = subContainer == null ? null : subContainer.getSubLevel(id);
            if (sub == null)
                return true;
            Vector3dc p = sub.logicalPose().position();
            double r = computeRadius(id, sub);
            aabb = new AABB(p.x() - r, p.y() - r, p.z() - r, p.x() + r, p.y() + r, p.z() + r);
        }
        if (aabb.minY - 2.0 <= level.getSeaLevel())
            return true;

        int minX = (int) Math.floor(aabb.minX) - 2, maxX = (int) Math.ceil(aabb.maxX) + 2;
        int minY = Math.max(level.getMinBuildHeight(), (int) Math.floor(aabb.minY) - 2);
        int maxY = Math.min(level.getMaxBuildHeight(), (int) Math.ceil(aabb.maxY) + 2);
        int minZ = (int) Math.floor(aabb.minZ) - 2, maxZ = (int) Math.ceil(aabb.maxZ) + 2;
        int sxz = Math.max(4, Math.max(maxX - minX, maxZ - minZ) / 24);
        int sy = Math.max(2, (maxY - minY) / 24);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x += sxz) {
            for (int y = minY; y <= maxY; y += sy) {
                for (int z = minZ; z <= maxZ; z += sxz) {
                    m.set(x, y, z);
                    if (!CompartmentTracker.realFluidState(level, m).isEmpty())
                        return true;
                }
            }
        }
        return false;
    }

    private static double computeRadius(UUID id, SubLevel sub) {
        Vector3d dims = CompartmentTracker.getCachedDimensions(id);
        if (dims != null)
            return Math.max(dims.x, Math.max(dims.y, dims.z)) * 0.75;
        BoundingBox3dc bb = sub.boundingBox();
        if (bb == null)
            return DEFAULT_RADIUS;
        return Math.max(bb.maxX() - bb.minX(), Math.max(bb.maxY() - bb.minY(), bb.maxZ() - bb.minZ())) * 0.75;
    }

    private static void repairFallbackHoles(Minecraft mc) {
        for (Long section : List.copyOf(SodiumWaterOcclusionBridge.FALLBACK_HOLES)) {
            SodiumWaterOcclusionBridge.FALLBACK_HOLES.remove(section);
            mc.levelRenderer.setSectionDirty(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
        }
    }

    private static void invalidateSections(Minecraft mc, AABB aabb) {
        if (mc.level == null)
            return;
        boolean pixelPerfect = SODIUM ? SodiumWaterOcclusionBridge.PIXEL_PERFECT_ACTIVE : WaterOcclusionRenderer.isEnabled();
        if (pixelPerfect && !remeshAll)
            return;
        boolean fluids = SODIUM && !pixelPerfect;
        Predicate<BlockState> hidden = SODIUM ? HIDDEN_IN_HULL : OTHER_FLUID;
        int minSx = ((int) Math.floor(aabb.minX)) >> 4;
        int maxSx = ((int) Math.ceil(aabb.maxX)) >> 4;
        int minSy = ((int) Math.floor(aabb.minY)) >> 4;
        int maxSy = ((int) Math.ceil(aabb.maxY)) >> 4;
        int minSz = ((int) Math.floor(aabb.minZ)) >> 4;
        int maxSz = ((int) Math.ceil(aabb.maxZ)) >> 4;
        for (int sx = minSx; sx <= maxSx; sx++) {
            for (int sz = minSz; sz <= maxSz; sz++) {
                ChunkAccess chunk = mc.level.getChunk(sx, sz, ChunkStatus.FULL, false);
                if (chunk == null)
                    continue;
                for (int sy = minSy; sy <= maxSy; sy++) {
                    int index = chunk.getSectionIndexFromSectionY(sy);
                    if (index < 0 || index >= chunk.getSectionsCount())
                        continue;
                    LevelChunkSection section = chunk.getSection(index);
                    if (section.hasOnlyAir() || (!fluids && !section.maybeHas(hidden)))
                        continue;
                    if (fluids)
                        SodiumWaterOcclusionBridge.FALLBACK_HOLES.remove(SectionPos.asLong(sx, sy, sz));
                    mc.levelRenderer.setSectionDirty(sx, sy, sz);
                }
            }
        }
    }

    public static void updateSubmarineOcclusion(UUID id, Collection<BlockPos> blocks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;
        mc.execute(() -> {
            WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(mc.level);
            if (container == null)
                return;
            WaterOcclusionRegion old = regions.remove(id);
            if (old != null)
                container.removeRegion(old);
            if (blocks == null || blocks.isEmpty()) {
                lastBlocks.remove(id);
                lastWet.remove(id);
                lastEdits.remove(id);
                CompartmentTracker.setOcclusionBlocks(id, null);
                return;
            }

            lastEdits.put(id, CompartmentTracker.clientEdits(id));
            Collection<BlockPos> previous = lastBlocks.put(id, blocks);
            if (previous == null || previous.size() != blocks.size() || !previous.containsAll(blocks)) {
                AABB known = CompartmentTracker.getWorldAABB(id);
                if (known != null && mc.levelRenderer != null)
                    invalidateSections(mc, known);
            }
            Collection<BlockPos> filtered = filterToCubes(mc.level, id, blocks);
            CompartmentTracker.setOcclusionBlocks(id, filtered);

            boolean wet = fluidNearby(mc.level, id);
            lastWet.put(id, wet);
            if (!wet || filtered.isEmpty())
                return;

            BoundedBitVolume3i volume = BoundedBitVolume3i.fromBlocks(filtered);
            if (volume == null)
                return;
            WaterOcclusionRegion region = container.addRegion(volume);
            if (region != null)
                regions.put(id, region);
        });
    }

    private static final Direction[] AROUND = { Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST,
            Direction.DOWN };

    private static Collection<BlockPos> filterToCubes(Level level, UUID id, Collection<BlockPos> blocks) {
        SubLevelContainer subContainer = SubLevelContainer.getContainer(level);
        if (subContainer == null)
            return blocks;
        SubLevel sub = subContainer.getSubLevel(id);
        if (sub == null)
            return blocks;
        LevelPlot plot = sub.getPlot();
        if (plot == null)
            return blocks;

        Set<BlockPos> union = blocks instanceof Set<BlockPos> set ? set : new HashSet<>(blocks);
        Set<BlockPos> solid = CompartmentTracker.solidBlocks(id);
        BoundingBox3ic bounds = plot.getBoundingBox();
        List<BlockPos> out = new ArrayList<>(blocks.size());
        long lastCp = Long.MIN_VALUE;
        LevelChunk lastChunk = null;
        for (BlockPos pos : blocks) {
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            long cp = ChunkPos.asLong(cx, cz);
            if (cp != lastCp) {
                lastCp = cp;
                lastChunk = plot.getChunk(plot.toLocal(new ChunkPos(cx, cz)));
            }
            if (lastChunk == null) {
                out.add(pos);
                continue;
            }
            BlockState state = lastChunk.getBlockState(pos);
            if (state.getBlock() instanceof LiquidBlock)
                continue;
            if (state.isAir()) {
                out.add(pos);
                continue;
            }
            boolean full = state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (full && !dry(level, sub, pos)) {
                out.add(pos);
                continue;
            }
            if (enclosed(pos, solid, bounds)) {
                out.add(pos);
                continue;
            }
            if (!full && !dry(level, sub, pos) && sealedByHull(state, pos, union))
                out.add(pos);
        }
        return out;
    }

    private static boolean enclosed(BlockPos pos, Set<BlockPos> solid, BoundingBox3ic b) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (Direction dir : AROUND) {
            p.set(pos);
            boolean hit = false;
            while (!hit) {
                p.move(dir);
                if (p.getX() < b.minX() || p.getX() > b.maxX() || p.getY() < b.minY() || p.getY() > b.maxY()
                        || p.getZ() < b.minZ() || p.getZ() > b.maxZ())
                    break;
                hit = solid.contains(p);
            }
            if (!hit)
                return false;
        }
        return true;
    }

    private static boolean dry(Level level, SubLevel sub, BlockPos pos) {
        Vector3d world = sub.logicalPose().transformPosition(new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        cell.set(BlockPos.containing(world.x, world.y, world.z));
        for (int i = 0; i < 3; i++) {
            if (!CompartmentTracker.realFluidState(level, cell).isEmpty())
                return false;
            cell.move(Direction.DOWN);
        }
        return true;
    }

    private static boolean sealedByHull(BlockState state, BlockPos pos, Set<BlockPos> union) {
        VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        for (Direction dir : Direction.values()) {
            if (!Block.isFaceFull(shape, dir) && !union.contains(pos.relative(dir)))
                return false;
        }
        return true;
    }
}
