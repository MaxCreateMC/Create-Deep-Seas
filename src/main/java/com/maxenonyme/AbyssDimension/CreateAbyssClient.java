package com.maxenonyme.AbyssDimension;

import com.maxenonyme.AbyssDimension.client.PDAManager;
import com.maxenonyme.AbyssDimension.entities.EntityRegistry;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class CreateAbyssClient {
    private CreateAbyssClient() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(CreateAbyssClient::onClientSetup);
        modEventBus.addListener(CreateAbyssClient::onRegisterRenderers);
        modEventBus.addListener(CreateAbyssClient::onRegisterLayers);
        NeoForge.EVENT_BUS.addListener(com.maxenonyme.AbyssDimension.experimental.worldgen.client.PlateDebugOverlay::onDebugText);
        modEventBus.register(PDAManager.ModEvents.class);

        NeoForge.EVENT_BUS.register(PDAManager.GameEvents.class);
        NeoForge.EVENT_BUS.register(com.maxenonyme.AbyssDimension.client.CookiecutterClientHandler.class);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                EntityRegistry.AMPHISTIUM.get(),
                com.maxenonyme.AbyssDimension.client.renderer.AmphistiumRenderer::new);
        event.registerEntityRenderer(
                EntityRegistry.COOKIECUTTER_SHARK.get(),
                com.maxenonyme.AbyssDimension.client.renderer.CookiecutterSharkRenderer::new);
        event.registerEntityRenderer(
                EntityRegistry.MAGMATIC_SNAIL.get(),
                com.maxenonyme.AbyssDimension.client.renderer.MagmaticSnailRenderer::new);
        event.registerEntityRenderer(
                EntityRegistry.ISOPOD.get(),
                com.maxenonyme.AbyssDimension.client.renderer.IsopodRenderer::new);
        event.registerEntityRenderer(
                EntityRegistry.ABYSSAL_CUTTLEFISH.get(),
                com.maxenonyme.AbyssDimension.client.renderer.AbyssalCuttlefishRenderer::new);
        event.registerEntityRenderer(
                EntityRegistry.PELICAN_EEL.get(),
                com.maxenonyme.AbyssDimension.client.renderer.PelicanEelRenderer::new);
        event.registerEntityRenderer(
                EntityRegistry.SEGMENT_HITBOX.get(),
                net.minecraft.client.renderer.entity.NoopRenderer::new);
    }

    private static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                com.maxenonyme.AbyssDimension.client.model.Amphistium.LAYER_LOCATION,
                com.maxenonyme.AbyssDimension.client.model.Amphistium::createBodyLayer);
        event.registerLayerDefinition(
                com.maxenonyme.AbyssDimension.client.model.CookiecutterShark.LAYER_LOCATION,
                com.maxenonyme.AbyssDimension.client.model.CookiecutterShark::createBodyLayer);
        event.registerLayerDefinition(
                com.maxenonyme.AbyssDimension.client.model.MagmaticSnail.LAYER_LOCATION,
                com.maxenonyme.AbyssDimension.client.model.MagmaticSnail::createBodyLayer);
        event.registerLayerDefinition(
                com.maxenonyme.AbyssDimension.client.model.Isopod.LAYER_LOCATION,
                com.maxenonyme.AbyssDimension.client.model.Isopod::createBodyLayer);
        event.registerLayerDefinition(
                com.maxenonyme.AbyssDimension.client.model.AbyssalCuttlefish.LAYER_LOCATION,
                com.maxenonyme.AbyssDimension.client.model.AbyssalCuttlefish::createBodyLayer);
        event.registerLayerDefinition(
                com.maxenonyme.AbyssDimension.client.model.PelicanEel.LAYER_LOCATION,
                com.maxenonyme.AbyssDimension.client.model.PelicanEel::createBodyLayer);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(LianaRegistry.LIANA_BLOCK.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(LianaRegistry.CREEPVINE_SEED.get(), RenderType.cutout());
        });
    }
}
