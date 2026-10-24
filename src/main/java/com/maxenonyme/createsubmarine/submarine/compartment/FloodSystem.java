package com.maxenonyme.createsubmarine.submarine.compartment;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import com.maxenonyme.createsubmarine.submarine.system.SubmarineSinkingSystem;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FloodSystem {

    private static final int STEP = 5;
    private static final double STEP_SECONDS = STEP / 20.0;
    private static final double GRAVITY = 9.81;
    private static final double ATMOSPHERE = 10.0;
    private static final double TRICKLE = 0.6;
    private static final double MIN_HEAD = 0.25;
    private static final double LIP = 0.25;
    private static final int MAX_MOVES = 192;
    private static final int SLOSH = 32;
    private static final double SLOSH_MARGIN = 0.5;
    private static final int NEAREST_INLETS = 8;
    private static final long REACH_CAP = 0xFFFF;
    private static final int STRAY_CLEANUP = 32;
    private static final int IDLE_RECHECK = 20;
    private static final int SURFACE_REACH = 320;
    private static final double WATER_WEIGHT = 1.5;
    private static final int AIR_POCKET = 10;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static class Domain {
        final CompartmentDetector.Component comp;
        final BlockPos[] cells;
        final BlockPos[] open;
        final BlockPos[] vents;
        final BlockPos[] openings;
        final BlockPos[] mouths;
        final boolean[] breaches;
        boolean crushed;
        BlockPos[] still = new BlockPos[0];
        double pending;
        boolean dry;
        int idleSteps;

        Domain(CompartmentDetector.Component comp, List<BlockPos> cells, List<BlockPos> open, List<BlockPos> vents,
                List<BlockPos> openings, List<BlockPos> mouths) {
            this.comp = comp;
            this.cells = cells.stream().sorted(Comparator.comparingInt(BlockPos::getY)).toArray(BlockPos[]::new);
            this.open = open.toArray(new BlockPos[0]);
            this.vents = vents.toArray(new BlockPos[0]);
            this.openings = openings.toArray(new BlockPos[0]);
            this.mouths = mouths.toArray(new BlockPos[0]);
            this.breaches = new boolean[this.openings.length];
        }
    }

    private static class Flood {
        List<CompartmentDetector.Component> comps;
        Set<BlockPos> plugs = Set.of();
        List<Domain> domains = List.of();
        volatile Set<BlockPos> water = Set.of();
        volatile Set<BlockPos> soaked = Set.of();
        volatile Set<BlockPos> awash = Set.of();
        volatile int weight;
        volatile Vector3d centre = new Vector3d();
        int version;
    }

    private record Tilt(double base, double x, double y, double z) {
        static Tilt of(Pose3dc pose) {
            Vector3d o = pose.transformPosition(new Vector3d());
            Vector3d ax = pose.transformPosition(new Vector3d(1.0, 0.0, 0.0));
            Vector3d ay = pose.transformPosition(new Vector3d(0.0, 1.0, 0.0));
            Vector3d az = pose.transformPosition(new Vector3d(0.0, 0.0, 1.0));
            return new Tilt(o.y, ax.y - o.y, ay.y - o.y, az.y - o.y);
        }

        double half() {
            return 0.5 * (Math.abs(x) + Math.abs(y) + Math.abs(z));
        }

        double at(BlockPos p) {
            return base + x * (p.getX() + 0.5) + y * (p.getY() + 0.5) + z * (p.getZ() + 0.5);
        }
    }

    private static final Map<UUID, Flood> FLOODS = new ConcurrentHashMap<>();

    public static void clearAll() {
        FLOODS.clear();
    }

    public static Set<BlockPos> waterCells(UUID id) {
        Flood flood = FLOODS.get(id);
        return flood == null ? Set.of() : flood.water;
    }

    public static Set<BlockPos> soakedCells(UUID id) {
        Flood flood = FLOODS.get(id);
        return flood == null ? Set.of() : flood.soaked;
    }

    public static int waterVersion(UUID id) {
        Flood flood = FLOODS.get(id);
        return flood == null ? 0 : flood.version;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % STEP != 0)
            return;
        if (!SubmarineConfig.progressiveFlooding()) {
            FLOODS.clear();
            return;
        }
        Set<UUID> seen = new HashSet<>();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null)
                continue;
            for (SubLevel subLevel : container.getAllSubLevels()) {
                UUID id = subLevel.getUniqueId();
                List<CompartmentDetector.Component> comps = CompartmentTracker.getCompartments(id);
                if (comps.isEmpty()) {
                    Flood adrift = FLOODS.get(id);
                    if (adrift != null && adrift.weight > 0)
                        seen.add(id);
                    continue;
                }
                seen.add(id);
                step(level, subLevel, id, comps);
            }
        }
        FLOODS.keySet().retainAll(seen);
    }

    public static void onPhysicsTick(ForgeSablePrePhysicsTickEvent event) {
        if (FLOODS.isEmpty())
            return;
        ServerLevel level = event.getPhysicsSystem().getLevel();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null)
            return;
        double g = Math.abs(DimensionPhysicsData.getGravity(level).y);
        double dt = event.getTimeStep();
        for (SubLevel raw : container.getAllSubLevels()) {
            if (!(raw instanceof ServerSubLevel subLevel))
                continue;
            UUID id = subLevel.getUniqueId();
            Flood flood = FLOODS.get(id);
            if (flood == null || flood.weight == 0)
                continue;
            RigidBodyHandle handle = event.getPhysicsSystem().getPhysicsHandle(subLevel);
            if (handle == null || !handle.isValid())
                continue;
            Vector3d weight = new Vector3d(0.0, -flood.weight * WATER_WEIGHT * g * dt, 0.0);
            subLevel.logicalPose().orientation().conjugate(new Quaterniond()).transform(weight);
            handle.applyImpulseAtPoint(new Vector3d(flood.centre), weight);
        }
    }

    private static void step(ServerLevel level, SubLevel subLevel, UUID id, List<CompartmentDetector.Component> comps) {
        Flood flood = FLOODS.computeIfAbsent(id, k -> new Flood());
        Set<BlockPos> plugs = CompartmentTracker.plugs(id);
        if (flood.comps != comps || !flood.plugs.equals(plugs)) {
            flood.comps = comps;
            flood.plugs = Set.copyOf(plugs);
            flood.domains = domains(id, comps, flood.plugs);
        }

        Set<BlockPos> water = new HashSet<>();
        Set<BlockPos> soaked = new HashSet<>();
        Set<BlockPos> awash = new HashSet<>();
        if (!flood.domains.isEmpty()) {
            Pose3dc pose = subLevel.logicalPose();
            Tilt tilt = Tilt.of(pose);
            Map<Long, Double> surfaces = new HashMap<>();
            for (Domain domain : flood.domains) {
                if (domain.crushed || !level.isLoaded(domain.comp.anchor()))
                    continue;
                boolean sunken = CompartmentTracker.isSunken(id, domain.comp.anchor());
                swamp(level, pose, domain, sunken, soaked, awash);
                if (sunken)
                    drown(level, domain, water, soaked);
                else if (domain.cells.length > 0)
                    settle(level, subLevel, id, pose, tilt, domain, surfaces, water, soaked);
            }
        }
        if (water.equals(flood.water) && soaked.equals(flood.soaked) && awash.equals(flood.awash))
            return;
        Vector3d centre = new Vector3d();
        for (BlockPos p : water)
            centre.add(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
        for (BlockPos p : awash)
            centre.add(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
        int weight = water.size() + awash.size();
        if (weight > 0)
            centre.div(weight);
        flood.centre = centre;
        flood.soaked = Collections.unmodifiableSet(soaked);
        flood.awash = Collections.unmodifiableSet(awash);
        flood.water = Collections.unmodifiableSet(water);
        flood.weight = weight;
        flood.version++;
    }

    private static List<Domain> domains(UUID id, List<CompartmentDetector.Component> comps, Set<BlockPos> plugs) {
        Set<BlockPos> solid = CompartmentTracker.solidBlocks(id);
        List<Domain> out = new ArrayList<>();
        for (CompartmentDetector.Component c : comps) {
            if (!c.sealed() || c.anchor() == null || CompartmentTracker.isCompromised(id, c.anchor()))
                continue;
            Set<BlockPos> inside = new HashSet<>();
            List<BlockPos> open = new ArrayList<>();
            List<BlockPos> vents = new ArrayList<>();
            Map<BlockPos, BlockPos> ventOf = new HashMap<>();
            for (BlockPos p : c.internal()) {
                if (p == null)
                    continue;
                BlockPos top = p;
                while (c.internal().contains(top.above()))
                    top = top.above();
                BlockPos cap = top.above();
                if (solid.contains(cap)) {
                    inside.add(p);
                } else {
                    open.add(p);
                    vents.add(cap);
                    ventOf.put(p, cap);
                }
            }

            Map<BlockPos, BlockPos> mouthOf = new LinkedHashMap<>();
            for (BlockPos p : inside) {
                for (Direction dir : Direction.values()) {
                    BlockPos n = p.relative(dir);
                    if (inside.contains(n))
                        continue;
                    if (plugs.contains(n) || !solid.contains(n))
                        mouthOf.putIfAbsent(ventOf.getOrDefault(n, n), p);
                }
            }
            List<BlockPos> openings = new ArrayList<>(mouthOf.keySet());
            List<BlockPos> mouths = new ArrayList<>(mouthOf.values());

            Domain domain = new Domain(c, new ArrayList<>(inside), open, vents, openings, mouths);
            for (int k = 0; k < domain.breaches.length; k++)
                domain.breaches[k] = plugs.contains(domain.openings[k]);
            out.add(domain);
        }
        return out;
    }

    private static void swamp(ServerLevel level, Pose3dc pose, Domain domain, boolean sunken, Set<BlockPos> soaked,
            Set<BlockPos> awash) {
        Vector3d point = new Vector3d();
        Map<BlockPos, Boolean> drowned = new HashMap<>();
        for (int i = 0; i < domain.open.length; i++) {
            if (!sunken) {
                Boolean under = drowned.computeIfAbsent(domain.vents[i], vent -> {
                    point.set(vent.getX() + 0.5, vent.getY() + 0.1, vent.getZ() + 0.5);
                    pose.transformPosition(point);
                    return CompartmentTracker.realFluidState(level, BlockPos.containing(point.x, point.y, point.z))
                            .is(CreateSubmarine.WATER_TAG);
                });
                if (!under)
                    continue;
            }
            soaked.add(domain.open[i]);
            awash.add(domain.open[i]);
        }
    }

    private static void drown(ServerLevel level, Domain domain, Set<BlockPos> water, Set<BlockPos> soaked) {
        int moves = 0;
        for (BlockPos p : domain.cells) {
            soaked.add(p);
            BlockState state = level.getBlockState(p);
            boolean full = state.is(Blocks.WATER) && state.getFluidState().isSource();
            if (!full && moves < MAX_MOVES && (state.isAir() || state.is(Blocks.WATER))) {
                pour(level, p);
                full = true;
                moves++;
            }
            if (full)
                water.add(p);
        }
    }

    private static void settle(ServerLevel level, SubLevel subLevel, UUID id, Pose3dc pose, Tilt tilt, Domain domain,
            Map<Long, Double> surfaces, Set<BlockPos> water, Set<BlockPos> soaked) {
        BlockPos[] cells = domain.cells;
        if (domain.openings.length == 0) {
            hold(level, domain, water, soaked);
            return;
        }

        int count = domain.openings.length;
        int inlets = 0;
        double[] inletY = new double[count];
        double[] inletSurface = new double[count];
        BlockPos[] inletMouth = new BlockPos[count];
        double[] outletY = new double[count];
        int outlets = 0;
        double plane = Double.NEGATIVE_INFINITY;
        double highestInlet = Double.NEGATIVE_INFINITY;
        boolean crush = false;
        Vector3d point = new Vector3d();
        for (int k = 0; k < count; k++) {
            BlockPos o = domain.openings[k];
            point.set(o.getX() + 0.5, o.getY() + 0.5, o.getZ() + 0.5);
            pose.transformPosition(point);
            BlockPos wp = BlockPos.containing(point.x, point.y, point.z);
            if (!level.isLoaded(wp))
                continue;
            if (CompartmentTracker.realFluidState(level, wp).is(CreateSubmarine.WATER_TAG)) {
                double surface = surface(level, wp, surfaces);
                inletY[inlets] = point.y;
                inletSurface[inlets] = surface;
                inletMouth[inlets] = domain.mouths[k];
                inlets++;
                plane = Math.max(plane, surface);
                highestInlet = Math.max(highestInlet, point.y + 0.5);
                if (domain.breaches[k] && surface - point.y > crushDepth())
                    crush = true;
            } else {
                outletY[outlets++] = point.y - 0.5;
            }
        }

        if (inlets == 0 && domain.dry && ++domain.idleSteps < IDLE_RECHECK)
            return;
        domain.idleSteps = 0;

        int n = cells.length;
        double[] heights = new double[n];
        boolean[] room = new boolean[n];
        boolean[] full = new boolean[n];
        List<BlockPos> strays = new ArrayList<>();
        int capacity = 0;
        int held = 0;
        for (int i = 0; i < n; i++) {
            BlockState state = level.getBlockState(cells[i]);
            boolean liquid = state.is(Blocks.WATER);
            if (!state.isAir() && !liquid)
                continue;
            room[i] = true;
            capacity++;
            heights[i] = tilt.at(cells[i]);
            if (liquid && state.getFluidState().isSource()) {
                full[i] = true;
                held++;
            } else if (liquid) {
                strays.add(cells[i]);
            }
        }
        domain.dry = held == 0 && strays.isEmpty();
        if (capacity == 0 || (inlets == 0 && domain.dry))
            return;
        int air = capacity - held - strays.size();
        if (crush && air * AIR_POCKET > capacity && !SubmarineConfig.DISABLE_IMPLOSION.get()) {
            domain.crushed = true;
            SubmarineSinkingSystem.implodeCompartment(id, subLevel, level, domain.comp);
            return;
        }

        int sources = Math.min(inlets, NEAREST_INLETS);
        long[] order = new long[capacity];
        int m = 0;
        for (int i = 0; i < n; i++) {
            if (!room[i])
                continue;
            long reach = 0;
            if (sources > 0) {
                double nearest = Double.MAX_VALUE;
                for (int j = 0; j < sources; j++)
                    nearest = Math.min(nearest, cells[i].distSqr(inletMouth[j]));
                reach = Math.min(REACH_CAP, (long) nearest);
            }
            long layer = (long) Math.floor(heights[i] + 4096.0);
            order[m++] = (layer << 40) | (reach << 24) | i;
        }
        Arrays.sort(order);

        double settled = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < held; j++)
            settled = Math.max(settled, heights[index(order[j])]);
        double floor = Double.POSITIVE_INFINITY;
        int drains = 0;
        for (int j = 0; j < outlets; j++) {
            if (outletY[j] + LIP < settled) {
                floor = Math.min(floor, outletY[j]);
                drains++;
            }
        }

        double half = tilt.half();
        int target;
        if (inlets > 0) {
            int below = 0;
            int vented = 0;
            for (int i = 0; i < n; i++) {
                if (!room[i])
                    continue;
                if (heights[i] + half <= plane)
                    below++;
                if (heights[i] <= highestInlet)
                    vented++;
            }
            double trapped = capacity - vented;
            target = 0;
            while (target < below) {
                double rise = heights[index(order[target])];
                if (outlets == 0 && rise > highestInlet) {
                    double pressure = ATMOSPHERE + Math.max(0.0, plane - rise);
                    if (capacity - target - 1 < trapped * ATMOSPHERE / pressure)
                        break;
                }
                target++;
            }
        } else if (drains > 0) {
            target = 0;
            for (int i = 0; i < n; i++) {
                if (room[i] && heights[i] < floor)
                    target++;
            }
            target = Math.min(target, held);
        } else {
            target = held;
        }

        int moves = 0;
        if (target != held) {
            double flow = 0.0;
            if (target > held) {
                for (int j = 0; j < inlets; j++) {
                    double head = inletSurface[j] - Math.max(inletY[j], settled);
                    flow += Math.max(TRICKLE, Math.sqrt(2.0 * GRAVITY * Math.max(0.0, head)));
                }
            } else {
                double head = Math.max(MIN_HEAD, settled - (inlets > 0 ? plane : floor));
                flow = (inlets > 0 ? inlets : drains) * Math.sqrt(2.0 * GRAVITY * head);
            }
            domain.pending = Math.min(MAX_MOVES, domain.pending + flow * STEP_SECONDS);
            moves = (int) domain.pending;
            domain.pending -= moves;
        } else {
            domain.pending = 0.0;
        }

        boolean[] wanted = new boolean[n];
        for (int j = 0; j < target; j++)
            wanted[index(order[j])] = true;

        int[] missing = new int[m];
        int missingCount = 0;
        for (int j = 0; j < m; j++) {
            int i = index(order[j]);
            if (wanted[i] && !full[i])
                missing[missingCount++] = i;
        }
        int[] extra = new int[m];
        int extraCount = 0;
        for (int j = m - 1; j >= 0; j--) {
            int i = index(order[j]);
            if (!wanted[i] && full[i])
                extra[extraCount++] = i;
        }

        int net = target - held;
        int fill = net > 0 ? Math.min(moves, net) : 0;
        int empty = net < 0 ? Math.min(moves, -net) : 0;
        int a = 0;
        int b = 0;
        for (; a < fill && a < missingCount; a++) {
            pour(level, cells[missing[a]]);
            full[missing[a]] = true;
        }
        for (; b < empty && b < extraCount; b++) {
            drain(level, cells[extra[b]]);
            full[extra[b]] = false;
        }
        for (int swaps = 0; swaps < SLOSH && a < missingCount && b < extraCount
                && heights[extra[b]] - heights[missing[a]] > SLOSH_MARGIN; swaps++, a++, b++) {
            pour(level, cells[missing[a]]);
            full[missing[a]] = true;
            drain(level, cells[extra[b]]);
            full[extra[b]] = false;
        }

        int cleaned = 0;
        for (BlockPos stray : strays) {
            if (cleaned >= STRAY_CLEANUP)
                break;
            BlockState state = level.getBlockState(stray);
            if (state.is(Blocks.WATER) && !state.getFluidState().isSource()) {
                drain(level, stray);
                cleaned++;
            }
        }

        for (int i = 0; i < n; i++) {
            if (full[i]) {
                water.add(cells[i]);
                soaked.add(cells[i]);
            } else if (inlets > 0 && room[i] && heights[i] + half > plane && heights[i] - half < plane) {
                soaked.add(cells[i]);
            }
        }
    }

    private static void hold(ServerLevel level, Domain domain, Set<BlockPos> water, Set<BlockPos> soaked) {
        if (--domain.idleSteps <= 0) {
            domain.idleSteps = IDLE_RECHECK;
            List<BlockPos> still = new ArrayList<>();
            for (BlockPos p : domain.cells) {
                BlockState state = level.getBlockState(p);
                if (state.is(Blocks.WATER) && state.getFluidState().isSource())
                    still.add(p);
            }
            domain.still = still.toArray(new BlockPos[0]);
        }
        for (BlockPos p : domain.still) {
            water.add(p);
            soaked.add(p);
        }
    }

    public static double crushDepth() {
        return SubmarineConfig.SERVER_SPEC.isLoaded() ? SubmarineConfig.IMPLOSION_DEPTH.get() : 120.0;
    }

    private static int index(long key) {
        return (int) (key & 0xFFFFFF);
    }

    private static double surface(ServerLevel level, BlockPos from, Map<Long, Double> cache) {
        long key = BlockPos.asLong(from.getX(), 0, from.getZ());
        Double known = cache.get(key);
        if (known != null && known > from.getY())
            return known;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(from.getX(), from.getY(), from.getZ());
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, from.getX(), from.getZ());
        double result;
        if (top > from.getY()
                && CompartmentTracker.realFluidState(level, cursor.setY(top - 1)).is(CreateSubmarine.WATER_TAG)) {
            result = top;
        } else {
            int y = from.getY();
            int limit = Math.min(level.getMaxBuildHeight(), from.getY() + SURFACE_REACH);
            while (y + 1 < limit
                    && CompartmentTracker.realFluidState(level, cursor.setY(y + 1)).is(CreateSubmarine.WATER_TAG))
                y++;
            result = y + 1;
        }
        cache.put(key, result);
        return result;
    }

    private static void pour(ServerLevel level, BlockPos p) {
        level.setBlock(p, Blocks.WATER.defaultBlockState(), FLAGS);
        level.getFluidTicks().clearArea(new BoundingBox(p));
    }

    private static void drain(ServerLevel level, BlockPos p) {
        level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
    }
}
