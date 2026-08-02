package com.maxenonyme.createsubmarine.worldgen;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PlateTectonicsConfig(int cellSize, long seed, List<TectonicPlate> plates) {

    public static final Codec<PlateTectonicsConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("cell_size").forGetter(PlateTectonicsConfig::cellSize),
        Codec.LONG.fieldOf("seed").forGetter(PlateTectonicsConfig::seed),
        TectonicPlate.CODEC.listOf().fieldOf("plates").forGetter(PlateTectonicsConfig::plates)
    ).apply(instance, PlateTectonicsConfig::new));
}
