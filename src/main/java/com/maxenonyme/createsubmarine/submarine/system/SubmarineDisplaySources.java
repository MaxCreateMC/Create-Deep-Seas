package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class SubmarineDisplaySources {
    public static final DeferredRegister<DisplaySource> DISPLAY_SOURCES = DeferredRegister.create(
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("create", "display_source")),
            CreateSubmarine.MOD_ID
    );

    public static final DeferredHolder<DisplaySource, BarometerDisplaySource> BAROMETER = DISPLAY_SOURCES.register(
            "barometer",
            BarometerDisplaySource::new
    );

    public static final DeferredHolder<DisplaySource, CommandSubDisplaySource> COMMAND_SUB = DISPLAY_SOURCES.register(
            "command_sub",
            CommandSubDisplaySource::new
    );

    public static void register(IEventBus modEventBus) {
        DISPLAY_SOURCES.register(modEventBus);
    }
}
