package com.maxenonyme.createsubmarine.submarine.stress;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

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

    public static final double RHO_G = 10000.0;
    public static final double MOON_POOL_FACTOR = 0.8;
    public static final double PLATE_BENDING_BETA = 0.3;

    static {
        for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
            final double d = Math.sqrt(DX[dir] * DX[dir] + DY[dir] * DY[dir] + DZ[dir] * DZ[dir]);
            INV_DIST[dir] = 1.0 / d;
            DIR_COS[dir][0] = DX[dir] / d;
            DIR_COS[dir][1] = DY[dir] / d;
            DIR_COS[dir][2] = DZ[dir] / d;
        }
    }

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

    private final Map<Long, Integer> blockIndex;

    public final double[] u;
    public final double[] blockWaterDepths;

    private double[] diagPrecon;

    public double poissonRatio = 0.3;
    public double tikhonovAlphaFraction = 1e-6;
    public double rhoG = RHO_G;
    public boolean useMoonPool = true;

    public double localDownX = 0, localDownY = -1, localDownZ = 0;

    public double[][] smoothedNormals;
    public boolean[][] exteriorFaces;

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
            final double[] blockWaterDepths) {
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

        this.blockIndex = new HashMap<>(n * 4 / 3 + 1);
        for (int i = 0; i < n; i++) {
            blockIndex.put(pack(x[i], y[i], z[i]), i);
        }
    }

    public static long pack(final int x, final int y, final int z) {
        return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | ((long) y & 0xFFFL);
    }

    public void buildRHS(final double[] b) {
        Arrays.fill(b, 0.0);
        boolean anyUnderwater = false;
        for (int i = 0; i < n; i++) {
            final double depth = blockWaterDepths[i];
            if (depth <= 0) continue;
            anyUnderwater = true;
            for (int dir = 0; dir < 6; dir++) {
                if (neighbors[i][dir] >= 0) continue;
                if (exteriorFaces != null && !exteriorFaces[i][dir]) continue;
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
                b[3 * i + comp] += -sign * localPressure;
            }
        }
        if (!anyUnderwater) {
            Arrays.fill(b, 0.0);
        }
    }

    public void applyK(final double[] uvec, final double[] Ku) {
        Arrays.fill(Ku, 0.0);
        if (n > 200) {
            IntStream.range(0, n).parallel().forEach(i -> accumulateK(i, uvec, Ku));
        } else {
            for (int i = 0; i < n; i++) accumulateK(i, uvec, Ku);
        }
    }

    private void accumulateK(final int i, final double[] uvec, final double[] Ku) {
        final int i3 = 3 * i;
        final double uix = uvec[i3], uiy = uvec[i3 + 1], uiz = uvec[i3 + 2];
        for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
            final int j = neighbors[i][dir];
            if (j < 0) continue;
            final double Kij = springK[i][dir];
            final int j3 = 3 * j;
            if (dir < 6) {
                final int comp = dir / 2;
                final double sign = (dir % 2 == 0) ? 1.0 : -1.0;
                final double du = sign * (uvec[j3 + comp] - (comp == 0 ? uix : comp == 1 ? uiy : uiz));
                Ku[i3 + comp] += Kij * du * sign;
            } else {
                final double cosX = DIR_COS[dir][0];
                final double cosY = DIR_COS[dir][1];
                final double cosZ = DIR_COS[dir][2];
                final double duProj = cosX * (uvec[j3] - uix) + cosY * (uvec[j3 + 1] - uiy) + cosZ * (uvec[j3 + 2] - uiz);
                final double force = Kij * duProj;
                Ku[i3] += force * cosX;
                Ku[i3 + 1] += force * cosY;
                Ku[i3 + 2] += force * cosZ;
            }
        }
    }

    private void ensurePreconditioner() {
        if (diagPrecon != null) return;
        diagPrecon = new double[3 * n];
        for (int i = 0; i < n; i++) {
            for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
                if (neighbors[i][dir] < 0) continue;
                final double k = springK[i][dir];
                if (dir < 6) {
                    diagPrecon[3 * i + dir / 2] += k;
                } else {
                    diagPrecon[3 * i] += k * DIR_COS[dir][0] * DIR_COS[dir][0];
                    diagPrecon[3 * i + 1] += k * DIR_COS[dir][1] * DIR_COS[dir][1];
                    diagPrecon[3 * i + 2] += k * DIR_COS[dir][2] * DIR_COS[dir][2];
                }
            }
        }
    }

    public void solveCG(final double[] b) {
        solveCG(b, 0);
    }

    public void solveCG(final double[] b, final int maxIterOverride) {
        final int N3 = 3 * n;
        double bNorm = 0;
        for (int k = 0; k < N3; k++) bNorm += b[k] * b[k];
        if (bNorm < 1e-30) {
            Arrays.fill(u, 0.0);
            return;
        }

        if (previousBNorm < 0) {
            Arrays.fill(u, 0.0);
        } else {
            final double scale = bNorm / previousBNorm;
            if (Math.abs(scale - 1.0) > 1e-6) {
                for (int k = 0; k < N3; k++) u[k] *= scale;
            }
        }
        previousBNorm = bNorm;

        final double avgE = n > 0 ? Arrays.stream(E).summaryStatistics().getAverage() : 0.0;
        final double tikhonovAlpha = tikhonovAlphaFraction * avgE;
        ensurePreconditioner();

        final double[] r = new double[N3];
        final double[] zv = new double[N3];
        final double[] p = new double[N3];
        final double[] Ap = new double[N3];

        applyK(u, r);
        for (int k = 0; k < N3; k++) {
            r[k] = b[k] - r[k] - tikhonovAlpha * u[k];
        }
        for (int k = 0; k < N3; k++) {
            zv[k] = r[k] / (diagPrecon[k] + tikhonovAlpha);
        }
        System.arraycopy(zv, 0, p, 0, N3);
        double rz = dot(r, zv);

        final int maxIter = maxIterOverride > 0 ? maxIterOverride : Math.max(300, N3);
        for (int iter = 0; iter < maxIter; iter++) {
            applyK(p, Ap);
            for (int k = 0; k < N3; k++) Ap[k] += tikhonovAlpha * p[k];
            final double pAp = dot(p, Ap);
            if (pAp <= 0) break;

            final double alpha = rz / pAp;
            for (int k = 0; k < N3; k++) {
                u[k] += alpha * p[k];
                r[k] -= alpha * Ap[k];
            }

            if (dot(r, r) < 1e-12 * bNorm) break;

            for (int k = 0; k < N3; k++) {
                zv[k] = r[k] / (diagPrecon[k] + tikhonovAlpha);
            }
            final double rzNew = dot(r, zv);
            if (rzNew <= 0) break;

            final double beta = rzNew / rz;
            for (int k = 0; k < N3; k++) p[k] = zv[k] + beta * p[k];
            rz = rzNew;
        }
    }

    public void removeRigidBodyMode(final double[] v) {
        double sumX = 0, sumY = 0, sumZ = 0;
        for (int i = 0; i < n; i++) {
            sumX += v[3 * i];
            sumY += v[3 * i + 1];
            sumZ += v[3 * i + 2];
        }
        final double avgX = sumX / n;
        final double avgY = sumY / n;
        final double avgZ = sumZ / n;

        double cx = 0, cy = 0, cz = 0;
        for (int i = 0; i < n; i++) {
            cx += x[i];
            cy += y[i];
            cz += z[i];
        }
        cx /= n;
        cy /= n;
        cz /= n;

        double Lx = 0, Ly = 0, Lz = 0;
        double Ixx = 0, Iyy = 0, Izz = 0;
        for (int i = 0; i < n; i++) {
            final double rx = x[i] - cx;
            final double ry = y[i] - cy;
            final double rz = z[i] - cz;
            Lx += ry * v[3 * i + 2] - rz * v[3 * i + 1];
            Ly += rz * v[3 * i] - rx * v[3 * i + 2];
            Lz += rx * v[3 * i + 1] - ry * v[3 * i];
            Ixx += ry * ry + rz * rz;
            Iyy += rx * rx + rz * rz;
            Izz += rx * rx + ry * ry;
        }
        final double wx = Ixx > 1e-30 ? Lx / Ixx : 0;
        final double wy = Iyy > 1e-30 ? Ly / Iyy : 0;
        final double wz = Izz > 1e-30 ? Lz / Izz : 0;

        for (int i = 0; i < n; i++) {
            final double rx = x[i] - cx;
            final double ry = y[i] - cy;
            final double rz = z[i] - cz;
            v[3 * i] -= avgX + (wy * rz - wz * ry);
            v[3 * i + 1] -= avgY + (wz * rx - wx * rz);
            v[3 * i + 2] -= avgZ + (wx * ry - wy * rx);
        }
    }

    public double computeVonMises(final int blockIdx) {
        final double Ei = E[blockIdx];
        final int i3 = 3 * blockIdx;
        final double uix = u[i3], uiy = u[i3 + 1], uiz = u[i3 + 2];

        double gxx = 0, gxy = 0, gxz = 0;
        double gyx = 0, gyy = 0, gyz = 0;
        double gzx = 0, gzy = 0, gzz = 0;
        double denomX = 0, denomY = 0, denomZ = 0;

        for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
            final int j = neighbors[blockIdx][dir];
            if (j < 0) continue;
            final int j3 = 3 * j;
            final double dux = u[j3] - uix;
            final double duy = u[j3 + 1] - uiy;
            final double duz = u[j3 + 2] - uiz;
            final double dx = DX[dir], dy = DY[dir], dz = DZ[dir];

            gxx += dux * dx;
            gxy += dux * dy;
            gxz += dux * dz;
            gyx += duy * dx;
            gyy += duy * dy;
            gyz += duy * dz;
            gzx += duz * dx;
            gzy += duz * dy;
            gzz += duz * dz;
            denomX += dx * dx;
            denomY += dy * dy;
            denomZ += dz * dz;
        }

        if (denomX > 0) {
            final double inv = 1.0 / denomX;
            gxx *= inv;
            gyx *= inv;
            gzx *= inv;
        }
        if (denomY > 0) {
            final double inv = 1.0 / denomY;
            gxy *= inv;
            gyy *= inv;
            gzy *= inv;
        }
        if (denomZ > 0) {
            final double inv = 1.0 / denomZ;
            gxz *= inv;
            gyz *= inv;
            gzz *= inv;
        }

        final double exy = (gxy + gyx) * 0.5;
        final double eyz = (gyz + gzy) * 0.5;
        final double ezx = (gxz + gzx) * 0.5;

        final double nu = poissonRatio;
        final double lambda = Ei * nu / ((1.0 + nu) * (1.0 - 2.0 * nu));
        final double mu = Ei / (2.0 * (1.0 + nu));
        final double eVol = gxx + gyy + gzz;
        final double sxx = 2.0 * mu * gxx + lambda * eVol;
        final double syy = 2.0 * mu * gyy + lambda * eVol;
        final double szz = 2.0 * mu * gzz + lambda * eVol;
        final double sxy = 2.0 * mu * exy;
        final double syz = 2.0 * mu * eyz;
        final double szx = 2.0 * mu * ezx;

        return Math.sqrt(0.5 * (
                (sxx - syy) * (sxx - syy) +
                        (syy - szz) * (syy - szz) +
                        (szz - sxx) * (szz - sxx) +
                        6.0 * (sxy * sxy + syz * syz + szx * szx)));
    }

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

        final double[] panelRatios = computePanelBendingRatios();

        for (int i = 0; i < n; i++) {
            final double ratio = panelRatios[i];
            if (yieldStress[i] <= 0 || blockWaterDepths[i] <= 0 || ratio <= 1e-30) {
                result[i] = Double.POSITIVE_INFINITY;
                continue;
            }
            final double depth = maxBlockDepth / ratio;
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
        result[n] = worstBlock;
        return result;
    }

    public double[] computePanelBendingRatios() {
        final double[] ratios = new double[n];
        final int[] thickness = new int[n];
        final boolean[] candidate = new boolean[n];
        final boolean[] visited = new boolean[n];
        final ArrayDeque<Integer> stack = new ArrayDeque<>();
        final int[] patch = new int[n];

        for (int dir = 0; dir < 6; dir++) {
            final int[] planeDirs = getPlaneDirections(dir);
            final int inward = dir ^ 1;
            boolean any = false;
            for (int i = 0; i < n; i++) {
                candidate[i] = neighbors[i][dir] < 0
                        && blockWaterDepths[i] > 0
                        && yieldStress[i] > 0
                        && (exteriorFaces == null || exteriorFaces[i][dir]);
                visited[i] = false;
                if (!candidate[i]) continue;
                any = true;
                int t = 1;
                int cx = x[i], cy = y[i], cz = z[i];
                while (true) {
                    cx += DX[inward];
                    cy += DY[inward];
                    cz += DZ[inward];
                    if (findBlock(cx, cy, cz) < 0) break;
                    t++;
                }
                thickness[i] = t;
            }
            if (!any) continue;

            for (int start = 0; start < n; start++) {
                if (!candidate[start] || visited[start]) continue;

                final int t = thickness[start];
                int size = 0;
                stack.push(start);
                visited[start] = true;
                while (!stack.isEmpty()) {
                    final int cur = stack.pop();
                    patch[size++] = cur;
                    for (int pd : planeDirs) {
                        final int ni = neighbors[cur][pd];
                        if (ni < 0 || visited[ni] || !candidate[ni] || thickness[ni] != t) continue;
                        visited[ni] = true;
                        stack.push(ni);
                    }
                }
                if (size < 2) continue;

                int minU = Integer.MAX_VALUE, maxU = Integer.MIN_VALUE;
                int minV = Integer.MAX_VALUE, maxV = Integer.MIN_VALUE;
                double avgDepth = 0;
                for (int k = 0; k < size; k++) {
                    final int idx = patch[k];
                    final int uu, vv;
                    switch (dir / 2) {
                        case 0 -> { uu = y[idx]; vv = z[idx]; }
                        case 1 -> { uu = x[idx]; vv = z[idx]; }
                        default -> { uu = x[idx]; vv = y[idx]; }
                    }
                    minU = Math.min(minU, uu);
                    maxU = Math.max(maxU, uu);
                    minV = Math.min(minV, vv);
                    maxV = Math.max(maxV, vv);
                    avgDepth += blockWaterDepths[idx];
                }
                final double shortSpan = Math.min(maxU - minU + 1, maxV - minV + 1);
                if (shortSpan < 2) continue;
                avgDepth /= size;

                final double bendingStress = PLATE_BENDING_BETA * this.rhoG * avgDepth * shortSpan * shortSpan
                        * curvatureFactor(patch, size) / ((double) t * t);
                for (int k = 0; k < size; k++) {
                    final int idx = patch[k];
                    ratios[idx] = Math.max(ratios[idx], bendingStress / yieldStress[idx]);
                }
            }
        }
        return ratios;
    }

    private double curvatureFactor(final int[] patch, final int size) {
        if (smoothedNormals == null) return 1.0;
        double avgNx = 0, avgNy = 0, avgNz = 0;
        int count = 0;
        for (int k = 0; k < size; k++) {
            final double[] nv = smoothedNormals[patch[k]];
            if (nv == null) continue;
            avgNx += nv[0];
            avgNy += nv[1];
            avgNz += nv[2];
            count++;
        }
        if (count < 2) return 1.0;
        final double len = Math.sqrt(avgNx * avgNx + avgNy * avgNy + avgNz * avgNz);
        if (len > 1e-10) {
            avgNx /= len;
            avgNy /= len;
            avgNz /= len;
        }
        double sumAngleSq = 0;
        for (int k = 0; k < size; k++) {
            final double[] nv = smoothedNormals[patch[k]];
            if (nv == null) continue;
            final double d = Math.max(-1.0, Math.min(1.0, avgNx * nv[0] + avgNy * nv[1] + avgNz * nv[2]));
            final double angle = Math.acos(d);
            sumAngleSq += angle * angle;
        }
        final double rmsAngle = Math.sqrt(sumAngleSq / count);
        return 1.0 / (1.0 + rmsAngle * rmsAngle * 10.0);
    }

    private static int[] getPlaneDirections(final int faceDir) {
        return switch (faceDir) {
            case 0, 1 -> new int[]{2, 3, 4, 5};
            case 2, 3 -> new int[]{0, 1, 4, 5};
            default -> new int[]{0, 1, 2, 3};
        };
    }

    public double[] computeCombinedStressRatios() {
        final double[] vmRatios = getStressDistribution();
        final double[] panelRatios = computePanelBendingRatios();
        final double[] combined = new double[n];
        for (int i = 0; i < n; i++) {
            combined[i] = Math.max(vmRatios[i], panelRatios[i]);
        }
        return combined;
    }

    public double[] getStressDistribution() {
        final double[] dist = new double[n];
        if (n > 200) {
            IntStream.range(0, n).parallel().forEach(i -> dist[i] = stressRatio(i));
        } else {
            for (int i = 0; i < n; i++) dist[i] = stressRatio(i);
        }
        return dist;
    }

    private double stressRatio(final int i) {
        if (blockWaterDepths[i] <= 0) return 0;
        final double vm = computeVonMises(i);
        return vm <= 1e-30 ? 0 : Math.min(1.0, vm / yieldStress[i]);
    }

    public static double dot(final double[] a, final double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }

    public int findBlock(final int bx, final int by, final int bz) {
        final Integer idx = blockIndex.get(pack(bx, by, bz));
        return idx != null ? idx : -1;
    }

    public void solve() {
        final long t0 = System.nanoTime();
        final double[] b = new double[3 * n];
        buildRHS(b);
        solveCG(b);
        removeRigidBodyMode(u);
        solveTimeNanos = System.nanoTime() - t0;
    }
}
