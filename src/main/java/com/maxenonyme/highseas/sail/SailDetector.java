package com.maxenonyme.highseas.sail;

import com.maxenonyme.highseas.wind.WindConfig;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import com.maxenonyme.highseas.block.BoatSailBlock;
import com.maxenonyme.highseas.block.SailCorner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.ChunkPos;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public final class SailDetector {
    private SailDetector() {
    }

    private static final Predicate<BlockState> SAIL = state -> state.is(BoatSailBlock.SAILS)
            && state.hasProperty(BlockStateProperties.AXIS);

    static void scan(LevelPlot plot, Predicate<BlockState> wanted, BiConsumer<BlockPos.MutableBlockPos, BlockState> found) {
        BoundingBox3ic b = plot.getBoundingBox();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) {
            for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) {
                ChunkAccess chunk = plot.getChunk(plot.toLocal(new ChunkPos(cx, cz)));
                if (chunk == null)
                    continue;
                for (int sy = b.minY() >> 4; sy <= b.maxY() >> 4; sy++) {
                    int index = chunk.getSectionIndexFromSectionY(sy);
                    if (index < 0 || index >= chunk.getSectionsCount())
                        continue;
                    LevelChunkSection section = chunk.getSection(index);
                    if (section.hasOnlyAir() || !section.maybeHas(wanted))
                        continue;
                    int x0 = Math.max(b.minX(), cx << 4), x1 = Math.min(b.maxX(), (cx << 4) + 15);
                    int y0 = Math.max(b.minY(), sy << 4), y1 = Math.min(b.maxY(), (sy << 4) + 15);
                    int z0 = Math.max(b.minZ(), cz << 4), z1 = Math.min(b.maxZ(), (cz << 4) + 15);
                    for (int y = y0; y <= y1; y++) {
                        for (int z = z0; z <= z1; z++) {
                            for (int x = x0; x <= x1; x++) {
                                BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                                if (wanted.test(state))
                                    found.accept(m.set(x, y, z), state);
                            }
                        }
                    }
                }
            }
        }
    }

    public static List<SailGroup> detect(Level level, LevelPlot plot) {
        BoundingBox3ic bounds = plot.getBoundingBox();
        long volume = (long) (bounds.maxX() - bounds.minX() + 1)
                * (bounds.maxY() - bounds.minY() + 1)
                * (bounds.maxZ() - bounds.minZ() + 1);
        if (volume <= 0 || volume > WindConfig.SAIL_SCAN_MAX_VOLUME) {
            return List.of();
        }

        Map<BlockPos, Direction.Axis> sails = new HashMap<>();
        scan(plot, SAIL, (pos, state) -> sails.put(pos.immutable(), state.getValue(BlockStateProperties.AXIS)));
        if (sails.isEmpty()) {
            return List.of();
        }

        List<SailGroup> groups = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        for (Map.Entry<BlockPos, Direction.Axis> entry : sails.entrySet()) {
            BlockPos start = entry.getKey();
            if (!visited.add(start)) {
                continue;
            }
            Direction.Axis axis = entry.getValue();

            List<BlockPos> component = new ArrayList<>();
            Deque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                BlockPos p = queue.poll();
                component.add(p);
                for (Direction dir : Direction.values()) {
                    BlockPos n = p.relative(dir);
                    if (visited.contains(n)) {
                        continue;
                    }
                    if (sails.get(n) == axis) {
                        visited.add(n);
                        queue.add(n);
                    }
                }
            }

            double sx = 0, sy = 0, sz = 0;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos p : component) {
                sx += p.getX() + 0.5;
                sy += p.getY() + 0.5;
                sz += p.getZ() + 0.5;
                minX = Math.min(minX, p.getX());
                minY = Math.min(minY, p.getY());
                minZ = Math.min(minZ, p.getZ());
                maxX = Math.max(maxX, p.getX());
                maxY = Math.max(maxY, p.getY());
                maxZ = Math.max(maxZ, p.getZ());
            }
            int count = component.size();
            int expectedArea = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            if (count != expectedArea) {
                if (hasHole(component, axis, minX, minY, minZ, maxX, maxY, maxZ)) {
                    continue;
                }
            }
            Vec3 center = new Vec3(sx / count, sy / count, sz / count);

            Direction plus = Direction.get(Direction.AxisDirection.POSITIVE, axis);
            Direction minus = plus.getOpposite();
            int plusSupport = 0, minusSupport = 0;
            for (BlockPos p : component) {
                if (isSupport(level, p.relative(plus))) {
                    plusSupport++;
                }
                if (isSupport(level, p.relative(minus))) {
                    minusSupport++;
                }
            }
            int supportSign = plusSupport > minusSupport ? 1 : (minusSupport > plusSupport ? -1 : 0);

            SailCorner cut = null;
            int cuts = 0;
            double reach = 0.0;
            boolean mixed = false;
            int width = axis == Direction.Axis.X ? maxZ - minZ + 1 : maxX - minX + 1;
            int height = maxY - minY + 1;
            for (BlockPos p : component) {
                BlockState s = level.getBlockState(p);
                SailCorner c = s.hasProperty(BoatSailBlock.CORNER) ? s.getValue(BoatSailBlock.CORNER) : SailCorner.NONE;
                if (c == SailCorner.NONE)
                    continue;
                if (cut != null && cut != c)
                    mixed = true;
                cut = c;
                cuts++;
                int i = axis == Direction.Axis.X ? p.getZ() - minZ : p.getX() - minX;
                int j = p.getY() - minY;
                if (c.h < 0)
                    i = width - 1 - i;
                if (c.v < 0)
                    j = height - 1 - j;
                reach += i + j + 1;
            }
            int area = Math.max(1, (int) Math.round(count - cuts * 0.5));
            if (cut == null || mixed) {
                groups.add(new SailGroup(axis, center, area, new BlockPos(minX, minY, minZ),
                        new BlockPos(maxX, maxY, maxZ), supportSign, 0, 0, 0.0, 0L));
            } else {
                groups.add(new SailGroup(axis, center, area, new BlockPos(minX, minY, minZ),
                        new BlockPos(maxX, maxY, maxZ), supportSign, cut.h, cut.v, reach / cuts, 0L));
            }
        }
        return groups;
    }

    public static BlockPos groupMin(BlockGetter level, BlockPos start) {
        BlockState s = level.getBlockState(start);
        if (!s.is(BoatSailBlock.SAILS) || !s.hasProperty(BlockStateProperties.AXIS)) {
            return null;
        }
        Direction.Axis axis = s.getValue(BlockStateProperties.AXIS);

        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        int minX = start.getX(), minY = start.getY(), minZ = start.getZ();
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            for (Direction dir : Direction.values()) {
                BlockPos n = p.relative(dir);
                if (visited.contains(n)) {
                    continue;
                }
                BlockState ns = level.getBlockState(n);
                if (ns.is(BoatSailBlock.SAILS) && ns.hasProperty(BlockStateProperties.AXIS)
                        && ns.getValue(BlockStateProperties.AXIS) == axis) {
                    visited.add(n);
                    queue.add(n);
                }
            }
        }
        return new BlockPos(minX, minY, minZ);
    }

    private static boolean isSupport(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && !state.is(BoatSailBlock.SAILS);
    }

    private static boolean hasHole(List<BlockPos> component, Direction.Axis axis, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int width, height;
        if (axis == Direction.Axis.X) {
            width = maxZ - minZ + 1;
            height = maxY - minY + 1;
        } else if (axis == Direction.Axis.Y) {
            width = maxX - minX + 1;
            height = maxZ - minZ + 1;
        } else {
            width = maxX - minX + 1;
            height = maxY - minY + 1;
        }

        if (width <= 2 || height <= 2) return false;

        boolean[][] sail = new boolean[width][height];
        for (BlockPos p : component) {
            int u, v;
            if (axis == Direction.Axis.X) {
                u = p.getZ() - minZ;
                v = p.getY() - minY;
            } else if (axis == Direction.Axis.Y) {
                u = p.getX() - minX;
                v = p.getZ() - minZ;
            } else {
                u = p.getX() - minX;
                v = p.getY() - minY;
            }
            sail[u][v] = true;
        }

        boolean[][] visited = new boolean[width][height];
        Deque<int[]> q = new ArrayDeque<>();
        for (int u = 0; u < width; u++) {
            if (!sail[u][0]) { q.add(new int[]{u, 0}); visited[u][0] = true; }
            if (!sail[u][height - 1]) { q.add(new int[]{u, height - 1}); visited[u][height - 1] = true; }
        }
        for (int v = 0; v < height; v++) {
            if (!sail[0][v] && !visited[0][v]) { q.add(new int[]{0, v}); visited[0][v] = true; }
            if (!sail[width - 1][v] && !visited[width - 1][v]) { q.add(new int[]{width - 1, v}); visited[width - 1][v] = true; }
        }

        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] cell = q.poll();
            for (int[] d : dirs) {
                int nu = cell[0] + d[0];
                int nv = cell[1] + d[1];
                if (nu >= 0 && nu < width && nv >= 0 && nv < height) {
                    if (!sail[nu][nv] && !visited[nu][nv]) {
                        visited[nu][nv] = true;
                        q.add(new int[]{nu, nv});
                    }
                }
            }
        }

        for (int u = 0; u < width; u++) {
            for (int v = 0; v < height; v++) {
                if (!sail[u][v] && !visited[u][v]) {
                    return true;
                }
            }
        }
        return false;
    }
}
