package com.maxenonyme.highseas;

import com.maxenonyme.createsubmarine.CreateSubmarine;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = CreateHighSeas.MOD_ID)
public class BoatManager {

    private static final int RESCAN_INTERVAL = 2;
    private static final int FAST_INTERVAL = 1;
    private static final int COVER_REFRESH = 200;
    private static final long AWAY_TICKS = 60;

    private static class Boat {
        long lastScan;
        boolean registered;
        CompartmentDetector.Result cover;
        CompartmentDetector.Result pushed;
        CompletableFuture<Survey> scan;
        BoatHoldScan.Glue glue;
        int scanVersion;
        int scanGlued;
        long coverTick = Long.MIN_VALUE / 2;
        int version = -1;
        int glued;
        long awaySince = -1;
        Map<BlockPos, Long> under = Map.of();
        List<Probe> probes = List.of();
    }

    private record Probe(BlockPos anchor, BlockPos[] openings, BlockPos[] tops) {
    }

    private record Survey(CompartmentDetector.Result cover, List<Probe> probes) {
    }

    private static final Map<ResourceKey<Level>, Map<UUID, Boat>> CLIENT = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<UUID, Boat>> SERVER = new HashMap<>();

    private static final Map<UUID, SubLevel> BOATS = new ConcurrentHashMap<>();

    public static Map<UUID, SubLevel> boatSubs() {
        return BOATS;
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

        Set<UUID> seen = new HashSet<>();
        for (ServerLevel level : event.getServer().getAllLevels())
            tick(level, false, seen);
        forgetMissing(SERVER, seen);
    }

    public static void tick(Level level, boolean client) {
        Set<UUID> seen = new HashSet<>();
        tick(level, client, seen);
        forgetMissing(client ? CLIENT : SERVER, seen);
    }

    private static void tick(Level level, boolean client, Set<UUID> seen) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null)
            return;

        Map<UUID, Boat> boats = (client ? CLIENT : SERVER)
                .computeIfAbsent(level.dimension(), k -> new HashMap<>());
        long now = level.getGameTime();
        UUID nearest = nearestSub(level, container);
        boolean busy = false;

        for (Map.Entry<UUID, Boat> e : boats.entrySet())
            if (e.getValue().scan != null && !e.getKey().equals(nearest))
                busy = true;

        Boat queued = null;
        SubLevel queuedSub = null;
        Set<BlockPos> queuedGlued = Set.of();
        long queuedSince = Long.MAX_VALUE;

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
            boat.glue = BoatHoldScan.contraptions(level, sub, boat.glue);
            Set<BlockPos> glued = boat.glue.cells();
            boolean changed = boat.version != version || boat.glued != boat.glue.hash();

            if (boat.scan == null && (changed || now - boat.coverTick >= COVER_REFRESH)) {
                if (id.equals(nearest)) {
                    boat.scan = survey(sub, glued, boat.cover);
                    boat.scanVersion = version;
                    boat.scanGlued = boat.glue.hash();
                } else {
                    long since = changed ? Long.MIN_VALUE : boat.coverTick;
                    if (since < queuedSince) {
                        queued = boat;
                        queuedSub = sub;
                        queuedGlued = glued;
                        queuedSince = since;
                    }
                }
            }

            if (boat.scan != null && boat.scan.isDone()) {
                Survey survey = boat.scan.join();
                if (survey != null) {
                    boat.cover = survey.cover();
                    boat.probes = survey.probes();
                }

                boat.scan = null;
                boat.coverTick = now;
                boat.version = boat.scanVersion;
                boat.glued = boat.scanGlued;
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

        if (!busy && queued != null) {
            queued.scan = survey(queuedSub, queuedGlued, queued.cover);
            queued.scanVersion = CompartmentTracker.structureVersion(queuedSub.getUniqueId());
            queued.scanGlued = queued.glue.hash();
        }
    }

    private static void forgetMissing(Map<ResourceKey<Level>, Map<UUID, Boat>> sides, Set<UUID> seen) {
        for (Iterator<Map<UUID, Boat>> dims = sides.values().iterator(); dims.hasNext(); ) {
            Map<UUID, Boat> boats = dims.next();

            for (Iterator<Map.Entry<UUID, Boat>> it = boats.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Boat> e = it.next();
                if (!seen.contains(e.getKey())) {
                    if (e.getValue().registered)
                        CompartmentTracker.remove(e.getKey());
                    BOATS.remove(e.getKey());
                    it.remove();
                }
            }

            if (boats.isEmpty())
                dims.remove();
        }
    }

    private static CompletableFuture<Survey> survey(SubLevel sub, Set<BlockPos> glued,
            CompartmentDetector.Result previous) {
        return BoatHoldScan.start(sub, glued).thenApplyAsync(fresh -> {
            if (same(previous, fresh))
                return null;

            return new Survey(fresh, probes(fresh));
        }, BoatHoldScan.WORKER);
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
        if (CompartmentTracker.realFluidState(level, cursor).is(CreateSubmarine.WATER_TAG))
            return false;

        for (int y = Mth.floor(high); y >= Mth.floor(low); y--) {
            if (CompartmentTracker.realFluidState(level, cursor.setY(y)).is(CreateSubmarine.WATER_TAG))
                return true;
        }

        return false;
    }

    private static Set<BlockPos> sunken(Level level, SubLevel sub, Boat boat, long now) {
        Pose3dc pose = sub.logicalPose();
        Set<BlockPos> sunk = new HashSet<>();
        List<BlockPos> submerged = new ArrayList<>();
        boolean floating = false;
        boolean instant = !SubmarineConfig.progressiveFlooding();

        for (Probe probe : boat.probes) {
            BlockPos anchor = probe.anchor();
            if (anchor == null)
                continue;

            boolean drowned = topUnderwater(level, pose, probe.tops());
            floating |= !drowned;
            if (instant && leaks(level, pose, probe.openings()))
                sunk.add(anchor);
            else if (drowned)
                submerged.add(anchor);
        }

        Map<BlockPos, Long> under = new HashMap<>();

        if (!floating) {
            for (BlockPos anchor : submerged) {
                long since = boat.under.getOrDefault(anchor, now);
                under.put(anchor, since);
                if (now - since >= DROWN_TICKS)
                    sunk.add(anchor);
            }
        }

        boat.under = under;
        return sunk;
    }

    private static boolean leaks(Level level, Pose3dc pose, BlockPos[] openings) {
        Vector3d point = new Vector3d();

        for (BlockPos n : openings) {
            pose.transformPosition(point.set(n.getX() + 0.5, n.getY() + 0.5, n.getZ() + 0.5));
            if (CompartmentTracker.realFluidState(level, BlockPos.containing(point.x, point.y, point.z))
                    .is(CreateSubmarine.WATER_TAG))
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
        if (a.components().size() != b.components().size() || !Objects.equals(a.solidBlocks(), b.solidBlocks()))
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
        Vector3d point = new Vector3d();
        Vector3d top = null;

        for (BlockPos p : tops) {
            point.set(p.getX() + 0.5, p.getY() + 0.95, p.getZ() + 0.5);
            pose.transformPosition(point);
            if (top == null || point.y > top.y)
                top = new Vector3d(point);
        }

        return top != null && CompartmentTracker.realFluidState(level, BlockPos.containing(top.x, top.y, top.z))
                .is(CreateSubmarine.WATER_TAG);
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
        Map<ResourceKey<Level>, Map<UUID, Boat>> sides = client ? CLIENT : SERVER;

        for (Map<UUID, Boat> boats : sides.values())
            for (Map.Entry<UUID, Boat> e : boats.entrySet())
                if (e.getValue().registered)
                    CompartmentTracker.remove(e.getKey());

        sides.clear();
    }

    public static void clearAll() {
        clearSide(true);
        clearSide(false);
    }
}
