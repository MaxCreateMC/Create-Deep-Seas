package com.maxenonyme.createsubmarine.submarine.compartment;

import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.*;

public class CompartmentDetector {
    private static int maxBlocks() {
        var spec = SubmarineConfig.SERVER_SPEC;
        return spec.isLoaded()
                ? SubmarineConfig.OXYGEN_MAX_FILL_BLOCKS.get()
                : 500_000;
    }

    public record Component(Set<BlockPos> internal, Set<BlockPos> hull, boolean sealed, BlockPos anchor) {
    }

    public record Result(List<Component> components, int totalScanned, Set<BlockPos> solidBlocks) {
    }

    public static class IncrementalScanState {
        final SubLevel sub;
        final LevelPlot plot;
        final int minX, maxX, minY, maxY, minZ, maxZ;
        int curX, curY, curZ;
        final Set<BlockPos> visited = new HashSet<>();
        final Set<BlockPos> solidBlocks = new HashSet<>();
        final List<Component> partial = new ArrayList<>();
        int total = 0;
        final ChunkCache cache = new ChunkCache();
        final Set<BlockPos> plugs;
        Set<BlockPos> activeInternal;
        Set<BlockPos> activeHull;
        boolean activeSealed;
        boolean chunksMissing = false;
        BlockPos activeAnchor;
        Deque<BlockPos> activeQueue;
        boolean done = false;

        IncrementalScanState(SubLevel sub, LevelPlot plot, BoundingBox3ic b, Set<BlockPos> plugs) {
            this.sub = sub;
            this.plot = plot;
            this.plugs = plugs;
            this.minX = b.minX();
            this.maxX = b.maxX();
            this.minY = b.minY();
            this.maxY = b.maxY();
            this.minZ = b.minZ();
            this.maxZ = b.maxZ();
            this.curX = minX;
            this.curY = minY;
            this.curZ = minZ;
        }

        boolean hasActiveBfs() {
            return activeQueue != null;
        }

        void startBfs(BlockPos start) {
            activeInternal = new HashSet<>();
            activeHull = new HashSet<>();
            activeSealed = true;
            activeAnchor = start;
            activeQueue = new ArrayDeque<>();
            activeQueue.add(start);
            activeInternal.add(start);
        }

        void finishBfs() {
            partial.add(new Component(
                    Collections.unmodifiableSet(activeInternal),
                    Collections.unmodifiableSet(activeHull),
                    activeSealed,
                    activeAnchor));
            activeInternal = null;
            activeHull = null;
            activeQueue = null;
            activeAnchor = null;
        }

        boolean advanceCursor() {
            curZ++;
            if (curZ > maxZ) {
                curZ = minZ;
                curY++;
            }
            if (curY > maxY) {
                curY = minY;
                curX++;
            }
            return curX <= maxX;
        }
    }

    public static IncrementalScanState beginScan(SubLevelAccess subAccess) {
        return beginScan(subAccess, Set.of());
    }

    public static IncrementalScanState beginScan(SubLevelAccess subAccess, Set<BlockPos> plugs) {
        if (!(subAccess instanceof SubLevel sub))
            return null;
        LevelPlot plot = sub.getPlot();
        if (plot == null)
            return null;
        return new IncrementalScanState(sub, plot, plot.getBoundingBox(), plugs);
    }

    public static boolean stepScan(IncrementalScanState st, int budget) {
        if (st == null || st.done)
            return true;

        int max = maxBlocks();
        for (int spent = 0; spent < budget; spent++) {
            if (st.total >= max) {
                if (st.hasActiveBfs())
                    st.finishBfs();
                st.done = true;
                return true;
            }

            if (st.hasActiveBfs()) {
                if (!stepActiveBfs(st, max))
                    st.finishBfs();
                continue;
            }

            BlockPos start = new BlockPos(st.curX, st.curY, st.curZ);
            if (st.visited.add(start)) {
                st.total++;
                BlockState startState = getStateInPlot(st.plot, start, st.cache);
                if (startState != null) {
                    if (isPermeable(startState) && !st.plugs.contains(start)) {
                        st.startBfs(start);
                    } else {
                        st.solidBlocks.add(start);
                    }
                } else {
                    st.chunksMissing = true;
                }
            }

            if (!st.advanceCursor()) {
                if (st.hasActiveBfs())
                    st.finishBfs();
                st.done = true;
                return true;
            }
        }
        return false;
    }

    private static boolean stepActiveBfs(IncrementalScanState st, int max) {
        if (st.activeQueue == null || st.activeQueue.isEmpty())
            return false;
        BlockPos curr = st.activeQueue.poll();
        if (curr == null)
            return false;
        for (Direction dir : Direction.values()) {
            BlockPos next = curr.relative(dir);
            if (st.activeInternal.contains(next))
                continue;
            int nx = next.getX(), ny = next.getY(), nz = next.getZ();
            boolean outOfBounds = nx < st.minX || nx > st.maxX
                    || ny < st.minY || ny > st.maxY
                    || nz < st.minZ || nz > st.maxZ;
            if (outOfBounds) {
                st.activeSealed = false;
                continue;
            }
            BlockState nextState = getStateInPlot(st.plot, next, st.cache);
            if (nextState == null) {
                st.chunksMissing = true;
                st.activeSealed = false;
                continue;
            }
            if (isPermeable(nextState) && !st.plugs.contains(next)) {
                if (st.visited.contains(next))
                    continue;
                st.visited.add(next);
                st.total++;
                st.activeInternal.add(next);
                st.activeQueue.add(next);
                if (compareLex(next, st.activeAnchor) < 0)
                    st.activeAnchor = next;
                if (st.total >= max)
                    return true;
            } else {
                st.activeHull.add(next);
                if (st.visited.add(next)) {
                    st.total++;
                    st.solidBlocks.add(next);
                }
            }
        }
        return true;
    }

    public static Result finishScan(IncrementalScanState st) {
        if (st == null)
            return new Result(List.of(), 0, Set.of());
        return new Result(Collections.unmodifiableList(st.partial), st.total, Collections.unmodifiableSet(st.solidBlocks));
    }

    public static Result detect(SubLevelAccess subAccess) {
        IncrementalScanState st = beginScan(subAccess);
        if (st == null)
            return new Result(List.of(), 0, Set.of());
        while (!stepScan(st, maxBlocks())) {
        }
        return finishScan(st);
    }

    private static class ChunkCache {
        ChunkPos lastPos = null;
        LevelChunk lastChunk = null;
    }

    public static boolean isPermeable(BlockState state) {
        if (state.isAir())
            return true;
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock)
            return state.getValue(BlockStateProperties.OPEN);
        return state.getCollisionShape(
                EmptyBlockGetter.INSTANCE,
                BlockPos.ZERO).isEmpty();
    }

    private static BlockState getStateInPlot(LevelPlot plot, BlockPos pos, ChunkCache cache) {
        ChunkPos posChunk = new ChunkPos(pos);
        if (cache.lastPos == null || !cache.lastPos.equals(posChunk)) {
            cache.lastPos = posChunk;
            ChunkPos localChunkPos = plot.toLocal(posChunk);
            cache.lastChunk = plot.getChunk(localChunkPos);
        }
        if (cache.lastChunk == null)
            return null;
        return cache.lastChunk.getBlockState(pos);
    }

    private static int compareLex(BlockPos a, BlockPos b) {
        int dx = Integer.compare(a.getX(), b.getX());
        if (dx != 0)
            return dx;
        int dy = Integer.compare(a.getY(), b.getY());
        if (dy != 0)
            return dy;
        return Integer.compare(a.getZ(), b.getZ());
    }
}