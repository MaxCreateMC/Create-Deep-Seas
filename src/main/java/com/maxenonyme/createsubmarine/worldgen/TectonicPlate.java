package com.maxenonyme.createsubmarine.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record TectonicPlate(String name, String uuid, double vx, double vz, double mass, boolean oceanic, double weight) {

    public static final Codec<TectonicPlate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("name").forGetter(TectonicPlate::name),
        Codec.STRING.optionalFieldOf("uuid", "").forGetter(TectonicPlate::uuid),
        Codec.DOUBLE.fieldOf("vx").forGetter(TectonicPlate::vx),
        Codec.DOUBLE.fieldOf("vz").forGetter(TectonicPlate::vz),
        Codec.DOUBLE.fieldOf("mass").forGetter(TectonicPlate::mass),
        Codec.BOOL.fieldOf("oceanic").forGetter(TectonicPlate::oceanic),
        Codec.DOUBLE.fieldOf("weight").forGetter(TectonicPlate::weight)
    ).apply(instance, TectonicPlate::new));
}
