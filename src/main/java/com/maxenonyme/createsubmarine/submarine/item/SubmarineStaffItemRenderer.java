package com.maxenonyme.createsubmarine.submarine.item;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;

public class SubmarineStaffItemRenderer extends BlockEntityWithoutLevelRenderer {

    public SubmarineStaffItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();

        float useTicks = staffUseTicks(displayContext);

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        boolean isCrystal = useTicks >= 20F;

        BakedModel baseModel = isCrystal ? AllPartialModels.SUBMARINE_STAFF_BASE_MODEL_CRYSTAL.get()
                : AllPartialModels.SUBMARINE_STAFF_BASE_MODEL.get();
        BakedModel topcore1 = isCrystal ? AllPartialModels.SUBMARINE_STAFF_TOPCORE_1_CRYSTAL.get()
                : AllPartialModels.SUBMARINE_STAFF_TOPCORE_1.get();
        BakedModel topcore2 = isCrystal ? AllPartialModels.SUBMARINE_STAFF_TOPCORE_2_CRYSTAL.get()
                : AllPartialModels.SUBMARINE_STAFF_TOPCORE_2.get();
        BakedModel topcore3 = isCrystal ? AllPartialModels.SUBMARINE_STAFF_TOPCORE_3_CRYSTAL.get()
                : AllPartialModels.SUBMARINE_STAFF_TOPCORE_3.get();
        BakedModel topcore4 = isCrystal ? AllPartialModels.SUBMARINE_STAFF_TOPCORE_4_CRYSTAL.get()
                : AllPartialModels.SUBMARINE_STAFF_TOPCORE_4.get();

        poseStack.translate(0.5F, 0.5F, 0.5F);

        if (baseModel != null) {
            itemRenderer.render(stack, ItemDisplayContext.NONE, false, poseStack, buffer, packedLight, packedOverlay,
                    baseModel);
        }

        float originX = 8.05999f / 16f;
        float originY = 17.52491f / 16f;
        float originZ = 8.05916f / 16f;

        if (topcore1 != null) {
            poseStack.pushPose();
            poseStack.translate(originX, originY, originZ);
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(12.5f));
            poseStack.translate(-originX, -originY, -originZ);
            itemRenderer.render(stack, ItemDisplayContext.NONE, false, poseStack, buffer, packedLight, packedOverlay,
                    topcore1);
            poseStack.popPose();
        }

        if (topcore2 != null) {
            poseStack.pushPose();
            poseStack.translate(originX, originY, originZ);
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-14.5f));
            poseStack.translate(-originX, -originY, -originZ);
            itemRenderer.render(stack, ItemDisplayContext.NONE, false, poseStack, buffer, packedLight, packedOverlay,
                    topcore2);
            poseStack.popPose();
        }

        if (topcore3 != null) {
            poseStack.pushPose();
            poseStack.translate(originX, originY, originZ);
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(94.46829f));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(89.80437f));
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(82.27781f));
            poseStack.translate(-originX, -originY, -originZ);
            itemRenderer.render(stack, ItemDisplayContext.NONE, false, poseStack, buffer, packedLight, packedOverlay,
                    topcore3);
            poseStack.popPose();
        }

        if (topcore4 != null) {
            poseStack.pushPose();
            poseStack.translate(originX, originY, originZ);
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(67.46829f));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(89.80437f));
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(82.27781f));
            poseStack.translate(-originX, -originY, -originZ);
            itemRenderer.render(stack, ItemDisplayContext.NONE, false, poseStack, buffer, packedLight, packedOverlay,
                    topcore4);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private float staffUseTicks(ItemDisplayContext ctx) {
        boolean held = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (!held) {
            return -1F;
        }
        var player = Minecraft.getInstance().player;
        if (player == null || !player.isUsingItem() || !(player.getUseItem().getItem() instanceof SubmarineStaffItem)) {
            return -1F;
        }
        return player.getTicksUsingItem() + Minecraft.getInstance().getTimer().getGameTimeDeltaTicks();
    }
}
