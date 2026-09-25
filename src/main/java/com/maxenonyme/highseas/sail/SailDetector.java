package com.maxenonyme.highseas.sail;

import com.maxenonyme.highseas.wind.WindConfig;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import com.maxenonyme.highseas.block.BoatSailBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
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

public final class SailDetector {
    private SailDetector() {
    }

    public static List<SailGroup> detect(BlockGetter level, BoundingBox3ic bounds) {
        long volume = (long) (bounds.maxX() - bounds.minX() + 1)
                * (bounds.maxY() - bounds.minY() + 1)
                * (bounds.maxZ() - bounds.minZ() + 1);
        if (volume <= 0 || volume > WindConfig.SAIL_SCAN_MAX_VOLUME) {
            return List.of();
        }

        Map<BlockPos, Direction.Axis> sails = new HashMap<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    m.set(x, y, z);
                    BlockState state = level.getBlockState(m);
                    if (state.is(BoatSailBlock.SAILS) && state.hasProperty(BlockStateProperties.AXIS)) {
                        sails.put(m.immutable(), state.getValue(BlockStateProperties.AXIS));
                    }
                }
            }
        }
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

            groups.add(new SailGroup(axis, center, count,
                    new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), supportSign, 0L));
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
