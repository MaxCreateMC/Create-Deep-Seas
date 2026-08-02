package com.maxenonyme.createsubmarine.submarine.item.harpoon_gun;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class HarpoonGunItemRenderer extends CustomRenderedItemModelRenderer {

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext context, PoseStack ms, MultiBufferSource buffer,
                          int light, int overlay) {
        float scale;
        switch (context) {
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> scale = 0.8F;
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> scale = 0.5F;
            case GUI -> scale = 0.6F;
            case GROUND -> scale = 0.25F;
            default -> scale = 0.5F;
        }

        ms.scale(scale, scale, scale);

        renderer.render(
            com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels.HARPOON_GUN.get(),
            light);

        float worldTime = AnimationTickHolder.getRenderTime() / 20;
        float bob = (float) (Math.sin(worldTime * 3.0) * 0.02);

        ms.translate(0, bob, 0);
    }
}
