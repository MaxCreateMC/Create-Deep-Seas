package com.maxenonyme.createsubmarine.submarine.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

public class PressureGogglesModel extends BakedModelWrapper<BakedModel> {
    public PressureGogglesModel(BakedModel inventory) {
        super(inventory);
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHand) {
        if (context == ItemDisplayContext.HEAD)
            return AllPartialModels.PRESSURE_GOGGLES.get().applyTransform(context, poseStack, leftHand);
        return super.applyTransform(context, poseStack, leftHand);
    }
}
