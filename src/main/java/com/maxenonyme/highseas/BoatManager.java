package com.maxenonyme.highseas;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.system.GirderFailure;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = CreateHighSeas.MOD_ID)
public final class BoatManager {

    private static final int RESCAN_INTERVAL = 2;
    private static final int FAST_INTERVAL = 1;
    private static final int COVER_REFRESH = 200;
    private static final int SCAN_BUDGET = 40000;
    private static final long AWAY_TICKS = 60;

    private static final class Boat {
        long lastScan;
        boolean registered;
        CompartmentDetector.Result cover;
        CompartmentDetector.Result pushed;
        CompartmentDetector.IncrementalScanState scan;
        int scanVersion;
        long coverTick = Long.MIN_VALUE / 2;
        int version = -1;
        long awaySince = -1;
        Map<BlockPos, Long> under = Map.of();
        List<Probe> probes = List.of();
    }

    private record Probe(BlockPos anchor, BlockPos[] openings, BlockPos[] tops) {
    }

    private static final Map<UUID, Boat> CLIENT = new HashMap<>();
    private static final Map<UUID, Boat> SERVER = new HashMap<>();

    private static final Map<UUID, SubLevel> BOATS = new ConcurrentHashMap<>();

    public static Map<UUID, SubLevel> boatSubs() {
        return BOATS;
    }

    private BoatManager() {
    }

    public static boolean isEnabled() {
        try {
            return SubmarineConfig.ENABLE_BOAT_WATER_CULLING.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (FMLEnvironment.dist == Dist.CLIENT)
            return;
        if (!isEnabled()) {
            clearSide(false);
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels())
            tick(level, false);
    }

    public static void tick(Level level, boolean client) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null)
            return;

        Map<UUID, Boat> boats = client ? CLIENT : SERVER;
        long now = level.getGameTime();
        Set<UUID> seen = new HashSet<>();
        UUID nearest = nearestSub(level, container);

        for (SubLevel sub : container.getAllSubLevels()) {
            UUID id = sub.getUniqueId();
            if (CompartmentTracker.isSubmarineManaged(id, now) || sub.getPlot() != null && GirderFailure.loose(level, sub))
                continue;
            seen.add(id);

            int interval = id.equals(nearest) ? FAST_INTERVAL : RESCAN_INTERVAL;
            Boat boat = boats.get(id);
            if (boat != null && (now - boat.lastScan) < interval)
                continue;
            if (boat == null) {
                boat = new Boat();
                boats.put(id, boat);
            }
            boat.lastScan = now;

            if (afloat(level, sub)) {
                boat.awaySince = -1;
            } else {
                if (boat.awaySince < 0)
                    boat.awaySince = now;
                if (!boat.registered || now - boat.awaySince >= AWAY_TICKS) {
                    if (boat.registered)
                        release(id, boat);
                    boat.scan = null;
                    boat.cover = null;
                    boat.probes = List.of();
                    boat.version = -1;
                    continue;
                }
            }

            int version = CompartmentTracker.structureVersion(id);
            if (boat.scan == null && (boat.version != version || now - boat.coverTick >= COVER_REFRESH)) {
                boat.scan = CompartmentDetector.beginScan(sub);
                boat.scanVersion = version;
            }
            if (boat.scan != null && CompartmentDetector.stepScan(boat.scan, SCAN_BUDGET)) {
                CompartmentDetector.Result fresh = underCover(sub, CompartmentDetector.finishScan(boat.scan));
                if (!same(boat.cover, fresh)) {
                    boat.cover = fresh;
                    boat.probes = probes(fresh);
                }
                boat.scan = null;
                boat.coverTick = now;
                boat.version = boat.scanVersion;
            }
            CompartmentDetector.Result pushed = boat.cover;

            if (pushed != null) {
                CompartmentTracker.setSunken(id, sunken(level, sub, boat, now));
                if (pushed != boat.pushed || !boat.registered
                        || CompartmentTracker.getCompartments(id) != pushed.components()) {
                    CompartmentTracker.update(id, sub, pushed, now);
                    boat.pushed = pushed;
                } else {
                    CompartmentTracker.touch(id, sub, now);
                }
                boat.registered = true;
                BOATS.put(id, sub);
            } else if (boat.registered) {
                release(id, boat);
            }
        }

        Iterator<Map.Entry<UUID, Boat>> it = boats.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Boat> e = it.next();
            if (!seen.contains(e.getKey())) {
                if (e.getValue().registered)
                    CompartmentTracker.remove(e.getKey());
                BOATS.remove(e.getKey());
                it.remove();
            }
        }
    }

    private static final long DROWN_TICKS = 40;

    private static void release(UUID id, Boat boat) {
        boat.pushed = null;
        boat.probes = List.of();
        boat.under = Map.of();
        CompartmentTracker.remove(id);
        boat.registered = false;
        BOATS.remove(id);
    }

    private static boolean afloat(Level level, SubLevel sub) {
        LevelPlot plot = sub.getPlot();
        if (plot == null)
            return false;
        BoundingBox3ic b = plot.getBoundingBox();
        Pose3dc pose = sub.logicalPose();
        Vector3d corner = new Vector3d();
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            corner.set((i & 1) == 0 ? b.minX() : b.maxX() + 1, (i & 2) == 0 ? b.minY() : b.maxY() + 1,
                    (i & 4) == 0 ? b.minZ() : b.maxZ() + 1);
            pose.transformPosition(corner);
            low = Math.min(low, corner.y);
            high = Math.max(high, corner.y);
        }
        Vector3dc centre = pose.position();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(Mth.floor(centre.x()), Mth.floor(high) + 1,
                Mth.floor(centre.z()));
        if (CompartmentTracker.realFluidState(level, cursor).is(FluidTags.WATER))
            return false;
        for (int y = Mth.floor(high); y >= Mth.floor(low); y--) {
            if (CompartmentTracker.realFluidState(level, cursor.setY(y)).is(FluidTags.WATER))
                return true;
        }
        return false;
    }

    private static Set<BlockPos> sunken(Level level, SubLevel sub, Boat boat, long now) {
        Pose3dc pose = sub.logicalPose();
        Map<BlockPos, Long> under = new HashMap<>();
        Set<BlockPos> sunk = new HashSet<>();
        boolean instant = !SubmarineConfig.progressiveFlooding();
        for (Probe probe : boat.probes) {
            BlockPos anchor = probe.anchor();
            if (anchor == null)
                continue;
            if (instant && leaks(level, pose, probe.openings())) {
                sunk.add(anchor);
                continue;
            }
            if (!topUnderwater(level, pose, probe.tops()))
                continue;
            long since = boat.under.getOrDefault(anchor, now);
            under.put(anchor, since);
            if (now - since >= DROWN_TICKS)
                sunk.add(anchor);
        }
        boat.under = under;
        return sunk;
    }

    private static boolean leaks(Level level, Pose3dc pose, BlockPos[] openings) {
        Vector3d w = new Vector3d();
        for (BlockPos n : openings) {
            pose.transformPosition(w.set(n.getX() + 0.5, n.getY() + 0.5, n.getZ() + 0.5));
            if (CompartmentTracker.realFluidState(level, BlockPos.containing(w.x, w.y, w.z)).is(FluidTags.WATER))
                return true;
        }
        return false;
    }

    private static List<Probe> probes(CompartmentDetector.Result r) {
        if (r == null)
            return List.of();
        Set<BlockPos> walls = r.solidBlocks() == null ? Set.of() : r.solidBlocks();
        List<Probe> out = new ArrayList<>(r.components().size());
        for (CompartmentDetector.Component c : r.components()) {
            Set<BlockPos> openings = new HashSet<>();
            List<BlockPos> tops = new ArrayList<>();
            for (BlockPos p : c.internal()) {
                for (Direction dir : Direction.values()) {
                    BlockPos n = p.relative(dir);
                    if (!c.internal().contains(n) && !walls.contains(n))
                        openings.add(n);
                }
                if (!c.internal().contains(p.above()))
                    tops.add(p);
            }
            out.add(new Probe(c.anchor(), openings.toArray(new BlockPos[0]), tops.toArray(new BlockPos[0])));
        }
        return out;
    }

    private static boolean same(CompartmentDetector.Result a, CompartmentDetector.Result b) {
        if (a == null || b == null)
            return a == b;
        if (a.components().size() != b.components().size() || !java.util.Objects.equals(a.solidBlocks(), b.solidBlocks()))
            return false;
        Map<BlockPos, CompartmentDetector.Component> byAnchor = new HashMap<>();
        for (CompartmentDetector.Component c : a.components())
            byAnchor.put(c.anchor(), c);
        for (CompartmentDetector.Component c : b.components()) {
            CompartmentDetector.Component old = byAnchor.get(c.anchor());
            if (old == null || !old.internal().equals(c.internal()))
                return false;
        }
        return true;
    }

    private static boolean topUnderwater(Level level, Pose3dc pose, BlockPos[] tops) {
        Vector3d w = new Vector3d();
        Vector3d top = null;
        for (BlockPos p : tops) {
            w.set(p.getX() + 0.5, p.getY() + 0.95, p.getZ() + 0.5);
            pose.transformPosition(w);
            if (top == null || w.y > top.y)
                top = new Vector3d(w);
        }
        return top != null && CompartmentTracker.realFluidState(level, BlockPos.containing(top.x, top.y, top.z))
                .is(FluidTags.WATER);
    }

    private static final int WEST = 1, EAST = 2, NORTH = 4, SOUTH = 8, FLOOR = 16,
            BOXED = WEST | EAST | NORTH | SOUTH | FLOOR;
    private static final long MAX_VOLUME = 4_000_000L;
    private static final int MEND_PASSES = 3;

    private static CompartmentDetector.Result underCover(SubLevel sub, CompartmentDetector.Result r) {
        if (r == null || r.components().isEmpty())
            return null;
        LevelPlot plot = sub.getPlot();
        if (plot == null)
            return null;

        BoundingBox3ic b = plot.getBoundingBox();
        int minX = b.minX(), maxX = b.maxX();
        int minY = b.minY(), maxY = b.maxY();
        int minZ = b.minZ(), maxZ = b.maxZ();
        int dx = maxX - minX + 1, dy = maxY - minY + 1, dz = maxZ - minZ + 1;
        if (dx <= 0 || dy <= 0 || dz <= 0 || (long) dx * dy * dz > MAX_VOLUME)
            return null;

        Set<BlockPos> walls = r.solidBlocks() == null ? Set.of() : r.solidBlocks();
        byte[] cover = sweep(walls, minX, minY, minZ, dx, dy, dz);

        List<Set<BlockPos>> holds = new ArrayList<>();
        for (CompartmentDetector.Component c : r.components()) {
            Set<BlockPos> kept = new HashSet<>();
            for (BlockPos p : c.internal()) {
                int ix = p.getX() - minX, iy = p.getY() - minY, iz = p.getZ() - minZ;
                if (ix < 0 || ix >= dx || iy < 0 || iy >= dy || iz < 0 || iz >= dz)
                    continue;
                if (cover[(iy * dz + iz) * dx + ix] != BOXED)
                    continue;
                kept.add(p);
            }
            holds.addAll(split(kept));
        }
        if (holds.isEmpty())
            return null;

        List<CompartmentDetector.Component> comps = new ArrayList<>(holds.size());
        for (Set<BlockPos> hold : holds) {
            BlockPos anchor = null;
            for (BlockPos p : hold) {
                if (anchor == null || lex(p, anchor) < 0)
                    anchor = p;
            }
            Set<BlockPos> group = brim(hold, walls);
            Set<BlockPos> skin = new HashSet<>();
            for (BlockPos p : group) {
                for (Direction dir : Direction.values()) {
                    BlockPos n = p.relative(dir);
                    if (walls.contains(n))
                        skin.add(n);
                }
            }
            comps.add(new CompartmentDetector.Component(group, skin, true, anchor));
        }
        return new CompartmentDetector.Result(comps, r.totalScanned(), r.solidBlocks());
    }

    private static Set<BlockPos> brim(Set<BlockPos> hold, Set<BlockPos> walls) {
        int top = Integer.MIN_VALUE;
        Map<Long, Integer> columns = new HashMap<>();
        for (BlockPos p : hold) {
            top = Math.max(top, p.getY());
            columns.merge(BlockPos.asLong(p.getX(), 0, p.getZ()), p.getY(), Math::min);
        }
        Map<Long, Integer> rim = new HashMap<>();
        for (Map.Entry<Long, Integer> e : columns.entrySet()) {
            int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey());
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                long key = BlockPos.asLong(x + dir.getStepX(), 0, z + dir.getStepZ());
                if (!columns.containsKey(key))
                    rim.merge(key, e.getValue(), Math::min);
            }
        }
        Set<BlockPos> out = new HashSet<>(hold);
        raise(out, columns, top, walls, true);
        raise(out, rim, top, walls, false);
        return out;
    }

    private static void raise(Set<BlockPos> out, Map<Long, Integer> columns, int top, Set<BlockPos> walls, boolean open) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (Map.Entry<Long, Integer> e : columns.entrySet()) {
            int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey());
            boolean held = open;
            for (int y = e.getValue(); y <= top; y++) {
                p.set(x, y, z);
                if (walls.contains(p)) {
                    held = true;
                    continue;
                }
                if (held)
                    out.add(p.immutable());
            }
        }
    }

    private static List<Set<BlockPos>> split(Set<BlockPos> cells) {
        List<Set<BlockPos>> groups = new ArrayList<>();
        Set<BlockPos> left = new HashSet<>(cells);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        while (!left.isEmpty()) {
            BlockPos seed = left.iterator().next();
            left.remove(seed);
            Set<BlockPos> group = new HashSet<>();
            group.add(seed);
            queue.add(seed);
            while (!queue.isEmpty()) {
                BlockPos cur = queue.poll();
                for (Direction dir : Direction.values()) {
                    BlockPos n = cur.relative(dir);
                    if (left.remove(n)) {
                        group.add(n);
                        queue.add(n);
                    }
                }
            }
            groups.add(group);
        }
        return groups;
    }

    private static byte[] sweep(Set<BlockPos> solid, int minX, int minY, int minZ, int dx, int dy, int dz) {
        boolean[] wall = new boolean[dx * dy * dz];
        for (BlockPos p : solid) {
            int ix = p.getX() - minX, iy = p.getY() - minY, iz = p.getZ() - minZ;
            if (ix >= 0 && ix < dx && iy >= 0 && iy < dy && iz >= 0 && iz < dz)
                wall[(iy * dz + iz) * dx + ix] = true;
        }

        byte[] cover = new byte[wall.length];
        for (int iy = 0; iy < dy; iy++) {
            for (int iz = 0; iz < dz; iz++) {
                int row = (iy * dz + iz) * dx;
                boolean seen = false;
                for (int ix = 0; ix < dx; ix++) {
                    if (wall[row + ix])
                        seen = true;
                    else if (seen)
                        cover[row + ix] |= WEST;
                }
                seen = false;
                for (int ix = dx - 1; ix >= 0; ix--) {
                    if (wall[row + ix])
                        seen = true;
                    else if (seen)
                        cover[row + ix] |= EAST;
                }
            }
            for (int ix = 0; ix < dx; ix++) {
                boolean seen = false;
                for (int iz = 0; iz < dz; iz++) {
                    int i = (iy * dz + iz) * dx + ix;
                    if (wall[i])
                        seen = true;
                    else if (seen)
                        cover[i] |= NORTH;
                }
                seen = false;
                for (int iz = dz - 1; iz >= 0; iz--) {
                    int i = (iy * dz + iz) * dx + ix;
                    if (wall[i])
                        seen = true;
                    else if (seen)
                        cover[i] |= SOUTH;
                }
            }
        }
        int layer = dx * dz;
        for (int column = 0; column < layer; column++) {
            boolean floored = false;
            for (int iy = 0; iy < dy; iy++) {
                int i = iy * layer + column;
                if (wall[i])
                    floored = true;
                else if (floored)
                    cover[i] |= FLOOR;
            }
        }
        return mend(cover, wall, dx, dy, dz);
    }

    private static byte[] mend(byte[] cover, boolean[] wall, int dx, int dy, int dz) {
        for (int pass = 0; pass < MEND_PASSES; pass++) {
            byte[] next = cover.clone();
            boolean changed = false;
            for (int iy = 0; iy < dy; iy++) {
                for (int iz = 0; iz < dz; iz++) {
                    for (int ix = 0; ix < dx; ix++) {
                        int i = (iy * dz + iz) * dx + ix;
                        if (wall[i] || cover[i] == BOXED)
                            continue;
                        int gained = 0;
                        if (iz > 0)
                            gained |= cover[i - dx] & (WEST | EAST);
                        if (iz < dz - 1)
                            gained |= cover[i + dx] & (WEST | EAST);
                        if (ix > 0)
                            gained |= cover[i - 1] & (NORTH | SOUTH);
                        if (ix < dx - 1)
                            gained |= cover[i + 1] & (NORTH | SOUTH);
                        if ((gained & ~cover[i]) != 0) {
                            next[i] = (byte) (cover[i] | gained);
                            changed = true;
                        }
                    }
                }
            }
            cover = next;
            if (!changed)
                break;
        }
        return cover;
    }

    private static int lex(BlockPos a, BlockPos b) {
        int d = Integer.compare(a.getX(), b.getX());
        if (d != 0)
            return d;
        d = Integer.compare(a.getY(), b.getY());
        return d != 0 ? d : Integer.compare(a.getZ(), b.getZ());
    }

    private static UUID nearestSub(Level level, SubLevelContainer container) {
        List<? extends Player> players = level.players();
        if (players.isEmpty())
            return null;
        double best = Double.MAX_VALUE;
        UUID bestId = null;
        for (SubLevel sub : container.getAllSubLevels()) {
            UUID id = sub.getUniqueId();
            Vector3dc p = sub.logicalPose().position();
            for (Player pl : players) {
                double d = pl.distanceToSqr(p.x(), p.y(), p.z());
                if (d < best) {
                    best = d;
                    bestId = id;
                }
            }
        }
        return bestId;
    }

    public static void clearSide(boolean client) {
        Map<UUID, Boat> boats = client ? CLIENT : SERVER;
        for (Map.Entry<UUID, Boat> e : boats.entrySet())
            if (e.getValue().registered)
                CompartmentTracker.remove(e.getKey());
        boats.clear();
    }

    public static void clearAll() {
        clearSide(true);
        clearSide(false);
    }
}
