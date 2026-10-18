package com.maxenonyme.createsubmarine.submarine.compartment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HullSeal {
    public static final Shape OPEN = new Shape(full());
    public static final Shape WALL = new Shape(null);

    private static final Map<BlockState, Shape> SHAPES = new ConcurrentHashMap<>();

    public record Shape(long[][] faces) {
        public boolean passable() {
            return faces != null;
        }

        public boolean opens(Direction dir) {
            if (faces == null)
                return false;

            for (long bits : faces[dir.ordinal()])
                if (bits != 0)
                    return true;

            return false;
        }

        public boolean crosses(Direction.Axis axis) {
            return opens(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE))
                    && opens(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE));
        }
    }

    public static Shape of(BlockState state) {
        if (state.isAir())
            return OPEN;

        return SHAPES.computeIfAbsent(state, HullSeal::measure);
    }

    public static boolean partial(Shape shape) {
        return shape.passable() && shape != OPEN;
    }

    public static boolean same(Shape a, Shape b) {
        if (a == b)
            return true;

        return a.passable() && b.passable() && a != OPEN && b != OPEN && Arrays.deepEquals(a.faces(), b.faces());
    }

    public static boolean connects(Shape from, Direction dir, Shape to) {
        if (!from.passable() || !to.passable())
            return false;

        long[] out = from.faces()[dir.ordinal()];
        long[] in = to.faces()[dir.getOpposite().ordinal()];

        for (int i = 0; i < 4; i++)
            if ((out[i] & in[i]) != 0)
                return true;

        return false;
    }

    private static Shape measure(BlockState state) {
        VoxelShape collision = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (collision.isEmpty())
            return OPEN;
        if (Block.isShapeFullBlock(collision))
            return WALL;

        Block block = state.getBlock();
        if (state.hasBlockEntity() && !(block instanceof DoorBlock || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock))
            return WALL;
        boolean[] solid = new boolean[4096];

        for (AABB box : collision.toAabbs()) {
            int x0 = cell(box.minX), x1 = cell(box.maxX);
            int y0 = cell(box.minY), y1 = cell(box.maxY);
            int z0 = cell(box.minZ), z1 = cell(box.maxZ);

            for (int x = x0; x < x1; x++)
                for (int y = y0; y < y1; y++)
                    for (int z = z0; z < z1; z++)
                        solid[x << 8 | y << 4 | z] = true;
        }

        int[] region = new int[4096];
        int[] queue = new int[4096];
        int regions = 0;

        for (int start = 0; start < 4096; start++) {
            if (solid[start] || region[start] != 0 || !onBoundary(start))
                continue;

            regions++;
            int head = 0, tail = 0;
            queue[tail++] = start;
            region[start] = regions;

            while (head < tail) {
                int voxel = queue[head++];
                int x = voxel >> 8, y = voxel >> 4 & 15, z = voxel & 15;

                for (Direction dir : Direction.values()) {
                    int nx = x + dir.getStepX(), ny = y + dir.getStepY(), nz = z + dir.getStepZ();
                    if (nx < 0 || nx > 15 || ny < 0 || ny > 15 || nz < 0 || nz > 15)
                        continue;

                    int next = nx << 8 | ny << 4 | nz;
                    if (solid[next] || region[next] != 0)
                        continue;

                    region[next] = regions;
                    queue[tail++] = next;
                }
            }
        }

        if (regions != 1)
            return WALL;

        long[][] faces = new long[6][4];

        for (Direction dir : Direction.values()) {
            int layer = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 15 : 0;

            for (int u = 0; u < 16; u++) {
                for (int v = 0; v < 16; v++) {
                    int voxel = switch (dir.getAxis()) {
                        case X -> layer << 8 | u << 4 | v;
                        case Y -> u << 8 | layer << 4 | v;
                        case Z -> u << 8 | v << 4 | layer;
                    };

                    if (!solid[voxel]) {
                        int bit = u << 4 | v;
                        faces[dir.ordinal()][bit >> 6] |= 1L << (bit & 63);
                    }
                }
            }
        }

        return new Shape(faces);
    }

    private static int cell(double coordinate) {
        return Math.max(0, Math.min(16, (int) Math.round(coordinate * 16)));
    }

    private static boolean onBoundary(int voxel) {
        int x = voxel >> 8, y = voxel >> 4 & 15, z = voxel & 15;
        return x == 0 || x == 15 || y == 0 || y == 15 || z == 0 || z == 15;
    }

    private static long[][] full() {
        long[][] faces = new long[6][4];

        for (long[] face : faces)
            Arrays.fill(face, -1L);

        return faces;
    }
}
