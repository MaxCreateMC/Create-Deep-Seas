package com.maxenonyme.createsubmarine.submarine.stress;

import net.minecraft.core.BlockPos;

import java.util.Arrays;
import java.util.HashMap;
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
            0, 0, 0, 0, 1, 1, -1, -1, 1, 1, -1, -1,
            1, -1, 1, -1, 1, -1, 1, -1
    };
    public static final double[] INV_DIST = new double[NEIGHBORS];
    static final double[][] DIR = new double[NEIGHBORS][3];
    static final double[][] T1 = new double[NEIGHBORS][3];
    static final double[][] T2 = new double[NEIGHBORS][3];
    static final double[] SHELL = new double[NEIGHBORS];

    public static final double RHO_G = 10000.0;
    public static final double REFERENCE_DEPTH = 100.0;
    public static final byte WET_DRY = 0;
    public static final byte WET_EXTERNAL = 1;
    public static final byte WET_INTERNAL = 2;
    public static final double MOON_POOL_FACTOR = 0.8;
    private static final double MODULUS_SCALE = 0.419;
    private static final double SHEAR_SCALE = 0.10;
    private static final int PARALLEL_FROM = 1500;
    private static final int MAX_ITERATIONS = 6000;
    private static final double TIKHONOV = 1e-6;
    private static final long BUDGET_NANOS = 15_000_000_000L;
    private static final long REFRESH_BUDGET_NANOS = 2_000_000_000L;
    private static final int MULTIGRID_FROM = 2000;

    public static boolean USE_MULTIGRID = true;

    public static void setUseMultigrid(boolean v) {
        USE_MULTIGRID = v;
    }

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

            SHELL[d] = switch (Math.abs(DX[d]) + Math.abs(DY[d]) + Math.abs(DZ[d])) {
                case 1 -> 1.0 / 6.0;
                case 2 -> 1.0 / 12.0;
                default -> 1.0 / 8.0;
            };
        }
    }

    final int n;
    final int[] x, y, z;
    final RealisticHull.Material[] materials;
    final double[] limit;
    final double[] nu;
    final double[] volume;
    final int[][] neighbors;
    final double[][] stiffness;
    final boolean[][] wet;
    final double[] u;

    byte[] faceClass;
    double[] faceDepth;
    double[] blockDepth;

    public double cgTolerance = 1e-10;
    public long solveTimeNanos;
    public int lastIterations;
    public boolean lastConverged;
    public double lastResidualRel;

    private boolean nextSolveIsRefresh;

    private final double meanModulus;
    private double[] precon;
    private double[] spin;
    private double px, py, pz;

    private double previousBNorm = -1;
    private double[] cgR, cgZv, cgP, cgAp;

    public RigidLatticeSolver(int n, int[] x, int[] y, int[] z, RealisticHull.Material[] materials, double[] limit,
            int[][] neighbors, double[][] stiffness, boolean[][] wet) {
        this(n, x, y, z, materials, limit, neighbors, stiffness, wet, null, null, null);
    }

    public RigidLatticeSolver(int n, int[] x, int[] y, int[] z, RealisticHull.Material[] materials, double[] limit,
            int[][] neighbors, double[][] stiffness, boolean[][] wet, byte[] faceClass, double[] faceDepth,
            double[] blockDepth) {
        this.n = n;
        this.x = x;
        this.y = y;
        this.z = z;
        this.materials = materials;
        this.limit = limit;
        this.neighbors = neighbors;
        this.stiffness = stiffness;
        this.wet = wet;
        this.faceClass = faceClass;
        this.faceDepth = faceDepth;
        this.blockDepth = blockDepth;
        this.u = new double[6 * n];
        this.nu = new double[n];
        this.volume = new double[n];

        double sum = 0;
        for (int i = 0; i < n; i++) {
            nu[i] = materials[i].nu();
            volume[i] = materials[i].geometry().volume();
            sum += materials[i].stiffness();
        }
        this.meanModulus = n > 0 ? sum / n : 1.0;
    }

    public boolean structureMatches(int n, int[] x, int[] y, int[] z) {
        return this.n == n && Arrays.equals(this.x, x) && Arrays.equals(this.y, y) && Arrays.equals(this.z, z);
    }

    public boolean structureMatches(int n, int[] x, int[] y, int[] z, RealisticHull.Material[] materials) {
        if (!structureMatches(n, x, y, z))
            return false;
        for (int i = 0; i < n; i++)
            if (this.materials[i] != materials[i])
                return false;
        return true;
    }

    public void setFaceLoads(byte[] faceClass, double[] faceDepth, double[] blockDepth) {
        this.faceClass = faceClass;
        this.faceDepth = faceDepth;
        this.blockDepth = blockDepth;
        this.nextSolveIsRefresh = true;
    }

    public double[] limits() {
        return limit;
    }

    public double[] crushDepths() {
        double[] b = new double[6 * n];
        if (!load(b))
            return null;

        solve(b);
        removeRigidModes();

        double[] crush = new double[n];
        if (n >= PARALLEL_FROM)
            IntStream.range(0, n).parallel().forEach(i -> crush[i] = crushDepth(i));
        else
            for (int i = 0; i < n; i++)
                crush[i] = crushDepth(i);
        return crush;
    }

    private double crushDepth(int i) {
        if (limit[i] <= 0)
            return Double.POSITIVE_INFINITY;

        double depth = blockDepth != null ? blockDepth[i] : REFERENCE_DEPTH;
        if (depth <= 0)
            return Double.POSITIVE_INFINITY;

        double stress = stress(i);
        if (stress <= 1e-30)
            return Double.POSITIVE_INFINITY;
        return depth * limit[i] / stress;
    }

    private boolean load(double[] b) {
        boolean any = false;
        for (int i = 0; i < n; i++) {
            RealisticHull.Geometry geometry = materials[i].geometry();
            double[] centroid = geometry.centroid();
            double blockDepthI = blockDepth != null ? blockDepth[i] : -1;
            if (blockDepth != null && blockDepthI <= 0)
                continue;
            for (int d = 0; d < 6; d++) {
                if (neighbors[i][d] >= 0)
                    continue;
                if (faceClass != null) {
                    int cls = faceClass[6 * i + d];
                    if (cls == WET_DRY)
                        continue;
                } else if (!wet[i][d]) {
                    continue;
                }

                double depth = faceDepth != null ? faceDepth[6 * i + d]
                        : (blockDepth != null ? blockDepthI : REFERENCE_DEPTH);
                if (depth <= 0)
                    continue;

                double pressure = RHO_G * depth;
                if (faceClass != null && faceClass[6 * i + d] == WET_INTERNAL)
                    pressure *= MOON_POOL_FACTOR;

                double force = pressure * geometry.faceArea()[d];
                double fx = -force * DX[d], fy = -force * DY[d], fz = -force * DZ[d];

                double rx = geometry.faceCenter()[3 * d] - centroid[0];
                double ry = geometry.faceCenter()[3 * d + 1] - centroid[1];
                double rz = geometry.faceCenter()[3 * d + 2] - centroid[2];

                int i6 = 6 * i;
                b[i6] += fx;
                b[i6 + 1] += fy;
                b[i6 + 2] += fz;
                b[i6 + 3] += ry * fz - rz * fy;
                b[i6 + 4] += rz * fx - rx * fz;
                b[i6 + 5] += rx * fy - ry * fx;
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
            double k = stiffness[i][d] * MODULUS_SCALE;
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
        return k * SHEAR_SCALE / (2.0 + nuI + nuJ);
    }

    private void buildPreconditioner() {
        precon = new double[6 * n];
        for (int i = 0; i < n; i++) {
            int i6 = 6 * i;
            for (int d = 0; d < NEIGHBORS; d++) {
                int j = neighbors[i][d];
                if (j < 0)
                    continue;

                double k = stiffness[i][d] * MODULUS_SCALE;
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

        double floor = Math.max(meanModulus * 1e-6, 1e-12);
        for (int k = 0; k < precon.length; k++)
            if (!(precon[k] > floor))
                precon[k] = floor;
    }

    private void solve(double[] b) {
        long t0 = System.nanoTime();
        long budget = nextSolveIsRefresh ? REFRESH_BUDGET_NANOS : BUDGET_NANOS;
        nextSolveIsRefresh = false;
        if (USE_MULTIGRID && n > MULTIGRID_FROM) {
            solveMultigrid(b, budget);
        } else {
            runCG(b, 0, budget);
        }
        solveTimeNanos = System.nanoTime() - t0;
    }

    private void solveMultigrid(double[] b, long budget) {
        int smoothIters = Math.max(10, Math.min(60, n / 200));
        runCG(b, smoothIters, budget);

        int size = 6 * n;
        double alpha = TIKHONOV * meanModulus;

        double[] r = new double[size];
        apply(u, r);
        for (int k = 0; k < size; k++)
            r[k] = b[k] - r[k] - alpha * u[k];
        double rNormBefore = dot(r, r);
        if (!(rNormBefore > 0))
            return;

        CoarseGrid coarse = new CoarseGrid();
        if (coarse.cn <= 0)
            return;

        double[] cr = new double[6 * coarse.cn];
        restrict(r, cr, coarse);

        double[] cu = new double[6 * coarse.cn];
        coarse.solveCG(cr, cu);

        double[] correction = new double[size];
        prolongate(cu, correction, coarse);

        double[] uBefore = u.clone();
        for (int k = 0; k < size; k++)
            u[k] += correction[k];

        apply(u, r);
        for (int k = 0; k < size; k++)
            r[k] = b[k] - r[k] - alpha * u[k];
        if (dot(r, r) > rNormBefore) {
            System.arraycopy(uBefore, 0, u, 0, size);
        }

        runCG(b, smoothIters, budget);
    }

    private void restrict(double[] fineR, double[] coarseR, CoarseGrid coarse) {
        Arrays.fill(coarseR, 0.0);
        for (int i = 0; i < n; i++) {
            int ci = coarse.group[i];
            if (ci < 0)
                continue;
            coarseR[6 * ci] += fineR[6 * i];
            coarseR[6 * ci + 1] += fineR[6 * i + 1];
            coarseR[6 * ci + 2] += fineR[6 * i + 2];
        }
    }

    private void prolongate(double[] coarseU, double[] fineCorrection, CoarseGrid coarse) {
        Arrays.fill(fineCorrection, 0.0);
        for (int i = 0; i < n; i++) {
            int ci = coarse.group[i];
            if (ci < 0)
                continue;
            fineCorrection[6 * i] = coarseU[6 * ci];
            fineCorrection[6 * i + 1] = coarseU[6 * ci + 1];
            fineCorrection[6 * i + 2] = coarseU[6 * ci + 2];
        }
    }

    private void runCG(double[] b, int maxIterOverride, long budgetNanos) {
        int size = 6 * n;
        double bNorm = dot(b, b);
        if (bNorm < 1e-30) {
            lastIterations = 0;
            lastConverged = true;
            lastResidualRel = 0.0;
            return;
        }

        if (previousBNorm < 0) {
            boolean hasU = false;
            for (int k = 0; k < size; k++) {
                if (u[k] != 0.0) {
                    hasU = true;
                    break;
                }
            }
            if (!hasU)
                Arrays.fill(u, 0.0);
        } else {
            double scale = bNorm / previousBNorm;
            if (Math.abs(scale - 1.0) > 1e-6)
                for (int k = 0; k < size; k++)
                    u[k] *= scale;
        }
        previousBNorm = bNorm;

        double alpha = TIKHONOV * meanModulus;
        if (precon == null)
            buildPreconditioner();
        buildProjection();

        if (cgR == null || cgR.length != size) {
            cgR = new double[size];
            cgZv = new double[size];
            cgP = new double[size];
            cgAp = new double[size];
        }
        double[] r = cgR, zv = cgZv, p = cgP, ap = cgAp;

        apply(u, r);
        for (int k = 0; k < size; k++)
            r[k] = b[k] - r[k] - alpha * u[k];
        project(r);

        for (int k = 0; k < size; k++)
            zv[k] = r[k] / (precon[k] + alpha);
        System.arraycopy(zv, 0, p, 0, size);
        double rz = dot(r, zv);

        double target = cgTolerance * cgTolerance * bNorm;
        long deadline = System.nanoTime() + budgetNanos;
        int cap = maxIterOverride > 0 ? maxIterOverride : Math.min(MAX_ITERATIONS, Math.max(300, size));

        double rr = dot(r, r);
        int iteration = 0;
        boolean converged = false;
        for (; iteration < cap && System.nanoTime() < deadline; iteration++) {
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
            project(r);
            rr = dot(r, r);
            if (rr < target) {
                converged = true;
                break;
            }

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

        lastIterations = iteration;
        lastConverged = converged;
        lastResidualRel = Math.sqrt(rr / bNorm);
    }

    private void buildProjection() {
        if (spin != null)
            return;
        px = py = pz = 0;
        for (int i = 0; i < n; i++) {
            px += x[i];
            py += y[i];
            pz += z[i];
        }
        px /= n;
        py /= n;
        pz /= n;

        double a = n, b = n, c = n, d = 0, e = 0, f = 0;
        for (int i = 0; i < n; i++) {
            double rx = x[i] - px, ry = y[i] - py, rz = z[i] - pz;
            a += ry * ry + rz * rz;
            b += rx * rx + rz * rz;
            c += rx * rx + ry * ry;
            d -= rx * ry;
            e -= rx * rz;
            f -= ry * rz;
        }

        double i00 = b * c - f * f, i01 = e * f - d * c, i02 = d * f - e * b;
        double i11 = a * c - e * e, i12 = d * e - a * f, i22 = a * b - d * d;
        double det = a * i00 + d * i01 + e * i02;
        spin = new double[]{i00 / det, i01 / det, i02 / det, i11 / det, i12 / det, i22 / det};
    }

    private void project(double[] v) {
        double tx = 0, ty = 0, tz = 0, lx = 0, ly = 0, lz = 0;
        for (int i = 0; i < n; i++) {
            int i6 = 6 * i;
            double rx = x[i] - px, ry = y[i] - py, rz = z[i] - pz;
            tx += v[i6];
            ty += v[i6 + 1];
            tz += v[i6 + 2];
            lx += ry * v[i6 + 2] - rz * v[i6 + 1] + v[i6 + 3];
            ly += rz * v[i6] - rx * v[i6 + 2] + v[i6 + 4];
            lz += rx * v[i6 + 1] - ry * v[i6] + v[i6 + 5];
        }

        tx /= n;
        ty /= n;
        tz /= n;
        double ax = spin[0] * lx + spin[1] * ly + spin[2] * lz;
        double ay = spin[1] * lx + spin[3] * ly + spin[4] * lz;
        double az = spin[2] * lx + spin[4] * ly + spin[5] * lz;

        for (int i = 0; i < n; i++) {
            int i6 = 6 * i;
            double rx = x[i] - px, ry = y[i] - py, rz = z[i] - pz;
            v[i6] -= tx + ay * rz - az * ry;
            v[i6 + 1] -= ty + az * rx - ax * rz;
            v[i6 + 2] -= tz + ax * ry - ay * rx;
            v[i6 + 3] -= ax;
            v[i6 + 4] -= ay;
            v[i6 + 5] -= az;
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

        for (int i = 0; i < n; i++) {
            double rx = x[i] - cx, ry = y[i] - cy, rz = z[i] - cz;
            u[6 * i] -= sx + (wy * rz - wz * ry);
            u[6 * i + 1] -= sy + (wz * rx - wx * rz);
            u[6 * i + 2] -= sz + (wx * ry - wy * rx);
            u[6 * i + 3] -= wx;
            u[6 * i + 4] -= wy;
            u[6 * i + 5] -= wz;
        }
    }

    private double stress(int i) {
        int i6 = 6 * i;
        double gxx = 0, gxy = 0, gxz = 0, gyx = 0, gyy = 0, gyz = 0, gzx = 0, gzy = 0, gzz = 0;
        double ex = 0, ey = 0, ez = 0;

        for (int d = 0; d < NEIGHBORS; d++) {
            int j = neighbors[i][d];
            if (j < 0)
                continue;

            int j6 = 6 * j;
            double dx = DX[d], dy = DY[d], dz = DZ[d];
            double rx = 0.5 * dx, ry = 0.5 * dy, rz = 0.5 * dz;
            double sx = u[i6 + 3] + u[j6 + 3], sy = u[i6 + 4] + u[j6 + 4], sz = u[i6 + 5] + u[j6 + 5];

            double dux = u[j6] - u[i6] - (sy * rz - sz * ry);
            double duy = u[j6 + 1] - u[i6 + 1] - (sz * rx - sx * rz);
            double duz = u[j6 + 2] - u[i6 + 2] - (sx * ry - sy * rx);

            double w = SHELL[d] * volume[j] / (volume[i] + volume[j] + 1e-9);
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
        }

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

        double modulus = materials[i].stiffness();
        double v = nu[i];
        double lambda = modulus * v / ((1.0 + v) * (1.0 - 2.0 * v));
        double mu = modulus / (2.0 * (1.0 + v));

        double vol = gxx + gyy + gzz;
        double sxx = 2.0 * mu * gxx + lambda * vol;
        double syy = 2.0 * mu * gyy + lambda * vol;
        double szz = 2.0 * mu * gzz + lambda * vol;
        double sxy = mu * (gxy + gyx);
        double syz = mu * (gyz + gzy);
        double szx = mu * (gxz + gzx);

        if (materials[i].brittle())
            return maxPrincipal(sxx, syy, szz, sxy, syz, szx);
        return Math.sqrt(0.5 * ((sxx - syy) * (sxx - syy) + (syy - szz) * (syy - szz) + (szz - sxx) * (szz - sxx)
                + 6.0 * (sxy * sxy + syz * syz + szx * szx)));
    }

    private static double maxPrincipal(double sxx, double syy, double szz, double sxy, double syz, double szx) {
        double mean = (sxx + syy + szz) / 3.0;
        double d0 = sxx - mean, d1 = syy - mean, d2 = szz - mean;
        double j2 = 0.5 * (d0 * d0 + d1 * d1 + d2 * d2) + sxy * sxy + syz * syz + szx * szx;
        if (j2 <= 3e-30)
            return mean;

        double det = d0 * d1 * d2 + 2.0 * sxy * syz * szx - d0 * syz * syz - d1 * szx * szx - d2 * sxy * sxy;
        double rho = Math.sqrt(j2 / 3.0);
        double cos = Math.clamp(det / (2.0 * rho * rho * rho), -1.0, 1.0);
        return mean + 2.0 * rho * Math.cos(Math.acos(cos) / 3.0);
    }

    private final class CoarseGrid {
        final int cn;
        final int[] group;
        private final int[] cx, cy, cz;
        private final int[][] neighborsC;
        private final double[][] springC;
        private final double[] nuAvg;

        CoarseGrid() {
            HashMap<Long, Integer> coarseMap = new HashMap<>();
            int[] coarseIdx = new int[n];
            int[][] cxyz = new int[n][];
            for (int i = 0; i < n; i++) {
                int gx = Math.floorDiv(x[i], 2), gy = Math.floorDiv(y[i], 2), gz = Math.floorDiv(z[i], 2);
                long key = BlockPos.asLong(gx, gy, gz);
                int ci = coarseMap.computeIfAbsent(key, k -> {
                    int idx = coarseMap.size();
                    cxyz[idx] = new int[]{gx, gy, gz};
                    return idx;
                });
                coarseIdx[i] = ci;
            }

            this.cn = coarseMap.size();
            this.group = coarseIdx;
            this.cx = new int[this.cn];
            this.cy = new int[this.cn];
            this.cz = new int[this.cn];
            for (int ci = 0; ci < this.cn; ci++) {
                cx[ci] = cxyz[ci][0];
                cy[ci] = cxyz[ci][1];
                cz[ci] = cxyz[ci][2];
            }

            neighborsC = new int[this.cn][NEIGHBORS];
            springC = new double[this.cn][NEIGHBORS];
            nuAvg = new double[this.cn];
            double[] aggStiff = new double[this.cn];
            int[] count = new int[this.cn];
            for (int i = 0; i < n; i++) {
                int ci = group[i];
                aggStiff[ci] += materials[i].stiffness();
                nuAvg[ci] += nu[i];
                count[ci]++;
            }
            for (int ci = 0; ci < this.cn; ci++) {
                aggStiff[ci] = count[ci] > 0 ? aggStiff[ci] / count[ci] : 1.0;
                nuAvg[ci] = count[ci] > 0 ? nuAvg[ci] / count[ci] : 0.3;
            }

            double aggregateVolume = 8.0;
            for (int ci = 0; ci < this.cn; ci++) {
                for (int d = 0; d < NEIGHBORS; d++) {
                    long key = BlockPos.asLong(cx[ci] + DX[d], cy[ci] + DY[d], cz[ci] + DZ[d]);
                    Integer cj = coarseMap.get(key);
                    if (cj != null && cj != ci) {
                        neighborsC[ci][d] = cj;
                        double axial = 0.5 * (aggStiff[ci] + aggStiff[cj]) * aggregateVolume;
                        springC[ci][d] = -(axial * INV_DIST[d]);
                    } else {
                        neighborsC[ci][d] = -1;
                        springC[ci][d] = 0.0;
                    }
                }
            }
        }

        void solveCG(double[] rhs, double[] uOut) {
            Arrays.fill(uOut, 0.0);
            int cn6 = 6 * cn;
            double bNorm = 0;
            for (int k = 0; k < cn6; k++)
                bNorm += rhs[k] * rhs[k];
            if (bNorm < 1e-30)
                return;

            double avgE = meanModulus;
            double alpha = TIKHONOV * avgE;

            double[] r = new double[cn6];
            double[] p = new double[cn6];
            double[] ap = new double[cn6];

            applyCoarse(uOut, r);
            double rr = 0;
            for (int k = 0; k < cn6; k++) {
                r[k] = rhs[k] - r[k] - alpha * uOut[k];
                rr += r[k] * r[k];
            }
            System.arraycopy(r, 0, p, 0, cn6);
            double rrOld = rr;

            int maxIter = Math.min(200, Math.max(50, cn6 / 8));
            for (int iter = 0; iter < maxIter; iter++) {
                applyCoarse(p, ap);
                for (int k = 0; k < cn6; k++)
                    ap[k] += alpha * p[k];
                double pAp = dot(p, ap);
                if (pAp <= 0)
                    break;

                double step = rr / pAp;
                for (int k = 0; k < cn6; k++) {
                    uOut[k] += step * p[k];
                    r[k] -= step * ap[k];
                }

                rr = dot(r, r);
                if (rr < 1e-12 * bNorm)
                    break;

                double beta = rr / rrOld;
                for (int k = 0; k < cn6; k++)
                    p[k] = r[k] + beta * p[k];
                rrOld = rr;
            }
        }

        private void applyCoarse(double[] v, double[] out) {
            Arrays.fill(out, 0.0);
            for (int ci = 0; ci < cn; ci++) {
                int ci6 = 6 * ci;
                double nuI = nuAvg[ci];
                for (int d = 0; d < NEIGHBORS; d++) {
                    int cj = neighborsC[ci][d];
                    if (cj < 0)
                        continue;

                    int cj6 = 6 * cj;
                    double k = -springC[ci][d] * MODULUS_SCALE;
                    double nx = DIR[d][0], ny = DIR[d][1], nz = DIR[d][2];
                    double h = 0.5 / INV_DIST[d];

                    double wIx = v[ci6 + 3], wIy = v[ci6 + 4], wIz = v[ci6 + 5];
                    double wJx = v[cj6 + 3], wJy = v[cj6 + 4], wJz = v[cj6 + 5];
                    double uax = v[ci6] + wIy * (h * nz) - wIz * (h * ny);
                    double uay = v[ci6 + 1] + wIz * (h * nx) - wIx * (h * nz);
                    double uaz = v[ci6 + 2] + wIx * (h * ny) - wIy * (h * nx);
                    double ubx = v[cj6] - wJy * (h * nz) + wJz * (h * ny);
                    double uby = v[cj6 + 1] - wJz * (h * nx) + wJx * (h * nz);
                    double ubz = v[cj6 + 2] - wJx * (h * ny) + wJy * (h * nx);
                    double rx = ubx - uax, ry = uby - uay, rz = ubz - uaz;

                    double t1x = T1[d][0], t1y = T1[d][1], t1z = T1[d][2];
                    double t2x = T2[d][0], t2y = T2[d][1], t2z = T2[d][2];
                    double kS = k * SHEAR_SCALE / (2.0 + nuI + nuAvg[cj]);
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

                    out[ci6] -= fx;
                    out[ci6 + 1] -= fy;
                    out[ci6 + 2] -= fz;
                    out[ci6 + 3] -= (h * ny) * fz - (h * nz) * fy + mx;
                    out[ci6 + 4] -= (h * nz) * fx - (h * nx) * fz + my;
                    out[ci6 + 5] -= (h * nx) * fy - (h * ny) * fx + mz;
                }
            }
        }
    }

    private static double dot(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++)
            s += a[i] * b[i];
        return s;
    }
}
