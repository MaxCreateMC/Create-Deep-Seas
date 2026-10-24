package com.maxenonyme.theabyss.dimension;

import com.maxenonyme.theabyss.CreateAbyss;
import com.maxenonyme.theabyss.block.AbyssCeilingBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class AbyssWorldgen {

    public static final Supplier<Block> CEILING = CreateAbyss.BLOCKS.register("abyss_ceiling",
            () -> new AbyssCeilingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WATER)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(-1, 3600000)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((state, level, pos, type) -> false)));

    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTIONS = DeferredRegister
            .create(Registries.DENSITY_FUNCTION_TYPE, CreateAbyss.MOD_ID);

    static {
        DENSITY_FUNCTIONS.register("surface", () -> AbyssSurface.CODEC);
        DENSITY_FUNCTIONS.register("terrain", () -> AbyssTerrain.CODEC);
    }

    public static void init(IEventBus modEventBus) {
        DENSITY_FUNCTIONS.register(modEventBus);
    }
}
