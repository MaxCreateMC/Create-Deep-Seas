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
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.UUID;
import com.maxenonyme.highseas.config.HighSeasConfig;
import dev.eriksonn.aeronautics.content.particle.GustParticleData;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SailWindSystem {
    private SailWindSystem() {
    }

    private static final double SERVER_STEP = 0.05;

    public static void onServerTick(ServerTickEvent.Post event) {
        long gameTime = event.getServer().getTickCount();
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
                applyWind(level, ship, root, gameTime);
            }
        }
    }

    private static Vector3d neutral(SailGroup group, Quaterniondc rootOrient) {
        Vec3 ln = group.localNormal();
        Vector3d rest = new Vector3d(ln.x, ln.y, ln.z);
        rootOrient.transform(rest);
        rest.y = 0.0;
        if (rest.lengthSquared() < 1.0e-9) {
            return new Vector3d();
        }
        return rest.normalize();
    }

    private static double bulgeSide(SailGroup group, Vec3 wind, Vector3d worldNormal) {
        if (group.supportSign() != 0) {
            return -group.supportSign();
        }
        double windDotN = wind.x * worldNormal.x + wind.y * worldNormal.y + wind.z * worldNormal.z;
        return windDotN >= 0 ? 1.0 : -1.0;
    }

    private static double pull(ServerLevel level, ServerSubLevel source, Pose3dc sailPose, Quaterniondc rootOrient,
            SailGroup group, double factor, double speedRatio, Vector3d windForward) {
        if (!BoatClassifier.inAir(level, source, group.localCenter()))
            return 0.0;
        Vec3 c = group.localCenter();
        Vector3d worldCenter = sailPose.transformPosition(new Vector3d(c.x, c.y, c.z));
        Vec3 ln = group.localNormal();
        Vector3d worldNormal = sailPose.orientation().transform(new Vector3d(ln.x, ln.y, ln.z));
        if (worldNormal.lengthSquared() < 1.0e-9)
            return 0.0;
        worldNormal.normalize();
        Vec3 wind = WindManager.getWind(level, worldCenter.x, worldCenter.y, worldCenter.z).vector();
        Vector3d rest = neutral(group, rootOrient);
        double side = bulgeSide(group, wind, worldNormal);
        double swing = rest.x * worldNormal.x + rest.z * worldNormal.z;
        double sailPower = SailForce.power(wind, worldNormal.x, worldNormal.y, worldNormal.z,
                rest.x * side, rest.y * side, rest.z * side, group.area());
        windForward.fma(side * group.area() * factor * swing, rest);
        if (Math.abs(sailPower) > 0.1 && speedRatio > 0.05 && level.random.nextFloat() < 0.4f * speedRatio) {
            Vector3f dir = new Vector3f((float) wind.x, (float) wind.y, (float) wind.z);
            if (dir.lengthSquared() > 1.0e-6f) {
                dir.normalize();
                Quaternionf gust = new Quaternionf().rotationTo(new Vector3f(0.0f, 1.0f, 0.0f), dir);
                level.sendParticles(new GustParticleData(gust), worldCenter.x, worldCenter.y, worldCenter.z,
                        (int) Math.ceil(4 * speedRatio), 3.0, 3.0, 3.0, 0.0);
            }
        }
        return sailPower * factor * Math.abs(swing);
    }

    private static void applyWind(ServerLevel parentLevel, ServerSubLevel sailSource, SubLevel root, long gameTime) {
        if (sailSource.getPlot() == null || root.getPlot() == null) {
            return;
        }
        List<SailGroup> sails = SailWindRegistry.getSails(sailSource, gameTime);
        if (sails.isEmpty()) {
            return;
        }

        Pose3dc sailPose = sailSource.logicalPose();
        Quaterniondc sailOrient = sailPose.orientation();

        Pose3dc rootPose = root.logicalPose();
        Quaterniondc rootOrient = rootPose.orientation();
        Vector3d forward = SailForce.sailForward(sailOrient, sails);
        if (forward == null) {
            return;
        }

        Object handle = SablePhysicsHelper.getHandle(root);
        if (handle == null) {
            return;
        }
        Vector3dc velocity = SablePhysicsHelper.getVelocity(handle);
        double forwardSpeed = 0.0;
        if (velocity != null) {
            forwardSpeed = velocity.x() * forward.x + velocity.y() * forward.y + velocity.z() * forward.z;
        }
        double hullMass = Math.max(1.0, SablePhysicsHelper.readMass(root));
        double section = Math.cbrt(hullMass * hullMass);
        double speedRatio = Mth.clamp(Math.abs(forwardSpeed) / WindConfig.SAIL_SPEED_REFERENCE, 0.0, 1.0);

        double power = 0;
        Vector3d windForward = new Vector3d();
        UUID sourceId = sailSource.getUniqueId();
        for (SailGroup group : sails) {
            if (group.axis() == Direction.Axis.Y || FurlState.isFurled(sourceId, group.min()))
                continue;
            double factor = Math.min(1.0, (gameTime - group.startTick()) / 60.0);
            power += pull(parentLevel, sailSource, sailPose, rootOrient, group, factor, speedRatio, windForward);
        }
        for (DecayingSail ds : SailWindRegistry.getDecayingSails(sourceId, gameTime)) {
            if (ds.group().axis() == Direction.Axis.Y)
                continue;
            double factor = Math.max(0.0, (60.0 - (gameTime - ds.startTick())) / 60.0);
            power += pull(parentLevel, sailSource, sailPose, rootOrient, ds.group(), factor, 0.0, windForward);
        }
        if (Math.abs(power) < 1.0e-9) {
            return;
        }

        windForward.y = 0.0;
        Vector3d dir = windForward.lengthSquared() > 1.0e-9 ? windForward.normalize() : forward;

        double accel = power * HighSeasConfig.sailThrust / section;
        double total = accel * hullMass * SERVER_STEP;
        if (Math.abs(total) < 1.0e-9) {
            return;
        }

        Vector3d forceWorld = new Vector3d(dir.x * total, 0.0, dir.z * total);
        rootOrient.conjugate(new Quaterniond()).transform(forceWorld);
        SablePhysicsHelper.applyLinearImpulse(handle, forceWorld);
    }
}
