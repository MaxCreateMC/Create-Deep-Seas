package com.maxenonyme.highseas.sail;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import com.maxenonyme.highseas.config.HighSeasConfig;

public final class SailForce {
    private SailForce() {
    }

    public static Vector3d forward(Quaterniondc orientation, Vec3 rudderLocal, Vec3 centerLocal,
                                   int spanX, int spanZ, List<SailGroup> sails) {
        if (rudderLocal != null && centerLocal != null) {
            Vector3d fwd = new Vector3d(centerLocal.x - rudderLocal.x, 0.0, centerLocal.z - rudderLocal.z);
            orientation.transform(fwd);
            fwd.y = 0;
            if (fwd.lengthSquared() >= 1.0e-6) {
                fwd.normalize();
                return fwd;
            }
        }
        return keelForward(orientation, spanX, spanZ, sails);
    }

    public static Vector3d sailForward(Quaterniondc orientation, List<SailGroup> sails) {
        Vector3d dir = new Vector3d();
        Vector3d tmp = new Vector3d();
        for (SailGroup group : sails) {
            if (group.axis() == Direction.Axis.Y) {
                continue;
            }
            Vec3 ln = group.localNormal();
            tmp.set(ln.x, ln.y, ln.z);
            orientation.transform(tmp);
            tmp.y = 0;
            if (tmp.lengthSquared() < 1.0e-9) {
                continue;
            }
            tmp.normalize();
            dir.add(tmp.mul(group.area()));
        }
        if (dir.lengthSquared() < 1.0e-9) {
            return null;
        }
        dir.y = 0;
        dir.normalize();
        return dir;
    }

    public static Vector3d keelForward(Quaterniondc orientation, int spanX, int spanZ, List<SailGroup> sails) {
        if (spanX == spanZ) {
            return sailForward(orientation, sails);
        }
        Vector3d keel = spanX >= spanZ ? new Vector3d(1, 0, 0) : new Vector3d(0, 0, 1);
        orientation.transform(keel);
        keel.y = 0;
        if (keel.lengthSquared() < 1.0e-9) {
            return null;
        }
        keel.normalize();

        double sign = 0;
        Vector3d tmp = new Vector3d();
        for (SailGroup group : sails) {
            if (group.axis() == Direction.Axis.Y) {
                continue;
            }
            Vec3 ln = group.localNormal();
            tmp.set(ln.x, ln.y, ln.z);
            orientation.transform(tmp);
            sign += (tmp.x * keel.x + tmp.z * keel.z) * group.area();
        }
        if (sign < 0) {
            keel.negate();
        }
        return keel;
    }

    private static final double[] SQUARE_AT = { 0.0, 45.0, 90.0, 120.0, 180.0 };
    private static final double[] SQUARE = { 1.0, 0.8, 0.3, 0.0, 0.0 };
    private static final double[] LATEEN_AT = { 0.0, 45.0, 90.0, 135.0, 140.0, 180.0 };
    private static final double[] LATEEN = { 0.6, 0.9, 1.0, 0.6, 0.0, 0.0 };

    public static double efficiency(Vec3 wind, double nx, double nz, Vector3dc keel) {
        double upwind = HighSeasConfig.sailUpwindEfficiency;
        double windLen = Math.sqrt(wind.x * wind.x + wind.z * wind.z);
        double normalLen = Math.sqrt(nx * nx + nz * nz);
        if (windLen < 1.0e-6 || normalLen < 1.0e-6) {
            return upwind;
        }
        double cos = Mth.clamp((wind.x * keel.x() + wind.z * keel.z()) / windLen, -1.0, 1.0);
        double angle = Math.toDegrees(Math.acos(cos));
        double across = (nx * keel.x() + nz * keel.z()) / normalLen;
        double square = across * across;
        double eff = square * curve(SQUARE_AT, SQUARE, angle) + (1.0 - square) * curve(LATEEN_AT, LATEEN, angle);
        return upwind + (1.0 - upwind) * eff;
    }


    public static double facing(Vec3 wind, double nx, double nz) {
        double windLen = Math.sqrt(wind.x * wind.x + wind.z * wind.z);
        double normalLen = Math.sqrt(nx * nx + nz * nz);
        if (windLen < 1.0e-6 || normalLen < 1.0e-6) {
            return 0.0;
        }
        return Math.abs(wind.x * nx + wind.z * nz) / (windLen * normalLen);
    }

    private static double curve(double[] at, double[] values, double x) {
        for (int i = 1; i < at.length; i++) {
            if (x <= at[i]) {
                double f = (x - at[i - 1]) / (at[i] - at[i - 1]);
                return values[i - 1] + (values[i] - values[i - 1]) * f;
            }
        }
        return values[values.length - 1];
    }

    public static double windFactor(Vec3 wind) {
        return Mth.clamp(0.5 + 0.5 * wind.length(), 0.5, 1.3);
    }
}
