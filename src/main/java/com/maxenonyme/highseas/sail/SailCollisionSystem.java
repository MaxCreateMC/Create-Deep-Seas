package com.maxenonyme.highseas.sail;

import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import com.maxenonyme.highseas.wind.WindManager;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.world.entity.MoverType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

public final class SailCollisionSystem {
    private SailCollisionSystem() {
    }

    private static final double IDLE_FILL = 0.5;
    private static final double DEPTH_PER_BLOCK = 0.26;
    private static final double DEPTH_MIN = 0.16;
    private static final double DEPTH_MAX = 2.6;
    private static final double WIND_REF = 1.0;
    private static final double SUPPORT_SHIFT = 0.375;
    private static final double SAIL_HALF_THICK = 0.28;
    private static final double SMOOTH_STEP = 0.04;
    private static final double PI = Math.PI;

    private static final Map<UUID, Long2DoubleOpenHashMap> SMOOTHED = new HashMap<>();

    private record Panel(Direction.Axis axis, double minA, double dimA, double minB, double dimB,
                         double baseN, double bulgeSign, double depth) {
    }

    private record ShipSails(Pose3dc pose, List<Panel> panels, AABB worldBox) {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        long gameTime = event.getServer().getTickCount();
        Map<ServerLevel, List<ShipSails>> byLevel = new HashMap<>();
        Set<UUID> rigged = new HashSet<>();

        for (ServerLevel level : event.getServer().getAllLevels()) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) {
                continue;
            }
            List<ServerSubLevel> subs = new ArrayList<>();
            container.getAllSubLevels().forEach(subs::add);

            List<ShipSails> ships = new ArrayList<>();
            for (ServerSubLevel ship : subs) {
                ShipSails s = buildShip(level, container, subs, ship, gameTime);
                if (s != null) {
                    ships.add(s);
                    rigged.add(ship.getUniqueId());
                }
            }
            if (ships.isEmpty()) {
                continue;
            }
            byLevel.put(level, ships);

            for (ShipSails ship : ships) {
                for (Entity entity : level.getEntities((Entity) null, ship.worldBox(),
                        e -> !(e instanceof Player) && e.isAlive() && !e.isPassenger())) {
                    Vector3d worldPos = new Vector3d(entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ());
                    pushEntity(entity, worldPos, null, ship);
                }
            }

            for (ServerSubLevel rider : subs) {
                if (rider.getPlot() == null || rider.getLevel() == null) {
                    continue;
                }
                Pose3dc riderPose = rider.logicalPose();
                BoundingBox3ic pb = rider.getPlot().getBoundingBox();
                AABB plotBox = new AABB(pb.minX(), pb.minY(), pb.minZ(), pb.maxX() + 1.0, pb.maxY() + 1.0, pb.maxZ() + 1.0);
                for (Entity entity : rider.getLevel().getEntities((Entity) null, plotBox,
                        e -> !(e instanceof Player) && e.isAlive() && !e.isPassenger())) {
                    Vector3d worldPos = riderPose.transformPosition(
                            new Vector3d(entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ()));
                    for (ShipSails ship : ships) {
                        if (pushEntity(entity, worldPos, riderPose, ship)) {
                            break;
                        }
                    }
                }
            }
        }
        SMOOTHED.keySet().retainAll(rigged);
        if (byLevel.isEmpty()) {
            return;
        }

        for (Player player : event.getServer().getPlayerList().getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            UUID subId = SubLevelRegistry.findUUID(player.level());
            SubLevelAccess sub = subId != null ? SubLevelRegistry.getAll().get(subId) : null;
            Level world = sub != null ? SubLevelRegistry.getLevel(subId) : player.level();
            List<ShipSails> ships = byLevel.get(world);
            if (ships == null) {
                continue;
            }
            Vector3d worldPos = new Vector3d(player.getX(), player.getY() + player.getBbHeight() / 2.0, player.getZ());
            Pose3dc entityPose = null;
            if (sub != null) {
                sub.logicalPose().transformPosition(worldPos);
                entityPose = sub.logicalPose();
            }
            for (ShipSails ship : ships) {
                if (pushEntity(player, worldPos, entityPose, ship)) {
                    break;
                }
            }
        }
    }

    private static ShipSails buildShip(ServerLevel level, ServerSubLevelContainer container,
                                       Iterable<ServerSubLevel> subs, ServerSubLevel ship, long gameTime) {
        if (ship.getPlot() == null || ship.getLevel() == null) {
            return null;
        }
        List<SailGroup> sails = SailWindRegistry.getSails(ship, gameTime);
        if (sails.isEmpty()) {
            return null;
        }

        Pose3dc sailPose = ship.logicalPose();
        Quaterniondc sailOrient = sailPose.orientation();
        Vector3d keel = keelOf(level, container, subs, ship, gameTime);

        List<Panel> panels = new ArrayList<>();
        double lminX = Double.MAX_VALUE, lminY = Double.MAX_VALUE, lminZ = Double.MAX_VALUE;
        double lmaxX = -Double.MAX_VALUE, lmaxY = -Double.MAX_VALUE, lmaxZ = -Double.MAX_VALUE;

        for (SailGroup g : sails) {
            if (g.axis() == Direction.Axis.Y) {
                continue;
            }
            if (FurlState.isFurled(ship.getUniqueId(), g.min())) {
                continue;
            }
            Panel p = buildPanel(level, sailPose, sailOrient, keel, g, gameTime, ship.getUniqueId());
            panels.add(p);

            BlockPos mn = g.min();
            BlockPos mx = g.max();
            double pad = p.depth() + 1.0;
            lminX = Math.min(lminX, mn.getX() - pad);
            lminY = Math.min(lminY, mn.getY() - pad);
            lminZ = Math.min(lminZ, mn.getZ() - pad);
            lmaxX = Math.max(lmaxX, mx.getX() + 1 + pad);
            lmaxY = Math.max(lmaxY, mx.getY() + 1 + pad);
            lmaxZ = Math.max(lmaxZ, mx.getZ() + 1 + pad);
        }
        if (panels.isEmpty()) {
            return null;
        }

        AABB worldBox = localBoxToWorld(sailPose, lminX, lminY, lminZ, lmaxX, lmaxY, lmaxZ);
        return new ShipSails(sailPose, panels, worldBox);
    }

    private static Panel buildPanel(ServerLevel level, Pose3dc sailPose, Quaterniondc sailOrient, Vector3d keel,
                                    SailGroup g, long gameTime, UUID shipId) {
        BlockPos mn = g.min();
        BlockPos mx = g.max();
        double countX = mx.getX() + 1 - mn.getX();
        double countY = mx.getY() + 1 - mn.getY();
        double countZ = mx.getZ() + 1 - mn.getZ();

        Vec3 axisVec = g.localNormal();
        Vector3d worldNormal = sailOrient.transform(new Vector3d(axisVec.x, axisVec.y, axisVec.z));

        double along = keel != null ? worldNormal.x * keel.x + worldNormal.z * keel.z : 1.0;
        double windSign = along < 0 ? -1.0 : 1.0;
        double bulgeSign = g.supportSign() != 0 ? -g.supportSign() : windSign;

        double target = 0.0;
        if (keel != null) {
            Vector3d center = new Vector3d(
                    (mn.getX() + mx.getX() + 1) * 0.5,
                    (mn.getY() + mx.getY() + 1) * 0.5,
                    (mn.getZ() + mx.getZ() + 1) * 0.5);
            sailPose.transformPosition(center);
            Vec3 wind = WindManager.getWind(level, center.x, center.y, center.z).vector();
            double tailwind = Math.max(0.0, wind.x * keel.x + wind.y * keel.y + wind.z * keel.z);
            double across = Math.abs(along);
            target = Mth.clamp(across * tailwind / WIND_REF, 0.0, 1.0);
        }

        Long2DoubleOpenHashMap fills = SMOOTHED.computeIfAbsent(shipId, id -> new Long2DoubleOpenHashMap());
        long key = mn.asLong() * 3 + g.axis().ordinal();
        double smoothed = fills.containsKey(key) ? fills.get(key) : target;
        smoothed += Mth.clamp(target - smoothed, -SMOOTH_STEP, SMOOTH_STEP);
        fills.put(key, smoothed);

        double fill = IDLE_FILL + (1.0 - IDLE_FILL) * smoothed;
        double depth = fill * maxDepth(g.axis(), countX, countY, countZ);

        double minA, dimA, minB, dimB, baseN;
        if (g.axis() == Direction.Axis.X) {
            minA = mn.getY();
            dimA = countY;
            minB = mn.getZ();
            dimB = countZ;
            baseN = (mn.getX() + mx.getX() + 1) * 0.5 + g.supportSign() * SUPPORT_SHIFT;
        } else {
            minA = mn.getX();
            dimA = countX;
            minB = mn.getY();
            dimB = countY;
            baseN = (mn.getZ() + mx.getZ() + 1) * 0.5 + g.supportSign() * SUPPORT_SHIFT;
        }
        return new Panel(g.axis(), minA, dimA, minB, dimB, baseN, bulgeSign, depth);
    }

    private static boolean pushEntity(Entity entity, Vector3d worldPos, Pose3dc entityPose, ShipSails ship) {
        Vector3d local = new Vector3d(worldPos);
        ship.pose().transformPositionInverse(local);

        double r = entity.getBbWidth() / 2.0;
        double h = entity.getBbHeight() / 2.0;

        for (Panel p : ship.panels()) {
            Vector3d push = testPanel(local, p, r, h);
            if (push == null) {
                continue;
            }
            Vector3d worldPush = ship.pose().transformNormal(new Vector3d(push));
            Vector3d applied = worldPush;
            if (entityPose != null) {
                applied = new Vector3d(worldPush);
                entityPose.transformNormalInverse(applied);
            }

            entity.move(MoverType.SHULKER_BOX, new Vec3(applied.x, applied.y, applied.z));

            Vec3 vel = entity.getDeltaMovement();
            Vector3d v = new Vector3d(vel.x, vel.y, vel.z);
            double len = applied.length();
            if (len > 1.0e-6) {
                Vector3d n = new Vector3d(applied).div(len);
                double dot = v.dot(n);
                if (dot < 0) {
                    v.sub(new Vector3d(n).mul(dot));
                }
                entity.setDeltaMovement(v.x, v.y, v.z);
            }
            if (entity instanceof ServerPlayer sp) {
                sp.hurtMarked = true;
            }
            return true;
        }
        return false;
    }

    private static Vector3d testPanel(Vector3d local, Panel p, double r, double h) {
        double a, b, n, marginA, marginB;
        if (p.axis() == Direction.Axis.X) {
            a = local.y;
            b = local.z;
            n = local.x;
            marginA = h;
            marginB = r;
        } else {
            a = local.x;
            b = local.y;
            n = local.z;
            marginA = r;
            marginB = h;
        }

        double u = (a - p.minA()) / p.dimA();
        double v = (b - p.minB()) / p.dimB();
        double mu = marginA / p.dimA();
        double mv = marginB / p.dimB();
        if (u < -mu || u > 1 + mu || v < -mv || v > 1 + mv) {
            return null;
        }

        double anchor = anchor(u, v);
        double surfaceN = p.baseN() + p.bulgeSign() * p.depth() * anchor;
        double dist = n - surfaceN;
        double half = SAIL_HALF_THICK + r;
        if (Math.abs(dist) >= half) {
            return null;
        }
        double side = dist >= 0 ? 1.0 : -1.0;
        double pushN = side * (half - Math.abs(dist));

        if (p.axis() == Direction.Axis.X) {
            return new Vector3d(pushN, 0, 0);
        }
        return new Vector3d(0, 0, pushN);
    }

    private static double anchor(double u, double v) {
        double uc = Mth.clamp(u, 0.0, 1.0);
        double vc = Mth.clamp(v, 0.0, 1.0);
        return Math.sqrt(Math.max(0.0, Math.sin(uc * PI))) * Math.sqrt(Math.max(0.0, Math.sin(vc * PI)));
    }

    private static double maxDepth(Direction.Axis axis, double countX, double countY, double countZ) {
        double planeA, planeB;
        if (axis == Direction.Axis.X) {
            planeA = countY;
            planeB = countZ;
        } else {
            planeA = countX;
            planeB = countY;
        }
        return Mth.clamp(DEPTH_PER_BLOCK * Math.sqrt(planeA * planeB), DEPTH_MIN, DEPTH_MAX);
    }

    private static Vector3d keelOf(ServerLevel level, ServerSubLevelContainer container,
                                  Iterable<ServerSubLevel> subs, ServerSubLevel ship, long gameTime) {
        SubLevel root = BoatClassifier.rootOf(level, subs, ship, gameTime);
        if (root == null || root.getPlot() == null) {
            return null;
        }
        BoundingBox3ic bb = root.getPlot().getBoundingBox();
        List<SailGroup> rootSails = SailWindRegistry.getSails(root, gameTime);
        Vec3 rudder = SailWindRegistry.getRudder(root.getUniqueId());
        Vec3 center = new Vec3((bb.minX() + bb.maxX()) * 0.5, (bb.minY() + bb.maxY()) * 0.5, (bb.minZ() + bb.maxZ()) * 0.5);
        return SailForce.forward(root.logicalPose().orientation(), rudder, center,
                bb.maxX() - bb.minX(), bb.maxZ() - bb.minZ(), rootSails);
    }

    private static AABB localBoxToWorld(Pose3dc pose, double minX, double minY, double minZ,
                                        double maxX, double maxY, double maxZ) {
        double wMinX = Double.MAX_VALUE, wMinY = Double.MAX_VALUE, wMinZ = Double.MAX_VALUE;
        double wMaxX = -Double.MAX_VALUE, wMaxY = -Double.MAX_VALUE, wMaxZ = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? minX : maxX;
            double y = (i & 2) == 0 ? minY : maxY;
            double z = (i & 4) == 0 ? minZ : maxZ;
            Vector3d corner = new Vector3d(x, y, z);
            pose.transformPosition(corner);
            wMinX = Math.min(wMinX, corner.x);
            wMinY = Math.min(wMinY, corner.y);
            wMinZ = Math.min(wMinZ, corner.z);
            wMaxX = Math.max(wMaxX, corner.x);
            wMaxY = Math.max(wMaxY, corner.y);
            wMaxZ = Math.max(wMaxZ, corner.z);
        }
        return new AABB(wMinX, wMinY, wMinZ, wMaxX, wMaxY, wMaxZ);
    }

    public static void clearAll() {
        SMOOTHED.clear();
    }
}
