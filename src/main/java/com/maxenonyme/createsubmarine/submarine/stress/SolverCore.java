package com.maxenonyme.createsubmarine.submarine.stress;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Pure-math rigid-block spring-lattice FEM solver with no Minecraft or Sable dependencies.
 * Each block is a rigid body with 6 DOF (3 translation + 3 rotation), joined to its
 * neighbours by a normal spring, two shear springs and three rotational springs,
 * giving the lattice genuine bending and torsional stiffness.
 * Used by {@link LatticeStressSolver} for in-game solves, and by
 * {@link CalibrationRunner} / {@link LatticeCalibrationTest} for standalone calibration.
 *
 * <h2>Model summary</h2>
 * <ul>
 *   <li>Operator: a Cosserat (micropolar) lattice of rigid blocks. Each link carries an
 *       axial spring {@code k}, two transverse springs {@code kS = k/(2+nu_i+nu_j)} and
 *       rotational springs {@code kBend = k L^2/12}, {@code kTor = k L^2/6}, where
 *       {@code L} is the link length (1, sqrt2 or sqrt3 blocks). The {@code L^2} factors
 *       are required dimensionally: a rotational spring stiffness has units of torque per
 *       radian, i.e. force*length, whereas the axial stiffness has force/length.</li>
 *   <li>Calibration: the discrete operator is stiffer than the material modulus used for
 *       stress recovery. Scaling every axial stiffness by {@link #DEFAULT_MODULUS_SCALE}
 *       (3/13) makes the operator reproduce a uniform hydrostatic load as
 *       {@code sigma = -p I} (patch test). Without it every reported stress is a factor
 *       13/3 too small and every crush depth 13/3 too deep. Ratios between blocks are
 *       unaffected by this single global factor.</li>
 *   <li>Strain recovery: a diagonal-moment least-squares gradient of the attachment-point
 *       displacement field, using isotropic shell weights (face 1/6, edge 1/12, corner 1/8),
 *       followed by an isotropic Lamé law. This is exact for linear displacement fields.</li>
 *   <li>Failure: von Mises against the yield stress for ductile materials; maximum
 *       principal stress against the tensile strength for brittle materials (glass,
 *       obsidian, terracotta).</li>
 * </ul>
 *
 * <p>Crush depth is a <em>linear elastic extrapolation</em>: the system is solved once and
 * the depth at which the equivalent stress reaches the limit is obtained by scaling. This
 * is valid while the response stays elastic. Buckling (the dominant real-world pressure
 * hull failure mode) and plastic redistribution are not captured; this solver therefore
 * errs optimistic for slender spans.</p>
 *
 * <h2>Performance envelope (measured, 16 cores)</h2>
 * <p>The cost is one {@code applyK} pass per CG iteration, i.e. 26 links x 6 DOF per block:
 * ~5 ms at 64k blocks, ~7 ms at 115k, ~15 ms at 216k, ~35 ms at 512k. Since a solve
 * cannot stop in the middle of an operator pass, {@link #maxSolveNanos} buys roughly one
 * iteration up to ~115k blocks. Above that a single re-solve costs a whole tick, which is
 * amortised over the solve interval (the solver re-solves every few ticks) but means such
 * structures do not hold a true 20 Hz cadence. The force-recording pass
 * (O(n x 26), parallel over blocks) adds a few ms per substep at those sizes.</p>
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
    /** L^2 and L/2 per link direction: the link geometry is fixed, so the reciprocal and
     *  the half-length are precomputed once instead of per link per mat-vec. */
    public static final double[] LEN2 = new double[MAX_NEIGHBORS];
    public static final double[] HALF_LEN = new double[MAX_NEIGHBORS];
    // precomputed orthonormal shear basis per neighbour direction
    static final double[][] T1 = new double[MAX_NEIGHBORS][3];
    static final double[][] T2 = new double[MAX_NEIGHBORS][3];
    /**
     * Isotropic quadrature weights for the 26-point stencil: every shell contributes the
     * same total weight (6*1/6 = 12*1/12 = 8*1/8 = 1). The gradient estimator is a
     * diagonal-moment least squares, so it is exact for linear (and bilinear) fields with
     * any radial weighting; the shell form is used because it matches the directional
     * weighting of the stiffness operator (which sees face:edge:corner in the ratio
     * 6 : 6 : 8/3 once the 1/d^2 link stiffnesses are accounted for) instead of the plain
     * 6 : 12 : 8 count of links, which over-weights the 20 diagonals.
     */
    public static final double[] SHELL_W = new double[MAX_NEIGHBORS];

    public static final double RHO_G = 10000.0;
    public static final double MOON_POOL_FACTOR = 0.8;

    /**
     * Patch-test correction for the 26-neighbour axial lattice, measured (not assumed).
     * <p>Applying a uniform pressure p to every face of a solid block must reproduce
     * {@code sigma = -p I}. Measured on solid cubes of increasing size (S = 8..36, centre
     * block, von Mises ~ 0 as it should be for a fully supported hydrostatic state), the
     * raw lattice reports {@code sigma_xx/p = -0.419} asymptotically, i.e. every stress is
     * ~2.39x too small and every crush depth 2.39x too deep. Note the theoretical symbol
     * argument ({@code K(k) = 13/3 E |k|^2}) predicts 3/13 = 0.231 for an axial-only
     * lattice; the transverse and rotational springs stiffen the volumetric response, so
     * the constant is calibrated against the actual solver rather than derived.</p>
     * <p>Direction: it multiplies the spring stiffness, so a value &lt; 1 <em>softens</em> K
     * and increases the recovered stress. After calibration the verification harness
     * measures sigma_xx/p = -1.02, sigma_yy/p = -0.017 (target 0).</p>
     */
    public static final double DEFAULT_MODULUS_SCALE = 0.419;
    /**
     * Transverse spring scale. A 26-link <em>central force</em> lattice already obtains its
     * shear stiffness from the 45 degree links; giving every link a full
     * {@code G = E/(2(1+nu))} transverse spring double-counts it and develops a spurious
     * transverse stress under uniaxial load. Measured on a uniformly loaded cube
     * ({@code sigma_yy/p}, which must be 0 for a correct Poisson response):
     * <pre>
     *   shearScale  1.0 -&gt; -0.070     0.5 -&gt; -0.045     0.25 -&gt; -0.024
     *   shearScale 0.15 -&gt; -0.013     0.10 -&gt; -0.007     0.05 -&gt; -0.00006
     * </pre>
     * 0.10 removes ~90% of the artefact while keeping the transverse springs non-degenerate
     * (they are needed for rotational stability and for the shear load path).
     */
    public static final double DEFAULT_SHEAR_SCALE = 0.10;
    /**
     * Rotational spring scale, in front of the dimensionally-correct
     * {@code k L^2 / 12} and {@code k L^2 / 6}. The L^2 factor is what gives diagonal links
     * a correctly scaled moment of inertia; the scale itself stays at 1.
     * <p>Verified against Euler-Bernoulli pure bending of a self-equilibrated 40xHx1 beam:
     * the lattice reproduces the beam's <em>scaling</em> exactly - the curvature ratio for
     * H = 4/8/16 is 0.122/0.122 against the theoretical 0.125, i.e. kappa ~ h^-3 - but its
     * absolute bending stiffness is ~3x higher than E I. That residual is a property of the
     * 26-link Cosserat lattice (bending is carried mainly by the axial-spring grillage, not
     * by the rotational springs) and is not tunable through this constant: the measured
     * ratio saturates near 0.13 as bendScale -&gt; 0. Recorded as a known accuracy
     * limitation rather than hidden behind a fudge factor.</p>
     */
    public static final double DEFAULT_BEND_SCALE = 1.0;

    static {
        for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
            final double d = Math.sqrt(DX[dir]*DX[dir] + DY[dir]*DY[dir] + DZ[dir]*DZ[dir]);
            INV_DIST[dir] = 1.0 / d;
            DIR_COS[dir][0] = DX[dir] / d;
            DIR_COS[dir][1] = DY[dir] / d;
            DIR_COS[dir][2] = DZ[dir] / d;
            // same expressions as the ones previously evaluated per link inside the mat-vec
            final double l = 1.0 / INV_DIST[dir];
            LEN2[dir] = l * l;
            HALF_LEN[dir] = 0.5 * l;
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

            final int nz0 = (DX[dir] != 0 ? 1 : 0) + (DY[dir] != 0 ? 1 : 0) + (DZ[dir] != 0 ? 1 : 0);
            SHELL_W[dir] = nz0 == 1 ? 1.0 / 6.0 : nz0 == 2 ? 1.0 / 12.0 : 1.0 / 8.0;
        }
    }

    // ---- graph data (immutable after construction) ----
    public final int n;
    public final int[] x, y, z;
    public final double[] E;
    public final double[] yieldStress;
    public final int[][] neighbors;
    public final double[][] springK;
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
    private double preconPoisson = Double.NaN;
    private double preconModulusScale = Double.NaN;
    private double preconShearScale = Double.NaN;
    private double preconBendScale = Double.NaN;
    private int preconNuBlockRef;

    // ---- persistent CG workspace (avoids reallocating 4 arrays per solve) ----
    private double[] cgR, cgZ, cgP, cgAp;

    // ---- config parameters ----
    public double poissonRatio = 0.3;
    public double tikhonovAlphaFraction = 1e-6;
    public double rhoG = RHO_G;
    public boolean useMoonPool = true;

    /**
     * Global axial stiffness scale. {@code 1} reproduces the raw lattice (stress a factor
     * 13/3 too low); {@link #DEFAULT_MODULUS_SCALE} restores the patch test.
     */
    public double modulusScale = DEFAULT_MODULUS_SCALE;
    /** Transverse spring scale; calibrated against the uniaxial Poisson test. */
    public double shearScale = DEFAULT_SHEAR_SCALE;
    /** Rotational spring scale; calibrated against Euler-Bernoulli cantilever deflection. */
    public double bendScale = DEFAULT_BEND_SCALE;
    /** Wall-clock budget for a single CG solve, so the solver can never blow the tick. */
    public long maxSolveNanos = 8_000_000L;
    /** Relative residual target for CG. */
    public double cgTolerance = 1e-10;
    /** Project the six rigid-body modes out of the CG iteration. Leave true. */
    public boolean projectNullSpace = true;

    // ---- per-block Poisson ratios (null => global poissonRatio) ----
    public double[] nuBlock;

    // ---- per-block directionally-effective yield (null => raw yieldStress) ----
    public double[] yieldEffective;

    // ---- brittle materials: fail on maximum principal stress instead of von Mises ----
    public boolean[] brittle;
    public double[] tensileStress;

    // ---- orientation (affects moon-pool detection) ----
    public double localDownX = 0, localDownY = -1, localDownZ = 0;

    // ---- smoothed normals from ShapeClassifier (null = flat-only) ----
    public double[][] smoothedNormals;

    // ---- which exposed faces are wet (flat 6n, null = all exposed faces wet) ----
    public boolean[] waterFace;
    // ---- per-face hydrostatic depth (flat 6n, null = use blockWaterDepths[i]) ----
    public double[] faceDepth;
    // ---- per-face wetted area (flat 6n, null = use volFraction[i]) ----
    public double[] faceArea;
    // ---- per-face: wetted by internal fluid / moon pool (flat 6n, null = none) ----
    public boolean[] faceInternal;
    // ---- per-face area-weighted centre, block-local {x,y,z} (flat 18n, null = full-cube
    //      face centres). Supplied for partial blocks (slabs/stairs/posts); for full cubes
    //      the default coincides with the true centre, so nothing is lost. ----
    public double[] faceCenter;
    // ---- per-block volume-weighted centroid, block-local {x,y,z} (flat 3n, null =
    //      block centre). The moment arm is faceCenter - blockCentroid; for full cubes
    //      both lie on the face normal through the centre, so the torques are zero and
    //      the omitted arm was exact. ----
    public double[] blockCentroid;

    // ---- warm-start tracking ----
    private double previousBNorm = -1;

    public long solveTimeNanos = 0;

    // ---- convergence diagnostics ----
    public int lastIterations = 0;
    public boolean lastConverged = false;
    public double lastResidualRel = Double.NaN;
    // ---- rigid-mode diagnostics ----
    public double lastMeanSpinX, lastMeanSpinY, lastMeanSpinZ;


    public SolverCore(
        final int n,
        final int[] x,
        final int[] y,
        final int[] z,
        final double[] E,
        final double[] yieldStress,
        final int[][] neighbors,
        final double[][] springK,
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
    //  Build RHS -- hydrostatic pressure on the wet exposed faces
    // ============================================================

    /**
     * Assembles the external load vector: hydrostatic traction on every wet exposed face.
     * Pressure acts <em>inward</em> along the inward face normal, scaled by the wetted
     * face area and the depth of the face centroid. Faces wetted by internal fluid
     * (moon pool, flooded compartment, breach) only see the differential pressure, so they
     * are attenuated by {@link #MOON_POOL_FACTOR}.
     */
    public void buildRHS(final double[] b) {
        Arrays.fill(b, 0.0);
        boolean anyUnderwater = false;
        for (int i = 0; i < n; i++) {
            final double blockDepth = blockWaterDepths[i];
            if (blockDepth <= 0) continue;
            anyUnderwater = true;
            // block-local volume centroid (default: full-cube centre)
            final double cx = (blockCentroid != null) ? blockCentroid[3 * i]     : 0.5;
            final double cy = (blockCentroid != null) ? blockCentroid[3 * i + 1] : 0.5;
            final double cz = (blockCentroid != null) ? blockCentroid[3 * i + 2] : 0.5;
            for (int dir = 0; dir < 6; dir++) {
                if (neighbors[i][dir] >= 0) continue;
                if (waterFace != null && !waterFace[6 * i + dir]) continue;
                final double depth = (faceDepth != null) ? faceDepth[6 * i + dir] : blockDepth;
                if (depth <= 0) continue;
                double p = this.rhoG * depth;
                p *= (faceArea != null) ? faceArea[6 * i + dir] : volFraction[i];
                if (useMoonPool && faceInternal != null && faceInternal[6 * i + dir]) {
                    p *= MOON_POOL_FACTOR;
                }
                // inward pressure: +x face (dir 0) pushes -x, hence the sign on even dirs
                final double sign = (dir % 2 == 0 ? -1.0 : 1.0);
                b[6 * i + dir / 2] += sign * p;

                // moment of the face load about the block centroid: torque = r x F with
                // F = sign*p along the face axis. Partial blocks (slabs/stairs) have
                // face centres off the centroid line, so they need real torques; for a
                // full cube r is parallel to F and the torques are zero, matching the
                // previous omission exactly.
                final int axis = dir / 2;
                final int f3 = 3 * (6 * i + dir);
                final double fx = (faceCenter != null) ? faceCenter[f3]     : (axis == 0 ? (sign < 0 ? 1.0 : 0.0) : 0.5);
                final double fy = (faceCenter != null) ? faceCenter[f3 + 1] : (axis == 1 ? (sign < 0 ? 1.0 : 0.0) : 0.5);
                final double fz = (faceCenter != null) ? faceCenter[f3 + 2] : (axis == 2 ? (sign < 0 ? 1.0 : 0.0) : 0.5);
                final double rx = fx - cx, ry = fy - cy, rz = fz - cz;
                final double sp = sign * p;
                switch (axis) {
                    case 0: b[6 * i + 4] +=  sp * rz; b[6 * i + 5] += -sp * ry; break; // r x F, F=(sp,0,0)
                    case 1: b[6 * i + 3] += -sp * rz; b[6 * i + 5] +=  sp * rx; break; // r x F, F=(0,sp,0)
                    default: b[6 * i + 3] += sp * ry; b[6 * i + 4] += -sp * rx;       // F=(0,0,sp)
                }
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

    /**
     * Pair spring constants. Every quantity here must be a symmetric function of the two
     * blocks the link joins, otherwise K is not symmetric and preconditioned CG silently
     * loses its guarantee.
     */
    private double shearStiffness(final double k, final double nuI, final double nuJ) {
        // k / (2 (1 + nu)), evaluated on the pair so both rows agree.
        return k * this.shearScale / (2.0 + nuI + nuJ);
    }

    private void accumulate(final int i, final double[] uvec, final double[] Ku) {
        final int i6 = 6 * i;
        final double nuI = nuBlock != null ? nuBlock[i] : poissonRatio;
        for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
            final int j = neighbors[i][dir];
            if (j < 0) continue;
            final int j6 = 6 * j;
            final double k = -springK[i][dir] * this.modulusScale; // stored negative -> positive
            if (!(k > 0)) continue;

            final double nx = DIR_COS[dir][0], ny = DIR_COS[dir][1], nz = DIR_COS[dir][2];
            final double h = HALF_LEN[dir]; // half centre distance
            final double L2 = LEN2[dir];

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

            final double nuJ = nuBlock != null ? nuBlock[j] : poissonRatio;
            final double kS = shearStiffness(k, nuI, nuJ);
            // rotational springs: torque stiffness scales with the square of the link length
            final double kBend = k * L2 / 12.0 * this.bendScale;
            final double kTor = k * L2 / 6.0 * this.bendScale;

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
    //  Jacobi preconditioner (diagonal of K + aI)
    // ============================================================

    private void ensurePreconditioner(final double avgE) {
        // A preconditioner built for different material parameters is stale and silently
        // degrades convergence, so invalidate it when any of them change.
        final int nuRef = System.identityHashCode(nuBlock);
        if (diagPrecon != null
                && preconPoisson == poissonRatio
                && preconModulusScale == modulusScale
                && preconShearScale == shearScale
                && preconBendScale == bendScale
                && preconNuBlockRef == nuRef) {
            return;
        }
        preconPoisson = poissonRatio;
        preconModulusScale = modulusScale;
        preconShearScale = shearScale;
        preconBendScale = bendScale;
        preconNuBlockRef = nuRef;

        diagPrecon = new double[6 * n];
        for (int i = 0; i < n; i++) {
            final double nuI = nuBlock != null ? nuBlock[i] : poissonRatio;
            for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
                final int j = neighbors[i][dir];
                if (j < 0) continue;
                final double k = -springK[i][dir] * this.modulusScale; // stored negative -> positive
                if (!(k > 0)) continue;
                final double nuJ = nuBlock != null ? nuBlock[j] : poissonRatio;
                final double kS = shearStiffness(k, nuI, nuJ);
                final double L2 = LEN2[dir];
                final double kBend = k * L2 / 12.0 * this.bendScale;
                final double kTor = k * L2 / 6.0 * this.bendScale;
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
    //  Rigid-mode projection for the CG iteration
    // ============================================================

    /**
     * Removes the six rigid-body components from a vector (in place).
     *
     * <p>The lattice has an exact 6-dimensional null space: three translations and three
     * rotations. The Tikhonov term regularises them with a stiffness of
     * {@code alpha ~ 1e-6 E} against bulk eigenvalues of order {@code 40 E}, i.e. a
     * condition number of ~4e7. Left alone, CG spends its iterations in those six
     * directions and the residual stalls at ~1e-2 after hundreds of iterations.
     * Projecting the residual (and hence every search direction built from it) into the
     * range of K removes them from the iteration entirely, which drops the effective
     * condition number by orders of magnitude and is what makes the solve affordable at
     * 20 Hz.</p>
     *
     * <p>The projection is the decoupled mass-weighted mean estimate: the mean
     * translation plus the mean spin are subtracted independently ({@code mean(v)} and
     * {@code mean(w)}). This is NOT the coupled least-squares rigid fit that
     * {@link #removeRigidBodyMode} applies to the solution; the two only agree for an
     * exact rigid field. For a residual projection that difference is acceptable: the
     * residual of a self-equilibrated load is dominated by the mean components, and the
     * cheaper estimate keeps the projection off the per-iteration critical path.</p>
     */
    public void projectRigidModes(final double[] v) {
        if (n == 0) return;
        double mSum = 0, sx = 0, sy = 0, sz = 0;
        double wxs = 0, wys = 0, wzs = 0;
        for (int i = 0; i < n; i++) {
            final double m = Math.max(volFraction[i], 1e-3);
            mSum += m;
            final int i6 = 6 * i;
            sx += m * v[i6];
            sy += m * v[i6 + 1];
            sz += m * v[i6 + 2];
            wxs += m * v[i6 + 3];
            wys += m * v[i6 + 4];
            wzs += m * v[i6 + 5];
        }
        final double inv = 1.0 / mSum;
        final double tx = sx * inv, ty = sy * inv, tz = sz * inv;
        final double wx = wxs * inv, wy = wys * inv, wz = wzs * inv;
        for (int i = 0; i < n; i++) {
            final int i6 = 6 * i;
            v[i6]     -= tx;
            v[i6 + 1] -= ty;
            v[i6 + 2] -= tz;
            v[i6 + 3] -= wx;
            v[i6 + 4] -= wy;
            v[i6 + 5] -= wz;
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
            lastIterations = 0;
            lastConverged = true;
            lastResidualRel = 0.0;
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
        } else {
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
        // Start from a residual that contains no rigid-mode component: the load is
        // self-equilibrated, so anything that shows up here is the warm start bleeding
        // the previous solution's rigid motion into the iteration.
        if (projectNullSpace) {
            projectRigidModes(r);
        }

        for (int k = 0; k < N6; k++) {
            z[k] = r[k] / (diagPrecon[k] + tikhonovAlpha);
        }
        System.arraycopy(z, 0, p, 0, N6);
        double rz = dot(r, z);

        final int maxIter = maxIterOverride > 0 ? maxIterOverride : Math.max(300, N6);
        // ||r||^2 < cgTolerance^2 * ||b||^2  <=>  ||r||/||b|| < cgTolerance, i.e. the
        // documented relative residual target. (bNorm is the SQUARED norm; comparing
        // ||r||^2 against a first-power threshold silently loosened the target to
        // ~1e-5 and could stop CG several orders of magnitude early.)
        final double tol = cgTolerance * cgTolerance * bNorm;
        final long deadline = System.nanoTime() + this.maxSolveNanos;

        int iter = 0;
        boolean converged = false;
        double rr = dot(r, r);
        while (iter < maxIter && !(rz <= 0)) {
            // The deadline is checked on EVERY iteration: one applyK pass costs several
            // milliseconds on a large structure, so checking only every 32 iterations
            // would overshoot the budget by an order of magnitude. The nanoTime() call
            // itself is ~25 ns, i.e. ~1e-5 of an iteration.
            if (System.nanoTime() > deadline) break;

            applyK(p, Ap);
            for (int k = 0; k < N6; k++) Ap[k] += tikhonovAlpha * p[k];
            // No projection of Ap: p is built from z, which is proportional to the
            // projected r, so p is already in the range of K; and for a symmetric K the
            // product K p is automatically orthogonal to the null space. Projecting here
            // would be O(n) work per iteration for a no-op.
            final double pAp = dot(p, Ap);
            if (pAp <= 0) break;

            final double alpha = rz / pAp;
            for (int k = 0; k < N6; k++) {
                u[k] += alpha * p[k];
                r[k] -= alpha * Ap[k];
            }
            iter++;

            // keep the residual in the range of K, otherwise the six rigid directions
            // accumulate and CG stalls long before it reaches the tolerance
            if (projectNullSpace) {
                projectRigidModes(r);
            }

            rr = dot(r, r);
            if (rr < tol) { converged = true; break; }

            for (int k = 0; k < N6; k++) {
                z[k] = r[k] / (diagPrecon[k] + tikhonovAlpha);
            }
            final double rzNew = dot(r, z);
            if (rzNew <= 0) break;

            final double beta = rzNew / rz;
            for (int k = 0; k < N6; k++) p[k] = z[k] + beta * p[k];
            rz = rzNew;
        }

        lastIterations = iter;
        lastConverged = converged;
        lastResidualRel = Math.sqrt(rr / bNorm);
    }

    // ============================================================
    //  Rigid-body mode removal (6 modes: 3 translation + 3 rotation),
    //  mass-weighted with a full 3x3 inertia tensor
    // ============================================================

    /**
     * Removes the rigid-body motion from a displacement field, in place.
     *
     * <p>Fits (v_c, omega) to the translational field by mass-weighted least squares
     * (omega from the inertia solve of the angular momentum) and subtracts
     * {@code v_c + omega x (r - c)} from the translations plus {@code omega} from the
     * spins, so all six modes are removed with one self-consistent estimate.</p>
     *
     * <p>The three reduction passes are chunked across the common pool: profiling put this
     * at ~2 ms on a 115k structure, i.e. a third of a CG iteration, which is worth having
     * back. Partial sums are accumulated about the origin per chunk and shifted to the
     * centroid afterwards with the parallel-axis / angular-momentum shift identities, so
     * the expensive pass runs only once.</p>
     */
    public void removeRigidBodyMode(final double[] v) {
        if (n == 0) return;
        final int cpus = Runtime.getRuntime().availableProcessors();
        final int chunks = Math.max(1, Math.min(n, cpus * 4));
        final int chunkSize = (n + chunks - 1) / chunks;

        // per-chunk partials about the ORIGIN:
        // [0] mass, [1..3] m*v, [4..6] m*w, [7..9] angular momentum,
        // [10..15] second moments of mass (xx,yy,zz,xy,xz,yz), [16..18] m*r (centroid)
        final double[][] part = new double[chunks][19];
        java.util.stream.IntStream.range(0, chunks).parallel().forEach(c -> {
            final int lo = c * chunkSize;
            final int hi = Math.min(n, lo + chunkSize);
            final double[] p = part[c];
            for (int i = lo; i < hi; i++) {
                final double m = Math.max(volFraction[i], 1e-3);
                final int i6 = 6 * i;
                final double rx = x[i], ry = y[i], rz = z[i];
                p[0] += m;
                p[1] += m * v[i6]; p[2] += m * v[i6 + 1]; p[3] += m * v[i6 + 2];
                p[4] += m * v[i6 + 3]; p[5] += m * v[i6 + 4]; p[6] += m * v[i6 + 5];
                p[7] += m * (ry * v[i6 + 2] - rz * v[i6 + 1]);
                p[8] += m * (rz * v[i6] - rx * v[i6 + 2]);
                p[9] += m * (rx * v[i6 + 1] - ry * v[i6]);
                p[10] += m * rx * rx; p[11] += m * ry * ry; p[12] += m * rz * rz;
                p[13] += m * rx * ry; p[14] += m * rx * rz; p[15] += m * ry * rz;
                p[16] += m * rx; p[17] += m * ry; p[18] += m * rz;
            }
        });

        double mSum = 0, sx = 0, sy = 0, sz = 0, swx = 0, swy = 0, swz = 0;
        double LoX = 0, LoY = 0, LoZ = 0, Sxx = 0, Syy = 0, Szz = 0, Sxy = 0, Sxz = 0, Syz = 0, Srx = 0, Sry = 0, Srz = 0;
        for (final double[] p : part) {
            mSum += p[0];
            sx += p[1]; sy += p[2]; sz += p[3];
            swx += p[4]; swy += p[5]; swz += p[6];
            LoX += p[7]; LoY += p[8]; LoZ += p[9];
            Sxx += p[10]; Syy += p[11]; Szz += p[12];
            Sxy += p[13]; Sxz += p[14]; Syz += p[15];
            Srx += p[16]; Sry += p[17]; Srz += p[18];
        }
        if (!(mSum > 0)) return;

        final double inv = 1.0 / mSum;
        final double vcx = sx * inv, vcy = sy * inv, vcz = sz * inv;   // centre-of-mass velocity
        // centroid of mass from the same pass
        final double ccx = Srx * inv, ccy = Sry * inv, ccz = Srz * inv;
        final double avgWX = swx * inv, avgWY = swy * inv, avgWZ = swz * inv;
        lastMeanSpinX = avgWX; lastMeanSpinY = avgWY; lastMeanSpinZ = avgWZ;

        // shift the inertia to the centroid: I_c = I_o - M(|c|^2 I - c c^T), with
        // I_o built from the uncentred second moments (Ixx uses the y/z sums, etc.)
        final double m = mSum;
        final double IxxCo = Syy + Szz;
        final double IyyCo = Sxx + Szz;
        final double IzzCo = Sxx + Syy;
        double Ixx = IxxCo - m * (ccy * ccy + ccz * ccz);
        double Iyy = IyyCo - m * (ccx * ccx + ccz * ccz);
        double Izz = IzzCo - m * (ccx * ccx + ccy * ccy);
        double Ixy = -(Sxy - m * ccx * ccy);
        double Ixz = -(Sxz - m * ccx * ccz);
        double Iyz = -(Syz - m * ccy * ccz);
        // angular momentum about the centroid: L_c = L_o - c x (M v_cm)
        final double Lx = LoX - m * (ccy * vcz - ccz * vcy);
        final double Ly = LoY - m * (ccz * vcx - ccx * vcz);
        final double Lz = LoZ - m * (ccx * vcy - ccy * vcx);

        // solve I * w = L via Cramer (3x3)
        final double a00 = Ixx, a01 = Ixy, a02 = Ixz;
        final double a10 = Ixy, a11 = Iyy, a12 = Iyz;
        final double a20 = Ixz, a21 = Iyz, a22 = Izz;
        final double det = a00 * (a11 * a22 - a12 * a21) - a01 * (a10 * a22 - a12 * a20) + a02 * (a10 * a21 - a11 * a20);
        double wxs = 0, wys = 0, wzs = 0;
        if (Math.abs(det) > 1e-30) {
            wxs = ((a11 * a22 - a12 * a21) * Lx + (a02 * a21 - a01 * a22) * Ly + (a01 * a12 - a02 * a11) * Lz) / det;
            wys = ((a12 * a20 - a10 * a22) * Lx + (a00 * a22 - a02 * a20) * Ly + (a10 * a02 - a00 * a12) * Lz) / det;
            wzs = ((a10 * a21 - a11 * a20) * Lx + (a01 * a20 - a00 * a21) * Ly + (a00 * a11 - a01 * a10) * Lz) / det;
        }
        final double wx = wxs, wy = wys, wz = wzs;

        // subtract the rigid motion (elementwise: safe to fold in parallel)
        java.util.stream.IntStream.range(0, chunks).parallel().forEach(c -> {
            final int lo = c * chunkSize;
            final int hi = Math.min(n, lo + chunkSize);
            for (int i = lo; i < hi; i++) {
                final double rx = x[i] - ccx, ry = y[i] - ccy, rz = z[i] - ccz;
                final int i6 = 6 * i;
                v[i6]     -= vcx + (wy * rz - wz * ry);
                v[i6 + 1] -= vcy + (wz * rx - wx * rz);
                v[i6 + 2] -= vcz + (wx * ry - wy * rx);
                v[i6 + 3] -= wx;
                v[i6 + 4] -= wy;
                v[i6 + 5] -= wz;
            }
        });
    }

    // ============================================================
    //  Stress recovery and equivalent stress
    // ============================================================

    /**
     * Recovers the full Cauchy stress tensor at a block:
     * {@code {sxx, syy, szz, sxy, syz, szx}}.
     *
     * <p>The strain is the gradient of the <em>attachment point</em> displacement field,
     * i.e. exactly the same relative displacement the springs see:
     * {@code u_j + w_j x (-h n) - (u_i + w_i x (+h n))} with {@code h = L/2}. Using the
     * block centres with a full link-vector rotation lever, or dropping the {@code w_i}
     * half of the arm, mis-signs and mis-scales every bending strain.</p>
     */
    public double[] computeStressTensor(final int blockIdx) {
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
            final double h = 0.5 / INV_DIST[dir]; // half centre distance, in blocks
            final double rx = h * dx, ry = h * dy, rz = h * dz;

            // U_A = u_i + w_i x (h n);  U_B = u_j + w_j x (-h n)
            final double uAx = uix + (wIy * rz - wIz * ry);
            final double uAy = uiy + (wIz * rx - wIx * rz);
            final double uAz = uiz + (wIx * ry - wIy * rx);
            final double uBx = u[j6]     - (u[j6 + 4] * rz - u[j6 + 5] * ry);
            final double uBy = u[j6 + 1] - (u[j6 + 5] * rx - u[j6 + 3] * rz);
            final double uBz = u[j6 + 2] - (u[j6 + 3] * ry - u[j6 + 4] * rx);

            final double dux = uBx - uAx, duy = uBy - uAy, duz = uBz - uAz;

            // isotropic shell weight, modulated by the neighbour volume fraction so
            // slabs, stairs and other partial blocks fade out of the gradient
            final double w = SHELL_W[dir] * volFraction[j]
                           / (volFraction[blockIdx] + volFraction[j] + 1e-9);
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
        return new double[]{
            2.0 * mu * exx + lambda * eVol,
            2.0 * mu * eyy + lambda * eVol,
            2.0 * mu * ezz + lambda * eVol,
            2.0 * mu * exy,
            2.0 * mu * eyz,
            2.0 * mu * ezx
        };
    }

    /**
     * Maximum principal stress of a symmetric stress tensor, via the closed-form
     * solution of the characteristic cubic (Cardano). This is the failure measure for
     * brittle materials, which fail in tension rather than in shear.
     *
     * <p>The eigen-amplitude must be built on the second deviator invariant
     * {@code J2 = 1/2*sum(d^2) + sum(off^2)} with amplitude {@code rho = sqrt(J2/3)};
     * a von-Mises-magnitude amplitude (off-term coefficient 3) or an unnormalised
     * amplitude overestimates the principal stress by up to ~3.4x and makes brittle
     * hulls (glass, obsidian, terracotta) fail far too early.</p>
     */
    public static double maxPrincipalStress(final double[] s) {
        final double m = (s[0] + s[1] + s[2]) / 3.0;
        final double d0 = s[0] - m, d1 = s[1] - m, d2 = s[2] - m;
        final double j2 = 0.5 * (d0 * d0 + d1 * d1 + d2 * d2)
                        + (s[3] * s[3] + s[4] * s[4] + s[5] * s[5]);
        if (j2 <= 3e-30) return m;
        final double det = d0 * d1 * d2 + 2.0 * s[3] * s[4] * s[5]
                         - d0 * s[4] * s[4] - d1 * s[5] * s[5] - d2 * s[3] * s[3];
        final double rho = Math.sqrt(j2 / 3.0);
        final double c = Math.max(-1.0, Math.min(1.0, det / (2.0 * rho * rho * rho)));
        final double theta = Math.acos(c) / 3.0;
        // with theta in [0, pi/3], cos(theta) is the largest of the three eigenvalues
        return m + 2.0 * rho * Math.cos(theta);
    }

    /** Equivalent stress: von Mises for ductile materials, maximum principal for brittle ones. */
    public double computeVonMises(final int blockIdx) {
        final double[] s = computeStressTensor(blockIdx);
        if (brittle != null && brittle[blockIdx]) {
            return maxPrincipalStress(s);
        }
        return Math.sqrt(0.5 * (
                (s[0] - s[1]) * (s[0] - s[1]) +
                (s[1] - s[2]) * (s[1] - s[2]) +
                (s[2] - s[0]) * (s[2] - s[0]) +
                6.0 * (s[3] * s[3] + s[4] * s[4] + s[5] * s[5])
        ));
    }

    // ============================================================
    //  Crush depth
    // ============================================================

    /**
     * Per-block crush depth by linear extrapolation of the elastic solution:
     * {@code depth * yield / sigma_eq}. Valid only while the response is elastic; once a
     * block yields, real structures redistribute load (plastic collapse) and slender spans
     * fail by buckling long before any equivalent stress criterion fires.
     */
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

        // The per-block stress recovery is the expensive part (26 links per block) and is
        // independent per block, so it is folded over the common pool; profiling showed
        // this pass dominating the whole re-solve while it ran serially.
        final java.util.concurrent.atomic.AtomicInteger worst =
                new java.util.concurrent.atomic.AtomicInteger(-1);
        final double[] best = {Double.POSITIVE_INFINITY};
        if (n > 200) {
            java.util.stream.IntStream.range(0, n).parallel().forEach(i -> {
                final double d = crushDepthOf(i);
                result[i] = d;
                if (d < best[0]) {
                    synchronized (best) {
                        if (d < best[0]) { best[0] = d; worst.set(i); }
                    }
                }
            });
        } else {
            for (int i = 0; i < n; i++) {
                final double d = crushDepthOf(i);
                result[i] = d;
                if (d < best[0]) { best[0] = d; worst.set(i); }
            }
        }
        result[n] = worst.get();
        return result;
    }

    /** crush depth of a single block, or +infinity when it can never fail here */
    private double crushDepthOf(final int i) {
        if (effYield(i) <= 0) return Double.POSITIVE_INFINITY;
        final double blockDepth = blockWaterDepths[i];
        if (blockDepth <= 0) return Double.POSITIVE_INFINITY;
        final double vm = computeVonMises(i);
        if (vm <= 1e-30) return Double.POSITIVE_INFINITY;
        final double ratio = vm / effYield(i);
        final double depth = blockDepth / ratio;
        return (depth > 1e15) ? Double.POSITIVE_INFINITY : depth;
    }

    public double[] getStressDistribution() {
        final double[] dist = new double[n];
        if (n > 200) {
            // per-block and independent: safe to fold over the common pool
            IntStream.range(0, n).parallel().forEach(i -> dist[i] = stressRatio(i));
        } else {
            for (int i = 0; i < n; i++) dist[i] = stressRatio(i);
        }
        return dist;
    }

    private double stressRatio(final int i) {
        if (blockWaterDepths[i] <= 0) return 0;
        final double vm = computeVonMises(i);
        return (vm <= 1e-30) ? 0 : Math.min(1.0, vm / effYield(i));
    }

    // ============================================================
    //  Utilities
    // ============================================================

    public static double dot(final double[] a, final double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }

    /** Failure limit of a block: tensile strength for brittle materials, yield otherwise. */
    private double effYield(final int i) {
        if (brittle != null && brittle[i] && tensileStress != null) return tensileStress[i];
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
