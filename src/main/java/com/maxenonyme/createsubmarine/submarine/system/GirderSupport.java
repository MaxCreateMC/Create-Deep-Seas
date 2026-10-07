package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.simibubi.create.content.decoration.girder.GirderBlock;
import com.simibubi.create.content.decoration.girder.GirderEncasedShaftBlock;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GirderSupport {
    private GirderSupport() {
    }

    public record Support(long a, long b, long[] girders) {
    }

    public record Layout(Support[] supports, long[] collapsed) {
    }

    public static final Layout NONE = new Layout(new Support[0], new long[0]);

    private static final int RADIUS = 4;
    private static final double BONUS = 0.6;
    private static final double COLLAPSED = 0.6;
    private static final int REFRESH = 100;
    private static final long MAX_VOLUME = 1_000_000L;

    private record Key(UUID id, boolean client) {
    }

    private record Cached(long tick, long version, long bounds, Layout layout) {
    }

    private static final Map<Key, Cached> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<Long>> COLLAPSES = new ConcurrentHashMap<>();

    public static Layout layout(Level level, UUID id, long version, int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ) {
        if (level == null)
            return NONE;
        long now = level.getGameTime();
        long bounds = ((((((long) minX * 31 + minY) * 31 + minZ) * 31 + maxX) * 31 + maxY) * 31) + maxZ;
        Key key = new Key(id, level.isClientSide);
        Cached cached = CACHE.get(key);
        if (cached != null && cached.version() == version && cached.bounds() == bounds
                && now >= cached.tick() && now - cached.tick() < REFRESH)
            return cached.layout();
        Support[] supports = scan(level, minX, minY, minZ, maxX, maxY, maxZ);
        Set<Long> collapses = COLLAPSES.get(id);
        long[] collapsed = new long[0];
        if (collapses != null) {
            for (Support s : supports) {
                collapses.remove(s.a());
                collapses.remove(s.b());
            }
            collapsed = collapses.stream().mapToLong(Long::longValue).toArray();
        }
        Layout layout = supports.length == 0 && collapsed.length == 0 ? NONE : new Layout(supports, collapsed);
        CACHE.put(key, new Cached(now, version, bounds, layout));
        return layout;
    }

    private static double distance(long c, BlockPos pos) {
        double dx = BlockPos.getX(c) - pos.getX();
        double dy = BlockPos.getY(c) - pos.getY();
        double dz = BlockPos.getZ(c) - pos.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double bonus(long c, BlockPos pos) {
        double d = distance(c, pos);
        return d <= RADIUS ? BONUS * (1.0 - d / (RADIUS + 1.0)) : 0.0;
    }

    public static double factor(Layout layout, BlockPos pos) {
        double best = 0.0;
        for (Support s : layout.supports())
            best = Math.max(best, Math.max(bonus(s.a(), pos), bonus(s.b(), pos)));
        double weak = 1.0;
        for (long c : layout.collapsed())
            if (distance(c, pos) <= RADIUS)
                weak = COLLAPSED;
        return (1.0 + best) * weak;
    }

    public static Support carrier(Layout layout, BlockPos pos) {
        Support found = null;
        double best = 0.0;
        for (Support s : layout.supports()) {
            double b = Math.max(bonus(s.a(), pos), bonus(s.b(), pos));
            if (b > best) {
                best = b;
                found = s;
            }
        }
        return found;
    }

    public static void collapse(UUID id, Support support) {
        Set<Long> set = COLLAPSES.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
        set.add(support.a());
        set.add(support.b());
        CACHE.remove(new Key(id, false));
        CACHE.remove(new Key(id, true));
    }

    private static Support[] scan(Level level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume <= 0 || volume > MAX_VOLUME)
            return new Support[0];
        LongOpenHashSet girders = new LongOpenHashSet();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    if (girder(level.getBlockState(m.set(x, y, z))))
                        girders.add(m.asLong());
        if (girders.isEmpty())
            return new Support[0];
        List<Support> out = new ArrayList<>();
        Direction[] axes = { Direction.UP, Direction.EAST, Direction.SOUTH };
        for (Direction up : axes) {
            for (long g : girders) {
                BlockPos start = BlockPos.of(g);
                if (girders.contains(start.relative(up.getOpposite()).asLong()))
                    continue;
                LongArrayList run = new LongArrayList();
                BlockPos end = start;
                run.add(end.asLong());
                while (girders.contains(end.relative(up).asLong())) {
                    end = end.relative(up);
                    run.add(end.asLong());
                }
                BlockPos a = start.relative(up.getOpposite());
                BlockPos b = end.relative(up);
                if (hull(level, a) && hull(level, b))
                    out.add(new Support(a.asLong(), b.asLong(), run.toLongArray()));
            }
        }
        return out.toArray(new Support[0]);
    }

    public static boolean girder(BlockState state) {
        return state.getBlock() instanceof GirderBlock || state.getBlock() instanceof GirderEncasedShaftBlock;
    }

    private static boolean hull(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && !girder(state) && !CompartmentDetector.isPermeable(state);
    }

    public static void forget(UUID id) {
        CACHE.remove(new Key(id, false));
        CACHE.remove(new Key(id, true));
        COLLAPSES.remove(id);
    }

    public static void clear() {
        CACHE.clear();
        COLLAPSES.clear();
    }
}
