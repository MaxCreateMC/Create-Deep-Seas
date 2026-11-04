package com.maxenonyme.vortexengine;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;

public class VortexEngine {

    public static final boolean DEV_ONLY = false;

    public static boolean installed() {
        return !DEV_ONLY || !FMLEnvironment.production;
    }

    public static void init(IEventBus modEventBus, ModContainer modContainer) {
        if (!installed())
            return;

        modContainer.registerConfig(ModConfig.Type.CLIENT, VortexConfig.SPEC, VortexStartup.FILE);

        if (FMLEnvironment.dist.isClient() && VortexStartup.enabled())
            VortexClient.init(modEventBus);
    }

    public static boolean enabled() {
        return installed() && VortexStartup.enabled() && VortexConfig.SPEC.isLoaded();
    }
}
