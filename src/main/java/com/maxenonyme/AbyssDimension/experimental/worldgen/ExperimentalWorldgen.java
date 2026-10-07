package com.maxenonyme.AbyssDimension.experimental.worldgen;

import com.maxenonyme.AbyssDimension.CreateAbyss;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ExperimentalWorldgen {
    private ExperimentalWorldgen() {
    }

    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTIONS = DeferredRegister
            .create(Registries.DENSITY_FUNCTION_TYPE, CreateAbyss.MOD_ID);

    static {
        DENSITY_FUNCTIONS.register("seafloor_height", () -> SeafloorHeightFunction.CODEC);
        DENSITY_FUNCTIONS.register("seafloor_noise", () -> SeafloorNoiseFunction.CODEC);
    }

    public static boolean enabled() {
        return CreateAbyss.enabled() && SubmarineConfig.COMMON_SPEC.isLoaded() && SubmarineConfig.EXPERIMENTAL_ABYSS_WORLDGEN.get();
    }

    public static void init(IEventBus modEventBus) {
        DENSITY_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(ExperimentalWorldgen::onAddPackFinders);
        NeoForge.EVENT_BUS.addListener(ExperimentalWorldgen::onRegisterCommands);
    }

    private static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA)
            return;
        if (enabled())
            event.addPackFinders(ResourceLocation.fromNamespaceAndPath(CreateAbyss.MOD_ID, "experimental/abyss_tectonic"),
                    PackType.SERVER_DATA, Component.literal("Abyss: tectonic seafloor (experimental)"), PackSource.BUILT_IN,
                    true, Pack.Position.TOP);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        if (enabled())
            TectonicCommand.register(event);
    }
}
