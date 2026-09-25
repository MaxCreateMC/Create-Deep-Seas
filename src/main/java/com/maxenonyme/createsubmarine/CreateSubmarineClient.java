package com.maxenonyme.createsubmarine;

import com.maxenonyme.createsubmarine.submarine.block.entity.renderer.ElectrolyzerBlockEntityRenderer;
import com.maxenonyme.createsubmarine.submarine.client.SubLevelCrackRenderer;
import com.maxenonyme.createsubmarine.submarine.client.SubmarineFogHandler;
import com.maxenonyme.createsubmarine.submarine.client.WatermarkOverlay;
import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.gui.ElectrolyzerScreen;
import com.maxenonyme.createsubmarine.submarine.ponder.SubmarinePonderPlugin;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
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
import com.maxenonyme.createsubmarine.submarine.block.entity.renderer.ArrestingHookBlockEntityRenderer;
import com.maxenonyme.createsubmarine.submarine.block.entity.renderer.BarometerBlockEntityRenderer;
import com.maxenonyme.createsubmarine.submarine.block.entity.renderer.BarometerItemRenderer;
import com.maxenonyme.createsubmarine.submarine.block.entity.renderer.CommandSubRenderer;
import com.maxenonyme.createsubmarine.submarine.block.entity.renderer.PulleyBlockEntityRenderer;
import com.maxenonyme.createsubmarine.submarine.block.entity.renderer.PumpControllerRenderer;
import com.maxenonyme.createsubmarine.submarine.block.entity.renderer.PumpControllerVisual;
import com.maxenonyme.createsubmarine.submarine.block.propeller.submarine_propeller.SubmarinePropellerRenderer;
import com.maxenonyme.createsubmarine.submarine.block.propeller.submarine_propeller.SubmarinePropellerVisual;
import com.maxenonyme.createsubmarine.submarine.client.ClientSteelCableItemHandler;
import com.maxenonyme.createsubmarine.submarine.client.CommandSubClientHandler;
import com.maxenonyme.createsubmarine.submarine.client.CommandSubDiagram;
import com.maxenonyme.createsubmarine.submarine.client.DeepSeasUpdateScreen;
import com.maxenonyme.createsubmarine.submarine.client.DeepSeasWelcomeScreen;
import com.maxenonyme.createsubmarine.submarine.client.HullStrengthConfigScreen;
import com.maxenonyme.createsubmarine.submarine.client.LithostitchedMissingScreen;
import com.maxenonyme.createsubmarine.submarine.item.SubmarineStaffItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public final class CreateSubmarineClient {
    private CreateSubmarineClient() {
    }

    public static void init(IEventBus modEventBus, ModContainer modContainer) {
        com.maxenonyme.createsubmarine.submarine.config.SubmarineClientState.load();
        com.maxenonyme.createsubmarine.submarine.system.UpdateChecker.check();
        AllPartialModels.init();
        com.maxenonyme.createsubmarine.submarine.client.BallastTankCT.register();
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (container, parent) -> new HullStrengthConfigScreen(
                        container, parent));

        modEventBus.addListener(CreateSubmarineClient::onClientSetup);
        modEventBus.addListener(CreateSubmarineClient::onRegisterRenderers);
        modEventBus.addListener(CreateSubmarineClient::onRegisterScreens);
        modEventBus.addListener(CreateSubmarineClient::onRegisterClientExtensions);
        com.maxenonyme.createsubmarine.submarine.util.CrackUtil.setChecker(SubLevelCrackRenderer::hasCrack);
        com.maxenonyme.createsubmarine.submarine.network.SubCrackPayload.handler = (payload, context) -> {
            context.enqueueWork(() -> {
                SubLevelCrackRenderer.updateCrack(
                        payload.subId(), payload.plotPos(), payload.crackLevel(), payload.blockId());
            });
        };

        modEventBus.addListener(WatermarkOverlay::register);

        NeoForge.EVENT_BUS.addListener(
                DeepSeasWelcomeScreen::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(
                LithostitchedMissingScreen::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(
                DeepSeasUpdateScreen::onScreenOpening);

        NeoForge.EVENT_BUS.register(SubmarineFogHandler.class);
        NeoForge.EVENT_BUS.register(SubLevelCrackRenderer.class);
        NeoForge.EVENT_BUS.register(com.maxenonyme.AbyssDimension.client.CameraShake.GameEvents.class);
        NeoForge.EVENT_BUS
                .addListener(ClientSteelCableItemHandler::onClientTick);
        if (CreateSubmarine.SUBMARINE_STAFF_ENABLED)
            NeoForge.EVENT_BUS
                    .addListener(com.maxenonyme.createsubmarine.submarine.client.SubmarineStaffClientHandler::onClientTick);
        NeoForge.EVENT_BUS
                .addListener(CommandSubClientHandler::onClientTick);
        NeoForge.EVENT_BUS
                .addListener(CommandSubClientHandler::onUseKey);
        NeoForge.EVENT_BUS
                .addListener(CommandSubClientHandler::onKey);
        NeoForge.EVENT_BUS
                .addListener(CommandSubDiagram::onFrameEnd);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            SubLevelCrackRenderer.clearAll();
            SubLevelRegistry.clearAll();
            CompartmentTracker.clearAll();
            com.maxenonyme.createsubmarine.submarine.system.SubmarineDriverRegistry.clearAll();
            com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig.load();
            com.maxenonyme.createsubmarine.submarine.client.SubmarineStaffClientHandler.reset();
        });
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                CreateSubmarine.ELECTROLYZER_BE.get(),
                ElectrolyzerBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(
                CreateSubmarine.PULLEY_BE.get(),
                PulleyBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(
                CreateSubmarine.ARRESTING_HOOK_BE.get(),
                ArrestingHookBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(
                CreateSubmarine.SUBMARINE_PROPELLER_BE.get(),
                SubmarinePropellerRenderer::new);
        event.registerBlockEntityRenderer(
                CreateSubmarine.BAROMETER_BE.get(),
                BarometerBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(
                CreateSubmarine.COMMAND_SUB_BE.get(),
                CommandSubRenderer::new);
        event.registerBlockEntityRenderer(
                CreateSubmarine.PUMP_CONTROLLER_BE.get(),
                PumpControllerRenderer::new);
    }

    private static void onRegisterClientExtensions(
            RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return new BarometerItemRenderer();
            }
        }, CreateSubmarine.BAROMETER_ITEM.get());
        if (!CreateSubmarine.SUBMARINE_STAFF_ENABLED)
            return;
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return new SubmarineStaffItemRenderer();
            }
        }, CreateSubmarine.SUBMARINE_STAFF.get());
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(CreateSubmarine.ELECTROLYZER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateSubmarine.OXYGENE_DIFFUSER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateSubmarine.WATER_THRUSTER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateSubmarine.IRON_PRESSURIZER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateSubmarine.COPPER_PRESSURIZER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateSubmarine.SUBMARINE_PROPELLER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateSubmarine.ARRESTING_HOOK.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateSubmarine.BAROMETER.get(), RenderType.cutout());
        });

        PonderIndex.addPlugin(new SubmarinePonderPlugin());
        WaterOcclusionRenderer.setIsEnabled(true);
        SimpleBlockEntityVisualizer
                .builder(CreateSubmarine.PUMP_CONTROLLER_BE.get())
                .factory(PumpControllerVisual::new)
                .skipVanillaRender(be -> true)
                .apply();
        SimpleBlockEntityVisualizer
                .builder(CreateSubmarine.SUBMARINE_PROPELLER_BE.get())
                .factory(
                        SubmarinePropellerVisual::new)
                .skipVanillaRender(be -> true)
                .apply();
    }

    private static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(
                CreateSubmarine.ELECTROLYZER_MENU.get(),
                ElectrolyzerScreen::new);
    }

}
