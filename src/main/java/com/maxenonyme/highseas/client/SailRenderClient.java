package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.mixin.ChunkRenderTypeSetAccessor;
import com.maxenonyme.highseas.block.BoatSailBlock;
import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.platform.VeilEventPlatform;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import com.maxenonyme.highseas.ponder.HighSeasPonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;

import java.util.List;
import com.maxenonyme.highseas.CreateHighSeas;
import java.util.Map;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

public final class SailRenderClient {
    private SailRenderClient() {
    }

    private static volatile Boolean billows;

    public static boolean billows() {
        Boolean known = billows;
        if (known == null) {
            if (!RenderSystem.isOnRenderThread())
                return false;
            known = VeilRenderSystem.tessellationSupported();
            if (!known)
                CreateHighSeas.LOGGER.warn("This GPU does not support OpenGL tessellation, sails will be drawn flat");
            billows = known;
        }
        return known;
    }

    public static void init(IEventBus modEventBus) {
        VeilEventPlatform.INSTANCE.onVeilRegisterBlockLayers(registry -> {
            if (billows())
                registry.registerBlockLayer(SailRenderTypes.sail());
        });
        VeilEventPlatform.INSTANCE.onVeilRegisterFixedBuffers(registry -> {
            if (billows())
                registry.registerFixedBuffer(VeilRenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES, SailRenderTypes.sail());
        });
        modEventBus.addListener(SailRenderClient::onClientSetup);
        modEventBus.addListener(SailRenderClient::onModelBaking);
    }

    private static void onModelBaking(ModelEvent.ModifyBakingResult event) {
        ResourceLocation rudderLoc = ResourceLocation.fromNamespaceAndPath(CreateHighSeas.MOD_ID, "rudder");
        
        for (Map.Entry<ModelResourceLocation, BakedModel> entry : event.getModels().entrySet()) {
            ResourceLocation id = entry.getKey().id();
            if (id.equals(rudderLoc)) {
                event.getModels().put(entry.getKey(), new RudderCopycatModel(entry.getValue()));
            } else if (id.getNamespace().equals(CreateHighSeas.MOD_ID) && id.getPath().endsWith("_sail")
                    && !ModelResourceLocation.INVENTORY_VARIANT.equals(entry.getKey().variant())) {
                event.getModels().put(entry.getKey(), new SailTriangleModel(entry.getValue()));
            }
        }
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ChunkRenderTypeSet set = billows() ? ChunkRenderTypeSet.of(SailRenderTypes.sail())
                    : ChunkRenderTypeSet.of(RenderType.cutout());
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block instanceof BoatSailBlock) {
                    ItemBlockRenderTypes.setRenderLayer(block, set);
                }
            }
            ItemBlockRenderTypes.setRenderLayer(CreateHighSeas.RUDDER.get(), ChunkRenderTypeSet.all());
            fixChunkRenderTypeSet();
        });
        PonderIndex.addPlugin(new HighSeasPonderPlugin());
    }

    private static void fixChunkRenderTypeSet() {
        List<RenderType> list = RenderType.chunkBufferLayers();
        ChunkRenderTypeSetAccessor.setChunkRenderTypesList(list);
        ChunkRenderTypeSetAccessor.setChunkRenderTypes(list.toArray(new RenderType[0]));
        ((ChunkRenderTypeSetAccessor) (Object) ChunkRenderTypeSet.all()).getBits().set(0, list.size());
    }
}
