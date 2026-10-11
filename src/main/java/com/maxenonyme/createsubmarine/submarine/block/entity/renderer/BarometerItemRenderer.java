package com.maxenonyme.createsubmarine.submarine.block.entity.renderer;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.entity.BarometerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.data.ModelData;

public class BarometerItemRenderer extends BlockEntityWithoutLevelRenderer {
    private BarometerBlockEntity dummyBE;

    public BarometerItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (dummyBE == null) {
            dummyBE = new BarometerBlockEntity(BlockPos.ZERO, CreateSubmarine.BAROMETER.get().defaultBlockState());
            dummyBE.setLevel(Minecraft.getInstance().level);
        }

        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
        blockRenderer.renderSingleBlock(CreateSubmarine.BAROMETER.get().defaultBlockState(), ms, buffer, light, overlay, ModelData.EMPTY, null);

        Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(dummyBE, ms, buffer, light, overlay);
    }
}
