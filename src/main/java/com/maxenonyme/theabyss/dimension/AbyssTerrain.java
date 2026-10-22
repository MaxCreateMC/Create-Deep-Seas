package com.maxenonyme.theabyss.dimension;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public record AbyssTerrain(DensityFunction surface, NoiseHolder detail) implements DensityFunction {
    public static final MapCodec<AbyssTerrain> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("surface").forGetter(AbyssTerrain::surface),
            NoiseHolder.CODEC.fieldOf("detail").forGetter(AbyssTerrain::detail)
    ).apply(instance, AbyssTerrain::new));
    private static final KeyDispatchDataCodec<AbyssTerrain> DISPATCH = KeyDispatchDataCodec.of(CODEC);

    private static final double SLOPE = 48;
    private static final double DETAIL = 0.35;

    @Override
    public double compute(FunctionContext context) {
        int y = context.blockY();
        double ground = (surface.compute(context) - y) / SLOPE;
        double density = ground + detail.getValue(context.blockX(), y * 0.6, context.blockZ()) * DETAIL;

        density = Math.min(density, (AbyssLayout.TERRAIN_TOP - y) / 8.0);
        if (y >= AbyssLayout.CEILING - AbyssLayout.SHELL)
            return 1;

        return Math.max(density, (AbyssLayout.BOTTOM + 8 - y) / 4.0);
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new AbyssTerrain(surface.mapAll(visitor), visitor.visitNoise(detail)));
    }

    @Override
    public double minValue() {
        return -40;
    }

    @Override
    public double maxValue() {
        return 40;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return DISPATCH;
    }
}
