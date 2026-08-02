package com.maxenonyme.createsubmarine;

import com.maxenonyme.createsubmarine.submarine.block.entity.renderer.ElectrolyzerBlockEntityRenderer;
import com.maxenonyme.createsubmarine.submarine.client.RotatedHitboxDebugRenderer;
import com.maxenonyme.createsubmarine.submarine.client.ShapeVizRenderer;
import com.maxenonyme.createsubmarine.submarine.client.SubLevelCrackRenderer;
import com.maxenonyme.createsubmarine.submarine.client.PlateDebugOverlay;
import com.maxenonyme.createsubmarine.submarine.client.SubmarineFogHandler;
import com.maxenonyme.createsubmarine.submarine.client.WatermarkOverlay;
import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.gui.ElectrolyzerScreen;
import com.maxenonyme.createsubmarine.submarine.sonar.SonarPingerRenderer;
import com.maxenonyme.createsubmarine.submarine.ponder.SubmarinePonderPlugin;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import com.maxenonyme.AbyssDimension.client.PDAManager;

public final class CreateSubmarineClient {
    private CreateSubmarineClient() {
    }

    public static void init(IEventBus modEventBus, ModContainer modContainer) {
        com.maxenonyme.createsubmarine.submarine.config.SubmarineClientState.load();
        com.maxenonyme.createsubmarine.submarine.system.UpdateChecker.check();
        AllPartialModels.init();
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (container, parent) -> new com.maxenonyme.createsubmarine.submarine.client.HullStrengthConfigScreen(
                        container, parent));

        modEventBus.addListener(CreateSubmarineClient::onClientSetup);
        modEventBus.addListener(CreateSubmarineClient::onRegisterRenderers);
        modEventBus.addListener(CreateSubmarineClient::onRegisterScreens);
        modEventBus.addListener(CreateSubmarineClient::onRegisterLayers);
        modEventBus.addListener(CreateSubmarineClient::onRegisterClientExtensions);
        com.maxenonyme.createsubmarine.submarine.util.CrackUtil.setChecker(SubLevelCrackRenderer::hasCrack);

        modEventBus.addListener(WatermarkOverlay::register);

        NeoForge.EVENT_BUS.addListener(
                com.maxenonyme.createsubmarine.submarine.client.DeepSeasWelcomeScreen::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(
                com.maxenonyme.createsubmarine.submarine.client.LithostitchedMissingScreen::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(
                com.maxenonyme.createsubmarine.submarine.client.DeepSeasUpdateScreen::onScreenOpening);

        NeoForge.EVENT_BUS.register(PlateDebugOverlay.class);
        NeoForge.EVENT_BUS.register(SubmarineFogHandler.class);
        NeoForge.EVENT_BUS.register(SubLevelCrackRenderer.class);
        NeoForge.EVENT_BUS.register(ShapeVizRenderer.class);
        NeoForge.EVENT_BUS.register(RotatedHitboxDebugRenderer.class);
        NeoForge.EVENT_BUS.register(com.maxenonyme.createsubmarine.submarine.client.LeakDetectorPathRenderer.class);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Pre.class,
            e -> ShapeVizRenderer.onClientTick());
        NeoForge.EVENT_BUS.register(PDAManager.GameEvents.class);
        NeoForge.EVENT_BUS.register(com.maxenonyme.AbyssDimension.client.CameraShake.GameEvents.class);
        modEventBus.register(PDAManager.ModEvents.class);
        NeoForge.EVENT_BUS
                .addListener(com.maxenonyme.createsubmarine.submarine.client.ClientSteelCableItemHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            SubLevelCrackRenderer.clearAll();
            ShapeVizRenderer.clearAll();
            SubLevelRegistry.clearAll();
            CompartmentTracker.clearAll();
            com.maxenonyme.createsubmarine.submarine.system.SubmarineDriverRegistry.clearAll();
            com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig.load();
            com.maxenonyme.createsubmarine.submarine.client.WardingEffectHandler.clearOnLogout();
            com.maxenonyme.createsubmarine.submarine.client.LeakDetectorPathRenderer.clearAll();
        });
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                CreateSubmarine.ELECTROLYZER_BE.get(),
                ElectrolyzerBlockEntityRenderer::new);
        event.registerEntityRenderer(
                CreateSubmarine.SONAR_PINGER_ENTITY.get(),
                ctx -> new SonarPingerRenderer(ctx));
        event.registerEntityRenderer(
                CreateSubmarine.HARPOON_ENTITY.get(),
                com.maxenonyme.createsubmarine.submarine.client.renderer.HarpoonEntityRenderer::new);
        event.registerBlockEntityRenderer(
                CreateSubmarine.PULLEY_BE.get(),
                com.maxenonyme.createsubmarine.submarine.block.entity.renderer.PulleyBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(
                CreateSubmarine.ARRESTING_HOOK_BE.get(),
                com.maxenonyme.createsubmarine.submarine.block.entity.renderer.ArrestingHookBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(
                CreateSubmarine.SUBMARINE_PROPELLER_BE.get(),
                com.maxenonyme.createsubmarine.submarine.block.propeller.submarine_propeller.SubmarinePropellerRenderer::new);
        event.registerBlockEntityRenderer(
                CreateSubmarine.BAROMETER_BE.get(),
                com.maxenonyme.createsubmarine.submarine.block.entity.renderer.BarometerBlockEntityRenderer::new);
    }

    private static void onRegisterLayers(
            net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                com.maxenonyme.createsubmarine.submarine.client.model.HarpoonModel.LAYER_LOCATION,
                com.maxenonyme.createsubmarine.submarine.client.model.HarpoonModel::createBodyLayer);
    }

    private static void onRegisterClientExtensions(
            net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return new com.maxenonyme.createsubmarine.submarine.block.entity.renderer.BarometerItemRenderer();
            }
        }, CreateSubmarine.BAROMETER_ITEM.get());
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(CreateSubmarine.ELECTROLYZER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateSubmarine.OXYGENE_DIFFUSER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateSubmarine.WATER_THRUSTER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateSubmarine.IRON_PRESSURIZER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateSubmarine.GLASS_PRESSURIZER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(com.maxenonyme.AbyssDimension.LianaRegistry.LIANA_BLOCK.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(com.maxenonyme.AbyssDimension.LianaRegistry.CREEPVINE_SEED.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateSubmarine.SUBMARINE_PROPELLER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateSubmarine.ARRESTING_HOOK.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateSubmarine.BAROMETER.get(), RenderType.cutout());
        });

        PonderIndex.addPlugin(new SubmarinePonderPlugin());
        WaterOcclusionRenderer.setIsEnabled(true);
        SimpleBlockEntityVisualizer
                .builder(CreateSubmarine.BALLAST_VENT_BE.get())
                .factory(SingleAxisRotatingVisual::shaft)
                .skipVanillaRender(be -> true)
                .apply();
        SimpleBlockEntityVisualizer
                .builder(CreateSubmarine.SUBMARINE_PROPELLER_BE.get())
                .factory(
                        com.maxenonyme.createsubmarine.submarine.block.propeller.submarine_propeller.SubmarinePropellerVisual::new)
                .skipVanillaRender(be -> false)
                .apply();
    }

    private static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(
                CreateSubmarine.ELECTROLYZER_MENU.get(),
                ElectrolyzerScreen::new);
    }

}
