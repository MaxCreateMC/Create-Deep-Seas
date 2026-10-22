package com.maxenonyme.vortexengine;

import com.maxenonyme.vortexengine.atmosphere.Atmosphere;
import com.maxenonyme.vortexengine.light.GlowDefinitions;
import com.maxenonyme.vortexengine.light.LightField;
import com.maxenonyme.vortexengine.light.LightIndex;
import com.maxenonyme.vortexengine.light.LightVolumes;
import com.maxenonyme.vortexengine.light.OpaqueDepth;
import com.maxenonyme.vortexengine.light.PixelLights;
import com.maxenonyme.vortexengine.mist.MistField;
import com.maxenonyme.vortexengine.mist.MistRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

public class VortexClient {

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(LightVolumes::onRegisterShaders);
        modEventBus.addListener(LightField::onRegisterShaders);
        modEventBus.addListener(PixelLights::onRegisterShaders);
        modEventBus.addListener(OpaqueDepth::onRegisterShaders);
        modEventBus.addListener(Atmosphere::onRegisterShaders);
        modEventBus.addListener(MistRenderer::onRegisterShaders);
        modEventBus.addListener(GlowDefinitions::onRegisterReloadListeners);

        NeoForge.EVENT_BUS.addListener(LightVolumes::onRenderStage);
        NeoForge.EVENT_BUS.addListener(LightIndex::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(LightIndex::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(LightIndex::onLevelUnload);

        NeoForge.EVENT_BUS.addListener(Atmosphere::onRenderStage);
        NeoForge.EVENT_BUS.addListener(MistRenderer::onRenderStage);
        NeoForge.EVENT_BUS.addListener(MistField::onClientTick);
        NeoForge.EVENT_BUS.addListener(MistField::onLevelUnload);

        NeoForge.EVENT_BUS.addListener(VortexCommand::onRegister);
        NeoForge.EVENT_BUS.addListener(VortexProfiler::onRenderStage);
    }
}
