package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.CreateHighSeas;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import com.maxenonyme.highseas.gui.BoatEngineScreen;
import com.maxenonyme.highseas.helm.HelmSeatRenderer;
import dev.simulated_team.simulated.index.SimClickInteractions;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class BoatEngineClient {
    private BoatEngineClient() {
    }

    public static void init(IEventBus modEventBus) {
        AllHighSeasPartialModels.init();
        registerThrottleInteraction();
        modEventBus.addListener(BoatEngineClient::onClientSetup);
        modEventBus.addListener(BoatEngineClient::onRegisterRenderers);
        modEventBus.addListener(BoatEngineClient::onRegisterScreens);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> SimpleBlockEntityVisualizer
                .builder(CreateHighSeas.BOAT_ENGINE_BE.get())
                .factory(BoatEngineExhaustVisual::new)
                .skipVanillaRender(be -> false)
                .apply());
    }

    private static void registerThrottleInteraction() {
        SimClickInteractions.CLICK_INTERACTION_ENTRIES.add(new EngineThrottleHandler());
    }

    private static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(CreateHighSeas.BOAT_ENGINE_MENU.get(), BoatEngineScreen::new);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CreateHighSeas.HELM_SEAT.get(),
                HelmSeatRenderer::new);
        event.registerEntityRenderer(CreateHighSeas.BUOY_SEAT.get(),
                BuoySeatRenderer::new);
        event.registerBlockEntityRenderer(CreateHighSeas.BOAT_ENGINE_BE.get(), BoatEngineRenderer::new);
    }
}
