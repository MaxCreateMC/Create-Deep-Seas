package com.maxenonyme.AbyssDimension;

import com.maxenonyme.AbyssDimension.client.CookiecutterClientHandler;
import com.maxenonyme.AbyssDimension.client.MagmaFlowModel;
import com.maxenonyme.AbyssDimension.client.PDAManager;
import com.maxenonyme.AbyssDimension.client.model.AbyssalCuttlefish;
import com.maxenonyme.AbyssDimension.client.model.Amphistium;
import com.maxenonyme.AbyssDimension.client.model.CookiecutterShark;
import com.maxenonyme.AbyssDimension.client.model.Isopod;
import com.maxenonyme.AbyssDimension.client.model.MagmaticSnail;
import com.maxenonyme.AbyssDimension.client.model.PelicanEel;
import com.maxenonyme.AbyssDimension.client.renderer.AbyssalCuttlefishRenderer;
import com.maxenonyme.AbyssDimension.client.renderer.AmphistiumRenderer;
import com.maxenonyme.AbyssDimension.client.renderer.CookiecutterSharkRenderer;
import com.maxenonyme.AbyssDimension.client.renderer.IsopodRenderer;
import com.maxenonyme.AbyssDimension.client.renderer.MagmaticSnailRenderer;
import com.maxenonyme.AbyssDimension.client.renderer.PelicanEelRenderer;
import com.maxenonyme.AbyssDimension.entities.EntityRegistry;
import com.maxenonyme.AbyssDimension.experimental.worldgen.client.PlateDebugOverlay;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.common.NeoForge;

public class CreateAbyssClient {

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(CreateAbyssClient::onClientSetup);
        modEventBus.addListener(CreateAbyssClient::onRegisterRenderers);
        modEventBus.addListener(CreateAbyssClient::onRegisterLayers);
        modEventBus.addListener(CreateAbyssClient::onModifyBakingResult);
        NeoForge.EVENT_BUS.addListener(PlateDebugOverlay::onDebugText);
        modEventBus.register(PDAManager.ModEvents.class);

        NeoForge.EVENT_BUS.register(PDAManager.GameEvents.class);
        NeoForge.EVENT_BUS.register(CookiecutterClientHandler.class);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                EntityRegistry.AMPHISTIUM.get(),
                AmphistiumRenderer::new);
        event.registerEntityRenderer(
                EntityRegistry.COOKIECUTTER_SHARK.get(),
                CookiecutterSharkRenderer::new);
        event.registerEntityRenderer(
                EntityRegistry.MAGMATIC_SNAIL.get(),
                MagmaticSnailRenderer::new);
        event.registerEntityRenderer(
                EntityRegistry.ISOPOD.get(),
                IsopodRenderer::new);
        event.registerEntityRenderer(
                EntityRegistry.ABYSSAL_CUTTLEFISH.get(),
                AbyssalCuttlefishRenderer::new);
        event.registerEntityRenderer(
                EntityRegistry.PELICAN_EEL.get(),
                PelicanEelRenderer::new);
        event.registerEntityRenderer(
                EntityRegistry.SEGMENT_HITBOX.get(),
                NoopRenderer::new);
    }

    private static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                Amphistium.LAYER_LOCATION,
                Amphistium::createBodyLayer);
        event.registerLayerDefinition(
                CookiecutterShark.LAYER_LOCATION,
                CookiecutterShark::createBodyLayer);
        event.registerLayerDefinition(
                MagmaticSnail.LAYER_LOCATION,
                MagmaticSnail::createBodyLayer);
        event.registerLayerDefinition(
                Isopod.LAYER_LOCATION,
                Isopod::createBodyLayer);
        event.registerLayerDefinition(
                AbyssalCuttlefish.LAYER_LOCATION,
                AbyssalCuttlefish::createBodyLayer);
        event.registerLayerDefinition(
                PelicanEel.LAYER_LOCATION,
                PelicanEel::createBodyLayer);
    }

    private static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        for (var state : GeothermalRegistry.MAGMA.get().getStateDefinition().getPossibleStates()) {
            var key = BlockModelShaper.stateToModelLocation(state);
            var model = event.getModels().get(key);
            if (model != null)
                event.getModels().put(key, new MagmaFlowModel(model));
        }
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(LianaRegistry.LIANA_BLOCK.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(LianaRegistry.CREEPVINE_SEED.get(), RenderType.cutout());
        });
    }
}
