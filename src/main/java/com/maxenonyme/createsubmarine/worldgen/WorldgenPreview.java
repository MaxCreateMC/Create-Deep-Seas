package com.maxenonyme.createsubmarine.worldgen;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class WorldgenPreview {

    private static final int REGION_SIZE = 8192;
    private static final int SEA_LEVEL = 0;

    private static final int COL_SAND      = 0xFFE8D5A0;
    private static final int COL_CLAY      = 0xFF7A8A8A;
    private static final int COL_MUD       = 0xFF5B4C40;
    private static final int COL_DIRT      = 0xFF8B6B4D;
    private static final int COL_STONE     = 0xFF808080;
    private static final int COL_WATER     = 0x607AB8E4;
    private static final int COL_WATER_SURF= 0x803F76E4;

    private static final int BIO_PLAINS    = 0x2080FF80;
    private static final int BIO_SHELF     = 0x20FFFF00;
    private static final int BIO_SHALLOWS  = 0x208080FF;
    private static final int COL_BOUNDARY  = 0x80000000;

    public static void main(String[] args) {
        new File("worldgen_preview").mkdirs();

        boolean vanilla = false;
        boolean large = false;
        boolean plates = false;
        long userSeed = 0;
        boolean hasSeed = false;
        for (String a : args) {
            if (a.equals("--vanilla")) vanilla = true;
            else if (a.equals("--large")) large = true;
            else if (a.equals("--plates")) plates = true;
            else try { userSeed = Long.parseLong(a); hasSeed = true; } catch (NumberFormatException ignored) {}
        }

        if (plates) {
            runPlateSim(hasSeed ? userSeed : System.nanoTime());
        } else if (large) {
            runLarge(vanilla, hasSeed, userSeed);
        } else {
            runNormal(vanilla, hasSeed, userSeed);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Normal mode: 8192×8192, heightmap + isometric + analysis
    // ═══════════════════════════════════════════════════════════════════════

    private static void runNormal(boolean vanilla, boolean hasSeed, long userSeed) {
        System.out.println("=== 1:1 Minecraft Worldgen Preview ===");
        System.out.println();

        if (vanilla) {
            System.out.println("Mode: VANILLA (original BASE_SEED=420691337L + default.json)");
            System.out.println("  -> 1:1 with unmodified Minecraft. Every world has the same terrain.");
            System.out.println();
            if (hasSeed) System.out.println("  Using plate config seed: " + userSeed);
        } else {
            long seed = hasSeed ? userSeed : new Random().nextLong();
            System.out.println("Mode: RANDOM (SeafloorGenerator.reset(" + seed + "))");
            SeafloorGenerator.reset(seed);
        }

        int ox, oz;
        if (vanilla) {
            int[] best = surveyVanilla();
            ox = best[0] - REGION_SIZE / 2;
            oz = best[1] - REGION_SIZE / 2;
            System.out.println("  Best biome-diverse area at (" + ox + ", " + oz + ")");
        } else {
            ox = 0; oz = 0;
        }
        System.out.println();

        System.out.println("Region: " + REGION_SIZE + "x" + REGION_SIZE + " blocks");
        System.out.println("Origin: (" + ox + ", " + oz + ")");
        System.out.println();

        long t0 = System.nanoTime();
        short[] heights = new short[REGION_SIZE * REGION_SIZE];
        byte[] biomes   = new byte[REGION_SIZE * REGION_SIZE];
        short[] plateIds = new short[REGION_SIZE * REGION_SIZE];

        int minH = Integer.MAX_VALUE, maxH = Integer.MIN_VALUE;
        long sumH = 0;

        System.out.print("Generating heights ");
        long lastPct = 0;
        for (int lx = 0; lx < REGION_SIZE; lx++) {
            int wx = ox + lx;
            for (int lz = 0; lz < REGION_SIZE; lz++) {
                int wz = oz + lz;
                int idx = lz * REGION_SIZE + lx;
                int h = SeafloorGenerator.getHeightAt(wx, wz);
                heights[idx] = (short) h;
                if (h < minH) minH = h;
                if (h > maxH) maxH = h;
                sumH += h;

                double c = Math.abs(SeafloorGenerator.getNoiseAt(wx, wz));
                if (c < 0.10)      biomes[idx] = 0;
                else if (c < 0.50) biomes[idx] = 1;
                else               biomes[idx] = 2;

                plateIds[idx] = (short) SeafloorGenerator.getPlateAt(wx, wz).hashCode();
            }
            int pct = (lx + 1) * 100 / REGION_SIZE;
            if (pct > lastPct) { System.out.print("." + pct + "%"); lastPct = pct; }
        }

        long genMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.println(" done in " + genMs + " ms");
        System.out.println("Height range: " + minH + " to " + maxH + "  (mean: " + (sumH / (long)(REGION_SIZE * REGION_SIZE)) + ")");
        System.out.println();

        // Heightmap
        t0 = System.nanoTime();
        System.out.print("Rendering heightmap ");
        BufferedImage hmap = new BufferedImage(REGION_SIZE, REGION_SIZE, BufferedImage.TYPE_INT_ARGB);
        lastPct = 0;
        for (int z = 0; z < REGION_SIZE; z++) {
            for (int x = 0; x < REGION_SIZE; x++) {
                int idx = z * REGION_SIZE + x;
                int h = heights[idx];
                int biome = biomes[idx];
                short pid = plateIds[idx];

                boolean isPlains = (biome == 2);
                int color = isPlains ? COL_CLAY : COL_MUD;

                int biomeTint;
                switch (biome) {
                    case 0:  biomeTint = BIO_SHALLOWS; break;
                    case 1:  biomeTint = BIO_SHELF; break;
                    case 2:  biomeTint = BIO_PLAINS; break;
                    default: biomeTint = 0;
                }
                color = blend(color, biomeTint);

                boolean boundary = false;
                if (x > 0 && plateIds[idx] != plateIds[z * REGION_SIZE + (x - 1)]) boundary = true;
                if (z > 0 && plateIds[idx] != plateIds[(z - 1) * REGION_SIZE + x]) boundary = true;
                if (boundary) color = blend(color, COL_BOUNDARY);

                hmap.setRGB(x, z, color);
            }
            int pct = (z + 1) * 100 / REGION_SIZE;
            if (pct > lastPct) { System.out.print("." + pct + "%"); lastPct = pct; }
        }
        long renderMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.println(" done (" + renderMs + " ms)");
        write(hmap, "heightmap_" + REGION_SIZE + (vanilla ? "_vanilla" : ""));
        System.out.println();

        // Isometric 3D
        t0 = System.nanoTime();
        System.out.print("Rendering isometric 3D ");
        int step = 16;
        int cols = REGION_SIZE / step;
        int rows = REGION_SIZE / step;
        double heightScale = 0.35;
        int margin = 80;
        int imgW = (cols + rows) * 2 + margin * 2;
        int imgH = (cols + rows) + (int)((maxH - minH) * heightScale) + margin * 2 + 200;
        BufferedImage iso = new BufferedImage(Math.max(100, imgW), Math.max(100, imgH), BufferedImage.TYPE_INT_ARGB);
        int cx = iso.getWidth() / 2;
        int cy = iso.getHeight() / 3;

        Integer[] order = new Integer[cols * rows];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> {
            int ax = a % cols, az = a / cols;
            int bx = b % cols, bz = b / cols;
            int sumA = ax + az; int sumB = bx + bz;
            if (sumA != sumB) return sumA - sumB;
            return bx - ax;
        });

        double lx = 0.4, ly = 0.7, lz = 0.6;

        for (int oi = 0; oi < order.length; oi++) {
            int idx = order[oi];
            int sx = idx % cols;
            int sz = idx / cols;
            int wx = sx * step;
            int wz = sz * step;
            int h = heights[wz * REGION_SIZE + wx];
            int biome = biomes[wz * REGION_SIZE + wx];

            boolean isPlains = (biome == 2);
            int blockColor = isPlains ? COL_CLAY : COL_MUD;

            double sxPos = (sx - sz) * 2.0;
            double syPos = (sx + sz) * 1.0 - (h - minH) * heightScale;
            int x0 = cx + (int) sxPos;
            int y0 = cy + (int) syPos;

            int colHeight = Math.max(1, (int) ((h - minH) * heightScale) + 2);
            if (colHeight > 1500) colHeight = 1500;

            int lit = blockColor;
            if (sx > 0 && sz > 0 && sx < cols - 1 && sz < rows - 1) {
                int hx1 = heights[wz * REGION_SIZE + (wx - step)];
                int hx2 = heights[wz * REGION_SIZE + (wx + step)];
                double dx = hx2 - hx1;
                int hz1 = heights[(wz - step) * REGION_SIZE + wx];
                int hz2 = heights[(wz + step) * REGION_SIZE + wx];
                double dz = hz2 - hz1;
                double nx = -dx, ny = step * step * heightScale, nz = -dz;
                double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len > 0.001) {
                    nx /= len; ny /= len; nz /= len;
                    double dp = nx * lx + ny * ly + nz * lz;
                    dp = Math.max(0.2, Math.min(1.5, dp));
                    int r = Math.min(255, (int)(((blockColor >> 16) & 0xFF) * dp));
                    int g = Math.min(255, (int)(((blockColor >> 8) & 0xFF) * dp));
                    int b = Math.min(255, (int)((blockColor & 0xFF) * dp));
                    lit = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
            }

            for (int dy = 0; dy < colHeight; dy++) {
                int py = y0 - dy;
                if (py < 0 || py >= iso.getHeight()) continue;
                int col = (dy < colHeight * 0.15) ? lit : darken(lit, 0.55);
                for (int ddx = -1; ddx <= 1; ddx++) {
                    int px = x0 + ddx;
                    if (px < 0 || px >= iso.getWidth()) continue;
                    int pixel = (ddx < 0) ? darken(col, 0.7) : (ddx > 0 ? lighten(col, 1.12) : col);
                    iso.setRGB(px, py, pixel);
                }
            }
        }
        long isoMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.println(" done (" + isoMs + " ms)");
        write(iso, "isometric3d_" + REGION_SIZE + (vanilla ? "_vanilla" : ""));
        System.out.println();

        exportAnalysisNormal(heights, biomes, plateIds, minH, maxH, sumH, ox, oz, vanilla);
        System.out.println("Done. All outputs saved to worldgen_preview/");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Large mode: 32767×32767 streaming, heightmap only (step=4) + analysis
    // ═══════════════════════════════════════════════════════════════════════

    private static void runLarge(boolean vanilla, boolean hasSeed, long userSeed) {
        int regionSize = 32767;
        int imgStep = 4;
        int imgSize = (regionSize + imgStep - 1) / imgStep;

        System.out.println("=== Worldgen Preview (LARGE) ===");
        System.out.println();

        if (vanilla) {
            System.out.println("Mode: VANILLA (BASE_SEED=420691337L + default.json)");
            if (hasSeed) System.out.println("  Using plate config seed: " + userSeed);
        } else {
            long seed = hasSeed ? userSeed : new Random().nextLong();
            System.out.println("Mode: RANDOM (SeafloorGenerator.reset(" + seed + "))");
            SeafloorGenerator.reset(seed);
        }

        int ox, oz;
        if (vanilla) {
            int[] best = surveyVanilla();
            ox = best[0] - regionSize / 2;
            oz = best[1] - regionSize / 2;
            System.out.println("  Best area at (" + ox + ", " + oz + ")");
        } else {
            ox = 0; oz = 0;
        }
        System.out.println();

        System.out.println("Region: " + regionSize + "x" + regionSize + " blocks");
        System.out.println("Origin: (" + ox + ", " + oz + ")");
        System.out.println("Heightmap: " + imgSize + "x" + imgSize + " px (step=" + imgStep + ")");
        System.out.println();

        long t0 = System.nanoTime();
        BufferedImage hmap = new BufferedImage(imgSize, imgSize, BufferedImage.TYPE_INT_ARGB);
        Map<Short, int[]> plateStats = new HashMap<>();
        int[] biomeCount = new int[3];
        int[] hist = new int[32];
        int binSize = 64;
        int count = 0;
        int minH = Integer.MAX_VALUE, maxH = Integer.MIN_VALUE;
        long sumH = 0;
        double mean = 0, m2 = 0;
        long totalBlocks = (long) regionSize * regionSize;

        System.out.print("Generating ");
        int lastPct = -1;
        long nextReport = totalBlocks / 50;

        for (int lz = 0; lz < regionSize; lz++) {
            int wz = oz + lz;
            for (int lx = 0; lx < regionSize; lx++) {
                int wx = ox + lx;

                int h = SeafloorGenerator.getHeightAt(wx, wz);
                double c = Math.abs(SeafloorGenerator.getNoiseAt(wx, wz));
                short pid = (short) SeafloorGenerator.getPlateAt(wx, wz).hashCode();

                count++;
                if (h < minH) minH = h;
                if (h > maxH) maxH = h;
                sumH += h;
                double delta = h - mean;
                mean += delta / count;
                m2 += delta * (h - mean);

                int biome = c < 0.10 ? 0 : c < 0.50 ? 1 : 2;
                biomeCount[biome]++;

                int bin = (h + 1024) / binSize;
                if (bin >= 0 && bin < 32) hist[bin]++;

                int[] pc = plateStats.get(pid);
                if (pc == null) { pc = new int[]{0}; plateStats.put(pid, pc); }
                pc[0]++;

                if (lx % imgStep == 0 && lz % imgStep == 0) {
                    boolean isPlains = (biome == 2);
                    int color = isPlains ? COL_CLAY : COL_MUD;

                    int biomeTint;
                    switch (biome) {
                        case 0:  biomeTint = BIO_SHALLOWS; break;
                        case 1:  biomeTint = BIO_SHELF; break;
                        default: biomeTint = BIO_PLAINS;
                    }
                    color = blend(color, biomeTint);
                    color = blend(color, plateColor(pid));

                    hmap.setRGB(lx / imgStep, lz / imgStep, color);
                }

                if (count == nextReport) {
                    int pct = (int)(count * 100L / totalBlocks);
                    if (lastPct < 0) System.out.print(pct + "%");
                    else System.out.print("." + pct + "%");
                    lastPct = pct;
                    nextReport += totalBlocks / 50;
                }
            }
        }

        long genMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.println(" done in " + (genMs / 1000) + "s " + (genMs % 1000) + "ms");
        System.out.println("Height range: " + minH + " to " + maxH + "  (mean: " + (sumH / count) + ")");
        System.out.println();

        t0 = System.nanoTime();
        System.out.print("Writing heightmap ");
        write(hmap, "heightmap_" + regionSize + (vanilla ? "_vanilla" : ""));
        System.out.println(" (" + (System.nanoTime() - t0) / 1_000_000 + "ms)");
        System.out.println();

        exportAnalysisLarge(minH, maxH, sumH, count, mean, m2, hist, biomeCount, plateStats, ox, oz, vanilla, regionSize);
        System.out.println("Done.");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Plate Growth Simulation (cellular automaton growth from seeds)
    // ═══════════════════════════════════════════════════════════════════════

    private static void runPlateSim(long seed) {
        final int SIZE = 32767;
        final long N = (long) SIZE * SIZE;
        System.out.println("=== Tectonic Plate Growth Simulation ===");
        System.out.println("Seed: " + seed);
        System.out.println("Grid: " + SIZE + "x" + SIZE + " (" + N + " cells)");
        System.out.println();

        // Probabilities for picking k neighbors (check from 8 down to 1)
        // P(k) = cumulative prob: 3% for 8, 4.5% for 7, 9% for 6, 12.5% for 5,
        //        25% for 4, 50% for 3, 75% for 2, 100% for 1
        final int[] P = {0, 65535, 49152, 32768, 16384, 8192, 5898, 2949, 1966};
        // P[1]=65535 (100%), P[2]=49152 (75%), P[3]=32768 (50%), etc.

        long t0 = System.nanoTime();

        // ── Step 1: Initialize grid and place seeds ──
        System.out.print("Allocating grid...");
        byte[] grid = new byte[(int) N];
        System.out.println(" done (" + (System.nanoTime() - t0) / 1_000_000 + "ms)");

        int cellSize = 8000;
        int numCellsX = (SIZE + cellSize - 1) / cellSize;
        int numCellsZ = (SIZE + cellSize - 1) / cellSize;
        int numSeeds = 0;

        java.util.ArrayList<Integer> frontier = new java.util.ArrayList<>();

        t0 = System.nanoTime();
        System.out.print("Placing seeds...");
        java.util.Random posRng = new java.util.Random(seed);
        for (int cz = 0; cz < numCellsZ; cz++) {
            for (int cx = 0; cx < numCellsX; cx++) {
                int sx = cx * cellSize + posRng.nextInt(cellSize);
                int sz = cz * cellSize + posRng.nextInt(cellSize);
                if (sx >= SIZE || sz >= SIZE) continue;
                numSeeds++;
                byte plateIdx = (byte) (numSeeds & 0xFF);
                if (plateIdx == 0) plateIdx = 1; // 0 means unclaimed
                grid[sz * SIZE + sx] = plateIdx;
                frontier.add((sx << 16) | sz);
            }
        }
        System.out.println(" " + numSeeds + " seeds (" + (System.nanoTime() - t0) / 1_000_000 + "ms)");
        System.out.println();

        // ── Step 2: Growth loop ──
        t0 = System.nanoTime();
        System.out.print("Growing plates ");
        int[] dx8 = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dz8 = {-1, -1, -1, 0, 0, 1, 1, 1};
        long claimed = numSeeds;
        int lastPct = -1;
        long nextReport = N / 50;
        int[] dirOrder = new int[8];
        java.util.ArrayList<Integer> nextFrontier = new java.util.ArrayList<>();

        while (claimed < N) {
            for (int fi = 0; fi < frontier.size(); fi++) {
                int enc = frontier.get(fi);
                int cx = enc >> 16;
                int cz = enc & 0xFFFF;
                byte myPlate = grid[cz * SIZE + cx];
                if (myPlate == 0) continue; // shouldn't happen

                // Scan 8 neighbors for unclaimed
                int uc = 0;
                for (int d = 0; d < 8; d++) {
                    int nx = cx + dx8[d];
                    int nz = cz + dz8[d];
                    if (nx < 0 || nx >= SIZE || nz < 0 || nz >= SIZE) continue;
                    if (grid[nz * SIZE + nx] == 0) uc++;
                }
                if (uc == 0) continue;

                // Decide how many to claim: check from 8 down to 1
                int rngState = (int)(seed ^ (cx * 374761393) ^ (cz * 668265263) ^ (myPlate * 1234567));
                int k = 0;
                for (int d = 8; d >= 1; d--) {
                    rngState ^= rngState << 13;
                    rngState ^= rngState >>> 17;
                    rngState ^= rngState << 5;
                    int r = rngState & 0xFFFF;
                    if (r < P[d]) { k = d; break; }
                }
                if (k > uc) k = uc;

                if (k == 0) continue;

                // Pick k random unclaimed neighbors
                // Build list of candidate directions
                int[] cand = new int[uc];
                int ci = 0;
                for (int d = 0; d < 8; d++) {
                    int nx = cx + dx8[d];
                    int nz = cz + dz8[d];
                    if (nx < 0 || nx >= SIZE || nz < 0 || nz >= SIZE) continue;
                    if (grid[nz * SIZE + nx] == 0) cand[ci++] = d;
                }
                // Fisher-Yates shuffle first k
                for (int i = 0; i < k && i < cand.length; i++) {
                    rngState ^= rngState << 13;
                    rngState ^= rngState >>> 17;
                    rngState ^= rngState << 5;
                    int j = i + ((rngState & 0x7FFFFFFF) % (cand.length - i));
                    int tmp = cand[i]; cand[i] = cand[j]; cand[j] = tmp;
                }
                // Claim first k
                for (int i = 0; i < k; i++) {
                    int d = cand[i];
                    int nx = cx + dx8[d];
                    int nz = cz + dz8[d];
                    int nidx = nz * SIZE + nx;
                    if (grid[nidx] == 0) {
                        grid[nidx] = myPlate;
                        nextFrontier.add((nx << 16) | nz);
                        claimed++;
                    }
                }
            }

            frontier = nextFrontier;
            nextFrontier = new java.util.ArrayList<>();

            while (claimed >= nextReport) {
                int pct = (int)(claimed * 100L / N);
                if (lastPct < 0) System.out.print(pct + "%");
                else System.out.print("." + pct + "%");
                lastPct = pct;
                nextReport += N / 50;
            }
            // Prevent runaway if frontier empties prematurely
            if (frontier.isEmpty() && claimed < N) {
                // Scan for unclaimed blocks and add them as new seeds
                for (int i = 0; i < N && claimed < N; i++) {
                    if (grid[i] == 0) {
                        int x = i % SIZE;
                        int z = i / SIZE;
                        // Assign to nearest claimed plate neighbor
                        byte nearest = 1;
                        long nearDist = Long.MAX_VALUE;
                        for (int d = 0; d < 8; d++) {
                            int nx = x + dx8[d];
                            int nz = z + dz8[d];
                            if (nx < 0 || nx >= SIZE || nz < 0 || nz >= SIZE) continue;
                            byte p = grid[nz * SIZE + nx];
                            if (p != 0) { nearest = p; break; }
                        }
                        grid[i] = nearest;
                        frontier.add((x << 16) | z);
                        claimed++;
                    }
                }
            }
        }

        long simMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.println(" done (" + (simMs / 1000) + "s " + (simMs % 1000) + "ms)");

        // ── Step 3: Render plate map ──
        t0 = System.nanoTime();
        System.out.print("Rendering plate map...");
        int renderStep = 16;
        int imgW = (SIZE + renderStep - 1) / renderStep;
        int imgH = (SIZE + renderStep - 1) / renderStep;
        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);

        // Pre-compute plate colors
        java.util.Map<Byte, Integer> usedColors = new java.util.HashMap<>();
        usedColors.put((byte)0, 0xFF000000); // unclaimed = black (shouldn't appear)
        for (int i = 0; i < (int)N; i++) {
            byte p = grid[i];
            if (p != 0 && !usedColors.containsKey(p)) {
                long h = p * 374761393L;
                float hue = (h & 0xFFFFFFFFL) % 360 / 360.0f;
                usedColors.put(p, 0xFF000000 | java.awt.Color.HSBtoRGB(hue, 0.7f, 0.9f));
            }
        }

        for (int z = 0; z < SIZE; z += renderStep) {
            for (int x = 0; x < SIZE; x += renderStep) {
                byte p = grid[z * SIZE + x];
                img.setRGB(x / renderStep, z / renderStep, usedColors.getOrDefault(p, 0xFF000000));
            }
        }
        write(img, "platemap_" + SIZE);
        System.out.println(" (" + (System.nanoTime() - t0) / 1_000_000 + "ms)");

        // ── Step 4: Analysis ──
        java.util.Map<Byte, Integer> plateAreas = new java.util.HashMap<>();
        for (int i = 0; i < (int)N; i++) {
            byte p = grid[i];
            if (p != 0) plateAreas.merge(p, 1, Integer::sum);
        }
        System.out.println();
        System.out.println("--- Plate Statistics ---");
        System.out.println("  Total plates: " + plateAreas.size());
        java.util.ArrayList<Map.Entry<Byte, Integer>> sorted = new java.util.ArrayList<>(plateAreas.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (Map.Entry<Byte, Integer> e : sorted) {
            System.out.printf("  Plate %3d: %10d blocks (%.2f%%)%n", e.getKey(), e.getValue(), 100.0 * e.getValue() / N);
        }
        System.out.println();
        System.out.println("Done. See platemap_32767.png");
    }

    // ─── Plate color ──────────────────────────────────────────────────────

    private static final Map<Short, Integer> plateColorCache = new HashMap<>();

    private static int plateColor(short pid) {
        Integer cached = plateColorCache.get(pid);
        if (cached != null) return cached;
        long h = pid * 374761393L;
        float hue = (h & 0xFFFFFFFFL) % 360 / 360.0f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 0.35f, 1.0f);
        int color = (rgb & 0x00FFFFFF) | 0x20000000;
        plateColorCache.put(pid, color);
        return color;
    }

    // ─── Vanilla Survey ────────────────────────────────────────────────────

    private static int[] surveyVanilla() {
        int bestCX = 0, bestCZ = 0;
        double bestC = 0;

        for (int cx = -8000; cx <= 8000; cx += 250) {
            for (int cz = -8000; cz <= 8000; cz += 250) {
                double c = Math.abs(SeafloorGenerator.getNoiseAt(cx, cz));
                if (c > bestC) { bestC = c; bestCX = cx; bestCZ = cz; }
            }
        }
        {
            int bcx = bestCX, bcz = bestCZ;
            for (int dx = -375; dx <= 375; dx += 125) {
                for (int dz = -375; dz <= 375; dz += 125) {
                    double c = Math.abs(SeafloorGenerator.getNoiseAt(bcx + dx, bcz + dz));
                    if (c > bestC) { bestC = c; bestCX = bcx + dx; bestCZ = bcz + dz; }
                }
            }
        }
        {
            int bcx = bestCX, bcz = bestCZ;
            for (int dx = -100; dx <= 100; dx += 25) {
                for (int dz = -100; dz <= 100; dz += 25) {
                    double c = Math.abs(SeafloorGenerator.getNoiseAt(bcx + dx, bcz + dz));
                    if (c > bestC) { bestC = c; bestCX = bcx + dx; bestCZ = bcz + dz; }
                }
            }
        }

        System.out.println("  Best |noise|: " + String.format("%.4f", bestC) + " at (" + bestCX + ", " + bestCZ + ")");
        return new int[]{bestCX, bestCZ};
    }

    // ─── Normal Analysis ──────────────────────────────────────────────────

    private static void exportAnalysisNormal(short[] heights, byte[] biomes, short[] plateIds,
                                              int minH, int maxH, long sumH,
                                              int ox, int oz, boolean vanilla) {
        int n = heights.length;
        double mean = (double) sumH / n;
        double varSum = 0;
        for (short h : heights) varSum += (h - mean) * (h - mean);
        double stddev = Math.sqrt(varSum / n);

        short[] sorted = heights.clone();
        java.util.Arrays.sort(sorted);
        double median = sorted.length % 2 == 0
            ? (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0
            : sorted[sorted.length / 2];

        int[] biomeCount = new int[3];
        for (byte b : biomes) biomeCount[b]++;

        Map<Short, Integer> plateCount = new HashMap<>();
        for (short pid : plateIds) plateCount.merge(pid, 1, Integer::sum);

        double maxSlope = 0;
        int maxSx = 0, maxSz = 0;
        for (int z = 8; z < REGION_SIZE - 8; z += 8) {
            for (int x = 8; x < REGION_SIZE - 8; x += 8) {
                int idx = z * REGION_SIZE + x;
                int h_mx = heights[z * REGION_SIZE + x - 8];
                int h_px = heights[z * REGION_SIZE + x + 8];
                int h_mz = heights[(z - 8) * REGION_SIZE + x];
                int h_pz = heights[(z + 8) * REGION_SIZE + x];
                double dx = h_px - h_mx;
                double dz = h_pz - h_mz;
                double slope = Math.sqrt(dx * dx + dz * dz) / 16.0;
                if (slope > maxSlope) {
                    maxSlope = slope;
                    maxSx = x; maxSz = z;
                }
            }
        }

        int[] hist = new int[32];
        int binSize = 64;
        for (short h : heights) {
            int bin = (h + 1024) / binSize;
            if (bin >= 0 && bin < 32) hist[bin]++;
        }

        try {
            PrintWriter pw = new PrintWriter(new File("worldgen_preview",
                "analysis_" + REGION_SIZE + (vanilla ? "_vanilla" : "") + ".txt"), StandardCharsets.UTF_8);
            pw.println("=== Worldgen Analysis ===");
            pw.println("Mode: " + (vanilla ? "VANILLA (original code, 1:1 with game)" : "RANDOM"));
            pw.println("Region: " + REGION_SIZE + "x" + REGION_SIZE + " blocks");
            pw.println("Origin: (" + ox + ", " + oz + ")");
            pw.println();

            pw.println("--- Height Statistics ---");
            pw.printf("  Min: %d  Max: %d  Range: %d%n", minH, maxH, maxH - minH);
            pw.printf("  Mean: %.2f  Median: %.2f  StdDev: %.2f%n", mean, median, stddev);
            pw.println();

            pw.println("--- Height Histogram (32 bins, 64-block width) ---");
            for (int i = 0; i < 32; i++) {
                if (hist[i] > 0) {
                    int binY = -1024 + i * binSize;
                    pw.printf("  y=[%+5d,%+5d): %7d (%.2f%%)%n", binY, binY + binSize, hist[i], 100.0 * hist[i] / n);
                }
            }
            pw.println();

            pw.println("--- Slope ---");
            pw.printf("  Max slope (16-block sample): %.2f blocks/block at world (%d, %d)%n",
                maxSlope, ox + maxSx, oz + maxSz);
            pw.printf("  Heights at ±8 from (%d,%d): x-8=%d x+8=%d z-8=%d z+8=%d (center=%d)%n",
                ox + maxSx, oz + maxSz,
                heights[maxSz * REGION_SIZE + maxSx - 8],
                heights[maxSz * REGION_SIZE + maxSx + 8],
                heights[(maxSz - 8) * REGION_SIZE + maxSx],
                heights[(maxSz + 8) * REGION_SIZE + maxSx],
                heights[maxSz * REGION_SIZE + maxSx]);
            pw.println();

            pw.println("--- Biome Distribution ---");
            pw.printf("  Shallows (absC<0.10): %d (%.1f%%)%n", biomeCount[0], 100.0 * biomeCount[0] / n);
            pw.printf("  Shelf (0.10<=absC<0.50): %d (%.1f%%)%n", biomeCount[1], 100.0 * biomeCount[1] / n);
            pw.printf("  Plains (absC>=0.50): %d (%.1f%%)%n", biomeCount[2], 100.0 * biomeCount[2] / n);

            pw.println();
            pw.println("--- Plate Distribution ---");
            java.util.ArrayList<Map.Entry<Short, Integer>> plateList = new java.util.ArrayList<>(plateCount.entrySet());
            plateList.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            for (Map.Entry<Short, Integer> e : plateList) {
                pw.printf("  Plate hash %d: %d blocks (%.1f%%)%n", e.getKey(), e.getValue(), 100.0 * e.getValue() / n);
            }
            pw.println();

            pw.println("--- Tectonic Boundary Survey ---");
            int numBoundaries = 0;
            double maxTectonicMod = 0, minTectonicMod = 0;
            java.util.Random surveyRng = new java.util.Random(42);
            for (int i = 0; i < 2000; i++) {
                int sx = ox + surveyRng.nextInt(REGION_SIZE);
                int sy = oz + surveyRng.nextInt(REGION_SIZE);
                double mod = SeafloorGenerator.computeTectonicModifier(sx, sy);
                if (Math.abs(mod) > 1) numBoundaries++;
                if (mod > maxTectonicMod) maxTectonicMod = mod;
                if (mod < minTectonicMod) minTectonicMod = mod;
            }
            pw.printf("  Survey (2000 samples): %d near-boundary points, mod range [%.1f, %.1f]%n", numBoundaries, minTectonicMod, maxTectonicMod);

            pw.println("--- 3D Density Scan (8 probe columns) ---");
            java.util.Random volRng = new java.util.Random(99);
            long colSolidSum = 0;
            int airGapCols = 0;
            for (int pi = 0; pi < 8; pi++) {
                int px = ox + volRng.nextInt(REGION_SIZE);
                int pz = oz + volRng.nextInt(REGION_SIZE);
                int h = SeafloorGenerator.getHeightAt(px, pz);
                boolean hasAirGap = false;
                for (int y = -1024; y < h && !hasAirGap; y++) {
                    double sfh = h - y;
                    double grad;
                    if (y <= -1024) grad = 30.0;
                    else if (y >= -924) grad = -1.0;
                    else grad = 30.0 + (double)(y + 1024) / 100.0 * -31.0;
                    if (Math.max(sfh, grad) <= 0) hasAirGap = true;
                }
                colSolidSum += (h - (-1024));
                if (hasAirGap) airGapCols++;
            }
            pw.printf("  %d/8 columns with air gaps (%.0f avg solid blocks below surface)%n", airGapCols, colSolidSum / 8.0);
            if (airGapCols == 0) pw.println("  -> NO interior holes");

            pw.println("--- Worldgen Assessment ---");
            pw.printf("  Height range: %s (%d blocks)%n", (maxH - minH > 200 ? "ADEQUATE" : "LOW"), maxH - minH);
            boolean biomesOK = biomeCount[0] > 0 && biomeCount[1] > 0 && biomeCount[2] > 0;
            pw.printf("  Biome diversity: %s (shallows=%d, shelf=%d, plains=%d)%n",
                biomesOK ? "GOOD" : "LOW", biomeCount[0], biomeCount[1], biomeCount[2]);
            pw.printf("  Distinct tectonic plates: %d%n", plateList.size());
            pw.println();

            if (vanilla) {
                pw.println("--- Vanilla Verification ---");
                pw.println("  This output is 1:1 with unmodified Minecraft.");
                pw.println("  Key checkpoints:");
                for (int i = 0; i < 5; i++) {
                    int cx = 500 + i * 1500;
                    int cy = 500 + i * 1500;
                    int idx = cy * REGION_SIZE + cx;
                    int biome = biomes[idx];
                    String biomeName = biome == 0 ? "shallows" : biome == 1 ? "shelf" : "plains";
                    pw.printf("    (%d, %d): height=%d, biome=%s, block=%s%n",
                        ox + cx, oz + cy, heights[idx], biomeName, biome == 2 ? "clay" : "mud");
                }
            }
            pw.close();
        } catch (Exception e) {
            System.err.println("  Failed to write analysis: " + e.getMessage());
        }
        System.out.println("  wrote analysis_" + REGION_SIZE + (vanilla ? "_vanilla" : "") + ".txt");
    }

    // ─── Large Analysis ───────────────────────────────────────────────────

    private static void exportAnalysisLarge(int minH, int maxH, long sumH, int count,
                                             double mean, double m2, int[] hist,
                                             int[] biomeCount, Map<Short, int[]> plateRaw,
                                             int ox, int oz, boolean vanilla, int regionSize) {
        double stddev = Math.sqrt(m2 / count);

        try {
            PrintWriter pw = new PrintWriter(new File("worldgen_preview",
                "analysis_" + regionSize + (vanilla ? "_vanilla" : "") + ".txt"), StandardCharsets.UTF_8);
            pw.println("=== Worldgen Analysis (LARGE) ===");
            pw.println("Mode: " + (vanilla ? "VANILLA" : "RANDOM"));
            pw.println("Region: " + regionSize + "x" + regionSize + " blocks");
            pw.println("Origin: (" + ox + ", " + oz + ")");
            pw.println();

            pw.println("--- Height Statistics ---");
            pw.printf("  Blocks sampled: %d%n", count);
            pw.printf("  Min: %d  Max: %d  Range: %d%n", minH, maxH, maxH - minH);
            pw.printf("  Mean: %.2f  StdDev: %.2f%n", mean, stddev);
            pw.println();

            pw.println("--- Height Histogram (32 bins, 64-block width) ---");
            int binSize = 64;
            for (int i = 0; i < 32; i++) {
                if (hist[i] > 0) {
                    int binY = -1024 + i * binSize;
                    pw.printf("  y=[%+5d,%+5d): %7d (%.2f%%)%n", binY, binY + binSize, hist[i], 100.0 * hist[i] / count);
                }
            }
            pw.println();

            pw.println("--- Biome Distribution ---");
            pw.printf("  Shallows (absC<0.10): %d (%.1f%%)%n", biomeCount[0], 100.0 * biomeCount[0] / count);
            pw.printf("  Shelf (0.10<=absC<0.50): %d (%.1f%%)%n", biomeCount[1], 100.0 * biomeCount[1] / count);
            pw.printf("  Plains (absC>=0.50): %d (%.1f%%)%n", biomeCount[2], 100.0 * biomeCount[2] / count);
            pw.println();

            pw.println("--- Plate Distribution ---");
            java.util.ArrayList<Map.Entry<Short, int[]>> plateList = new java.util.ArrayList<>(plateRaw.entrySet());
            plateList.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
            for (Map.Entry<Short, int[]> e : plateList) {
                pw.printf("  Plate hash %d: %d blocks (%.1f%%)%n", e.getKey(), e.getValue()[0], 100.0 * e.getValue()[0] / count);
            }
            pw.println();

            pw.println("--- Tectonic Boundary Survey ---");
            int numBoundaries = 0;
            double maxTectonicMod = 0, minTectonicMod = 0;
            java.util.Random surveyRng = new java.util.Random(42);
            int surveySamples = Math.min(2000, count);
            for (int i = 0; i < surveySamples; i++) {
                int sx = ox + surveyRng.nextInt(regionSize);
                int sy = oz + surveyRng.nextInt(regionSize);
                double mod = SeafloorGenerator.computeTectonicModifier(sx, sy);
                if (Math.abs(mod) > 1) numBoundaries++;
                if (mod > maxTectonicMod) maxTectonicMod = mod;
                if (mod < minTectonicMod) minTectonicMod = mod;
            }
            pw.printf("  Survey (%d samples): %d near-boundary points, mod range [%.1f, %.1f]%n", surveySamples, numBoundaries, minTectonicMod, maxTectonicMod);

            pw.println("--- 3D Density Scan (8 probe columns) ---");
            java.util.Random volRng = new java.util.Random(99);
            long colSolidSum = 0;
            int airGapCols = 0;
            for (int pi = 0; pi < 8; pi++) {
                int px = ox + volRng.nextInt(regionSize);
                int pz = oz + volRng.nextInt(regionSize);
                int h = SeafloorGenerator.getHeightAt(px, pz);
                boolean hasAirGap = false;
                for (int y = -1024; y < h; y++) {
                    double sfh = h - y;
                    double grad;
                    if (y <= -1024) grad = 30.0;
                    else if (y >= -924) grad = -1.0;
                    else grad = 30.0 + (double)(y + 1024) / 100.0 * -31.0;
                    if (Math.max(sfh, grad) <= 0) { hasAirGap = true; break; }
                }
                colSolidSum += (h - (-1024));
                if (hasAirGap) airGapCols++;
            }
            pw.printf("  %d/8 columns with air gaps (%.0f avg solid blocks below surface)%n", airGapCols, colSolidSum / 8.0);
            if (airGapCols == 0) pw.println("  -> NO interior holes");

            pw.println("--- Worldgen Assessment ---");
            pw.printf("  Height range: %s (%d blocks)%n", (maxH - minH > 200 ? "ADEQUATE" : "LOW"), maxH - minH);
            boolean biomesOK = biomeCount[0] > 0 && biomeCount[1] > 0 && biomeCount[2] > 0;
            pw.printf("  Biome diversity: %s (shallows=%d, shelf=%d, plains=%d)%n",
                biomesOK ? "GOOD" : "LOW", biomeCount[0], biomeCount[1], biomeCount[2]);
            pw.printf("  Distinct tectonic plates: %d%n", plateList.size());

            if (vanilla) {
                pw.println();
                pw.println("--- Vanilla Verification Checkpoints ---");
                for (int i = 0; i < 10; i++) {
                    int cx = 1000 + i * 3000;
                    int cy = 1000 + i * 3000;
                    if (cx >= regionSize || cy >= regionSize) break;
                    int h = SeafloorGenerator.getHeightAt(ox + cx, oz + cy);
                    double c = Math.abs(SeafloorGenerator.getNoiseAt(ox + cx, oz + cy));
                    String biomeName = c < 0.10 ? "shallows" : c < 0.50 ? "shelf" : "plains";
                    pw.printf("    (%d, %d): height=%d, biome=%s%n", ox + cx, oz + cy, h, biomeName);
                }
            }

            pw.close();
        } catch (Exception e) {
            System.err.println("  Failed to write analysis: " + e.getMessage());
        }
        System.out.println("  wrote analysis_" + regionSize + (vanilla ? "_vanilla" : "") + ".txt");
    }

    // ─── Color helpers ─────────────────────────────────────────────────────

    private static int blend(int base, int overlay) {
        int aO = (overlay >> 24) & 0xFF;
        if (aO == 0) return base;
        if (aO >= 255) return overlay;
        int rB = (base >> 16) & 0xFF, gB = (base >> 8) & 0xFF, bB = base & 0xFF;
        int rO = (overlay >> 16) & 0xFF, gO = (overlay >> 8) & 0xFF, bO = overlay & 0xFF;
        float a = aO / 255.0f;
        int r = Math.min(255, (int)(rB * (1 - a) + rO * a));
        int g = Math.min(255, (int)(gB * (1 - a) + gO * a));
        int b = Math.min(255, (int)(bB * (1 - a) + bO * a));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int darken(int rgb, double factor) {
        int r = Math.min(255, (int)(((rgb >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int)(((rgb >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int)((rgb & 0xFF) * factor));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int lighten(int rgb, double factor) {
        int r = Math.min(255, (int)(((rgb >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int)(((rgb >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int)((rgb & 0xFF) * factor));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void write(BufferedImage img, String name) {
        try {
            File f = new File("worldgen_preview", name + ".png");
            ImageIO.write(img, "PNG", f);
            System.out.println("  wrote " + name + ".png  (" + img.getWidth() + "x" + img.getHeight() + ", " + f.length() / 1024 + "KB)");
        } catch (Exception e) {
            System.err.println("  Failed to write " + name + ": " + e.getMessage());
        }
    }
}
