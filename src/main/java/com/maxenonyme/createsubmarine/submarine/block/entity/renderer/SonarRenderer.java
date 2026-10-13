package com.maxenonyme.createsubmarine.submarine.block.entity.renderer;

import com.maxenonyme.createsubmarine.submarine.block.entity.SonarBlockEntity;
import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class SonarRenderer implements BlockEntityRenderer<SonarBlockEntity> {
    public SonarRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SonarBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light,
            int overlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (be.visualized)
            return;

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotation(SonarVisual.yaw(state)));
        ms.mulPose(Axis.XP.rotation(SonarVisual.tilt(state)));
        ms.translate(-0.5, -0.5, -0.5);
        ms.translate(0, SonarVisual.PIVOT_Y, SonarVisual.PIVOT_Z);
        ms.mulPose(Axis.XP.rotation(SonarVisual.angle(level)));
        ms.translate(0, -SonarVisual.PIVOT_Y, -SonarVisual.PIVOT_Z);

        CachedBuffers.partial(AllPartialModels.SONAR_TURN, state)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
        CachedBuffers.partial(AllPartialModels.SONAR_TURN_EMISSIVE, state)
                .light(LightTexture.FULL_BRIGHT)
                .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
        ms.popPose();
    }
}
