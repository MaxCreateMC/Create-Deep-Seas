package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.CreateHighSeas;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class WindVaneClient {
    private WindVaneClient() {
    }

    public static void init(IEventBus modEventBus) {
        AllHighSeasPartialModels.init();
        modEventBus.addListener(WindVaneClient::onClientSetup);
        modEventBus.addListener(WindVaneClient::onRegisterRenderers);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(CreateHighSeas.WIND_VANE.get(), RenderType.cutout());
            SimpleBlockEntityVisualizer
                    .builder(CreateHighSeas.WIND_VANE_BE.get())
                    .factory(WindVaneVisual::new)
                    .skipVanillaRender(be -> false)
                    .apply();
        });
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CreateHighSeas.WIND_VANE_BE.get(), WindVaneRenderer::new);
        event.registerBlockEntityRenderer(CreateHighSeas.ANCHOR_BE.get(), RopeHolderRenderer::new);
        event.registerBlockEntityRenderer(CreateHighSeas.BUOY_BE.get(), RopeHolderRenderer::new);
    }
}
