package com.maxenonyme.createsubmarine.submarine.stress;

import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import net.minecraft.core.BlockPos;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class BatchSolverRunner {

    private static final double OAK_E       = 1.00e10;
    private static final double OAK_YIELD   = 4.00e7;
    private static final double IRON_E      = 2.00e11;
    private static final double IRON_YIELD  = 2.50e8;
    private static final double DIAMOND_E   = 1.22e12;
    private static final double DIAMOND_YIELD = 5.00e9;

    private static final String[] MAT_NAMES = {"oak","iron","diamond"};
    private static final double[][] MAT_PROPS = {
        {OAK_E, OAK_YIELD},
        {IRON_E, IRON_YIELD},
        {DIAMOND_E, DIAMOND_YIELD},
    };

    private static final double UNIFORM_WATER_DEPTH = 64.0;

    private static final int[] DX6 = {1,-1,0,0,0,0};
    private static final int[] DY6 = {0,0,1,-1,0,0};
    private static final int[] DZ6 = {0,0,0,0,1,-1};

    private static final int MAX_NEIGHBORS = 26;
    private static final int[] DX = {
        1,-1,0,0,0,0, 1,1,-1,-1,0,0,0,0,1,1,-1,-1, 1,1,1,1,-1,-1,-1,-1
    };
    private static final int[] DY = {
        0,0,1,-1,0,0, 1,-1,1,-1,1,-1,1,-1,0,0,0,0, 1,1,-1,-1,1,1,-1,-1
    };
    private static final int[] DZ = {
        0,0,0,0,1,-1, 0,0,0,0,1,1,-1,-1,1,-1,1,-1, 1,-1,1,-1,1,-1,1,-1
    };
    private static final double[] INV_DIST = new double[MAX_NEIGHBORS];
    static {
        for (int d = 0; d < MAX_NEIGHBORS; d++)
            INV_DIST[d] = 1.0 / Math.sqrt(DX[d]*DX[d] + DY[d]*DY[d] + DZ[d]*DZ[d]);
    }

    // â”€â”€ shape abstraction â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    @FunctionalInterface
    interface Occ { boolean test(int x, int y, int z, int w, int h, int d); }

    record ShapeDef(String name, Function<Integer,int[]> dims, Occ solid, Occ hollow) {}

    // â”€â”€ main â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public static void main(String[] args) throws Exception {
        System.out.println("BatchSolverRunner: building structures and running solver...");
        long t0 = System.currentTimeMillis();

        List<ShapeDef> shapes = buildShapeDefs();
        int[] sizes = {10, 20, 50, 100, 256};
        int total = shapes.size() * sizes.length * MAT_NAMES.length * 2; // *2 = solid+hollow
        int done = 0, skipped = 0;

        try (PrintWriter pw = new PrintWriter(new FileOutputStream("test_battery.csv"))) {
            pw.println("shape,size,sx,sy,sz,material,internal_struct,blocks,hull_blocks,solve_time_ms,avg_stress_pct,max_stress_pct,min_crush_depth_blocks,hull_ratio");

            for (ShapeDef sd : shapes) {
                for (int baseSize : sizes) {
                    int[] whd = sd.dims.apply(baseSize);
                    int w = whd[0], h = whd[1], d = whd[2];

                    long estSolid = (long) w * h * d;
                    if (estSolid > 2_000_000) {
                        System.out.printf("  ! %s sz=%d %dx%dx%d ~%d blk â€” skip (>2M estimate)%n",
                            sd.name, baseSize, w, h, d, estSolid);
                        skipped += MAT_NAMES.length * 2;
                        continue;
                    }
                    if (estSolid > 100_000) {
                        System.out.printf("  ~ %s sz=%d %dx%dx%d ~%d blk â€” may be slow%n",
                            sd.name, baseSize, w, h, d, estSolid);
                    }

                    for (int mi = 0; mi < MAT_NAMES.length; mi++) {
                        done += runVariant(pw, sd, baseSize, w, h, d, mi, false);
                        done += runVariant(pw, sd, baseSize, w, h, d, mi, true );
                    }
                }
            }
        }

        long dt = System.currentTimeMillis() - t0;
        System.out.printf("Done! %d done / %d total (%d skipped) â€” test_battery.csv  (%d min %d sec)%n",
            done, total, skipped, dt/60000, (dt/1000)%60);
    }

    // â”€â”€ shape definitions â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static List<ShapeDef> buildShapeDefs() {
        List<ShapeDef> list = new ArrayList<>();

        // 1. sphere  (diameter = size)
        list.add(new ShapeDef("sphere", s -> new int[]{s,s,s},
            (x,y,z,w,h,d) -> {
                int cx=w/2, cy=h/2, cz=d/2, r=Math.min(Math.min(cx,cy),cz);
                int dx=x-cx, dy=y-cy, dz=z-cz;
                return dx*dx + dy*dy + dz*dz <= r*r;
            },
            (x,y,z,w,h,d) -> {
                int cx=w/2, cy=h/2, cz=d/2, r=Math.min(Math.min(cx,cy),cz), ri=Math.max(0,r-1);
                int dx=x-cx, dy=y-cy, dz=z-cz;
                double d2 = dx*dx + dy*dy + dz*dz;
                return d2 <= r*r && d2 > ri*ri;
            }
        ));

        // 2. hollow_sphere  (already hollow by definition, shell=1)
        list.add(new ShapeDef("hollow_sphere", s -> new int[]{s,s,s},
            (x,y,z,w,h,d) -> {
                int cx=w/2, cy=h/2, cz=d/2, r=Math.min(Math.min(cx,cy),cz);
                int dx=x-cx, dy=y-cy, dz=z-cz;
                return dx*dx + dy*dy + dz*dz <= r*r;
            },
            (x,y,z,w,h,d) -> {
                int cx=w/2, cy=h/2, cz=d/2, r=Math.min(Math.min(cx,cy),cz), ri=Math.max(0,r-1);
                int dx=x-cx, dy=y-cy, dz=z-cz;
                double d2 = dx*dx + dy*dy + dz*dz;
                return d2 <= r*r && d2 > ri*ri;
            }
        ));

        // 3. cube
        list.add(new ShapeDef("cube", s -> new int[]{s,s,s},
            (x,y,z,w,h,d) -> true,
            (x,y,z,w,h,d) -> {
                int dx = Math.min(x, w-1-x), dy = Math.min(y, h-1-y), dz = Math.min(z, d-1-z);
                return Math.min(Math.min(dx,dy),dz) < 1;
            }
        ));

        // 4-6. cuboid ratios
        int[][] cubRat = {{2,1,1},{1,2,1},{1,1,2}};
        for (int[] r : cubRat) {
            String nm = String.format("cuboid_%d%d%d", r[0], r[1], r[2]);
            int rx=r[0], ry=r[1], rz=r[2];
            list.add(new ShapeDef(nm, s -> new int[]{s*rx, s*ry, s*rz},
                (x,y,z,w,h,d) -> true,
                (x,y,z,w,h,d) -> {
                    int dx = Math.min(x, w-1-x), dy = Math.min(y, h-1-y), dz = Math.min(z, d-1-z);
                    return Math.min(Math.min(dx,dy),dz) < 1;
                }
            ));
        }

        // 7-9. ellipsoid ratios
        int[][] ellRat = {{2,1,1},{1,2,1},{1,1,2}};
        for (int[] r : ellRat) {
            String nm = String.format("ellipsoid_%d%d%d", r[0], r[1], r[2]);
            double rx=r[0], ry=r[1], rz=r[2];
            int frx=r[0], fry=r[1], frz=r[2];
            list.add(new ShapeDef(nm, s -> new int[]{2*s*frx, 2*s*fry, 2*s*frz},
                (x,y,z,w,h,d) -> {
                    double hx=w/2.0, hy=h/2.0, hz=d/2.0;
                    double dx=(x-hx)/hx, dy=(y-hy)/hy, dz=(z-hz)/hz;
                    return dx*dx + dy*dy + dz*dz <= 1.0;
                },
                (x,y,z,w,h,d) -> {
                    double hx=w/2.0, hy=h/2.0, hz=d/2.0;
                    double dx=(x-hx)/hx, dy=(y-hy)/hy, dz=(z-hz)/hz;
                    double d2 = dx*dx + dy*dy + dz*dz;
                    if (d2 > 1.0) return false;
                    double sc = 1.0 - 1.0/Math.max(hx,Math.max(hy,hz));
                    return d2 > sc*sc;
                }
            ));
        }

        //for testing
        list.add(new ShapeDef("torus", s -> {
            int mr = Math.max(1, s / 8);
            int R = s / 2;
            int ext = R + mr;
            return new int[]{2*ext+1, 2*mr+1, 2*ext+1};
        },
            (x,y,z,w,h,d) -> {
                int mr = (h-1)/2, R = (w-h)/2;
                int cx = w/2, cy = h/2, cz = d/2;
                double rXY = Math.sqrt((x-cx)*(x-cx) + (z-cz)*(z-cz));
                double dy = y - cy;
                return (R - rXY)*(R - rXY) + dy*dy <= mr*mr;
            },
            (x,y,z,w,h,d) -> {
                int mr = (h-1)/2, R = (w-h)/2, ir = Math.max(0, mr - 1);
                int cx = w/2, cy = h/2, cz = d/2;
                double rXY = Math.sqrt((x-cx)*(x-cx) + (z-cz)*(z-cz));
                double dy = y - cy;
                double d2 = (R - rXY)*(R - rXY) + dy*dy;
                return d2 <= mr*mr && d2 > ir*ir;
            }
        ));

        // 11. cylinder  (radius = s/2, height = s)
        list.add(new ShapeDef("cylinder", s -> new int[]{s+1, s, s+1},
            (x,y,z,w,h,d) -> {
                int cx=w/2, cz=d/2, r=Math.min(cx,cz);
                int dx=x-cx, dz=z-cz;
                return dx*dx + dz*dz <= r*r;
            },
            (x,y,z,w,h,d) -> {
                int cx=w/2, cz=d/2, r=Math.min(cx,cz), ri=Math.max(0, r-1);
                int dx=x-cx, dz=z-cz;
                double d2 = dx*dx + dz*dz;
                if (d2 > r*r) return false;
                boolean surfSide = d2 > ri*ri;
                boolean surfEnd = y == 0 || y == h-1;
                return surfSide || (surfEnd && d2 <= r*r);
            }
        ));

        // 12. pyramid  (square base = s, height = s)
        list.add(new ShapeDef("pyramid", s -> new int[]{s, s, s},
            (x,y,z,w,h,d) -> {
                double mid = (w-1)/2.0;
                double frac = 1.0 - (double)y / (h-1);
                int half = (int)Math.ceil(mid * frac);
                return Math.abs(x - mid) <= half && Math.abs(z - mid) <= half && y >= 0 && y < h;
            },
            (x,y,z,w,h,d) -> {
                double mid = (w-1)/2.0;
                double frac = 1.0 - (double)y / (h-1);
                int half = (int)Math.ceil(mid * frac);
                if (Math.abs(x - mid) > half || Math.abs(z - mid) > half) return false;
                if (y == 0 || y == h-1) return true;
                double frac1 = 1.0 - (double)(y-1) / (h-1);
                double frac2 = 1.0 - (double)(y+1) / (h-1);
                int half1 = (int)Math.ceil(mid * frac1);
                int half2 = (int)Math.ceil(mid * frac2);
                boolean atEdgeX = Math.abs(x - mid) >= Math.min(half1, half2);
                boolean atEdgeZ = Math.abs(z - mid) >= Math.min(half1, half2);
                return atEdgeX || atEdgeZ;
            }
        ));

        return list;
    }

    // â”€â”€ run one variant â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static int runVariant(PrintWriter pw, ShapeDef sd, int baseSize,
                                   int w, int h, int d, int mi, boolean hollow) {
        String struct = hollow ? "hollow" : "solid";
        Occ occ = hollow ? sd.hollow : sd.solid;

        // collect positions
        List<BlockPos> posList = new ArrayList<>();
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                for (int z = 0; z < d; z++)
                    if (occ.test(x, y, z, w, h, d))
                        posList.add(new BlockPos(x, y, z));

        int n = posList.size();
        if (n == 0) return 0;

        System.out.printf("  %s sz=%d %s %s: %d blk ...%n",
            sd.name, baseSize, MAT_NAMES[mi], struct, n);

        long t1 = System.nanoTime();
        try {
            // build solver data
            BlockPos[] positions = posList.toArray(new BlockPos[n]);
            double E = MAT_PROPS[mi][0], yield = MAT_PROPS[mi][1];
            double[] E_arr = new double[n];
            double[] yield_arr = new double[n];
            java.util.Arrays.fill(E_arr, E);
            java.util.Arrays.fill(yield_arr, yield);

            long[] keys = new long[n];
            for (int i = 0; i < n; i++)
                keys[i] = BlockPos.asLong(positions[i].getX(), positions[i].getY(), positions[i].getZ());

            int[][] neighbors = new int[n][MAX_NEIGHBORS];
            double[][] springK = new double[n][MAX_NEIGHBORS];
            int[] exposedFaceCount = new int[n];
            boolean[] isHullBlock = new boolean[n];
            int hullBlockCount = 0;

            for (int i = 0; i < n; i++) {
                int px = positions[i].getX(), py = positions[i].getY(), pz = positions[i].getZ();
                for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
                    int nx = px + DX[dir], ny = py + DY[dir], nz = pz + DZ[dir];
                    long nk = BlockPos.asLong(nx, ny, nz);
                    int j = -1;
                    for (int k = 0; k < n; k++) {
                        if (keys[k] == nk) { j = k; break; }
                    }
                    if (j >= 0) {
                        neighbors[i][dir] = j;
                        double kVol = 1.0;
                        double axialK = 0.5 * (E + E_arr[j]) * kVol;
                        springK[i][dir] = -(axialK * INV_DIST[dir]);
                    } else {
                        neighbors[i][dir] = -1;
                        springK[i][dir] = 0.0;
                        if (dir < 6) exposedFaceCount[i]++;
                    }
                }
                if (exposedFaceCount[i] > 0) {
                    isHullBlock[i] = true;
                    hullBlockCount++;
                }
            }

            int minX=Integer.MAX_VALUE, minY=Integer.MAX_VALUE, minZ=Integer.MAX_VALUE;
            int maxX=Integer.MIN_VALUE, maxY=Integer.MIN_VALUE, maxZ=Integer.MIN_VALUE;
            for (BlockPos p : positions) {
                if (p.getX() < minX) minX = p.getX();
                if (p.getY() < minY) minY = p.getY();
                if (p.getZ() < minZ) minZ = p.getZ();
                if (p.getX() > maxX) maxX = p.getX();
                if (p.getY() > maxY) maxY = p.getY();
                if (p.getZ() > maxZ) maxZ = p.getZ();
            }

            long hash = 0;
            for (BlockPos p : positions)
                hash ^= BlockPos.asLong(p.getX(), p.getY(), p.getZ());

            double[] u = new double[6 * n];
            double[] blockWaterDepths = new double[n];
            java.util.Arrays.fill(blockWaterDepths, UNIFORM_WATER_DEPTH);

            LatticeStressSolver solver = new LatticeStressSolver(
                n, positions, E_arr, yield_arr, neighbors, springK,
                u, blockWaterDepths, exposedFaceCount, isHullBlock, hullBlockCount,
                new BoundingBox3i(minX, minY, minZ, maxX, maxY, maxZ), hash);

            double[] crush = solver.computeCrushDepth();
            double[] stress = solver.getStressDistribution(UNIFORM_WATER_DEPTH, crush);
            double maxF = 0, sumF = 0;
            for (int i = 0; i < n; i++) {
                if (stress[i] > maxF) maxF = stress[i];
                sumF += stress[i];
            }
            double avgF = sumF / n;
            int worst = (int) crush[n];
            double minCrush = worst >= 0 ? crush[worst] : Double.POSITIVE_INFINITY;
            double hRatio = (double) hullBlockCount / n;
            long dt = solver.getSolveTimeNanos();

            pw.printf("%s,%d,%d,%d,%d,%s,%s,%d,%d,%.3f,%.6f,%.6f,%.1f,%.6f%n",
                sd.name, baseSize, w, h, d, MAT_NAMES[mi], struct,
                n, hullBlockCount, dt / 1e6, avgF * 100, maxF * 100, minCrush, hRatio);
            pw.flush();

            System.out.printf("  %s sz=%d %s %s: %d blk %d hull %.0fms crush=%.0f%n",
                sd.name, baseSize, MAT_NAMES[mi], struct,
                n, hullBlockCount, dt / 1e6, minCrush);

            return 1;

        } catch (Exception e) {
            System.err.printf("  ERROR: %s sz=%d %s %s: %s%n",
                sd.name, baseSize, MAT_NAMES[mi], struct, e);
            return 0;
        }
    }
}
