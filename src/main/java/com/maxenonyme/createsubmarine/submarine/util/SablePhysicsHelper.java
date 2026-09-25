package com.maxenonyme.createsubmarine.submarine.util;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public class SablePhysicsHelper {

    private static final double DEFAULT_MASS = 1000.0;

    private static final Vector3d ZERO_VEC = new Vector3d(0, 0, 0);

    public static Object getHandle(SubLevelAccess sub) {
        if (!(sub instanceof ServerSubLevel server))
            return null;
        RigidBodyHandle handle = RigidBodyHandle.of(server);
        return handle != null && handle.isValid() ? handle : null;
    }

    public static Vector3dc getVelocity(SubLevelAccess sub) {
        return getVelocity(getHandle(sub));
    }

    public static Vector3dc getVelocity(Object handle) {
        return handle instanceof RigidBodyHandle h ? h.getLinearVelocity(new Vector3d()) : null;
    }

    public static Vector3dc getAngularVelocity(Object handle) {
        return handle instanceof RigidBodyHandle h ? h.getAngularVelocity(new Vector3d()) : null;
    }

    public static void wakeUp(Object handle) {
        if (handle instanceof RigidBodyHandle h)
            h.applyLinearAndAngularImpulse(ZERO_VEC, ZERO_VEC, true);
    }

    public static void setAsleep(Object handle, boolean asleep) {
        if (asleep) return;
        wakeUp(handle);
    }

    public static void applyLinearImpulse(Object handle, Vector3d force) {
        if (handle instanceof RigidBodyHandle h)
            h.applyLinearImpulse(force);
    }

    public static void addLinearVelocity(Object handle, Vector3d velocity) {
        if (handle instanceof RigidBodyHandle h)
            h.addLinearAndAngularVelocity(velocity, ZERO_VEC);
    }

    public static void addAngularVelocity(Object handle, Vector3d velocity) {
        if (handle instanceof RigidBodyHandle h)
            h.addLinearAndAngularVelocity(ZERO_VEC, velocity);
    }

    public static void applyAngularImpulse(Object handle, Vector3d torque) {
        if (handle instanceof RigidBodyHandle h)
            h.applyAngularImpulse(torque);
    }

    public static boolean applyImpulseAtPoint(Object handle, Vector3d pointLocal, Vector3d impulseLocal) {
        if (!(handle instanceof RigidBodyHandle h))
            return false;
        h.applyImpulseAtPoint(pointLocal, impulseLocal);
        return true;
    }

    public static double readMass(SubLevelAccess sub) {
        if (!(sub instanceof ServerSubLevel server))
            return DEFAULT_MASS;
        MassData mass = server.getMassTracker();
        return mass == null ? DEFAULT_MASS : mass.getMass();
    }
}
