package com.maxenonyme.highseas.sail;

import com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper;
import com.maxenonyme.highseas.wind.WindConfig;
import com.maxenonyme.highseas.wind.WindManager;
import com.maxenonyme.highseas.wind.WindSample;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import dev.ryanhcode.sable.api.SubLevelHelper;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import com.maxenonyme.highseas.BoatBuoyancySystem;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import com.maxenonyme.highseas.config.HighSeasConfig;
import dev.eriksonn.aeronautics.content.particle.GustParticleData;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SailWindSystem {
    private SailWindSystem() {
    }

    private static final double SERVER_STEP = 0.05;
    private static final double MAX_ACCEL = 3.0;
    private static final double REFERENCE_LENGTH = 8.0;
    private static final double SAIL_DRAG = 1.75;
    private static final double MAX_HIDDEN_DRAG = 8.0;
    private static final double HIDDEN_DRAG_BLEND = 0.08;

    private static final Map<UUID, Trim> TRIMS = new HashMap<>();
    private static final Map<UUID, Double> HEADING = new HashMap<>();

    private static final class Trim {
        double speed;
        double push;
        double modelled;
        double hidden;
    }

    private static final class Drive {
        final Vector3d keel;
        final boolean afloat;
        final Vector3d drift = new Vector3d();
        double canvas;
        double weighted;
        double dragXX, dragXZ, dragZZ;

        Drive(Vector3d keel, boolean afloat) {
            this.keel = keel;
            this.afloat = afloat;
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        long gameTime = event.getServer().getTickCount();
        Map<SubLevel, Drive> drives = new IdentityHashMap<>();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) {
                continue;
            }
            var subs = container.getAllSubLevels();
            for (ServerSubLevel ship : subs) {
                SubLevel root = BoatClassifier.rootOf(level, subs, ship, gameTime);
                if (root == null) {
                    continue;
                }
                collect(level, ship, root, gameTime, drives);
            }
        }
        drives.forEach(SailWindSystem::sail);
        if (!TRIMS.isEmpty()) {
            Set<UUID> sailing = new HashSet<>();
            for (SubLevel root : drives.keySet())
                sailing.add(root.getUniqueId());
            TRIMS.keySet().retainAll(sailing);
            HEADING.keySet().retainAll(sailing);
        }
    }

    static Vector3d keel(SubLevel root, long gameTime, Vector3dc velocity) {
        BoundingBox3ic bb = root.getPlot().getBoundingBox();
        Quaterniondc orient = root.logicalPose().orientation();
        List<SailGroup> rootSails = SailWindRegistry.getSails(root, gameTime);
        Vec3 rudder = SailWindRegistry.getRudder(root.getUniqueId());
        if (rudder == null)
            rudder = chainRudder(root);
        if (rudder != null) {
            Vec3 center = new Vec3((bb.minX() + bb.maxX()) * 0.5, (bb.minY() + bb.maxY()) * 0.5, (bb.minZ() + bb.maxZ()) * 0.5);
            Vector3d fwd = SailForce.forward(orient, rudder, center, bb.maxX() - bb.minX(), bb.maxZ() - bb.minZ(), rootSails);
            if (fwd != null)
                return fwd;
        }
        Vector3d axis = bb.maxX() - bb.minX() >= bb.maxZ() - bb.minZ() ? new Vector3d(1, 0, 0) : new Vector3d(0, 0, 1);
        orient.transform(axis);
        axis.y = 0.0;
        if (axis.lengthSquared() < 1.0e-9)
            return null;
        axis.normalize();
        double sign = sailPush(root, axis, gameTime);
        UUID id = root.getUniqueId();
        if (Math.abs(sign) < 1.0e-6) {
            Double last = HEADING.get(id);
            if (last != null)
                sign = last;
            else if (velocity != null)
                sign = velocity.x() * axis.x + velocity.z() * axis.z;
        }
        if (Math.abs(sign) >= 1.0e-6)
            HEADING.put(id, Math.signum(sign));
        if (sign < 0.0)
            axis.negate();
        return axis;
    }

    private static double sailPush(SubLevel root, Vector3d axis, long gameTime) {
        Collection<SubLevel> chain;
        try {
            chain = SubLevelHelper.getConnectedChain(root);
        } catch (Exception e) {
            chain = List.of(root);
        }
        double sign = 0.0;
        for (SubLevel s : chain) {
            Quaterniondc orient = s.logicalPose().orientation();
            for (SailGroup g : SailWindRegistry.getSails(s, gameTime)) {
                if (g.supportSign() == 0 || g.axis() == Direction.Axis.Y)
                    continue;
                Vec3 ln = g.localNormal();
                Vector3d push = orient.transform(new Vector3d(ln.x, ln.y, ln.z).mul(-g.supportSign()));
                sign += (push.x * axis.x + push.z * axis.z) * g.area();
            }
        }
        return sign;
    }

    private static Vec3 chainRudder(SubLevel root) {
        Collection<SubLevel> chain;
        try {
            chain = SubLevelHelper.getConnectedChain(root);
        } catch (Exception e) {
            return null;
        }
        for (SubLevel s : chain) {
            if (s == root)
                continue;
            Vec3 r = SailWindRegistry.getRudder(s.getUniqueId());
            if (r == null)
                continue;
            Vector3d w = s.logicalPose().transformPosition(new Vector3d(r.x, r.y, r.z));
            root.logicalPose().transformPositionInverse(w);
            return new Vec3(w.x, w.y, w.z);
        }
        return null;
    }

    private static boolean afloat(ServerLevel level, SubLevel root) {
        if (BoatBuoyancySystem.afloat(root.getUniqueId()))
            return true;
        BoundingBox3ic bb = root.getPlot().getBoundingBox();
        Pose3dc pose = root.logicalPose();
        double x0 = bb.minX() + 0.5, x1 = bb.maxX() + 0.5, xm = (x0 + x1) * 0.5;
        double z0 = bb.minZ() + 0.5, z1 = bb.maxZ() + 0.5, zm = (z0 + z1) * 0.5;
        double[][] probes = { { xm, zm }, { x0, z0 }, { x0, z1 }, { x1, z0 }, { x1, z1 }, { xm, z0 }, { xm, z1 }, { x0, zm }, { x1, zm } };
        Vector3d p = new Vector3d();
        for (int dy = 0; dy <= 1; dy++) {
            for (double[] probe : probes) {
                pose.transformPosition(p.set(probe[0], bb.minY() + 0.5 + dy, probe[1]));
                if (!level.getFluidState(BlockPos.containing(p.x, p.y, p.z)).isEmpty())
                    return true;
            }
        }
        return false;
    }

    private static void pull(ServerLevel level, ServerSubLevel source, Pose3dc sailPose, SailGroup group,
            double factor, double speedRatio, Drive drive) {
        if (!BoatClassifier.inAir(level, source, group.localCenter()))
            return;
        Vec3 c = group.localCenter();
        Vector3d worldCenter = sailPose.transformPosition(new Vector3d(c.x, c.y, c.z));
        Vec3 ln = group.localNormal();
        Vector3d normal = sailPose.orientation().transform(new Vector3d(ln.x, ln.y, ln.z));
        normal.y = 0.0;
        if (normal.lengthSquared() < 1.0e-9)
            return;
        normal.normalize();
        Vec3 wind = WindManager.getWind(level, worldCenter.x, worldCenter.y, worldCenter.z).vector();
        double canvas = group.area() * factor;
        double eff = drive.afloat ? SailForce.efficiency(wind, normal.x, normal.z, drive.keel)
                : SailForce.facing(wind, normal.x, normal.z);
        drive.canvas += canvas;
        drive.weighted += canvas * eff * SailForce.windFactor(wind);
        drive.drift.add(wind.x * canvas * eff, 0.0, wind.z * canvas * eff);
        if (canvas > 0.1 && speedRatio > 0.05 && level.random.nextFloat() < 0.4f * speedRatio) {
            Vector3f dir = new Vector3f((float) wind.x, (float) wind.y, (float) wind.z);
            if (dir.lengthSquared() > 1.0e-6f) {
                dir.normalize();
                Quaternionf gust = new Quaternionf().rotationTo(new Vector3f(0.0f, 1.0f, 0.0f), dir);
                level.sendParticles(new GustParticleData(gust), worldCenter.x, worldCenter.y, worldCenter.z,
                        (int) Math.ceil(4 * speedRatio), 3.0, 3.0, 3.0, 0.0);
            }
        }
    }

    private static void collect(ServerLevel parentLevel, ServerSubLevel sailSource, SubLevel root, long gameTime,
            Map<SubLevel, Drive> drives) {
        if (sailSource.getPlot() == null || root.getPlot() == null) {
            return;
        }
        List<SailGroup> sails = SailWindRegistry.getSails(sailSource, gameTime);
        if (sails.isEmpty()) {
            return;
        }

        Object handle = SablePhysicsHelper.getHandle(root);
        if (handle == null) {
            return;
        }
        Vector3dc velocity = SablePhysicsHelper.getVelocity(handle);
        Drive drive = drives.get(root);
        if (drive == null) {
            Vector3d keel = keel(root, gameTime, velocity);
            if (keel == null) {
                return;
            }
            drive = new Drive(keel, afloat(parentLevel, root));
            drives.put(root, drive);
        }

        Pose3dc sailPose = sailSource.logicalPose();
        double forwardSpeed = 0.0;
        if (velocity != null) {
            forwardSpeed = velocity.x() * drive.keel.x + velocity.z() * drive.keel.z;
        }
        double speedRatio = Mth.clamp(Math.abs(forwardSpeed) / WindConfig.SAIL_SPEED_REFERENCE, 0.0, 1.0);

        for (SailGroup group : sails) {
            if (group.axis() == Direction.Axis.Y)
                continue;
            Vec3 c = group.localCenter();
            double drag = group.area() * DimensionPhysicsData.getAirPressure(parentLevel,
                    sailPose.transformPosition(new Vector3d(c.x, c.y, c.z)));
            Vec3 ln = group.localNormal();
            Vector3d normal = sailPose.orientation().transform(new Vector3d(ln.x, ln.y, ln.z));
            drive.dragXX += drag * normal.x * normal.x;
            drive.dragXZ += drag * normal.x * normal.z;
            drive.dragZZ += drag * normal.z * normal.z;
        }
        UUID sourceId = sailSource.getUniqueId();
        for (SailGroup group : sails) {
            float reefed = FurlState.amount(sourceId, group.min());
            if (group.axis() == Direction.Axis.Y || reefed >= 0.999f)
                continue;
            double factor = Math.min(1.0, (gameTime - group.startTick()) / 60.0) * (1.0 - reefed);
            pull(parentLevel, sailSource, sailPose, group, factor, speedRatio, drive);
        }
        for (DecayingSail ds : SailWindRegistry.getDecayingSails(sourceId, gameTime)) {
            if (ds.group().axis() == Direction.Axis.Y)
                continue;
            double factor = Math.max(0.0, (60.0 - (gameTime - ds.startTick())) / 60.0);
            pull(parentLevel, sailSource, sailPose, ds.group(), factor, 0.0, drive);
        }
    }

    private static void sail(SubLevel root, Drive drive) {
        if (drive.canvas < 1.0e-6 || root.getPlot() == null) {
            return;
        }
        Object handle = SablePhysicsHelper.getHandle(root);
        if (handle == null) {
            return;
        }

        Vector3d dir = new Vector3d(drive.keel);
        if (!drive.afloat && drive.drift.lengthSquared() > 1.0e-9)
            dir.set(drive.drift).normalize();

        double length = BoatBuoyancySystem.keelLength(root.getUniqueId());
        if (length <= 0.0) {
            BoundingBox3ic bb = root.getPlot().getBoundingBox();
            length = Math.max(bb.maxX() - bb.minX(), bb.maxZ() - bb.minZ()) + 1;
        }
        double hullMass = Math.max(1.0, SablePhysicsHelper.readMass(root));
        double section = Math.cbrt(hullMass * hullMass);

        double hullSpeed = HighSeasConfig.sailHullSpeed * Math.sqrt(length);
        double rigging = Math.min(1.0, Math.sqrt(drive.canvas / (HighSeasConfig.sailCanvasNeeded * section)));
        double target = Math.min(HighSeasConfig.sailMaxSpeed, hullSpeed * rigging * drive.weighted / drive.canvas);

        Vector3dc velocity = SablePhysicsHelper.getVelocity(handle);
        double speed = velocity == null ? 0.0 : velocity.x() * dir.x + velocity.z() * dir.z;
        double response = HighSeasConfig.sailResponseTime * Mth.clamp(Math.sqrt(length / REFERENCE_LENGTH), 0.7, 2.0);
        UUID id = root.getUniqueId();
        double sailDrag = drive.dragXX * dir.x * dir.x + 2.0 * drive.dragXZ * dir.x * dir.z + drive.dragZZ * dir.z * dir.z;
        double drag = BoatBuoyancySystem.forwardDrag(id, speed) + SAIL_DRAG * sailDrag * speed / hullMass;

        Trim trim = TRIMS.get(id);
        if (trim == null) {
            trim = new Trim();
            TRIMS.put(id, trim);
        } else {
            double observed = (speed - trim.speed) / SERVER_STEP;
            double hidden = Mth.clamp(trim.push - trim.modelled - observed, -MAX_HIDDEN_DRAG, MAX_HIDDEN_DRAG);
            trim.hidden = Mth.lerp(HIDDEN_DRAG_BLEND, trim.hidden, hidden);
        }

        double accel = drag + trim.hidden + Math.min((target - speed) / response, MAX_ACCEL);
        trim.speed = speed;
        trim.modelled = drag;
        trim.push = Math.max(0.0, accel);
        if (accel <= 0.0) {
            return;
        }

        double total = accel * hullMass * SERVER_STEP;
        Vector3d forceWorld = new Vector3d(dir.x * total, 0.0, dir.z * total);
        root.logicalPose().orientation().conjugate(new Quaterniond()).transform(forceWorld);
        SablePhysicsHelper.applyLinearImpulse(handle, forceWorld);
    }
}
