package com.maxenonyme.highseas.wind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.maxenonyme.highseas.config.HighSeasConfig;

public final class WindManager {
    private WindManager() {
    }

    private static final long SEED = 0x5EA1_C0DEL;
    private static final SimplexNoise DIR_NOISE = new SimplexNoise(RandomSource.create(SEED));
    private static final SimplexNoise GUST_NOISE = new SimplexNoise(RandomSource.create(SEED ^ 0x9E3779B97F4A7C15L));

    private record ZoneKey(ResourceKey<Level> dim, int zx, int zz) {
    }

    private record ZoneWind(long tick, Vec3 regional) {
    }

    private static final Map<ZoneKey, ZoneWind> ZONE_CACHE = new ConcurrentHashMap<>();

    public static WindSample getWind(Level level, double x, double y, double z) {
        Vec3 regional = regionalWind(level, x, z);
        if (regional.lengthSqr() < 1.0e-9) {
            return WindSample.NONE;
        }

        double altMult = altitudeMult(level, y);
        double occlusion = occlusion(level, x, y, z, regional);

        Vec3 finalVec = regional.scale(altMult * occlusion);
        return new WindSample(finalVec, weatherMult(level), 1.0, altMult, occlusion, 1.0);
    }

    public static WindSample getWind(Level level, Vec3 pos) {
        return getWind(level, pos.x, pos.y, pos.z);
    }

    private static Vec3 regionalWind(Level level, double x, double z) {
        int zx = Math.floorDiv((int) Math.floor(x), WindConfig.ZONE_SIZE);
        int zz = Math.floorDiv((int) Math.floor(z), WindConfig.ZONE_SIZE);
        long now = level.getGameTime();

        ZoneKey key = new ZoneKey(level.dimension(), zx, zz);
        ZoneWind cached = ZONE_CACHE.get(key);
        if (cached != null && cached.tick() == now) {
            return cached.regional();
        }

        Vec3 regional = computeRegional(level, zx, zz, now);
        ZONE_CACHE.put(key, new ZoneWind(now, regional));
        return regional;
    }

    private static Vec3 computeRegional(Level level, int zx, int zz, long gameTime) {
        int cx = zx * WindConfig.ZONE_SIZE + WindConfig.ZONE_SIZE / 2;
        int cz = zz * WindConfig.ZONE_SIZE + WindConfig.ZONE_SIZE / 2;

        float thunder = level.getThunderLevel(1.0f);

        double prevailing = DIR_NOISE.getValue(gameTime * WindConfig.PREVAILING_TURN_SCALE, 0.0) * Math.PI;

        double perturbMax = WindConfig.PERTURB_MAX * (1.0 + thunder * WindConfig.THUNDER_CHAOS);
        double perturb = DIR_NOISE.getValue(
                zx * WindConfig.NOISE_SCALE * WindConfig.ZONE_SIZE,
                gameTime * WindConfig.TIME_SCALE,
                zz * WindConfig.NOISE_SCALE * WindConfig.ZONE_SIZE) * perturbMax;

        double dayFraction = (level.getDayTime() % 24000L) / 24000.0;
        double thermal = Math.sin(dayFraction * 2.0 * Math.PI) * WindConfig.THERMAL_MAX;

        double angle = prevailing + perturb + thermal;
        Vec3 dir = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));

        double gust = GUST_NOISE.getValue(
                zx * WindConfig.NOISE_SCALE * WindConfig.ZONE_SIZE,
                gameTime * WindConfig.TIME_SCALE,
                zz * WindConfig.NOISE_SCALE * WindConfig.ZONE_SIZE);
        double gustMult = 1.0 + gust * HighSeasConfig.gustAmplitude;

        double magnitude = HighSeasConfig.baseWind
                * weatherMult(level)
                * biomeMult(level, cx, cz)
                * gustMult;

        return dir.scale(magnitude);
    }

    private static double weatherMult(Level level) {
        double rain = level.getRainLevel(1.0f);
        double thunder = level.getThunderLevel(1.0f);
        return 1.0 + rain * HighSeasConfig.rainBoost + thunder * HighSeasConfig.thunderBoost;
    }

    private static double biomeMult(Level level, int x, int z) {
        Holder<Biome> biome = level.getBiome(new BlockPos(x, level.getSeaLevel(), z));
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER)) {
            return WindConfig.FRICTION_OCEAN;
        }
        if (biome.is(BiomeTags.IS_BEACH)) {
            return WindConfig.FRICTION_BEACH;
        }
        if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)) {
            return WindConfig.FRICTION_MOUNTAIN;
        }
        if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_JUNGLE) || biome.is(BiomeTags.IS_TAIGA)) {
            return WindConfig.FRICTION_FOREST;
        }
        return WindConfig.FRICTION_DEFAULT;
    }

    private static double altitudeMult(Level level, double y) {
        double above = y - level.getSeaLevel();
        double t = Mth.clamp(above / WindConfig.ALT_FULL_HEIGHT, 0.0, 1.0);
        return Mth.lerp(t, WindConfig.ALT_MULT_SEA, WindConfig.ALT_MULT_HIGH);
    }

    private static double occlusion(Level level, double x, double y, double z, Vec3 regional) {
        Vec3 dir = regional.normalize();
        double worst = 0.0;
        for (int step = 1; step <= 2; step++) {
            int sx = (int) Math.floor(x - dir.x * WindConfig.OCCLUSION_UPWIND * step);
            int sz = (int) Math.floor(z - dir.z * WindConfig.OCCLUSION_UPWIND * step);
            int terrain = level.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz);
            double over = terrain - y;
            if (over > 0) {
                worst = Math.max(worst, Mth.clamp(over / 24.0, 0.0, 1.0));
            }
        }
        return 1.0 - worst * HighSeasConfig.windOcclusion;
    }

    public static void clearAll() {
        ZONE_CACHE.clear();
    }

    public static void clearForDimension(ResourceKey<Level> dim) {
        ZONE_CACHE.keySet().removeIf(k -> k.dim().equals(dim));
    }
}
