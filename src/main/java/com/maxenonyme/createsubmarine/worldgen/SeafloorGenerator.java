package com.maxenonyme.createsubmarine.worldgen;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

public class SeafloorGenerator {
    static final int TILE_SIZE = 256;
    private static final int SEA_LEVEL = 0;

    private static final int SHALLOW_HEIGHT = -50;
    private static final int PLAINS_HEIGHT = -200;
    private static final int MIN_FLOOR_HEIGHT = -52;
    private static final double PLAINS_NOISE_AMP = 8.0;   // ±8 blocks (subtle abyssal hills)
    private static final double SHELF_NOISE_AMP = 15.0;   // ±15 blocks (moderate slope)
    private static final double SHALLOWS_NOISE_AMP = 4.0;  // ±4 blocks (gentle shelf)

    private static final double NOISE_SCALE = 0.002;

    private static final double SHELF_BOUNDARY = 0.10;

    // --- Plate Tectonics Constants ---
    public static final int PLATE_CELL_SIZE = 5000;
    private static final double ISLAND_ARC_OFFSET = 400.0;
    private static final int CANYON_CELL_SIZE = 120;
    private static final double CANYON_MAX_DEPTH = 40.0;
    private static final double VENT_CELL_SIZE = 600;
    private static final int HOTSPOT_CELL_SIZE = 5000;

    // Coarse grid resolution for tectonic features (bilinear interpolation)
    private static final int COARSE_RES = 8;

    private static long activeSeed = 420691337L;
    private static boolean skipJsonConfig = false;

    // --- Data-driven Plate Tectonics Config ---
    private static PlateTectonicsConfig config;

    private static synchronized void ensureConfig() {
        if (config != null) return;
        if (skipJsonConfig) {
            config = RandomPlateGenerator.generate(activeSeed);
            return;
        }
        try {
            InputStream is = SeafloorGenerator.class.getResourceAsStream("/data/create_submarine/tectonic_plates/default.json");
            if (is != null) {
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                config = PlateTectonicsConfig.CODEC.parse(JsonOps.INSTANCE, new Gson().fromJson(json, JsonElement.class)).result().orElse(null);
            }
        } catch (Exception ignored) {}
        if (config == null) {
            config = RandomPlateGenerator.generate(activeSeed);
        }
    }

    private static TectonicPlate getPlateForCell(int cellX, int cellZ) {
        ensureConfig();
        long cellHash = (cellX * 374761393L) ^ (cellZ * 668265263L) ^ config.seed();
        Random rng = new Random(cellHash);
        double totalWeight = 0;
        for (TectonicPlate p : config.plates()) totalWeight += p.weight();
        if (totalWeight <= 0) return new TectonicPlate("default", "", 0, 0, 1, true, 1);
        double r = rng.nextDouble() * totalWeight;
        double cumulative = 0;
        for (TectonicPlate p : config.plates()) {
            cumulative += p.weight();
            if (r < cumulative) return p;
        }
        return config.plates().getLast();
    }

    private static PlateInfo buildPlateInfo(int cellX, int cellZ) {
        long hash = (cellX * 374761393L) ^ (cellZ * 668265263L) ^ (activeSeed + 999);
        Random rng = new Random(hash);
        double x = cellX * PLATE_CELL_SIZE + rng.nextDouble() * PLATE_CELL_SIZE;
        double z = cellZ * PLATE_CELL_SIZE + rng.nextDouble() * PLATE_CELL_SIZE;
        TectonicPlate plate = getPlateForCell(cellX, cellZ);
        int pid = (cellX * 374761393) ^ (cellZ * 668265263);
        return new PlateInfo(pid, x, z, plate.vx(), plate.vz(), plate.oceanic(), plate.mass());
    }

    private static final ConcurrentHashMap<Long, short[]> sharedHeightCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, short[]> sharedNoiseCache = new ConcurrentHashMap<>();

    private static PerlinNoise terrainNoise;
    private static PerlinNoise canyonNoise;

    private static PerlinNoise getTerrainNoise() {
        PerlinNoise n = terrainNoise;
        if (n == null) {
            synchronized (SeafloorGenerator.class) {
                n = terrainNoise;
                if (n == null) {
                    n = new PerlinNoise(activeSeed, 4, 2.0, 0.5);
                    terrainNoise = n;
                }
            }
        }
        return n;
    }

    private static PerlinNoise getCanyonNoise() {
        PerlinNoise n = canyonNoise;
        if (n == null) {
            synchronized (SeafloorGenerator.class) {
                n = canyonNoise;
                if (n == null) {
                    n = new PerlinNoise(activeSeed + 13, 4, 2.0, 0.5);
                    canyonNoise = n;
                }
            }
        }
        return n;
    }

    private static long tileKey(int tileX, int tileZ) {
        return ((long) tileX << 32) | (tileZ & 0xFFFFFFFFL);
    }

    public static short[] getOrGenerateTile(int tileX, int tileZ) {
        long key = tileKey(tileX, tileZ);
        return sharedHeightCache.computeIfAbsent(key, k -> {
            TileData td = computeTileData(tileX, tileZ);
            sharedNoiseCache.put(k, td.noises);
            return td.heights;
        });
    }

    public static double getNoiseAt(int wx, int wz) {
        int tileX = Math.floorDiv(wx, TILE_SIZE);
        int tileZ = Math.floorDiv(wz, TILE_SIZE);
        long key = tileKey(tileX, tileZ);

        short[] noises = sharedNoiseCache.get(key);
        if (noises == null) {
            getOrGenerateTile(tileX, tileZ);
            noises = sharedNoiseCache.get(key);
            if (noises == null) {
                TileData td = computeTileData(tileX, tileZ);
                sharedNoiseCache.put(key, td.noises);
                noises = td.noises;
            }
        }

        int lx = wx - tileX * TILE_SIZE;
        int lz = wz - tileZ * TILE_SIZE;
        return noises[lz * TILE_SIZE + lx] / 32767.0;
    }

    private static record TileData(short[] heights, short[] noises) {}

    // --- Plate Tectonics ---

    private static record PlateInfo(int plateId, double seedX, double seedZ, double vx, double vz, boolean oceanic, double mass) {}

    private static PlateInfo getPlateInfo(int wx, int wz) {
        int cellX = Math.floorDiv(wx, PLATE_CELL_SIZE);
        int cellZ = Math.floorDiv(wz, PLATE_CELL_SIZE);
        double nearestDist2 = Double.MAX_VALUE;
        PlateInfo nearest = null;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;
                PlateInfo info = buildPlateInfo(cx, cz);
                double d2 = (wx - info.seedX()) * (wx - info.seedX()) + (wz - info.seedZ()) * (wz - info.seedZ());
                if (d2 < nearestDist2) {
                    nearestDist2 = d2;
                    nearest = info;
                }
            }
        }
        return nearest;
    }

    private static record PairData(double convergence, double shear, double dist, double nx, double nz,
                                    PlateInfo a, PlateInfo b) {}

    // Signed distance from boundary bisector (positive toward plate A, negative toward B)
    private static double signedBoundaryDist(int wx, int wz, PlateInfo a, PlateInfo b, double nx, double nz) {
        double mx = (a.seedX() + b.seedX()) * 0.5;
        double mz = (a.seedZ() + b.seedZ()) * 0.5;
        return (wx - mx) * nx + (wz - mz) * nz;
    }

    private static record NearestPlates(PairData[] pairs, PlateInfo p1, PlateInfo p2, PlateInfo p3) {}

    // Collect all unique plates in the 3x3 neighborhood
    private static List<PlateInfo> computeUniquePlates(int wx, int wz) {
        int cellX = Math.floorDiv(wx, PLATE_CELL_SIZE);
        int cellZ = Math.floorDiv(wz, PLATE_CELL_SIZE);
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        java.util.ArrayList<PlateInfo> plates = new java.util.ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                PlateInfo info = buildPlateInfo(cellX + dx, cellZ + dz);
                if (seen.add(info.plateId())) {
                    plates.add(info);
                }
            }
        }
        return plates;
    }

    // Find the 3 nearest plate seeds at (wx, wz) and compute pairwise boundary data
    private static NearestPlates findNearestPlates(int wx, int wz) {
        int cellX = Math.floorDiv(wx, PLATE_CELL_SIZE);
        int cellZ = Math.floorDiv(wz, PLATE_CELL_SIZE);
        PlateInfo[] infos = new PlateInfo[3];
        double[] dist2 = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                PlateInfo info = buildPlateInfo(cellX + dx, cellZ + dz);
                double d2 = (wx - info.seedX()) * (wx - info.seedX()) + (wz - info.seedZ()) * (wz - info.seedZ());
                for (int i = 0; i < 3; i++) {
                    if (d2 < dist2[i]) {
                        for (int j = 2; j > i; j--) {
                            infos[j] = infos[j - 1];
                            dist2[j] = dist2[j - 1];
                        }
                        infos[i] = info;
                        dist2[i] = d2;
                        break;
                    }
                }
            }
        }

        if (infos[1] == null) return null;

        // Compute boundaries between all pairs
        PairData[] pairs = new PairData[3];
        int[][] combos = {{0,1}, {0,2}, {1,2}};
        for (int pi = 0; pi < 3; pi++) {
            int ai = combos[pi][0], bi = combos[pi][1];
            PlateInfo a = infos[ai], b = infos[bi];
            if (b == null) { pairs[pi] = null; continue; }

            double nx = a.seedX() - b.seedX();
            double nz = a.seedZ() - b.seedZ();
            double len = Math.sqrt(nx * nx + nz * nz);
            if (len < 0.001) { pairs[pi] = null; continue; }
            nx /= len;
            nz /= len;

            double vrx = a.vx() - b.vx();
            double vrz = a.vz() - b.vz();
            double convergence = vrx * nx + vrz * nz;
            double shear = Math.abs(vrx * (-nz) + vrz * nx);
            double dist = Math.sqrt(dist2[bi]);

            pairs[pi] = new PairData(convergence, shear, dist, nx, nz, a, b);
        }

        return new NearestPlates(pairs, infos[0], infos[1], infos[2]);
    }

    // Compute tectonic modifier for a single plate pair interaction
    private static double pairModifier(int wx, int wz, PairData pd) {
        if (pd == null) return 0.0;

        // Signed distance from boundary bisector (positive = A side, negative = B side)
        double signedDist = signedBoundaryDist(wx, wz, pd.a, pd.b, pd.nx, pd.nz);
        double d = Math.abs(signedDist);

        // Farthest Gaussian reach: collision plateau σ=200 → 600 blocks (3σ)
        if (d > 600) return 0.0;

        double convergence = pd.convergence;
        double shear = pd.shear;
        double absConv = Math.abs(convergence);

        // Obliquity: 0 = pure convergence/divergence, π/2 = pure strike-slip
        double obliquity = Math.atan2(shear, Math.max(absConv, 1e-10));

        // Strength scaling (convergence 0-15 cm/yr maps to 0-1)
        double strength = Math.min(absConv, 15.0) / 15.0;

        boolean aOc = pd.a.oceanic(), bOc = pd.b.oceanic();
        boolean bothContinental = !aOc && !bOc;
        boolean subductIsA;
        if (aOc && !bOc) subductIsA = true;
        else if (!aOc && bOc) subductIsA = false;
        else subductIsA = pd.a.mass() > pd.b.mass();
        boolean onSubductingSide = (signedDist > 0) == subductIsA;

        double modifier = 0;

        if (convergence > 0.001) { // convergent
            if (obliquity < Math.PI / 6) { // < 30° — pure convergence
                if (bothContinental) {
                    // COLLISION: narrow range + broad plateau
                    modifier = 450.0 * Math.exp(-d*d/(2.0*3600.0))
                             + 250.0 * Math.exp(-d*d/(2.0*40000.0));
                } else {
                    // SUBDUCTION: trench + outer rise (subducting) + volcanic arc (overriding)
                    double trench = -224.0 * Math.exp(-d*d/(2.0*1225.0));
                    double outerRise = 0.0;
                    double arc = 0.0;
                    if (onSubductingSide) {
                        outerRise = 50.0 * Math.exp(-Math.pow(d-55.0, 2)/(2.0*625.0));
                    } else {
                        arc = 300.0 * Math.exp(-Math.pow(d-100.0, 2)/(2.0*2025.0));
                    }
                    modifier = trench + outerRise;
                    if (arc > modifier) modifier = arc;
                }
            } else if (obliquity < Math.PI / 3) { // 30°-60° — oblique convergence
                // TRANSPRESSIONAL
                double tgSpeed = Math.abs(shear);
                double transpress = 250.0 * Math.exp(-d*d/(2.0*3025.0)) * Math.min(tgSpeed / 50.0, 2.0);
                if (transpress > modifier) modifier = transpress;
            } else { // ≥ 60° — strike-slip dominated
                // TRANSFORM valley
                double transformValley = -60.0 * Math.exp(-d*d/(2.0*900.0));
                if (transformValley < modifier) modifier = transformValley;
            }
        } else if (convergence < -0.001) { // divergent
            if (obliquity < Math.PI / 6) { // < 30° — pure divergence
                // DIVERGENT: rift valley + shoulders + thermal swell
                modifier = -250.0 * Math.exp(-d*d/(2.0*625.0))
                          + 150.0 * Math.exp(-Math.pow(d-70.0, 2)/(2.0*1225.0))
                          + 50.0  * Math.exp(-Math.pow(d-150.0, 2)/(2.0*2500.0));
            } else if (obliquity < Math.PI / 3) { // 30°-60° — oblique divergence
                // TRANSTENSIONAL
                double transten = -100.0 * Math.exp(-d*d/(2.0*2500.0));
                if (transten < modifier) modifier = transten;
            } else { // ≥ 60° — strike-slip dominated
                double transformValley = -60.0 * Math.exp(-d*d/(2.0*900.0));
                if (transformValley < modifier) modifier = transformValley;
            }
        } else { // near-zero convergence — pure strike-slip
            // TRANSFORM valley
            modifier = -60.0 * Math.exp(-d*d/(2.0*900.0));
        }

        return modifier * strength;
    }

    // Compute tectonic modifier using all-pair bisector-distance blending across ALL plates
    // in the 3x3 neighborhood. Weight = 1/(d²/σ² + 1) with σ=200 ensures smooth ~400-block
    // transitions between overlapping boundaries at triple junctions.
    private static double computeTectonicModifier(int wx, int wz, List<PlateInfo> plates) {
        if (plates.size() < 2) return 0.0;
        double modifier = 0;
        double totalWeight = 0;
        for (int i = 0; i < plates.size(); i++) {
            for (int j = i + 1; j < plates.size(); j++) {
                PlateInfo a = plates.get(i);
                PlateInfo b = plates.get(j);

                double nx = a.seedX() - b.seedX();
                double nz = a.seedZ() - b.seedZ();
                double len = Math.sqrt(nx*nx + nz*nz);
                if (len < 0.001) continue;
                nx /= len; nz /= len;

                double mx = (a.seedX() + b.seedX()) * 0.5;
                double mz = (a.seedZ() + b.seedZ()) * 0.5;
                double signedDist = (wx - mx) * nx + (wz - mz) * nz;
                double d = Math.abs(signedDist);
                if (d > 1200) continue;

                double vrx = a.vx() - b.vx();
                double vrz = a.vz() - b.vz();
                double convergence = vrx * nx + vrz * nz;
                double shear = Math.abs(vrx * (-nz) + vrz * nx);

                PairData pd = new PairData(convergence, shear, 0, nx, nz, a, b);
                double mod = pairModifier(wx, wz, pd);
                double w = 1.0 / (d*d / 40000.0 + 1.0);
                modifier += mod * w;
                totalWeight += w;
            }
        }
        return totalWeight > 0 ? modifier / totalWeight : 0.0;
    }

    // Legacy entry-point for backward compat (computes plates per call)
    public static double computeTectonicModifier(int wx, int wz) {
        return computeTectonicModifier(wx, wz, computeUniquePlates(wx, wz));
    }

    // --- Submarine Canyons (purely noise-based, no grid cells) ---

    private static double computeCanyonDepth(int wx, int wz, double continentValue) {
        double absC = Math.abs(continentValue);
        double canyonEdge = SHELF_BOUNDARY + 0.18; // 0.25 — matches shelf edge
        if (absC < SHELF_BOUNDARY || absC > canyonEdge) return 0;

        double shelfMid = (SHELF_BOUNDARY + canyonEdge) / 2.0;
        double slopeFactor = 1.0 - Math.abs(absC - shelfMid) / (canyonEdge - SHELF_BOUNDARY) * 2;
        if (slopeFactor < 0) return 0;
        slopeFactor = Math.min(1.0, slopeFactor);

        PerlinNoise cn = getCanyonNoise();

        // Presence mask: ~60% of slope area has canyons (erratic shelf)
        double presence = cn.noise(wx * 0.004, wz * 0.004);
        if (presence < -0.2) return 0;

        // Domain-warped noise for branching dendritic pattern
        double warpX = wx * 0.01 + cn.noise(wx * 0.02, wz * 0.02) * 20;
        double warpZ = wz * 0.01 + cn.noise(wx * 0.02 + 500, wz * 0.02 + 500) * 20;
        double canyonVal = cn.noise(warpX, warpZ);

        // Absolute value creates V-shaped cross-section
        double canyonShape = Math.max(0, Math.abs(canyonVal) - 0.25) / 0.75;
        return canyonShape * CANYON_MAX_DEPTH * slopeFactor;
    }

    // --- Hydrothermal Vents ---

    private static double computeVentHeight(int wx, int wz, PairData bd) {
        if (bd == null || bd.convergence >= -0.1) return 0;
        // Only at divergent boundaries near ridge crest
        if (bd.dist > 300) return 0;

        int cellX = (int) Math.floor(wx / VENT_CELL_SIZE);
        int cellZ = (int) Math.floor(wz / VENT_CELL_SIZE);
        long hash = (cellX * 374761393L) ^ (cellZ * 668265263L) ^ (activeSeed + 42);
        Random rng = new Random(hash);

        double ventX = cellX * VENT_CELL_SIZE + rng.nextDouble() * VENT_CELL_SIZE;
        double ventZ = cellZ * VENT_CELL_SIZE + rng.nextDouble() * VENT_CELL_SIZE;
        double d2 = (wx - ventX) * (wx - ventX) + (wz - ventZ) * (wz - ventZ);

        if (d2 > 1600) return 0; // 40-block radius

        double dist = Math.sqrt(d2);
        double t = 1.0 - dist / 40;
        double chimneyHeight = 8 + rng.nextDouble() * 15;
        return chimneyHeight * (t * t * (3 - 2 * t));
    }

    // --- Hotspot Seamount Chains ---

    private static double computeHotspotHeight(int wx, int wz) {
        int cellX = Math.floorDiv(wx, HOTSPOT_CELL_SIZE);
        int cellZ = Math.floorDiv(wz, HOTSPOT_CELL_SIZE);
        double maxHeight = 0;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;
                long hash = (cx * 374761393L) ^ (cz * 668265263L) ^ (activeSeed + 55);
                Random rng = new Random(hash);

                double plumeX = cx * HOTSPOT_CELL_SIZE + rng.nextDouble() * HOTSPOT_CELL_SIZE;
                double plumeZ = cz * HOTSPOT_CELL_SIZE + rng.nextDouble() * HOTSPOT_CELL_SIZE;

                // Plate velocity at this hotspot cell — creates chain direction
                PlateInfo spotPI = buildPlateInfo(cx, cz);
                double speed = Math.sqrt(spotPI.vx() * spotPI.vx() + spotPI.vz() * spotPI.vz());

                // Project position relative to plume, accounting for plate motion
                double rdx = wx - plumeX;
                double rdz = wz - plumeZ;
                double along = rdx * (-spotPI.vz()) + rdz * spotPI.vx(); // perpendicular to motion
                double across = rdx * spotPI.vx() + rdz * spotPI.vz();  // along motion direction

                // Hotspot creates a chain: taller near the plume, fading along the motion direction
                double plumeDist = Math.sqrt(rdx * rdx + rdz * rdz);
                double chainWidth = 80 + speed * 20;
                double chainLen = 2000 + speed * 500;

                double widthFactor = Math.exp(-(along * along) / (chainWidth * chainWidth));
                double lenFactor = Math.exp(-(across * across) / (chainLen * chainLen));
                double height = 60 + rng.nextDouble() * 140;
                double h = height * widthFactor * lenFactor * (1.0 - plumeDist / (chainLen * 2));
                if (h > maxHeight) maxHeight = h;
            }
        }
        return maxHeight;
    }

    // --- Coarse Grid Helpers ---

    private static double[][] computeCoarseGrid(int baseX, int baseZ, int size, GridFunction func) {
        int cellsX = (size + COARSE_RES - 1) / COARSE_RES + 1;
        int cellsZ = (size + COARSE_RES - 1) / COARSE_RES + 1;
        double[][] grid = new double[cellsZ][cellsX];
        for (int gz = 0; gz < cellsZ; gz++) {
            for (int gx = 0; gx < cellsX; gx++) {
                int wx = baseX + gx * COARSE_RES;
                int wz = baseZ + gz * COARSE_RES;
                grid[gz][gx] = func.compute(wx, wz);
            }
        }
        return grid;
    }

    private static double interpGrid(double[][] grid, int baseX, int baseZ, int wx, int wz) {
        double fx = (double)(wx - baseX) / COARSE_RES;
        double fz = (double)(wz - baseZ) / COARSE_RES;
        int gx = (int) Math.floor(fx);
        int gz = (int) Math.floor(fz);
        if (gx < 0) gx = 0;
        if (gz < 0) gz = 0;
        if (gx >= grid[0].length - 1) gx = grid[0].length - 2;
        if (gz >= grid.length - 1) gz = grid.length - 2;
        double lx = fx - gx;
        double lz = fz - gz;
        double v00 = grid[gz][gx];
        double v10 = grid[gz][gx + 1];
        double v01 = grid[gz + 1][gx];
        double v11 = grid[gz + 1][gx + 1];
        double v0 = v00 + lx * (v10 - v00);
        double v1 = v01 + lx * (v11 - v01);
        return v0 + lz * (v1 - v0);
    }

    private interface GridFunction {
        double compute(int wx, int wz);
    }

    private static TileData computeTileData(int tileX, int tileZ) {
        PerlinNoise terrain = getTerrainNoise();

        short[] heights = new short[TILE_SIZE * TILE_SIZE];
        short[] noises = new short[TILE_SIZE * TILE_SIZE];
        int baseX = tileX * TILE_SIZE;
        int baseZ = tileZ * TILE_SIZE;

        // Precompute plate list for this tile (same 3x3 neighborhood for all coarse nodes)
        List<PlateInfo> tilePlates = computeUniquePlates(baseX, baseZ);

        // Precompute tectonic modifier at coarse resolution
        double[][] tectonicGrid = computeCoarseGrid(baseX, baseZ, TILE_SIZE, (wx, wz) ->
            computeTectonicModifier(wx, wz, tilePlates)
        );

        // Precompute canyon modifier at coarse resolution
        double[][] canyonGrid = computeCoarseGrid(baseX, baseZ, TILE_SIZE, (wx, wz) -> {
            // Need continent value for slope detection
            double c = terrain.fbm(wx * NOISE_SCALE, wz * NOISE_SCALE);
            return computeCanyonDepth(wx, wz, c);
        });

        // Precompute vent heights at coarse resolution
        double[][] ventGrid = computeCoarseGrid(baseX, baseZ, TILE_SIZE, (wx, wz) -> {
            NearestPlates np = findNearestPlates(wx, wz);
            PairData pd = np != null ? np.pairs()[0] : null;
            return computeVentHeight(wx, wz, pd);
        });

        // Precompute hotspot heights at coarse resolution
        double[][] hotspotGrid = computeCoarseGrid(baseX, baseZ, TILE_SIZE, (wx, wz) ->
            computeHotspotHeight(wx, wz)
        );

        // Precompute boundary proximity [0, 1] for noise amping
        double[][] boundaryProxGrid = computeCoarseGrid(baseX, baseZ, TILE_SIZE, (wx, wz) -> {
            double mod = Math.abs(computeTectonicModifier(wx, wz, tilePlates));
            return Math.min(1.0, mod / 120.0);
        });

        for (int lz = 0; lz < TILE_SIZE; lz++) {
            for (int lx = 0; lx < TILE_SIZE; lx++) {
                int wx = baseX + lx;
                int wz = baseZ + lz;

                // --- Pass 1: Base terrain with domain warping for organic shapes ---
                double warpOffsetX = terrain.noise(wx * 0.003, wz * 0.003) * 40.0;
                double warpOffsetZ = terrain.noise(wx * 0.003 + 1000, wz * 0.003 + 1000) * 40.0;
                double warpX = wx * NOISE_SCALE + warpOffsetX;
                double warpZ = wz * NOISE_SCALE + warpOffsetZ;
                double c = terrain.fbm(warpX, warpZ);
                c = Math.max(-1.0, Math.min(1.0, c));

                double height;
                double absC = Math.abs(c);

                // Three-band terrain with smoothly blended noise across all zones.
                // |noise| ~ [0, 0.10) → shallows (30%, y=-250), [0.10, 0.50) → shelf (50%, y=-250→-800), [0.50, 1] → plains (20%, y=-800).
                double transitionWidth = 0.40;
                double shelfEdge = SHELF_BOUNDARY + transitionWidth; // 0.50

                // Smoothstep parameter t: 0 at shallows edge, 1 at plains edge
                double t;
                if (absC < SHELF_BOUNDARY) {
                    t = 0;
                } else if (absC < shelfEdge) {
                    t = (absC - SHELF_BOUNDARY) / transitionWidth;
                } else {
                    t = 1;
                }
                double smooth = t * t * (3 - 2 * t);

                // Base height blends from shallow to plain
                double baseElev = SHALLOW_HEIGHT + smooth * (PLAINS_HEIGHT - SHALLOW_HEIGHT);

                // Single noise field (same scale everywhere, continuous across zones)
                double detailNoise = terrain.fbm(wx * 0.005, wz * 0.005);

                // Boundary noise amping: near fault zones, use fbmEroded for sharper terrain
                double boundaryProx = interpGrid(boundaryProxGrid, baseX, baseZ, wx, wz);
                if (boundaryProx > 0.2) {
                    double erodedNoise = terrain.fbmEroded(wx * 0.005, wz * 0.005, boundaryProx * 2.0);
                    detailNoise = detailNoise * (1.0 - boundaryProx * 0.5) + erodedNoise * (boundaryProx * 0.5);
                }

                // Parabolic amplitude: low at shallows, peak mid-shelf, low at plains
                double peakAmp = 4 * t * (1 - t); // 0 at t=0, 1 at t=0.5, 0 at t=1
                double noiseAmp = SHALLOWS_NOISE_AMP + smooth * (PLAINS_NOISE_AMP - SHALLOWS_NOISE_AMP)
                                + peakAmp * (SHELF_NOISE_AMP - SHALLOWS_NOISE_AMP);

                height = baseElev + detailNoise * noiseAmp;

                // --- Tectonic boundary modifiers ---
                double tectonicMod = interpGrid(tectonicGrid, baseX, baseZ, wx, wz);
                height += tectonicMod;

                // --- Canyons (on slopes) ---
                double canyonMod = interpGrid(canyonGrid, baseX, baseZ, wx, wz);
                height -= canyonMod;

                // --- Vents (at ridge crests) ---
                double ventMod = interpGrid(ventGrid, baseX, baseZ, wx, wz);
                height += ventMod;

                // --- Hotspot seamounts ---
                double hotspotMod = interpGrid(hotspotGrid, baseX, baseZ, wx, wz);
                height += hotspotMod;

                int finalHeight = (int) Math.round(height);
                finalHeight = Math.max(MIN_FLOOR_HEIGHT, Math.min(finalHeight, 1024));
                heights[lz * TILE_SIZE + lx] = (short) finalHeight;
                noises[lz * TILE_SIZE + lx] = (short) (c * 32767);
            }
        }
        return new TileData(heights, noises);
    }

    public static short[] generateTile(int tileX, int tileZ) {
        return getOrGenerateTile(tileX, tileZ);
    }

    public static void clearCache() {
        sharedHeightCache.clear();
        sharedNoiseCache.clear();
    }

    public static void reset(long seed) {
        clearCache();
        config = null;
        terrainNoise = null;
        canyonNoise = null;
        activeSeed = seed;
        skipJsonConfig = true;
    }

    public static int getHeightAt(int wx, int wz) {
        int tileX = Math.floorDiv(wx, TILE_SIZE);
        int tileZ = Math.floorDiv(wz, TILE_SIZE);
        short[] tile = getOrGenerateTile(tileX, tileZ);
        int lx = wx - tileX * TILE_SIZE;
        int lz = wz - tileZ * TILE_SIZE;
        return tile[lz * TILE_SIZE + lx];
    }

    private static final ConcurrentHashMap<Long, TectonicPlate> plateCellCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, double[]> plateCenterCache = new ConcurrentHashMap<>();

    public static TectonicPlate getPlateAt(int wx, int wz) {
        int cellX = Math.floorDiv(wx, PLATE_CELL_SIZE);
        int cellZ = Math.floorDiv(wz, PLATE_CELL_SIZE);
        // Find the nearest cell center (Voronoi) for organic boundaries
        double nearestDist2 = Double.MAX_VALUE;
        int nearestCX = cellX, nearestCZ = cellZ;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;
                long ck = (long) cx << 32 | (cz & 0xFFFFFFFFL);
                double[] center = plateCenterCache.computeIfAbsent(ck, k -> {
                    long hash = (cx * 374761393L) ^ (cz * 668265263L) ^ (activeSeed + 999);
                    Random rng = new Random(hash);
                    double sx = cx * PLATE_CELL_SIZE + rng.nextDouble() * PLATE_CELL_SIZE;
                    double sz = cz * PLATE_CELL_SIZE + rng.nextDouble() * PLATE_CELL_SIZE;
                    return new double[]{sx, sz};
                });
                double d2 = (wx - center[0]) * (wx - center[0]) + (wz - center[1]) * (wz - center[1]);
                if (d2 < nearestDist2) {
                    nearestDist2 = d2;
                    nearestCX = cx;
                    nearestCZ = cz;
                }
            }
        }
        long key = (long) nearestCX << 32 | (nearestCZ & 0xFFFFFFFFL);
        int fnCX = nearestCX, fnCZ = nearestCZ;
        return plateCellCache.computeIfAbsent(key, k -> getPlateForCell(fnCX, fnCZ));
    }

    public static int getPlateCellCenterX(int cellX, int cellZ) {
        long hash = (cellX * 374761393L) ^ (cellZ * 668265263L) ^ (activeSeed + 999);
        Random rng = new Random(hash);
        return (int) Math.round(cellX * PLATE_CELL_SIZE + rng.nextDouble() * PLATE_CELL_SIZE);
    }

    public static int getPlateCellCenterZ(int cellX, int cellZ) {
        long hash = (cellX * 374761393L) ^ (cellZ * 668265263L) ^ (activeSeed + 999);
        Random rng = new Random(hash);
        return (int) Math.round(cellZ * PLATE_CELL_SIZE + rng.nextDouble() * PLATE_CELL_SIZE);
    }

    public static String[] getAllPlateNames() {
        ensureConfig();
        String[] names = new String[config.plates().size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = config.plates().get(i).name();
        }
        return names;
    }

    public static PlateTectonicsConfig getConfig() {
        ensureConfig();
        return config;
    }

    public static boolean isAtTectonicBoundary(int wx, int wz) {
        double mod = computeTectonicModifier(wx, wz);
        return Math.abs(mod) > 30;
    }

    public static double getBoundaryStrength(int wx, int wz) {
        double mod = Math.abs(computeTectonicModifier(wx, wz));
        return Math.min(1.0, mod / 100.0);
    }



    private static class PerlinNoise {
        private final int[] perm;
        private final int octaves;
        private final double lacunarity;
        private final double gain;
        // Pre-computed amplitude and frequency per octave for fbm()
        private final double[] ampTable;
        private final double[] freqTable;
        private final double maxAmp;

        PerlinNoise(long seed, int maxOctaves, double lacunarity, double gain) {
            this.octaves = maxOctaves;
            this.lacunarity = lacunarity;
            this.gain = gain;
            Random rng = new Random(seed);
            int[] p = new int[256];
            for (int i = 0; i < 256; i++) p[i] = i;
            for (int i = 255; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int tmp = p[i]; p[i] = p[j]; p[j] = tmp;
            }
            perm = new int[512];
            System.arraycopy(p, 0, perm, 0, 256);
            System.arraycopy(p, 0, perm, 256, 256);

            // Precompute octave tables for performance
            ampTable = new double[maxOctaves];
            freqTable = new double[maxOctaves];
            double amp = 1;
            double freq = 1;
            double totalAmp = 0;
            for (int i = 0; i < maxOctaves; i++) {
                ampTable[i] = amp;
                freqTable[i] = freq;
                totalAmp += amp;
                amp *= gain;
                freq *= lacunarity;
            }
            maxAmp = totalAmp;
        }

        // Standard FBM (no gradient erosion — much faster)
        double fbm(double x, double y) {
            double value = 0;
            for (int i = 0; i < octaves; i++) {
                value += raw(x * freqTable[i], y * freqTable[i]) * ampTable[i];
            }
            return value / maxAmp;
        }

        // Single octave noise (for domain warping)
        double noise(double x, double y) {
            return raw(x, y);
        }

        // Full FBM with erosion weighting (kept for reference, used by zone variant)
        double fbmEroded(double x, double y, double erosionStrength) {
            double value = 0;
            double totalWeight = 0;
            double gx = 0, gy = 0;

            for (int i = 0; i < octaves; i++) {
                double steepness = Math.sqrt(gx * gx + gy * gy);
                double influence = 1.0 / (1.0 + steepness * erosionStrength);

                double[] ng = rawWithGradient(x * freqTable[i], y * freqTable[i]);

                double weight = ampTable[i] * influence;
                value += ng[0] * weight;
                totalWeight += weight;

                gx += ng[1] * freqTable[i] * weight;
                gy += ng[2] * freqTable[i] * weight;
            }

            return totalWeight > 0 ? value / totalWeight : 0;
        }

        private double raw(double x, double y) {
            int xi = (int) Math.floor(x) & 255;
            int yi = (int) Math.floor(y) & 255;
            double xf = x - Math.floor(x);
            double yf = y - Math.floor(y);
            double u = fade(xf);
            double v = fade(yf);
            int aa = perm[perm[xi] + yi];
            int ab = perm[perm[xi] + yi + 1];
            int ba = perm[perm[xi + 1] + yi];
            int bb = perm[perm[xi + 1] + yi + 1];
            double x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u);
            double x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u);
            return lerp(x1, x2, v);
        }

        private static double fade(double t) {
            return t * t * t * (t * (t * 6 - 15) + 10);
        }

        private static double lerp(double a, double b, double t) {
            return a + t * (b - a);
        }

        private static double grad(int hash, double x, double y) {
            switch (hash & 3) {
                case 0: return x + y;
                case 1: return -x + y;
                case 2: return x - y;
                case 3: return -x - y;
                default: return 0;
            }
        }

        private static double fadeDerivative(double t) {
            return 30 * t * t * (1 - t) * (1 - t);
        }

        private double[] rawWithGradient(double x, double y) {
            int xi = (int) Math.floor(x) & 255;
            int yi = (int) Math.floor(y) & 255;
            double xf = x - Math.floor(x);
            double yf = y - Math.floor(y);
            double u = fade(xf);
            double v = fade(yf);
            double du = fadeDerivative(xf);
            double dv = fadeDerivative(yf);

            int aa = perm[perm[xi] + yi];
            int ab = perm[perm[xi] + yi + 1];
            int ba = perm[perm[xi + 1] + yi];
            int bb = perm[perm[xi + 1] + yi + 1];

            int h00 = aa & 3, h10 = ba & 3, h01 = ab & 3, h11 = bb & 3;

            double gx00 = 1 - 2 * (h00 & 1), gy00 = 1 - (h00 & 2);
            double gx10 = 1 - 2 * (h10 & 1), gy10 = 1 - (h10 & 2);
            double gx01 = 1 - 2 * (h01 & 1), gy01 = 1 - (h01 & 2);
            double gx11 = 1 - 2 * (h11 & 1), gy11 = 1 - (h11 & 2);

            double n00 = gx00 * xf + gy00 * yf;
            double n10 = gx10 * (xf - 1) + gy10 * yf;
            double n01 = gx01 * xf + gy01 * (yf - 1);
            double n11 = gx11 * (xf - 1) + gy11 * (yf - 1);

            double A = n00 + u * (n10 - n00);
            double B = n01 + u * (n11 - n01);
            double value = A + v * (B - A);

            double dAx = gx00 + u * (gx10 - gx00) + du * (n10 - n00);
            double dBx = gx01 + u * (gx11 - gx01) + du * (n11 - n01);
            double dx = dAx + v * (dBx - dAx);

            double dAy = gy00 + u * (gy10 - gy00);
            double dBy = gy01 + u * (gy11 - gy01);
            double dy = dAy + v * (dBy - dAy) + dv * (B - A);

            return new double[]{value, dx, dy};
        }
    }
}
