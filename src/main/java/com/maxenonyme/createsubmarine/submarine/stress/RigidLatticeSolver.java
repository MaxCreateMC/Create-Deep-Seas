package com.maxenonyme.createsubmarine.submarine.stress;

import java.util.Arrays;
import java.util.stream.IntStream;

public class RigidLatticeSolver {
    public static final int NEIGHBORS = 26;
    public static final int[] DX = {
            1, -1, 0, 0, 0, 0,
            1, 1, -1, -1, 0, 0, 0, 0, 1, 1, -1, -1,
            1, 1, 1, 1, -1, -1, -1, -1
    };
    public static final int[] DY = {
            0, 0, 1, -1, 0, 0,
            1, -1, 1, -1, 1, -1, 1, -1, 0, 0, 0, 0,
            1, 1, -1, -1, 1, 1, -1, -1
    };
    public static final int[] DZ = {
            0, 0, 0, 0, 1, -1,
            0, 0, 0, 0, 1, 1, -1, -1, 1, -1, 1, -1,
            1, -1, 1, -1, 1, -1, 1, -1
    };
    public static final double[] INV_DIST = new double[NEIGHBORS];
    static final double[][] DIR = new double[NEIGHBORS][3];
    static final double[][] T1 = new double[NEIGHBORS][3];
    static final double[][] T2 = new double[NEIGHBORS][3];

    public static final double RHO_G = 10000.0;
    public static final double REFERENCE_DEPTH = 100.0;
    private static final int PARALLEL_FROM = 1500;
    private static final int MAX_ITERATIONS = 6000;
    private static final double TOLERANCE = 1e-12;
    private static final double TIKHONOV = 1e-6;

    static {
        for (int d = 0; d < NEIGHBORS; d++) {
            double len = Math.sqrt(DX[d] * DX[d] + DY[d] * DY[d] + DZ[d] * DZ[d]);
            INV_DIST[d] = 1.0 / len;
            double nx = DX[d] / len, ny = DY[d] / len, nz = DZ[d] / len;
            DIR[d][0] = nx;
            DIR[d][1] = ny;
            DIR[d][2] = nz;
            double vx, vy, vz;
            double ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
            if (ax <= ay && ax <= az) {
                vx = 1; vy = 0; vz = 0;
            } else if (ay <= az) {
                vx = 0; vy = 1; vz = 0;
            } else {
                vx = 0; vy = 0; vz = 1;
            }
            double t1x = ny * vz - nz * vy, t1y = nz * vx - nx * vz, t1z = nx * vy - ny * vx;
            double tl = Math.sqrt(t1x * t1x + t1y * t1y + t1z * t1z);
            t1x /= tl;
            t1y /= tl;
            t1z /= tl;
            T1[d][0] = t1x;
            T1[d][1] = t1y;
            T1[d][2] = t1z;
            T2[d][0] = ny * t1z - nz * t1y;
            T2[d][1] = nz * t1x - nx * t1z;
            T2[d][2] = nx * t1y - ny * t1x;
        }
    }

    final int n;
    final int[] x, y, z;
    final double[] yield;
    final double[] nu;
    final double[] volume;
    final int[][] neighbors;
    final double[][] stiffness;
    final boolean[][] wet;
    final double[] u;
    private final double meanStiffness;
    private double[] precon;
    public int iterations;

    public RigidLatticeSolver(int n, int[] x, int[] y, int[] z, double[] yield, double[] nu, double[] volume,
            int[][] neighbors, double[][] stiffness, boolean[][] wet) {
        this.n = n;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yield = yield;
        this.nu = nu;
        this.volume = volume;
        this.neighbors = neighbors;
        this.stiffness = stiffness;
        this.wet = wet;
        this.u = new double[6 * n];
        double sum = 0;
        int count = 0;
        for (int i = 0; i < n; i++)
            for (int d = 0; d < NEIGHBORS; d++)
                if (neighbors[i][d] >= 0) {
                    sum += stiffness[i][d];
                    count++;
                }
        this.meanStiffness = count > 0 ? sum / count : 1.0;
    }

    public double[] crushDepths() {
        double[] b = new double[6 * n];
        if (!load(b))
            return null;
        solve(b);
        removeRigidModes();
        double[] crush = new double[n];
        for (int i = 0; i < n; i++) {
            double vm = vonMises(i);
            crush[i] = vm <= 1e-30 ? Double.POSITIVE_INFINITY : REFERENCE_DEPTH * yield[i] / vm;
        }
        return crush;
    }

    private boolean load(double[] b) {
        boolean any = false;
        double pressure = RHO_G * REFERENCE_DEPTH;
        for (int i = 0; i < n; i++) {
            for (int d = 0; d < 6; d++) {
                if (neighbors[i][d] >= 0 || !wet[i][d])
                    continue;
                double sign = d % 2 == 0 ? -1.0 : 1.0;
                double f = -sign * pressure * volume[i];
                b[6 * i + d / 2] += f;
                any = true;
            }
        }
        return any;
    }

    private void apply(double[] v, double[] out) {
        Arrays.fill(out, 0.0);
        if (n >= PARALLEL_FROM)
            IntStream.range(0, n).parallel().forEach(i -> accumulate(i, v, out));
        else
            for (int i = 0; i < n; i++)
                accumulate(i, v, out);
    }

    private void accumulate(int i, double[] v, double[] out) {
        int i6 = 6 * i;
        double nuI = nu[i];
        for (int d = 0; d < NEIGHBORS; d++) {
            int j = neighbors[i][d];
            if (j < 0)
                continue;
            int j6 = 6 * j;
            double k = stiffness[i][d];
            double nx = DIR[d][0], ny = DIR[d][1], nz = DIR[d][2];
            double h = 0.5 / INV_DIST[d];

            double wIx = v[i6 + 3], wIy = v[i6 + 4], wIz = v[i6 + 5];
            double wJx = v[j6 + 3], wJy = v[j6 + 4], wJz = v[j6 + 5];
            double uax = v[i6] + wIy * (h * nz) - wIz * (h * ny);
            double uay = v[i6 + 1] + wIz * (h * nx) - wIx * (h * nz);
            double uaz = v[i6 + 2] + wIx * (h * ny) - wIy * (h * nx);
            double ubx = v[j6] - wJy * (h * nz) + wJz * (h * ny);
            double uby = v[j6 + 1] - wJz * (h * nx) + wJx * (h * nz);
            double ubz = v[j6 + 2] - wJx * (h * ny) + wJy * (h * nx);
            double rx = ubx - uax, ry = uby - uay, rz = ubz - uaz;

            double t1x = T1[d][0], t1y = T1[d][1], t1z = T1[d][2];
            double t2x = T2[d][0], t2y = T2[d][1], t2z = T2[d][2];
            double kS = shear(k, nuI, nu[j]);
            double length2 = 1.0 / (INV_DIST[d] * INV_DIST[d]);
            double kBend = k * length2 / 12.0;
            double kTor = k * length2 / 6.0;
            double fN = k * (nx * rx + ny * ry + nz * rz);
            double fS1 = kS * (t1x * rx + t1y * ry + t1z * rz);
            double fS2 = kS * (t2x * rx + t2y * ry + t2z * rz);
            double fx = fN * nx + fS1 * t1x + fS2 * t2x;
            double fy = fN * ny + fS1 * t1y + fS2 * t2y;
            double fz = fN * nz + fS1 * t1z + fS2 * t2z;

            double wx = wJx - wIx, wy = wJy - wIy, wz = wJz - wIz;
            double b1 = kBend * (wx * t1x + wy * t1y + wz * t1z);
            double b2 = kBend * (wx * t2x + wy * t2y + wz * t2z);
            double tor = kTor * (wx * nx + wy * ny + wz * nz);
            double mx = b1 * t1x + b2 * t2x + tor * nx;
            double my = b1 * t1y + b2 * t2y + tor * ny;
            double mz = b1 * t1z + b2 * t2z + tor * nz;

            out[i6] -= fx;
            out[i6 + 1] -= fy;
            out[i6 + 2] -= fz;
            out[i6 + 3] -= (h * ny) * fz - (h * nz) * fy + mx;
            out[i6 + 4] -= (h * nz) * fx - (h * nx) * fz + my;
            out[i6 + 5] -= (h * nx) * fy - (h * ny) * fx + mz;
        }
    }

    private static double shear(double k, double nuI, double nuJ) {
        return k * 0.1 / (2.0 + nuI + nuJ);
    }

    private void buildPreconditioner() {
        precon = new double[6 * n];
        for (int i = 0; i < n; i++) {
            int i6 = 6 * i;
            for (int d = 0; d < NEIGHBORS; d++) {
                int j = neighbors[i][d];
                if (j < 0)
                    continue;
                double k = stiffness[i][d];
                double kS = shear(k, nu[i], nu[j]);
                double rot = (k / 12.0 + k / 6.0) / (INV_DIST[d] * INV_DIST[d]);
                double cx = DIR[d][0] * DIR[d][0], cy = DIR[d][1] * DIR[d][1], cz = DIR[d][2] * DIR[d][2];
                precon[i6] += k * cx + kS * (1 - cx);
                precon[i6 + 1] += k * cy + kS * (1 - cy);
                precon[i6 + 2] += k * cz + kS * (1 - cz);
                precon[i6 + 3] += kS * (cy + cz) + rot;
                precon[i6 + 4] += kS * (cx + cz) + rot;
                precon[i6 + 5] += kS * (cx + cy) + rot;
            }
        }
        double floor = Math.max(meanStiffness * 1e-6, 1e-12);
        for (int k = 0; k < precon.length; k++)
            if (!(precon[k] > floor))
                precon[k] = floor;
    }

    private void solve(double[] b) {
        int size = 6 * n;
        double bNorm = dot(b, b);
        if (bNorm < 1e-30)
            return;
        double alpha = TIKHONOV * meanStiffness;
        buildPreconditioner();
        double[] r = b.clone();
        double[] zv = new double[size];
        double[] p = new double[size];
        double[] ap = new double[size];
        for (int k = 0; k < size; k++)
            zv[k] = r[k] / (precon[k] + alpha);
        System.arraycopy(zv, 0, p, 0, size);
        double rz = dot(r, zv);
        int cap = Math.min(MAX_ITERATIONS, Math.max(300, size));
        for (iterations = 0; iterations < cap; iterations++) {
            apply(p, ap);
            for (int k = 0; k < size; k++)
                ap[k] += alpha * p[k];
            double pap = dot(p, ap);
            if (pap <= 0)
                break;
            double step = rz / pap;
            for (int k = 0; k < size; k++) {
                u[k] += step * p[k];
                r[k] -= step * ap[k];
            }
            if (dot(r, r) < TOLERANCE * bNorm)
                break;
            for (int k = 0; k < size; k++)
                zv[k] = r[k] / (precon[k] + alpha);
            double next = dot(r, zv);
            if (next <= 0)
                break;
            double beta = next / rz;
            for (int k = 0; k < size; k++)
                p[k] = zv[k] + beta * p[k];
            rz = next;
        }
    }

    private void removeRigidModes() {
        double m = 0, sx = 0, sy = 0, sz = 0, cx = 0, cy = 0, cz = 0;
        for (int i = 0; i < n; i++) {
            double w = Math.max(volume[i], 1e-3);
            m += w;
            sx += w * u[6 * i];
            sy += w * u[6 * i + 1];
            sz += w * u[6 * i + 2];
            cx += w * x[i];
            cy += w * y[i];
            cz += w * z[i];
        }
        sx /= m;
        sy /= m;
        sz /= m;
        cx /= m;
        cy /= m;
        cz /= m;
        double lx = 0, ly = 0, lz = 0, ixx = 0, iyy = 0, izz = 0, ixy = 0, ixz = 0, iyz = 0;
        for (int i = 0; i < n; i++) {
            double w = Math.max(volume[i], 1e-3);
            double rx = x[i] - cx, ry = y[i] - cy, rz = z[i] - cz;
            double vx = u[6 * i], vy = u[6 * i + 1], vz = u[6 * i + 2];
            lx += w * (ry * vz - rz * vy);
            ly += w * (rz * vx - rx * vz);
            lz += w * (rx * vy - ry * vx);
            ixx += w * (ry * ry + rz * rz);
            iyy += w * (rx * rx + rz * rz);
            izz += w * (rx * rx + ry * ry);
            ixy -= w * rx * ry;
            ixz -= w * rx * rz;
            iyz -= w * ry * rz;
        }
        double det = ixx * (iyy * izz - iyz * iyz) - ixy * (ixy * izz - iyz * ixz) + ixz * (ixy * iyz - iyy * ixz);
        double wx = 0, wy = 0, wz = 0;
        if (Math.abs(det) > 1e-30) {
            wx = ((iyy * izz - iyz * iyz) * lx + (ixz * iyz - ixy * izz) * ly + (ixy * iyz - ixz * iyy) * lz) / det;
            wy = ((iyz * ixz - ixy * izz) * lx + (ixx * izz - ixz * ixz) * ly + (ixy * ixz - ixx * iyz) * lz) / det;
            wz = ((ixy * iyz - iyy * ixz) * lx + (ixz * ixy - ixx * iyz) * ly + (ixx * iyy - ixy * ixy) * lz) / det;
        }
        double mwx = 0, mwy = 0, mwz = 0;
        for (int i = 0; i < n; i++) {
            double rx = x[i] - cx, ry = y[i] - cy, rz = z[i] - cz;
            u[6 * i] -= sx + (wy * rz - wz * ry);
            u[6 * i + 1] -= sy + (wz * rx - wx * rz);
            u[6 * i + 2] -= sz + (wx * ry - wy * rx);
            u[6 * i + 3] -= wx;
            u[6 * i + 4] -= wy;
            u[6 * i + 5] -= wz;
            mwx += u[6 * i + 3];
            mwy += u[6 * i + 4];
            mwz += u[6 * i + 5];
        }
        mwx /= n;
        mwy /= n;
        mwz /= n;
        for (int i = 0; i < n; i++) {
            u[6 * i + 3] -= mwx;
            u[6 * i + 4] -= mwy;
            u[6 * i + 5] -= mwz;
        }
    }

    private double vonMises(int i) {
        int i6 = 6 * i;
        double gxx = 0, gxy = 0, gxz = 0, gyx = 0, gyy = 0, gyz = 0, gzx = 0, gzy = 0, gzz = 0;
        double ex = 0, ey = 0, ez = 0;
        double E = 0;
        int springs = 0;
        for (int d = 0; d < NEIGHBORS; d++) {
            int j = neighbors[i][d];
            if (j < 0)
                continue;
            int j6 = 6 * j;
            double dx = DX[d], dy = DY[d], dz = DZ[d];
            double dux = (u[j6] - u[i6]) + (u[j6 + 4] * dz - u[j6 + 5] * dy);
            double duy = (u[j6 + 1] - u[i6 + 1]) + (u[j6 + 5] * dx - u[j6 + 3] * dz);
            double duz = (u[j6 + 2] - u[i6 + 2]) + (u[j6 + 3] * dy - u[j6 + 4] * dx);
            double w = volume[j] / (volume[i] + volume[j] + 1e-9);
            gxx += dux * dx * w;
            gxy += dux * dy * w;
            gxz += dux * dz * w;
            gyx += duy * dx * w;
            gyy += duy * dy * w;
            gyz += duy * dz * w;
            gzx += duz * dx * w;
            gzy += duz * dy * w;
            gzz += duz * dz * w;
            ex += dx * dx * w;
            ey += dy * dy * w;
            ez += dz * dz * w;
            E += stiffness[i][d] / INV_DIST[d];
            springs++;
        }
        if (springs == 0)
            return 0;
        E /= springs;
        if (ex > 0) {
            gxx /= ex;
            gyx /= ex;
            gzx /= ex;
        }
        if (ey > 0) {
            gxy /= ey;
            gyy /= ey;
            gzy /= ey;
        }
        if (ez > 0) {
            gxz /= ez;
            gyz /= ez;
            gzz /= ez;
        }
        double exy = (gxy + gyx) * 0.5, eyz = (gyz + gzy) * 0.5, ezx = (gxz + gzx) * 0.5;
        double v = nu[i];
        double lambda = E * v / ((1.0 + v) * (1.0 - 2.0 * v));
        double mu = E / (2.0 * (1.0 + v));
        double vol = gxx + gyy + gzz;
        double sxx = 2.0 * mu * gxx + lambda * vol;
        double syy = 2.0 * mu * gyy + lambda * vol;
        double szz = 2.0 * mu * gzz + lambda * vol;
        return Math.sqrt(0.5 * ((sxx - syy) * (sxx - syy) + (syy - szz) * (syy - szz) + (szz - sxx) * (szz - sxx)
                + 6.0 * ((2.0 * mu * exy) * (2.0 * mu * exy) + (2.0 * mu * eyz) * (2.0 * mu * eyz)
                        + (2.0 * mu * ezx) * (2.0 * mu * ezx))));
    }

    private static double dot(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++)
            s += a[i] * b[i];
        return s;
    }
}
