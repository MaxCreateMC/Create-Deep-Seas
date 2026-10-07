package com.maxenonyme.highseas;

import com.maxenonyme.highseas.config.HighSeasConfig;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.compartment.FloodSystem;
import com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BoatBuoyancySystem {
    private BoatBuoyancySystem() {
    }

    private static final double LIFT_PER_CELL = 1.5;
    private static final double DRAG_FORWARD_LINEAR = 0.25;
    private static final double DRAG_FORWARD_QUADRATIC = 0.055;
    private static final double DRAG_LATERAL = 12.0;
    private static final double DRAG_VERTICAL = 2.2;
    private static final double ANG_DAMP_TIP = 6.0;
    private static final double ANG_DAMP_YAW = 0.6;
    private static final double WET_REFERENCE = 8.0;
    private static final double SURFACE_REACH = 24.0;

    private record Hull(double[] cells, int count, double surfaceY, double mass, boolean forwardIsX, double length,
            List<CompartmentDetector.Component> comps, int water) {
    }

    private static final Map<UUID, Hull> HULLS = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> WET = new ConcurrentHashMap<>();

    public static void onServerTick(ServerTickEvent.Post event) {
        Map<UUID, SubLevel> boats = BoatManager.boatSubs();
        if (boats.isEmpty()) {
            HULLS.clear();
            WET.clear();
            return;
        }
        HULLS.keySet().removeIf(id -> !boats.containsKey(id));
        WET.keySet().retainAll(HULLS.keySet());
        for (ServerLevel level : event.getServer().getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) {
                continue;
            }
            for (SubLevel sub : container.getAllSubLevels()) {
                UUID id = sub.getUniqueId();
                if (!boats.containsKey(id)) {
                    continue;
                }
                Hull hull = scan(level, sub, id);
                if (hull == null) {
                    HULLS.remove(id);
                    WET.remove(id);
                } else {
                    HULLS.put(id, hull);
                }
            }
        }
    }

    private static Hull scan(ServerLevel level, SubLevel sub, UUID id) {
        List<CompartmentDetector.Component> comps = CompartmentTracker.getCompartments(id);
        if (comps.isEmpty()) {
            return null;
        }
        Pose3dc pose = sub.logicalPose();
        Vector3dc origin = pose.position();
        double surface = surfaceNear(level, origin.x(), origin.y(), origin.z());

        double mass = Math.max(1.0, SablePhysicsHelper.readMass(sub));
        Set<BlockPos> sunken = CompartmentTracker.sunkenAnchors(id);
        int water = 31 * FloodSystem.waterVersion(id) + sunken.hashCode();
        Hull prev = HULLS.get(id);
        if (prev != null && prev.comps() == comps && prev.water() == water)
            return new Hull(prev.cells(), prev.count(), surface, mass, prev.forwardIsX(), prev.length(), comps, water);

        Set<BlockPos> flooded = FloodSystem.soakedCells(id);
        int count = 0;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (CompartmentDetector.Component c : comps) {
            if (!c.sealed() || sunken.contains(c.anchor()))
                continue;
            for (BlockPos cell : c.internal()) {
                minX = Math.min(minX, cell.getX());
                maxX = Math.max(maxX, cell.getX());
                minZ = Math.min(minZ, cell.getZ());
                maxZ = Math.max(maxZ, cell.getZ());
                if (!flooded.contains(cell))
                    count++;
            }
        }
        if (count == 0) {
            return null;
        }

        double[] cells = new double[count * 3];
        int k = 0;
        for (CompartmentDetector.Component c : comps) {
            if (!c.sealed() || sunken.contains(c.anchor()))
                continue;
            for (BlockPos cell : c.internal()) {
                if (flooded.contains(cell))
                    continue;
                cells[k++] = cell.getX() + 0.5;
                cells[k++] = cell.getY() + 0.5;
                cells[k++] = cell.getZ() + 0.5;
            }
        }

        boolean forwardIsX = true;
        if (maxX - minX != maxZ - minZ) {
            forwardIsX = maxX - minX > maxZ - minZ;
        } else if (sub.getPlot() != null) {
            BoundingBox3ic b = sub.getPlot().getBoundingBox();
            forwardIsX = (b.maxX() - b.minX()) >= (b.maxZ() - b.minZ());
        }
        double length = Math.max(maxX - minX, maxZ - minZ) + 3;
        return new Hull(cells, k / 3, surface, mass, forwardIsX, length, comps, water);
    }

    public static double surfaceNear(ServerLevel level, double x, double y, double z) {
        int cx = Mth.floor(x);
        int cz = Mth.floor(z);
        int top = Mth.floor(y + SURFACE_REACH);
        int bottom = Mth.floor(y - SURFACE_REACH);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int cy = top; cy >= bottom; cy--) {
            p.set(cx, cy, cz);
            if (CompartmentTracker.realFluidState(level, p).is(FluidTags.WATER)) {
                return cy + 1.0;
            }
        }
        return Double.NEGATIVE_INFINITY;
    }

    public static double surfaceFor(UUID id) {
        Hull hull = HULLS.get(id);
        return hull == null ? Double.NEGATIVE_INFINITY : hull.surfaceY();
    }

    public static boolean afloat(UUID id) {
        Double wet = WET.get(id);
        return wet != null && wet > 0.0;
    }

    public static double keelLength(UUID id) {
        Hull hull = HULLS.get(id);
        return hull == null ? 0.0 : hull.length();
    }

    public static double forwardDrag(UUID id, double speed) {
        Double wet = WET.get(id);
        if (wet == null)
            return 0.0;
        return (DRAG_FORWARD_LINEAR + DRAG_FORWARD_QUADRATIC * Math.abs(speed)) * speed * wet;
    }

    public static void onPhysicsTick(ForgeSablePrePhysicsTickEvent event) {
        if (HULLS.isEmpty()) {
            return;
        }
        ServerLevel level = event.getPhysicsSystem().getLevel();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        double g = Math.abs(DimensionPhysicsData.getGravity(level).y);
        double dt = event.getTimeStep();
        for (SubLevel raw : container.getAllSubLevels()) {
            if (!(raw instanceof ServerSubLevel sub)) {
                continue;
            }
            Hull hull = HULLS.get(sub.getUniqueId());
            if (hull == null || hull.surfaceY() == Double.NEGATIVE_INFINITY) {
                continue;
            }
            RigidBodyHandle handle = event.getPhysicsSystem().getPhysicsHandle(sub);
            if (handle == null || !handle.isValid()) {
                continue;
            }
            apply(sub, handle, hull, g, dt);
        }
    }

    private static void apply(ServerSubLevel sub, RigidBodyHandle handle, Hull hull, double g, double dt) {
        Pose3dc pose = sub.logicalPose();
        Quaterniond inverse = pose.orientation().conjugate(new Quaterniond());

        Vector3d w = new Vector3d();
        Vector3d centre = new Vector3d();
        double base = pose.transformPosition(w.set(0.0, 0.0, 0.0)).y;
        double ax = pose.transformPosition(w.set(1.0, 0.0, 0.0)).y - base;
        double ay = pose.transformPosition(w.set(0.0, 1.0, 0.0)).y - base;
        double az = pose.transformPosition(w.set(0.0, 0.0, 1.0)).y - base;
        double waterline = hull.surfaceY() + 0.5 - base;
        double volume = 0.0;
        double[] cells = hull.cells();
        for (int i = 0, k = 0; i < hull.count(); i++, k += 3) {
            double x = cells[k], y = cells[k + 1], z = cells[k + 2];
            double immersion = Mth.clamp(waterline - (ax * x + ay * y + az * z), 0.0, 1.0);
            if (immersion <= 0.0) {
                continue;
            }
            volume += immersion;
            centre.add(x * immersion, y * immersion, z * immersion);
        }
        if (volume < 1.0e-4) {
            WET.remove(sub.getUniqueId());
            return;
        }
        centre.div(volume);
        double wet = Math.min(1.0, volume / WET_REFERENCE);
        WET.put(sub.getUniqueId(), wet);

        Vector3d lift = new Vector3d(0.0, volume * LIFT_PER_CELL * g * dt, 0.0);
        inverse.transform(lift);
        handle.applyImpulseAtPoint(centre, lift);

        Vector3dc velocity = handle.getLinearVelocity();
        if (velocity != null) {
            Vector3d local = new Vector3d(velocity.x(), velocity.y(), velocity.z());
            inverse.transform(local);
            double along = hull.forwardIsX() ? local.x : local.z;
            double lateral = hull.forwardIsX() ? local.z : local.x;
            double dAlong = -(DRAG_FORWARD_LINEAR + DRAG_FORWARD_QUADRATIC * Math.abs(along)) * along;
            double dLateral = -DRAG_LATERAL * lateral;
            double dVertical = -DRAG_VERTICAL * local.y;
            Vector3d drag = hull.forwardIsX()
                    ? new Vector3d(dAlong, dVertical, dLateral)
                    : new Vector3d(dLateral, dVertical, dAlong);
            drag.mul(hull.mass() * wet * dt);
            handle.applyLinearImpulse(drag);
        }

        Vector3dc angular = handle.getAngularVelocity();
        if (angular != null) {
            Vector3d local = new Vector3d(angular.x(), angular.y(), angular.z());
            inverse.transform(local);
            Vector3d damp = new Vector3d(
                    -local.x * ANG_DAMP_TIP,
                    -local.y * ANG_DAMP_YAW,
                    -local.z * ANG_DAMP_TIP);
            damp.mul(wet * dt);
            pose.orientation().transform(damp);
            handle.addLinearAndAngularVelocity(new Vector3d(), damp);
        }

        if (HighSeasConfig.boatSelfRighting > 0.0) {
            Vector3d up = pose.orientation().transform(new Vector3d(0.0, 1.0, 0.0));
            Vector3d spin = new Vector3d(-up.z, 0.0, up.x).mul(HighSeasConfig.boatSelfRighting * wet * dt);
            handle.addLinearAndAngularVelocity(new Vector3d(), spin);
        }
    }

    public static void clearAll() {
        HULLS.clear();
        WET.clear();
    }
}
