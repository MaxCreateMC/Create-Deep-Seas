package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.mixin.ChunkRenderTypeSetAccessor;
import com.maxenonyme.highseas.block.BoatSailBlock;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.platform.VeilEventPlatform;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
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

    public static void init(IEventBus modEventBus) {
        VeilEventPlatform.INSTANCE.onVeilRegisterBlockLayers(registry -> registry.registerBlockLayer(SailRenderTypes.sail()));
        VeilEventPlatform.INSTANCE.onVeilRegisterFixedBuffers(registry ->
                registry.registerFixedBuffer(VeilRenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES, SailRenderTypes.sail()));
        modEventBus.addListener(SailRenderClient::onClientSetup);
        modEventBus.addListener(SailRenderClient::onModelBaking);
    }

    private static void onModelBaking(ModelEvent.ModifyBakingResult event) {
        ResourceLocation rudderLoc = ResourceLocation.fromNamespaceAndPath(CreateHighSeas.MOD_ID, "rudder");
        
        for (Map.Entry<ModelResourceLocation, BakedModel> entry : event.getModels().entrySet()) {
            if (entry.getKey().id().equals(rudderLoc)) {
                event.getModels().put(entry.getKey(), new RudderCopycatModel(entry.getValue()));
            }
        }
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ChunkRenderTypeSet set = ChunkRenderTypeSet.of(SailRenderTypes.sail());
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block instanceof BoatSailBlock) {
                    ItemBlockRenderTypes.setRenderLayer(block, set);
                }
            }
            ItemBlockRenderTypes.setRenderLayer(CreateHighSeas.RUDDER.get(), ChunkRenderTypeSet.all());
            fixChunkRenderTypeSet();
        });
    }

    private static void fixChunkRenderTypeSet() {
        List<RenderType> list = RenderType.chunkBufferLayers();
        ChunkRenderTypeSetAccessor.setChunkRenderTypesList(list);
        ChunkRenderTypeSetAccessor.setChunkRenderTypes(list.toArray(new RenderType[0]));
        ((ChunkRenderTypeSetAccessor) (Object) ChunkRenderTypeSet.all()).getBits().set(0, list.size());
    }
}
