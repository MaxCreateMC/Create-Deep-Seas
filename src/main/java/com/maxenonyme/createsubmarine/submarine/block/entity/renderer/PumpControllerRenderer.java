package com.maxenonyme.createsubmarine.submarine.block.entity.renderer;

import com.maxenonyme.createsubmarine.submarine.block.PumpControllerBlock;
import com.maxenonyme.createsubmarine.submarine.block.entity.PumpControllerBlockEntity;
import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class PumpControllerRenderer extends KineticBlockEntityRenderer<PumpControllerBlockEntity> {

    public PumpControllerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(PumpControllerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
            int light, int overlay) {
        if (!be.getBlockState().getValue(PumpControllerBlock.SEIZED)) {
            super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
            return;
        }
        if (VisualizationManager.supportsVisualization(be.getLevel()))
            return;

        BlockState state = getRenderedBlockState(be);
        standardKineticRotationTransform(getRotatedModel(be, state), be, light)
                .color(PumpControllerVisual.SEIZED_TINT)
                .renderInto(ms, buffer.getBuffer(getRenderType(be, state)));
    }

    @Override
    protected SuperByteBuffer getRotatedModel(PumpControllerBlockEntity be, BlockState state) {
        Direction front = state.getValue(PumpBlock.FACING);
        float offset = PumpControllerVisual.COG_OFFSET;
        return CachedBuffers.partialFacing(AllPartialModels.PUMP_CONTROLLER_COG, state)
                .translate(front.getStepX() * offset, front.getStepY() * offset, front.getStepZ() * offset);
    }
}
