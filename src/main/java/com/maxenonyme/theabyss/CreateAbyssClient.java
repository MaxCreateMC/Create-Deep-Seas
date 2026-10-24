package com.maxenonyme.theabyss;

import com.maxenonyme.theabyss.client.CookiecutterClientHandler;
import com.maxenonyme.theabyss.client.MagmaFlowModel;
import com.maxenonyme.theabyss.client.PDAManager;
import com.maxenonyme.theabyss.client.model.AbyssalCuttlefish;
import com.maxenonyme.theabyss.client.model.Amphistium;
import com.maxenonyme.theabyss.client.model.CookiecutterShark;
import com.maxenonyme.theabyss.client.model.Isopod;
import com.maxenonyme.theabyss.client.model.MagmaticSnail;
import com.maxenonyme.theabyss.client.model.PelicanEel;
import com.maxenonyme.theabyss.client.renderer.AbyssalCuttlefishRenderer;
import com.maxenonyme.theabyss.client.renderer.AmphistiumRenderer;
import com.maxenonyme.theabyss.client.renderer.CookiecutterSharkRenderer;
import com.maxenonyme.theabyss.client.renderer.IsopodRenderer;
import com.maxenonyme.theabyss.client.renderer.MagmaticSnailRenderer;
import com.maxenonyme.theabyss.client.renderer.PelicanEelRenderer;
import com.maxenonyme.theabyss.entities.EntityRegistry;
import com.maxenonyme.theabyss.experimental.worldgen.client.PlateDebugOverlay;
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
