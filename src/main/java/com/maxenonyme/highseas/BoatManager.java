package com.maxenonyme.highseas;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
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
    private static final int COVER_REFRESH = 40;

    private static final class Boat {
        long lastScan;
        boolean registered;
        Set<BlockPos> flooded = Set.of();
        Map<BlockPos, Integer> dry = Map.of();
        Map<BlockPos, Long> under = Map.of();
        CompartmentDetector.Result cover;
        long coverTick = Long.MIN_VALUE / 2;
        int version = -1;
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
            if (CompartmentTracker.isSubmarineManaged(id, now))
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

            int version = CompartmentTracker.structureVersion(id);
            if (boat.version != version || now - boat.coverTick >= COVER_REFRESH) {
                boat.cover = underCover(sub, CompartmentDetector.detect(sub));
                boat.coverTick = now;
                boat.version = version;
            }
            CompartmentDetector.Result pushed = boat.cover;

            if (pushed != null) {
                CompartmentTracker.setFloodedAnchors(id, flooding(level, sub, pushed, boat));
                CompartmentTracker.update(id, sub, pushed, now);
                boat.registered = true;
                BOATS.put(id, sub);
            } else if (boat.registered) {
                boat.flooded = Set.of();
                boat.dry = Map.of();
                boat.under = Map.of();
                CompartmentTracker.remove(id);
                boat.registered = false;
                BOATS.remove(id);
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

    private static final double SWAMP_ENGAGE = 0.5;
    private static final double SWAMP_RELEASE = 0.2;
    private static final int DRY_SCANS = 10;
    private static final long DROWN_TICKS = 40;
    private static final Direction[] HULL_SIDES = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST };

    private static Set<BlockPos> flooding(Level level, SubLevel sub, CompartmentDetector.Result r, Boat boat) {
        Set<BlockPos> solid = r.solidBlocks() == null ? Set.of() : r.solidBlocks();
        Pose3dc pose = sub.logicalPose();
        Set<BlockPos> flooded = new HashSet<>();
        Map<BlockPos, Integer> dry = new HashMap<>();
        Map<BlockPos, Long> under = new HashMap<>();
        long now = level.getGameTime();

        for (CompartmentDetector.Component c : r.components()) {
            BlockPos anchor = c.anchor();
            if (anchor == null)
                continue;
            double wet = openWetness(level, pose, solid, c);
            boolean sunk = false;
            if (drowned(level, pose, c)) {
                long since = boat.under.getOrDefault(anchor, now);
                under.put(anchor, since);
                sunk = now - since >= DROWN_TICKS;
            }
            if (wet >= SWAMP_ENGAGE || breached(level, pose, solid, c) || sunk) {
                flooded.add(anchor);
                dry.put(anchor, 0);
            } else if (boat.flooded.contains(anchor)) {
                int clear = wet <= SWAMP_RELEASE ? boat.dry.getOrDefault(anchor, 0) + 1 : 0;
                if (clear < DRY_SCANS) {
                    flooded.add(anchor);
                    dry.put(anchor, clear);
                }
            }
        }
        boat.flooded = flooded;
        boat.dry = dry;
        boat.under = under;
        return flooded;
    }

    private static double openWetness(Level level, Pose3dc pose, Set<BlockPos> solid,
            CompartmentDetector.Component c) {
        Vector3d w = new Vector3d();
        int wet = 0, total = 0;
        for (BlockPos p : c.internal()) {
            BlockPos up = p.above();
            if (solid.contains(up) || c.internal().contains(up))
                continue;
            total++;
            w.set(up.getX() + 0.5, up.getY() + 0.5, up.getZ() + 0.5);
            pose.transformPosition(w);
            if (CompartmentTracker.realFluidState(level, BlockPos.containing(w.x, w.y, w.z))
                    .is(FluidTags.WATER))
                wet++;
        }
        return total == 0 ? 0.0 : (double) wet / total;
    }

    private static boolean drowned(Level level, Pose3dc pose, CompartmentDetector.Component c) {
        Vector3d w = new Vector3d();
        Vector3d top = null;
        for (BlockPos p : c.internal()) {
            w.set(p.getX() + 0.5, p.getY() + 0.95, p.getZ() + 0.5);
            pose.transformPosition(w);
            if (top == null || w.y > top.y)
                top = new Vector3d(w);
        }
        return top != null && CompartmentTracker.realFluidState(level, BlockPos.containing(top.x, top.y, top.z))
                .is(FluidTags.WATER);
    }

    private static boolean breached(Level level, Pose3dc pose, Set<BlockPos> solid,
            CompartmentDetector.Component c) {
        Vector3d w = new Vector3d();
        for (BlockPos p : c.internal()) {
            for (Direction dir : HULL_SIDES) {
                BlockPos gap = p.relative(dir);
                if (solid.contains(gap) || c.internal().contains(gap))
                    continue;
                w.set(gap.getX() + 0.5, gap.getY() + 0.5, gap.getZ() + 0.5);
                pose.transformPosition(w);
                if (CompartmentTracker.realFluidState(level, BlockPos.containing(w.x, w.y, w.z))
                        .is(FluidTags.WATER))
                    return true;
            }
        }
        return false;
    }

    private static final int WEST = 1, EAST = 2, NORTH = 4, SOUTH = 8, BOXED = WEST | EAST | NORTH | SOUTH;
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
        for (Set<BlockPos> group : holds) {
            BlockPos anchor = null;
            Set<BlockPos> skin = new HashSet<>();
            for (BlockPos p : group) {
                if (anchor == null || lex(p, anchor) < 0)
                    anchor = p;
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
