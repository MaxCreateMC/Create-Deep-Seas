package com.maxenonyme.theabyss.dimension;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

public record AbyssSurface(NoiseHolder tiers, NoiseHolder relief) implements DensityFunction {
    public static final MapCodec<AbyssSurface> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            NoiseHolder.CODEC.fieldOf("tiers").forGetter(AbyssSurface::tiers),
            NoiseHolder.CODEC.fieldOf("relief").forGetter(AbyssSurface::relief)
    ).apply(instance, AbyssSurface::new));
    private static final KeyDispatchDataCodec<AbyssSurface> DISPATCH = KeyDispatchDataCodec.of(CODEC);

    private static final double CLIFF = 0.12;

    @Override
    public double compute(FunctionContext context) {
        double shallow = Mth.clamp(tiers.getValue(context.blockX(), 0, context.blockZ()) * 1.6, -1, 1);
        double band = Math.min((1 - shallow) * 1.5, 2.999);
        int tier = (int) band;
        double cliff = Mth.smoothstep(Mth.clamp((band - tier - (1 - CLIFF)) / CLIFF, 0, 1));

        int next = Math.min(tier + 1, AbyssLayout.TIER_HEIGHT.length - 1);
        double height = Mth.lerp(cliff, AbyssLayout.TIER_HEIGHT[tier], AbyssLayout.TIER_HEIGHT[next]);
        double amplitude = Mth.lerp(cliff, AbyssLayout.TIER_RELIEF[tier], AbyssLayout.TIER_RELIEF[next]);

        double bumps = Mth.clamp(relief.getValue(context.blockX(), 0, context.blockZ()) * 1.4, -1, 1);
        return height + amplitude * bumps;
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new AbyssSurface(visitor.visitNoise(tiers), visitor.visitNoise(relief)));
    }

    @Override
    public double minValue() {
        return AbyssLayout.BOTTOM;
    }

    @Override
    public double maxValue() {
        return AbyssLayout.TERRAIN_TOP;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return DISPATCH;
    }
}
