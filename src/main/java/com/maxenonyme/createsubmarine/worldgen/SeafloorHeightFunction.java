package com.maxenonyme.createsubmarine.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public class SeafloorHeightFunction implements DensityFunction {
    public static final MapCodec<SeafloorHeightFunction> CODEC = MapCodec.unit(new SeafloorHeightFunction());
    public static final KeyDispatchDataCodec<SeafloorHeightFunction> CODEC_HOLDER = KeyDispatchDataCodec.of(CODEC);

    private static final int TILE_SHIFT = 8;
    private static final int TILE_SIZE = 1 << TILE_SHIFT;

    public SeafloorHeightFunction() {}

    @Override
    public double compute(FunctionContext ctx) {
        return getHeight(ctx.blockX(), ctx.blockZ()) - ctx.blockY();
    }

    private int getHeight(int x, int z) {
        long tileX = Math.floorDiv(x, TILE_SIZE);
        long tileZ = Math.floorDiv(z, TILE_SIZE);
        short[] tile = SeafloorGenerator.getOrGenerateTile((int) tileX, (int) tileZ);
        int lx = x - (int) (tileX * TILE_SIZE);
        int lz = z - (int) (tileZ * TILE_SIZE);
        return tile[lz * TILE_SIZE + lx];
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
        return -2048.0;
    }

    @Override
    public double maxValue() {
        return 2048.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC_HOLDER;
    }
}
