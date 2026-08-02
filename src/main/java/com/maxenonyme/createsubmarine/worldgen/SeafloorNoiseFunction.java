package com.maxenonyme.createsubmarine.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public class SeafloorNoiseFunction implements DensityFunction {
    public static final MapCodec<SeafloorNoiseFunction> CODEC = MapCodec.unit(new SeafloorNoiseFunction());
    public static final KeyDispatchDataCodec<SeafloorNoiseFunction> CODEC_HOLDER = KeyDispatchDataCodec.of(CODEC);

    public SeafloorNoiseFunction() {}

    @Override
    public double compute(FunctionContext ctx) {
        // Return negative absolute value so continentalness is in [-1, 0] (ocean-like).
        // Matches biome source params: shallows=0.0, shelf=-0.15, plains=-0.5
        return -Math.abs(SeafloorGenerator.getNoiseAt(ctx.blockX(), ctx.blockZ()));
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(this);
    }

    @Override
    public double minValue() {
        return -1.0;
    }

    @Override
    public double maxValue() {
        return 0.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC_HOLDER;
    }
}
