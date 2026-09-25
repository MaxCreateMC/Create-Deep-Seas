package com.maxenonyme.highseas.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;

public class SeaglideItemRenderer extends CustomRenderedItemModelRenderer {

    private static final float PIVOT_X = 8.5f / 16.0f - 0.5f;
    private static final float PIVOT_Y = 7.5f / 16.0f - 0.5f;

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
            ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        boolean tilt = SeaglideClientHandler.isActive() && (transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND);

        ms.pushPose();
        if (tilt) {
            ms.mulPose(Axis.XP.rotationDegrees(90.0f));
        }
        if (transformType.firstPerson() || tilt) {
            SeaglideClientHandler.captureNozzle(
                    ms.last().pose().transformPosition(PIVOT_X, PIVOT_Y, 0.0f, new Vector3f()));
        }
        renderer.render(model.getOriginalModel(), light);

        float angle = SeaglideClientHandler.angle(AnimationTickHolder.getPartialTicks());
        spin(renderer, ms, light, angle, AllHighSeasPartialModels.SEAGLIDE_FAN_1);
        spin(renderer, ms, light, -angle, AllHighSeasPartialModels.SEAGLIDE_FAN_2);
        spin(renderer, ms, light, angle, AllHighSeasPartialModels.SEAGLIDE_FAN_3);
        spin(renderer, ms, light, -angle, AllHighSeasPartialModels.SEAGLIDE_FAN_4);
        spin(renderer, ms, light, angle, AllHighSeasPartialModels.SEAGLIDE_FAN_5);
        spin(renderer, ms, light, -angle, AllHighSeasPartialModels.SEAGLIDE_FAN_6);
        ms.popPose();
    }

    private static void spin(PartialItemModelRenderer renderer, PoseStack ms, int light, float angle,
            PartialModel model) {
        ms.pushPose();
        ms.translate(PIVOT_X, PIVOT_Y, 0.0f);
        ms.mulPose(Axis.ZP.rotationDegrees(angle));
        ms.translate(-PIVOT_X, -PIVOT_Y, 0.0f);
        renderer.render(model.get(), light);
        ms.popPose();
    }
}
