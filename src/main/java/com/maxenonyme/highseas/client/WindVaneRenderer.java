package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.block.entity.WindVaneBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;

public class WindVaneRenderer implements BlockEntityRenderer<WindVaneBlockEntity> {

    public WindVaneRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(WindVaneBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (be.getLevel() != null && VisualizationManager.supportsVisualization(be.getLevel())) {
            return;
        }

        float angle = WindVaneAngle.update(be);

        ms.pushPose();
        ms.translate(0.5, 0.0, 0.5);
        ms.mulPose(Axis.YP.rotation(angle));
        ms.translate(-0.5, 0.0, -0.5);

        CachedBuffers.partial(AllHighSeasPartialModels.WIND_VANE_ARROW, be.getBlockState())
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS)));

        ms.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(WindVaneBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.5);
    }
}
