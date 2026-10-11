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

    // ---- which exposed faces are wet (null = all exposed faces wet) ----
    public boolean[][] waterFace;
    /** per-face hydrostatic depth (null => use blockWaterDepths[i] for every face) */
    public double[][] faceDepth;
    /** per-face wetted area (null => use volFraction[i], the legacy approximation) */
    public double[][] faceArea;
    /** faces wetted by internal fluid (moon pool / flooded compartment) */
    public boolean[][] faceInternal;

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
            for (int dir = 0; dir < 6; dir++) {
                if (neighbors[i][dir] >= 0) continue;
                if (waterFace != null && !waterFace[i][dir]) continue;
                final double depth = (faceDepth != null) ? faceDepth[i][dir] : blockDepth;
                if (depth <= 0) continue;
                double p = this.rhoG * depth;
                p *= (faceArea != null) ? faceArea[i][dir] : volFraction[i];
                if (useMoonPool && faceInternal != null && faceInternal[i][dir]) {
                    p *= MOON_POOL_FACTOR;
                }
                // inward pressure: +x face (dir 0) pushes -x, hence the sign on even dirs
                b[6 * i + dir / 2] += (dir % 2 == 0 ? -1.0 : 1.0) * p;
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
            final double L = 1.0 / INV_DIST[dir];
            final double h = 0.5 * L; // half centre distance
            final double L2 = L * L;

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
                final double L = 1.0 / INV_DIST[dir];
                final double L2 = L * L;
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
     * <p>The projection used is the mass-weighted least-squares rigid fit, i.e. the same
     * estimate {@link #removeRigidBodyMode} applies to the solution; it is an orthogonal
     * projection for a diagonal inertia tensor, which is exact here because every block
     * carries the same mass model.</p>
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
        final double tol = cgTolerance * bNorm;
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

    public void removeRigidBodyMode(final double[] v) {
        if (n == 0) return;
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

        // mean angular velocity (volume-weighted): the least-squares rigid-spin estimate.
        // Reported for diagnostics; the mode that is actually subtracted from the spin DOF
        // is the (v_c, omega) pair fitted to the translational field below, so that the
        // six rigid modes are removed with one self-consistent estimate.
        double wSumX = 0, wSumY = 0, wSumZ = 0;
        for (int i = 0; i < n; i++) {
            final double m = Math.max(volFraction[i], 1e-3);
            wSumX += m * v[6 * i + 3];
            wSumY += m * v[6 * i + 4];
            wSumZ += m * v[6 * i + 5];
        }
        final double avgWX = wSumX / mSum;
        final double avgWY = wSumY / mSum;
        final double avgWZ = wSumZ / mSum;
        lastMeanSpinX = avgWX; lastMeanSpinY = avgWY; lastMeanSpinZ = avgWZ;

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
        // subtract the rigid-mode angular velocity from the spin DOF. This is the same
        // (v_c, omega) pair that was just subtracted from the translational part, so the
        // six rigid modes are removed consistently; subtracting a mean-spin estimate here
        // as well would remove part of the rigid motion twice.
        for (int i = 0; i < n; i++) {
            v[6 * i + 3] -= wx;
            v[6 * i + 4] -= wy;
            v[6 * i + 5] -= wz;
        }
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
     */
    public static double maxPrincipalStress(final double[] s) {
        final double m = (s[0] + s[1] + s[2]) / 3.0;
        final double d0 = s[0] - m, d1 = s[1] - m, d2 = s[2] - m;
        final double r2 = 0.5 * (d0 * d0 + d1 * d1 + d2 * d2)
                        + 3.0 * (s[3] * s[3] + s[4] * s[4] + s[5] * s[5]);
        if (r2 <= 1e-30) return m;
        final double det = d0 * d1 * d2 + 2.0 * s[3] * s[4] * s[5]
                         - d0 * s[4] * s[4] - d1 * s[5] * s[5] - d2 * s[3] * s[3];
        final double r = Math.sqrt(r2);
        final double c = Math.max(-1.0, Math.min(1.0, det / (2.0 * r * r * r)));
        final double theta = Math.acos(c) / 3.0;
        // with theta in [0, pi/3], cos(theta) is the largest of the three eigenvalues
        return m + 2.0 * r * Math.cos(theta);
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