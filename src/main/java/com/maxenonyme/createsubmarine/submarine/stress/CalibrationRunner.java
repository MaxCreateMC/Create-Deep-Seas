package com.maxenonyme.createsubmarine.submarine.stress;

import java.util.*;

/**
 * Standalone pipeline for calibrating crush depth physics.
 * Uses the exact same {@link SolverCore} math as in-game.
 *
 * Run via Gradle:
 *   ./gradlew run -PmainClass=com.maxenonyme.createsubmarine.submarine.stress.CalibrationRunner
 *
 * Or from IDE: right-click â†’ Run 'CalibrationRunner.main()'
 */
public class CalibrationRunner {

    public static void main(String[] args) {
        // Test geometries: (width, height, length, hollow, ribbed)
        final int[][] geo = {
            {3, 3, 5, 0, 0},   // solid 3x3x5
            {3, 3, 5, 1, 0},   // hollow 3x3x5
            {5, 5, 5, 0, 0},   // solid 5x5x5
            {5, 5, 5, 1, 0},   // hollow 5x5x5
            {7, 5, 11, 0, 0},  // solid 7x5x11
            {7, 5, 11, 1, 0},  // hollow 7x5x11
            {7, 5, 11, 1, 1},  // hollow 7x5x11 with internal rib wall (wall at x=3)
            {9, 5, 15, 1, 1},  // hollow 9x5x15 with 2 internal rib walls
        };

        for (int[] g : geo) {
            runTest(g[0], g[1], g[2], g[3] == 1, g[4] == 1);
        }
    }

    private static void runTest(int width, int height, int length, boolean hollow, boolean ribbed) {
        System.out.println();
        System.out.println("=== " + (hollow ? "Hollow" : "Solid") + " " + width + "x" + height + "x" + length + (ribbed ? " ribbed" : "") + " ===");

        // Iron properties
        final double youngsModulus = 2.0e11;
        final double yieldStress = 2.5e8;

        final List<int[]> blocks = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    if (hollow && x > 0 && x < width - 1 && y > 0 && y < height - 1 && z > 0 && z < length - 1) {
                        // leave internal rib walls across the middle longitudinal section
                        final boolean isRib = ribbed && (x == width / 2 || (width >= 9 && (x == width / 3 || x == (2 * width) / 3)));
                        if (!isRib) continue;
                    }
                    blocks.add(new int[]{x, y, z});
                }
            }
        }
        final int n = blocks.size();

        final int[] sx = new int[n], sy = new int[n], sz = new int[n];
        final double[] E = new double[n];
        final double[] yield = new double[n];
        final double[] volFrac = new double[n];
        final int[] exposedFaceCount = new int[n];
        final boolean[] isHull = new boolean[n];
        final int[][] neighbors = new int[n][SolverCore.MAX_NEIGHBORS];
        final double[][] springK = new double[n][SolverCore.MAX_NEIGHBORS];

        final Map<Long, Integer> posToIdx = new HashMap<>();
        for (int i = 0; i < n; i++) {
            sx[i] = blocks.get(i)[0];
            sy[i] = blocks.get(i)[1];
            sz[i] = blocks.get(i)[2];
            E[i] = youngsModulus;
            yield[i] = yieldStress;
            volFrac[i] = 1.0;
            posToIdx.put(key(sx[i], sy[i], sz[i]), i);
        }

        int hullCount = 0;
        for (int i = 0; i < n; i++) {
            int exteriorFaces = 0;
            for (int dir = 0; dir < SolverCore.MAX_NEIGHBORS; dir++) {
                final int nx = sx[i] + SolverCore.DX[dir];
                final int ny = sy[i] + SolverCore.DY[dir];
                final int nz = sz[i] + SolverCore.DZ[dir];
                final Integer j = posToIdx.get(key(nx, ny, nz));
                if (j != null) {
                    neighbors[i][dir] = j;
                    final double kVol = 0.5 * (volFrac[i] + volFrac[j]);
                    final double EiEff = E[i];
                    final double EjEff = E[j];
                    final double axialK = (2.0 * EiEff * EjEff / (EiEff + EjEff + 1e-30)) * kVol;
                    springK[i][dir] = -(axialK * SolverCore.INV_DIST[dir]);
                } else {
                    neighbors[i][dir] = -1;
                    springK[i][dir] = 0.0;
                    if (dir < 6) exteriorFaces++;
                }
            }
            if (exteriorFaces > 0) { isHull[i] = true; hullCount++; }
            exposedFaceCount[i] = exteriorFaces;
        }

        final int[] neighborCount = new int[n];
        for (int i = 0; i < n; i++) {
            int cnt = 0;
            for (int dir = 0; dir < SolverCore.MAX_NEIGHBORS; dir++)
                if (neighbors[i][dir] >= 0) cnt++;
            neighborCount[i] = cnt;
        }

        final double[] u = new double[6 * n];
        final double[] blockWaterDepths = new double[n];

        final SolverCore solver = new SolverCore(
            n, sx, sy, sz, E, yield, neighbors, springK, neighborCount,
            exposedFaceCount, isHull, hullCount, volFrac, u, blockWaterDepths
        );

        // Depth sweep: VM must scale linearly with depth (linear system).
        // Print VM / (rho*g*depth) — a shape factor comparable across depths.
        for (double depth : new double[]{25, 50, 100, 200, 400}) {
            Arrays.fill(u, 0.0);
            Arrays.fill(blockWaterDepths, depth);
            solver.solve();
            double maxVM = 0;
            for (int i = 0; i < n; i++) {
                final double vm = solver.computeVonMises(i);
                if (vm > maxVM) maxVM = vm;
            }
            final double[] crush = solver.computeCrushDepth();
            final int worstBlock = (int) crush[n];
            final double globalCrush = worstBlock >= 0 ? crush[worstBlock] : Double.POSITIVE_INFINITY;
            System.out.printf("  depth=%4d  MaxVM=%.3e  VM/P=%.3f  Crush=%.1f%n",
                (int) depth, maxVM, maxVM / (10000.0 * depth), globalCrush);
        }
    }

    private static long key(int x, int y, int z) {
        return (((long) x) & 0x3FFFFFFL) << 38
             | (((long) y) & 0x3FFFFFFL) << 12
             | (((long) z) & 0xFFFL);
    }
}
