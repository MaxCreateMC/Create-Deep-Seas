package com.maxenonyme.highseas;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
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

import java.util.ArrayList;
import java.util.HashMap;
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

    private static final double FILL_PER_TICK = 1.0 / 300.0;
    private static final double DRAIN_PER_TICK = 1.0 / 200.0;

    private record Hull(double[] cells, int count, double surfaceY, double mass, boolean forwardIsX,
            List<CompartmentDetector.Component> comps, Map<BlockPos, Double> fills) {
    }

    private static final Map<UUID, Hull> HULLS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<BlockPos, Double>> WATER_IN = new ConcurrentHashMap<>();

    public static void onServerTick(ServerTickEvent.Post event) {
        Map<UUID, SubLevel> boats = BoatManager.boatSubs();
        if (boats.isEmpty()) {
            HULLS.clear();
            return;
        }
        HULLS.keySet().removeIf(id -> !boats.containsKey(id));
        WATER_IN.keySet().removeIf(id -> !boats.containsKey(id));
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

        Set<BlockPos> flooded = CompartmentTracker.floodedAnchors(id);
        Map<BlockPos, Double> previous = WATER_IN.get(id);
        Map<BlockPos, Double> levels = new HashMap<>();
        int count = 0;
        for (CompartmentDetector.Component c : comps) {
            if (!c.sealed()) {
                continue;
            }
            BlockPos anchor = c.anchor();
            double filled = previous == null || anchor == null ? 0.0 : previous.getOrDefault(anchor, 0.0);
            if (anchor != null && flooded.contains(anchor)) {
                filled = surface != Double.NEGATIVE_INFINITY && topWorldY(pose, c) < surface
                        ? 1.0
                        : Math.min(1.0, filled + FILL_PER_TICK);
            } else {
                filled = Math.max(0.0, filled - DRAIN_PER_TICK);
            }
            if (anchor != null && filled > 0.0) {
                levels.put(anchor, filled);
            }
            if (filled < 1.0)
                count += c.internal().size();
        }
        if (levels.isEmpty()) {
            WATER_IN.remove(id);
        } else {
            WATER_IN.put(id, levels);
        }
        if (count == 0) {
            return null;
        }

        double mass = Math.max(1.0, SablePhysicsHelper.readMass(sub));
        Hull prev = HULLS.get(id);
        if (prev != null && prev.comps() == comps && prev.fills().equals(levels))
            return new Hull(prev.cells(), prev.count(), surface, mass, prev.forwardIsX(), comps, levels);

        double[] cells = new double[count * 4];
        int k = 0;
        for (CompartmentDetector.Component c : comps) {
            if (!c.sealed())
                continue;
            double lift = 1.0 - (c.anchor() == null ? 0.0 : levels.getOrDefault(c.anchor(), 0.0));
            if (lift <= 0.0)
                continue;
            for (BlockPos cell : c.internal()) {
                cells[k++] = cell.getX() + 0.5;
                cells[k++] = cell.getY() + 0.5;
                cells[k++] = cell.getZ() + 0.5;
                cells[k++] = lift;
            }
        }

        boolean forwardIsX = true;
        if (sub.getPlot() != null) {
            BoundingBox3ic b = sub.getPlot().getBoundingBox();
            forwardIsX = (b.maxX() - b.minX()) >= (b.maxZ() - b.minZ());
        }
        return new Hull(cells, k / 4, surface, mass, forwardIsX, comps, levels);
    }

    private static double topWorldY(Pose3dc pose, CompartmentDetector.Component c) {
        int top = Integer.MIN_VALUE;
        for (BlockPos p : c.internal()) {
            if (p.getY() > top) {
                top = p.getY();
            }
        }
        if (top == Integer.MIN_VALUE) {
            return Double.NEGATIVE_INFINITY;
        }
        Vector3d w = new Vector3d();
        double best = Double.NEGATIVE_INFINITY;
        for (BlockPos p : c.internal()) {
            if (p.getY() != top) {
                continue;
            }
            w.set(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5);
            pose.transformPosition(w);
            if (w.y > best) {
                best = w.y;
            }
        }
        return best;
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
        double volume = 0.0;
        double[] cells = hull.cells();
        for (int i = 0, k = 0; i < hull.count(); i++, k += 4) {
            double x = cells[k], y = cells[k + 1], z = cells[k + 2];
            pose.transformPosition(w.set(x, y, z));
            double immersion = Mth.clamp(hull.surfaceY() - w.y + 0.5, 0.0, 1.0) * cells[k + 3];
            if (immersion <= 0.0) {
                continue;
            }
            volume += immersion;
            centre.add(x * immersion, y * immersion, z * immersion);
        }
        if (volume < 1.0e-4) {
            return;
        }
        centre.div(volume);
        double wet = Math.min(1.0, volume / WET_REFERENCE);

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
    }

    public static void clearAll() {
        HULLS.clear();
        WATER_IN.clear();
    }
}
