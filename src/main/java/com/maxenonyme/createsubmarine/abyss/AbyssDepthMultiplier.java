package com.maxenonyme.createsubmarine.abyss;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public record AbyssDepthMultiplier(Holder<DensityFunction> input, Holder<DensityFunction> continents)
        implements DensityFunction {
    public static final MapCodec<AbyssDepthMultiplier> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.CODEC.fieldOf("input").forGetter(AbyssDepthMultiplier::input),
            DensityFunction.CODEC.fieldOf("continents").forGetter(AbyssDepthMultiplier::continents))
            .apply(instance, AbyssDepthMultiplier::new));

    public static final KeyDispatchDataCodec<AbyssDepthMultiplier> CODEC_HOLDER = KeyDispatchDataCodec.of(CODEC);

    @Override
    public double compute(FunctionContext context) {
        double oOrig = input.value().compute(context);
        //commented out for testing because of a bug re enable later
        //if (!com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.ENABLE_ABYSS_GENERATION.get()) {
        //    return oOrig;
        //}
        double c = continents.value().compute(context);

        double S = 0.0;
        if (c >= -0.8 && c <= -0.35) {
            S = 1.0;
        } else if (c > -1.05 && c < -0.8) {
            S = (c + 1.05) / 0.25;
        } else if (c > -0.35 && c < -0.15) {
            S = (-0.15 - c) / 0.20;
        }

        int x = context.blockX();
        int z = context.blockZ();
        long cellX = Math.floorDiv(x, 4000);
        long cellZ = Math.floorDiv(z, 4000);

        double minDistance = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long cx = cellX + dx;
                long cz = cellZ + dz;
                long hash = (cx * 31273709L) ^ (cz * 43903207L);
                java.util.Random rand = new java.util.Random(hash);
                double centerX = cx * 4000.0 + 500.0 + rand.nextDouble() * 3000.0;
                double centerZ = cz * 4000.0 + 500.0 + rand.nextDouble() * 3000.0;
                double dist = Math.sqrt((x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ));
                if (dist < minDistance) {
                    minDistance = dist;
                }
            }
        }

        double t = 0.0;
        if (minDistance < 480) {
            t = 1.0;
        } else if (minDistance < 520) {
            t = (520 - minDistance) / 40.0;
            t = t * t * (3 - 2 * t);
        }

        t = t * S;

        double oMin = -1.35 - 0.70 * t;
        double oNew = oOrig - 3.5 * S;
        double clampMin = -90.0 * (1 - S) + oMin * S;

        return Math.max(oNew, clampMin);
    }

    @Override
    public void fillArray(double[] doubles, ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(doubles, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new AbyssDepthMultiplier(
                Holder.direct(input.value().mapAll(visitor)),
                Holder.direct(continents.value().mapAll(visitor))));
    }

    @Override
    public double minValue() {
        return -2.0;
    }

    @Override
    public double maxValue() {
        return 90.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC_HOLDER;
    }
}
