package com.maxenonyme.createsubmarine.submarine.stress;

import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Shell: `gradlew runStressTest --args="path/to/schem.nbt"`
 */
public class StandaloneStressTest {

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

    // â”€â”€ schematic data (block ID strings, no BlockState) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    record SchemData(
        int width, int height, int length,
        String[] palette,       // block IDs like "minecraft:iron_block"
        int[] blockData,        // palette indices, one per block
        int[] offset
    ) { int volume() { return width * height * length; } }

    // â”€â”€ results â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    record Result(
        String schemName, int n, int hullBlocks, double solveTimeMs,
        double avgStress, double maxStress, double minCrush, double hullRatio,
        long structureHash
    ) {}

    // â”€â”€ main â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java " + StandaloneStressTest.class.getName() + " <schem-file> [--water-y <y>] [--csv] [--json] [--quiet]");
            System.out.println();
            System.out.println("  Loads a Sponge v2 .schem and runs the stress solver.");
            System.out.println("  Outputs: stress distribution and crush depth analysis.");
            return;
        }

        String schemPath = null;
        double waterY = Double.NaN;
        boolean csv = false, json = false, quiet = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--water-y" -> waterY = Double.parseDouble(args[++i]);
                case "--csv"     -> csv = true;
                case "--json"    -> json = true;
                case "--quiet"   -> quiet = true;
                default -> schemPath = args[i];
            }
        }

        if (schemPath == null) { System.err.println("error: no schematic file specified"); return; }

        File file = new File(schemPath);
        if (!file.exists()) { System.err.println("error: file not found: " + file); return; }

        if (!quiet) System.out.println("Loading schematic: " + file.getName());
        SchemData data = loadSchem(file);
        if (!quiet) System.out.printf("  dimensions: %d x %d x %d, blocks: %s%n",
            data.width, data.height, data.length, formatNum(data.volume()));

        Result r = runSolver(data, file.getName(), Double.isNaN(waterY) ? null : waterY);

        if (json)   printJson(r);
        else if (csv) printCsv(r);
        else        printHuman(r);
    }

    // â”€â”€ load schematic â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    static SchemData loadSchem(File file) throws IOException {
        CompoundTag root;
        try (FileInputStream fis = new FileInputStream(file)) {
            root = NbtIo.readCompressed(fis, NbtAccounter.create(Long.MAX_VALUE));
        }

        int version = root.getInt("Version");
        if (version != 2) {
            throw new IOException("Unsupported schematic version: " + version + " (expected 2)");
        }

        int width  = root.getShort("Width")  & 0xFFFF;
        int height = root.getShort("Height") & 0xFFFF;
        int length = root.getShort("Length") & 0xFFFF;

        int[] offset = new int[3];
        if (root.contains("OffsetX", 99)) {
            offset[0] = root.getInt("OffsetX");
            offset[1] = root.getInt("OffsetY");
            offset[2] = root.getInt("OffsetZ");
        }

        CompoundTag paletteTag = root.getCompound("Palette");
        int paletteMax = root.getInt("PaletteMax");

        String[] palette = new String[paletteMax];
        for (String key : paletteTag.getAllKeys()) {
            int idx = paletteTag.getInt(key);
            palette[idx] = key;
        }

        byte[] rawBlockData = root.getByteArray("BlockData");
        int[] blockData = decodeVarInts(rawBlockData, width * height * length);

        return new SchemData(width, height, length, palette, blockData, offset);
    }

    // â”€â”€ decode VarInt sequence (Sponge v2) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static int[] decodeVarInts(byte[] data, int count) {
        int[] result = new int[count];
        int pos = 0;
        for (int i = 0; i < count; i++) {
            int value = 0, shift = 0;
            while (true) {
                byte b = data[pos++];
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
            }
            result[i] = value;
        }
        return result;
    }

    // â”€â”€ run solver â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static Result runSolver(SchemData data, String name, Double waterSurfaceY) {
        int vol = data.volume();
        int w = data.width, h = data.height, l = data.length;

        // collect non-air positions and their material properties
        // Use LOCAL positions (relative to schematic origin).
        // The world transform is applied via subLevelPose if waterSurfaceY is set.
        List<BlockPos> posList = new ArrayList<>();
        List<double[]> propsList = new ArrayList<>();

        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    int idx = (y * l + z) * w + x;
                    int palIdx = data.blockData[idx];
                    if (palIdx < 0 || palIdx >= data.palette.length) continue;
                    String blockId = stripProperties(data.palette[palIdx]);
                    if (isAir(blockId)) continue;

                    posList.add(new BlockPos(x, y, z));
                    propsList.add(lookupProps(blockId));
                }
            }
        }

        int n = posList.size();
        if (n == 0) {
            System.err.println("  warning: no solid blocks found in schematic");
            return new Result(name, 0, 0, 0, 0, 0, Double.POSITIVE_INFINITY, 0, 0);
        }

        // build solver arrays
        BlockPos[] positions = posList.toArray(new BlockPos[n]);
        double[] E_arr = new double[n];
        double[] yield_arr = new double[n];
        for (int i = 0; i < n; i++) {
            E_arr[i] = propsList.get(i)[0];
            yield_arr[i] = propsList.get(i)[1];
        }

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
                    double axialK = 0.5 * (E_arr[i] + E_arr[j]) * kVol;
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

        // bounds
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
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
        if (waterSurfaceY != null) {
            // computed during solve via buildRHS; initial guess is uniform depth = surface - minY
            double avgDepth = Math.max(1, waterSurfaceY - minY);
            java.util.Arrays.fill(blockWaterDepths, avgDepth);
        } else {
            java.util.Arrays.fill(blockWaterDepths, 64.0);
        }

        // create solver
        LatticeStressSolver solver = new LatticeStressSolver(
            n, positions, E_arr, yield_arr, neighbors, springK,
            u, blockWaterDepths, exposedFaceCount, isHullBlock, hullBlockCount,
            new BoundingBox3i(minX, minY, minZ, maxX, maxY, maxZ), hash);

        // if water surface Y was given, re-solve with proper water surface code path
        if (waterSurfaceY != null && Double.isFinite(waterSurfaceY)) {
            dev.ryanhcode.sable.companion.math.Pose3d identity = new dev.ryanhcode.sable.companion.math.Pose3d();
            identity.position().set(data.offset[0], data.offset[1], data.offset[2]);
            solver.refreshWaterDepths(waterSurfaceY, identity);
            solver.resolve();
        }

        // gather results
        double[] stress = solver.getStressDistribution();
        double[] crush = solver.computeCrushDepth();

        double maxStress = 0, sumStress = 0;
        int worstBlock = -1;
        double globalMinCrush = Double.POSITIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            if (stress[i] > maxStress) { maxStress = stress[i]; worstBlock = i; }
            sumStress += stress[i];
            if (Double.isFinite(crush[i]) && crush[i] < globalMinCrush) {
                globalMinCrush = crush[i];
            }
        }
        double avgStress = n > 0 ? sumStress / n : 0;
        long solveTime = solver.getSolveTimeNanos();

        if (worstBlock >= 0) {
            System.out.printf("  worst block: idx=%d pos=%s stress=%.1f%% yield=%.2e Pa crush=%.1f m%n",
                worstBlock, positions[worstBlock], maxStress * 100,
                yield_arr[worstBlock],
                Double.isFinite(crush[worstBlock]) ? crush[worstBlock] : Double.POSITIVE_INFINITY);
        }

        return new Result(name, n, hullBlockCount, solveTime / 1e6,
                          avgStress, maxStress, globalMinCrush, (double) hullBlockCount / n, hash);
    }

    // â”€â”€ material lookup â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static double[] lookupProps(String blockId) {
        if (blockId == null || blockId.isEmpty()) return new double[]{5e9, 4e7};
        ResourceLocation rl;
        try {
            rl = ResourceLocation.parse(blockId);
        } catch (Exception e) {
            return new double[]{5e9, 4e7};
        }
        double[] props = DefaultMaterialProperties.getProperties(rl);
        if (props != null) return new double[]{props[0], props[1]};
        return new double[]{5e9, 4e7};
    }

    private static boolean isAir(String blockId) {
        if (blockId == null) return true;
        return blockId.equals("minecraft:air") || blockId.equals("air");
    }

    private static String stripProperties(String key) {
        if (key == null) return null;
        int bracket = key.indexOf('[');
        return bracket >= 0 ? key.substring(0, bracket) : key;
    }

    // â”€â”€ output formatters â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static void printJson(Result r) {
        System.out.printf("{\"schem\":\"%s\",\"blocks\":%d,\"hull\":%d,\"solve_ms\":%.1f," +
            "\"avg_stress_pct\":%.6f,\"max_stress_pct\":%.6f,\"crush_m\":%.1f," +
            "\"hull_ratio\":%.6f,\"hash\":%d}%n",
            r.schemName, r.n, r.hullBlocks, r.solveTimeMs,
            r.avgStress * 100, r.maxStress * 100,
            Double.isFinite(r.minCrush) ? r.minCrush : -1,
            r.hullRatio, r.structureHash);
    }

    private static void printCsv(Result r) {
        System.out.printf("%s,%d,%d,%.1f,%.6f,%.6f,%.1f,%.6f,%d%n",
            r.schemName, r.n, r.hullBlocks, r.solveTimeMs,
            r.avgStress * 100, r.maxStress * 100,
            Double.isFinite(r.minCrush) ? r.minCrush : -1,
            r.hullRatio, r.structureHash);
    }

    private static void printHuman(Result r) {
        System.out.println();
        if (r.n == 0) {
            System.out.println("  No blocks to analyze.");
            return;
        }
        System.out.printf("  â”€â”€ %s â”€â”€%n", r.schemName);
        System.out.printf("  blocks:        %s%n", formatNum(r.n));
        System.out.printf("  hull blocks:   %s (%.1f%%)%n", formatNum(r.hullBlocks), r.hullRatio * 100);
        System.out.printf("  solve time:    %.1f ms%n", r.solveTimeMs);
        System.out.printf("  avg stress:    %.4f%% of yield%n", r.avgStress * 100);
        System.out.printf("  max stress:    %.4f%% of yield%n", r.maxStress * 100);
        System.out.printf("  min crush:     %s%n",
            Double.isFinite(r.minCrush) ? String.format("%.1f m", r.minCrush) : "infinite");
        System.out.printf("  structure hash: %d%n", r.structureHash);
    }

    private static String formatNum(int n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) return String.format("%d (%.1fK)", n, n / 1000.0);
        return String.format("%d (%.2fM)", n, n / 1_000_000.0);
    }
}
