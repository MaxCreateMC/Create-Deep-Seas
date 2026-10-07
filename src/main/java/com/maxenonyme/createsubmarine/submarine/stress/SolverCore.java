package com.maxenonyme.createsubmarine.submarine.stress;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Pure-math rigid-block spring-lattice FEM solver with no Minecraft or Sable dependencies.
 * Each block is a rigid body with 6 DOF (3 translation + 3 rotation), joined to its
 * neighbours by a normal spring, two shear springs and three rotational springs,
 * giving the lattice genuine bending and torsional stiffness.
 * Used by {@link LatticeStressSolver} for in-game solves, and by
 * {@link CalibrationRunner} for standalone calibration.
 */
public class SolverCore {

    public static final int MAX_NEIGHBORS = 26;
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

    public static final double[] INV_DIST = new double[MAX_NEIGHBORS];
    public static final double[][] DIR_COS = new double[MAX_NEIGHBORS][3];
    // precomputed orthonormal shear basis per neighbour direction
    static final double[][] T1 = new double[MAX_NEIGHBORS][3];
    static final double[][] T2 = new double[MAX_NEIGHBORS][3];

    public static final double RHO_G = 10000.0;
    public static final double MOON_POOL_FACTOR = 0.8;

    static {
        for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
            final double d = Math.sqrt(DX[dir]*DX[dir] + DY[dir]*DY[dir] + DZ[dir]*DZ[dir]);
            INV_DIST[dir] = 1.0 / d;
            DIR_COS[dir][0] = DX[dir] / d;
            DIR_COS[dir][1] = DY[dir] / d;
            DIR_COS[dir][2] = DZ[dir] / d;
            final double nx = DIR_COS[dir][0], ny = DIR_COS[dir][1], nz = DIR_COS[dir][2];
            double vx, vy, vz;
            final double ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
            if (ax <= ay && ax <= az) { vx = 1; vy = 0; vz = 0; }
            else if (ay <= az)        { vx = 0; vy = 1; vz = 0; }
            else                      { vx = 0; vy = 0; vz = 1; }
            double t1x = ny * vz - nz * vy, t1y = nz * vx - nx * vz, t1z = nx * vy - ny * vx;
            final double tl = Math.sqrt(t1x * t1x + t1y * t1y + t1z * t1z);
            t1x /= tl; t1y /= tl; t1z /= tl;
            T1[dir][0] = t1x; T1[dir][1] = t1y; T1[dir][2] = t1z;
            T2[dir][0] = ny * t1z - nz * t1y; T2[dir][1] = nz * t1x - nx * t1z; T2[dir][2] = nx * t1y - ny * t1x;
        }
    }

    // ---- graph data (immutable after construction) ----
    public final int n;
    public final int[] x, y, z;
    public final double[] E;
    public final double[] yieldStress;
    public final int[][] neighbors;
    public final double[][] springK;
    public final int[] neighborCount;
    public final int[] exposedFaceCount;
    public final boolean[] isHullBlock;
    public final int hullBlockCount;
    public final double[] volFraction;

    // ---- position lookup (built at construction) ----
    private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap blockIndex;

    // ---- mutable solver state (6 DOF per block) ----
    public final double[] u;
    public final double[] blockWaterDepths;

    // ---- cached Jacobi preconditioner ----
    private double[] diagPrecon;

    // ---- persistent CG workspace (avoids reallocating 4 arrays per solve) ----
    private double[] cgR, cgZ, cgP, cgAp;

    // ---- config parameters ----
    public double poissonRatio = 0.3;
    public double tikhonovAlphaFraction = 1e-6;
    public double rhoG = RHO_G;
    public boolean useMoonPool = true;

    // ---- per-block Poisson ratios (null => global poissonRatio) ----
    public double[] nuBlock;

    // ---- per-block directionally-effective yield (null => raw yieldStress) ----
    public double[] yieldEffective;

    // ---- orientation (affects moon-pool detection) ----
    public double localDownX = 0, localDownY = -1, localDownZ = 0;

    // ---- smoothed normals from ShapeClassifier (null = flat-only) ----
    public double[][] smoothedNormals;

    // ---- which exposed faces are wet (null = all exposed faces wet) ----
    public boolean[][] waterFace;

    // ---- warm-start tracking ----
    private double previousBNorm = -1;

    public long solveTimeNanos = 0;


    public SolverCore(
        final int n,
        final int[] x,
        final int[] y,
        final int[] z,
        final double[] E,
        final double[] yieldStress,
        final int[][] neighbors,
        final double[][] springK,
        final int[] neighborCount,
        final int[] exposedFaceCount,
        final boolean[] isHullBlock,
        final int hullBlockCount,
        final double[] volFraction,
        final double[] u,
        final double[] blockWaterDepths
    ) {
        this.n = n;
        this.x = x;
        this.y = y;
        this.z = z;
        this.E = E;
        this.yieldStress = yieldStress;
        this.neighbors = neighbors;
        this.springK = springK;
        this.neighborCount = neighborCount;
        this.exposedFaceCount = exposedFaceCount;
        this.isHullBlock = isHullBlock;
        this.hullBlockCount = hullBlockCount;
        this.volFraction = volFraction;
        this.u = u;
        this.blockWaterDepths = blockWaterDepths;

        this.blockIndex = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap(n * 4 / 3 + 1);
        this.blockIndex.defaultReturnValue(-1);
        for (int i = 0; i < n; i++) {
            this.blockIndex.put(pack(x[i], y[i], z[i]), i);
        }
    }

    // 21 bits per axis (63 bits total) so the three fields cannot overlap.
    // Valid range: -1048575 .. 1048575 per axis, which comfortably covers
    // sublevel-local coordinates. Do not shrink these fields: the previous
    // 19-bit layout with shifts 18/36 aliased x and y on their shared bit 18.
    private static long pack(final int x, final int y, final int z) {
        return ((long) x & 0x1FFFFF) | (((long) y & 0x1FFFFF) << 21) | (((long) z & 0x1FFFFF) << 42);
    }

    // ============================================================
    //  Build RHS ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â hydrostatic pressure and face torque on wet faces
    // ============================================================

    public void buildRHS(final double[] b) {
        Arrays.fill(b, 0.0);
        boolean anyUnderwater = false;
        for (int i = 0; i < n; i++) {
            final double depth = blockWaterDepths[i];
            if (depth <= 0) continue;
            anyUnderwater = true;
            for (int dir = 0; dir < 6; dir++) {
                if (neighbors[i][dir] >= 0) continue;
                if (waterFace != null && !waterFace[i][dir]) continue;
                final int comp = dir / 2;
                final double sign = (dir % 2 == 0) ? -1.0 : 1.0;
                double localPressure = this.rhoG * depth * volFraction[i];
                if (useMoonPool) {
                    final double faceDot = DX[dir] * localDownX + DY[dir] * localDownY + DZ[dir] * localDownZ;
                    if (faceDot > 0.7) {
                        final int belowIdx = findBlock(x[i] + DX[dir], y[i] + DY[dir], z[i] + DZ[dir]);
                        if (belowIdx >= 0 && isHullBlock[belowIdx]) {
                            localPressure *= MOON_POOL_FACTOR;
                        }
                    }
                }
                // pressure force acts along the face normal through the face centre,
                // i.e. through the block centre -> zero torque about the centre.
                // Bending is induced kinematically through the shear/moment springs.
                final double fx = DX[dir] != 0 ? -sign * localPressure : 0;
                final double fy = DY[dir] != 0 ? -sign * localPressure : 0;
                final double fz = DZ[dir] != 0 ? -sign * localPressure : 0;
                b[6 * i + 0] += fx;
                b[6 * i + 1] += fy;
                b[6 * i + 2] += fz;
            }
        }
        if (!anyUnderwater) {
            Arrays.fill(b, 0.0);
        }
    }

    // ============================================================
    //  Sparse mat-vec: Ku = K * uvec  (6 DOF per node)
    // ============================================================

    public void applyK(final double[] uvec, final double[] Ku) {
        Arrays.fill(Ku, 0.0);
        if (n > 200) {
            IntStream.range(0, n).parallel().forEach(i -> accumulate(i, uvec, Ku));
        } else {
            for (int i = 0; i < n; i++) {
                accumulate(i, uvec, Ku);
            }
        }
    }

    private void accumulate(final int i, final double[] uvec, final double[] Ku) {
        final int i6 = 6 * i;
        final double nuI = nuBlock != null ? nuBlock[i] : poissonRatio;
        for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
            final int j = neighbors[i][dir];
            if (j < 0) continue;
            final int j6 = 6 * j;
            final double k = -springK[i][dir]; // stored negative -> positive stiffness

            final double nx = DIR_COS[dir][0], ny = DIR_COS[dir][1], nz = DIR_COS[dir][2];
            final double L = 1.0 / INV_DIST[dir];
            final double h = 0.5 * L; // half centre distance

            // attachment point displacements: U = u + w x r, r = +/- h * n
            final double uAx = uvec[i6], uAy = uvec[i6 + 1], uAz = uvec[i6 + 2];
            final double wIx = uvec[i6 + 3], wIy = uvec[i6 + 4], wIz = uvec[i6 + 5];
            final double uBx = uvec[j6], uBy = uvec[j6 + 1], uBz = uvec[j6 + 2];
            final double wJx = uvec[j6 + 3], wJy = uvec[j6 + 4], wJz = uvec[j6 + 5];

            // U_A = u_i + w_i x (h n); U_B = u_j + w_j x (-h n)
            final double UAx = uAx + wIy * (h * nz) - wIz * (h * ny);
            final double UAy = uAy + wIz * (h * nx) - wIx * (h * nz);
            final double UAz = uAz + wIx * (h * ny) - wIy * (h * nx);
            final double UBx = uBx - wJy * (h * nz) + wJz * (h * ny);
            final double UBy = uBy - wJz * (h * nx) + wJx * (h * nz);
            final double UBz = uBz - wJx * (h * ny) + wJy * (h * nx);

            final double relX = UBx - UAx, relY = UBy - UAy, relZ = UBz - UAz;
            final double eN = nx * relX + ny * relY + nz * relZ;

            // shear basis (precomputed per direction)
            final double t1x = T1[dir][0], t1y = T1[dir][1], t1z = T1[dir][2];
            final double t2x = T2[dir][0], t2y = T2[dir][1], t2z = T2[dir][2];

            final double eS1 = t1x * relX + t1y * relY + t1z * relZ;
            final double eS2 = t2x * relX + t2y * relY + t2z * relZ;

            final double kS = k / (2.0 * (1.0 + nuI));
            final double kBend = k / 12.0;
            final double kTor = k / 6.0;

            final double fN = k * eN;
            final double fS1 = kS * eS1;
            final double fS2 = kS * eS2;

            // force on i from normal+shear springs
            final double FxI = (fN * nx + fS1 * t1x + fS2 * t2x);
            final double FyI = (fN * ny + fS1 * t1y + fS2 * t2y);
            final double FzI = (fN * nz + fS1 * t1z + fS2 * t2z);

            // rotational springs: moment = -k (w_j - w_i) on i, about transverse axes for bending, about n for torsion
            final double wRelX = wJx - wIx, wRelY = wJy - wIy, wRelZ = wJz - wIz;
            final double bend1 = wRelX * t1x + wRelY * t1y + wRelZ * t1z;
            final double bend2 = wRelX * t2x + wRelY * t2y + wRelZ * t2z;
            final double tor = wRelX * nx + wRelY * ny + wRelZ * nz;
            final double MxI = kBend * bend1 * t1x + kBend * bend2 * t2x + kTor * tor * nx;
            final double MyI = kBend * bend1 * t1y + kBend * bend2 * t2y + kTor * tor * ny;
            final double MzI = kBend * bend1 * t1z + kBend * bend2 * t2z + kTor * tor * nz;

            // torque on i from face forces: rA x F, rA = h n
            final double TxI = (h * ny) * FzI - (h * nz) * FyI + MxI;
            final double TyI = (h * nz) * FxI - (h * nx) * FzI + MyI;
            final double TzI = (h * nx) * FyI - (h * ny) * FxI + MzI;

            // Ku = -(assembled spring force/torque)  (stiffness form)
            Ku[i6]     -= FxI;
            Ku[i6 + 1] -= FyI;
            Ku[i6 + 2] -= FzI;
            Ku[i6 + 3] -= TxI;
            Ku[i6 + 4] -= TyI;
            Ku[i6 + 5] -= TzI;
        }
    }

    // ============================================================
    //  Jacobi preconditioner (diagonal of K + ÃƒÅ½Ã‚Â±I)
    // ============================================================

    private void ensurePreconditioner(final double avgE) {
        if (diagPrecon != null) return;
        diagPrecon = new double[6 * n];
        for (int i = 0; i < n; i++) {
            for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
                if (neighbors[i][dir] < 0) continue;
                final double k = -springK[i][dir]; // negative stored -> positive
                final double nuI = nuBlock != null ? nuBlock[i] : poissonRatio;
                final double kS = k / (2.0 * (1.0 + nuI));
                final double kBend = k / 12.0;
                final double kTor = k / 6.0;
                final int i6 = 6 * i;
                diagPrecon[i6]     += k * DIR_COS[dir][0] * DIR_COS[dir][0];
                diagPrecon[i6 + 1] += k * DIR_COS[dir][1] * DIR_COS[dir][1];
                diagPrecon[i6 + 2] += k * DIR_COS[dir][2] * DIR_COS[dir][2];
                diagPrecon[i6 + 3] += kS * (DIR_COS[dir][1] * DIR_COS[dir][1] + DIR_COS[dir][2] * DIR_COS[dir][2]) + kBend + kTor;
                diagPrecon[i6 + 4] += kS * (DIR_COS[dir][0] * DIR_COS[dir][0] + DIR_COS[dir][2] * DIR_COS[dir][2]) + kBend + kTor;
                diagPrecon[i6 + 5] += kS * (DIR_COS[dir][0] * DIR_COS[dir][0] + DIR_COS[dir][1] * DIR_COS[dir][1]) + kBend + kTor;
            }
        }
        // A strictly positive diagonal is required. Blocks with no spring
        // neighbours leave an exact zero, which would divide by tikhonovAlpha
        // alone and blow up z.
        final double floor = Math.max(avgE * 1e-6, 1e-12);
        for (int k = 0; k < 6 * n; k++) {
            if (!(diagPrecon[k] > floor)) diagPrecon[k] = floor;
        }
    }

    // ============================================================
    //  Preconditioned Conjugate Gradient solver
    //  with Tikhonov regularization
    // ============================================================

    public void solveCG(final double[] b) {
        solveCG(b, 0);
    }

    public void solveCG(final double[] b, final int maxIterOverride) {
        final int N6 = 6 * n;
        double bNorm = 0;
        for (int k = 0; k < N6; k++) bNorm += b[k] * b[k];
        if (bNorm < 1e-30) {
            Arrays.fill(u, 0.0);
            return;
        }

        // Cold start on first solve (previousBNorm < 0 means new SolverCore
        // or world reload). Warm-start only on repeated calls within the same
        // SolverCore where previousBNorm is a valid reference.
        // On a fresh SolverCore, only zero-start if no usable warm displacement was
        // passed in (otherwise the caller explicitly handed us a warm u).
        if (previousBNorm < 0) {
            boolean hasU = false;
            for (int k = 0; k < N6; k++) {
                if (u[k] != 0.0) { hasU = true; break; }
            }
            if (!hasU) {
                Arrays.fill(u, 0.0);
            }
        } else if (bNorm > 1e-30) {
            final double scale = bNorm / previousBNorm;
            if (Math.abs(scale - 1.0) > 1e-6) {
                for (int k = 0; k < N6; k++) u[k] *= scale;
            }
        }
        previousBNorm = bNorm;

        final double avgE = n > 0 ? Arrays.stream(E).summaryStatistics().getAverage() : 0.0;
        final double tikhonovAlpha = tikhonovAlphaFraction * avgE;
        ensurePreconditioner(avgE);

        if (cgR == null || cgR.length != N6) {
            cgR = new double[N6];
            cgZ = new double[N6];
            cgP = new double[N6];
            cgAp = new double[N6];
        }
        final double[] r = cgR;
        final double[] z = cgZ;
        final double[] p = cgP;
        final double[] Ap = cgAp;

        applyK(u, r);
        for (int k = 0; k < N6; k++) {
            r[k] = b[k] - r[k] - tikhonovAlpha * u[k];
        }

        for (int k = 0; k < N6; k++) {
            z[k] = r[k] / (diagPrecon[k] + tikhonovAlpha);
        }
        System.arraycopy(z, 0, p, 0, N6);
        double rz = dot(r, z);

        final int maxIter = maxIterOverride > 0 ? maxIterOverride : Math.max(300, N6);
        for (int iter = 0; iter < maxIter; iter++) {
            applyK(p, Ap);
            for (int k = 0; k < N6; k++) Ap[k] += tikhonovAlpha * p[k];
            final double pAp = dot(p, Ap);
            if (pAp <= 0) break;

            final double alpha = rz / pAp;
            for (int k = 0; k < N6; k++) {
                u[k] += alpha * p[k];
                r[k] -= alpha * Ap[k];
            }

            if (dot(r, r) < 1e-12 * bNorm) break;

            for (int k = 0; k < N6; k++) {
                z[k] = r[k] / (diagPrecon[k] + tikhonovAlpha);
            }
            final double rzNew = dot(r, z);
            if (rzNew <= 0) break;

            final double beta = rzNew / rz;
            for (int k = 0; k < N6; k++) p[k] = z[k] + beta * p[k];
            rz = rzNew;
        }
    }

    // ============================================================
    //  Rigid-body mode removal (6 modes: 3 translation + 3 rotation),
    //  mass-weighted with a full 3x3 inertia tensor
    // ============================================================

    public void removeRigidBodyMode(final double[] v) {
        double mSum = 0, sumX = 0, sumY = 0, sumZ = 0;
        for (int i = 0; i < n; i++) {
            final double m = Math.max(volFraction[i], 1e-3);
            mSum += m;
            sumX += m * v[6 * i];
            sumY += m * v[6 * i + 1];
            sumZ += m * v[6 * i + 2];
        }
        final double avgX = sumX / mSum;
        final double avgY = sumY / mSum;
        final double avgZ = sumZ / mSum;

        // mean angular velocity (volume-weighted)
        double wSumX = 0, wSumY = 0, wSumZ = 0;
        for (int i = 0; i < n; i++) {
            final double m = Math.max(volFraction[i], 1e-3);
            wSumX += m * v[6 * i + 3];
            wSumY += m * v[6 * i + 4];
            wSumZ += m * v[6 * i + 5];
        }
        final double avgWX = wSumX / mSum, avgWY = wSumY / mSum, avgWZ = wSumZ / mSum;

        double cx = 0, cy = 0, cz = 0, wSum = 0;
        for (int i = 0; i < n; i++) {
            final double m = Math.max(volFraction[i], 1e-3);
            cx += m * x[i]; cy += m * y[i]; cz += m * z[i]; wSum += m;
        }
        cx /= wSum; cy /= wSum; cz /= wSum;

        // angular momentum L = sum r x (m v) and full inertia tensor
        double Lx = 0, Ly = 0, Lz = 0;
        double Ixx = 0, Iyy = 0, Izz = 0, Ixy = 0, Ixz = 0, Iyz = 0;
        for (int i = 0; i < n; i++) {
            final double m = Math.max(volFraction[i], 1e-3);
            final double rx = x[i] - cx, ry = y[i] - cy, rz = z[i] - cz;
            final double vx = v[6 * i], vy = v[6 * i + 1], vz = v[6 * i + 2];
            Lx += m * (ry * vz - rz * vy);
            Ly += m * (rz * vx - rx * vz);
            Lz += m * (rx * vy - ry * vx);
            Ixx += m * (ry * ry + rz * rz);
            Iyy += m * (rx * rx + rz * rz);
            Izz += m * (rx * rx + ry * ry);
            Ixy -= m * rx * ry;
            Ixz -= m * rx * rz;
            Iyz -= m * ry * rz;
        }
        // solve I * w = L via Cramer (3x3)
        final double a00 = Ixx, a01 = Ixy, a02 = Ixz;
        final double a10 = Ixy, a11 = Iyy, a12 = Iyz;
        final double a20 = Ixz, a21 = Iyz, a22 = Izz;
        final double det = a00 * (a11 * a22 - a12 * a21) - a01 * (a10 * a22 - a12 * a20) + a02 * (a10 * a21 - a11 * a20);
        double wx = 0, wy = 0, wz = 0;
        if (Math.abs(det) > 1e-30) {
            wx = ((a11 * a22 - a12 * a21) * Lx + (a02 * a21 - a01 * a22) * Ly + (a01 * a12 - a02 * a11) * Lz) / det;
            wy = ((a12 * a20 - a10 * a22) * Lx + (a00 * a22 - a02 * a20) * Ly + (a10 * a02 - a00 * a12) * Lz) / det;
            wz = ((a10 * a21 - a11 * a20) * Lx + (a01 * a20 - a00 * a21) * Ly + (a00 * a11 - a01 * a10) * Lz) / det;
        }

        for (int i = 0; i < n; i++) {
            final double rx = x[i] - cx, ry = y[i] - cy, rz = z[i] - cz;
            v[6 * i]     -= avgX + (wy * rz - wz * ry);
            v[6 * i + 1] -= avgY + (wz * rx - wx * rz);
            v[6 * i + 2] -= avgZ + (wx * ry - wy * rx);
        }
        // subtract the rigid-mode angular velocity from the spin DOF
        for (int i = 0; i < n; i++) {
            v[6 * i + 3] -= wx;
            v[6 * i + 4] -= wy;
            v[6 * i + 5] -= wz;
        }
        // then kill any residual mean spin
        double mwx = 0, mwy = 0, mwz = 0;
        for (int i = 0; i < n; i++) {
            mwx += v[6 * i + 3]; mwy += v[6 * i + 4]; mwz += v[6 * i + 5];
        }
        mwx /= n; mwy /= n; mwz /= n;
        for (int i = 0; i < n; i++) {
            v[6 * i + 3] -= mwx;
            v[6 * i + 4] -= mwy;
            v[6 * i + 5] -= mwz;
        }
    }

    // ============================================================
    //  Von Mises stress
    // ============================================================

    public double computeVonMises(final int blockIdx) {
        final double Ei = E[blockIdx];
        final int i6 = 6 * blockIdx;
        final double uix = u[i6], uiy = u[i6 + 1], uiz = u[i6 + 2];
        final double wIx = u[i6 + 3], wIy = u[i6 + 4], wIz = u[i6 + 5];

        double gxx = 0, gxy = 0, gxz = 0;
        double gyx = 0, gyy = 0, gyz = 0;
        double gzx = 0, gzy = 0, gzz = 0;
        double denomX = 0, denomY = 0, denomZ = 0;

        for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
            final int j = neighbors[blockIdx][dir];
            if (j < 0) continue;
            final int j6 = 6 * j;
            final double dx = DX[dir], dy = DY[dir], dz = DZ[dir];
            // material point displacement of j closest to i: u_j + w_j x d_ji
            final double dux = (u[j6]     - uix) + (u[j6 + 4] * dz - u[j6 + 5] * dy);
            final double duy = (u[j6 + 1] - uiy) + (u[j6 + 5] * dx - u[j6 + 3] * dz);
            final double duz = (u[j6 + 2] - uiz) + (u[j6 + 3] * dy - u[j6 + 4] * dx);

            // volume-weighted neighbour coupling so edges/holes fade correctly
            final double w = volFraction[j] / (volFraction[blockIdx] + volFraction[j] + 1e-9);
            gxx += dux * dx * w; gxy += dux * dy * w; gxz += dux * dz * w;
            gyx += duy * dx * w; gyy += duy * dy * w; gyz += duy * dz * w;
            gzx += duz * dx * w; gzy += duz * dy * w; gzz += duz * dz * w;
            denomX += dx * dx * w; denomY += dy * dy * w; denomZ += dz * dz * w;
        }

        if (denomX > 0) { final double inv = 1.0 / denomX; gxx *= inv; gyx *= inv; gzx *= inv; }
        if (denomY > 0) { final double inv = 1.0 / denomY; gxy *= inv; gyy *= inv; gzy *= inv; }
        if (denomZ > 0) { final double inv = 1.0 / denomZ; gxz *= inv; gyz *= inv; gzz *= inv; }

        final double exx = gxx;
        final double eyy = gyy;
        final double ezz = gzz;
        final double exy = (gxy + gyx) * 0.5;
        final double eyz = (gyz + gzy) * 0.5;
        final double ezx = (gxz + gzx) * 0.5;

        final double nu = nuBlock != null ? nuBlock[blockIdx] : poissonRatio;
        final double lambda = Ei * nu / ((1.0 + nu) * (1.0 - 2.0 * nu));
        final double mu = Ei / (2.0 * (1.0 + nu));
        final double eVol = exx + eyy + ezz;
        final double sxx = 2.0 * mu * exx + lambda * eVol;
        final double syy = 2.0 * mu * eyy + lambda * eVol;
        final double szz = 2.0 * mu * ezz + lambda * eVol;
        final double sxy = 2.0 * mu * exy;
        final double syz = 2.0 * mu * eyz;
        final double szx = 2.0 * mu * ezx;

        return Math.sqrt(0.5 * (
                (sxx - syy) * (sxx - syy) +
                (syy - szz) * (syy - szz) +
                (szz - sxx) * (szz - sxx) +
                6.0 * (sxy * sxy + syz * syz + szx * szx)
        ));
    }

    // ============================================================
    //  Crush depth
    // ============================================================

    public double[] computeCrushDepth() {
        final double[] result = new double[n + 1];
        double globalDepth = Double.POSITIVE_INFINITY;
        int worstBlock = -1;

        double maxBlockDepth = 0;
        for (int i = 0; i < n; i++) {
            if (blockWaterDepths[i] > maxBlockDepth) maxBlockDepth = blockWaterDepths[i];
        }
        if (maxBlockDepth <= 0) {
            result[n] = -1;
            return result;
        }

        for (int i = 0; i < n; i++) {
            if (effYield(i) <= 0) {
                result[i] = Double.POSITIVE_INFINITY;
                continue;
            }
            final double blockDepth = blockWaterDepths[i];
            if (blockDepth <= 0) {
                result[i] = Double.POSITIVE_INFINITY;
                continue;
            }
            final double vm = computeVonMises(i);
            if (vm <= 1e-30) {
                result[i] = Double.POSITIVE_INFINITY;
                continue;
            }
            final double ratio = vm / effYield(i);
            final double depth = blockDepth / ratio;
            if (depth > 1e15) {
                result[i] = Double.POSITIVE_INFINITY;
                continue;
            }
            result[i] = depth;
            if (depth < globalDepth) {
                globalDepth = depth;
                worstBlock = i;
            }
        }
        result[n] = (double) worstBlock;
        return result;
    }
    public double[] getStressDistribution() {
        final double[] dist = new double[n];
        if (n > 200) {
            IntStream.range(0, n).parallel().forEach(i -> {
                if (blockWaterDepths[i] <= 0) {
                    dist[i] = 0;
                } else {
                    final double vm = computeVonMises(i);
                    if (vm <= 1e-30) {
                        dist[i] = 0;
                    } else {
                        dist[i] = Math.min(1.0, vm / effYield(i));
                    }
                }
            });
        } else {
            for (int i = 0; i < n; i++) {
                if (blockWaterDepths[i] <= 0) {
                    dist[i] = 0;
                } else {
                    final double vm = computeVonMises(i);
                    if (vm <= 1e-30) {
                        dist[i] = 0;
                    } else {
                        dist[i] = Math.min(1.0, vm / effYield(i));
                    }
                }
            }
        }
        return dist;
    }

    // ============================================================
    //  Utilities
    // ============================================================

    public static double dot(final double[] a, final double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }

    private double effYield(final int i) {
        return yieldEffective != null ? yieldEffective[i] : yieldStress[i];
    }

    public int findBlock(final int bx, final int by, final int bz) {
        final int idx = blockIndex.get(pack(bx, by, bz));
        return idx >= 0 ? idx : -1;
    }

    public void solve() {
        final long t0 = System.nanoTime();
        final double[] b = new double[6 * n];
        buildRHS(b);
        solveCG(b);
        removeRigidBodyMode(u);
        solveTimeNanos = System.nanoTime() - t0;
    }
}
