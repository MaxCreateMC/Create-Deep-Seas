package com.maxenonyme.createsubmarine.submarine.block.entity.renderer;

import com.maxenonyme.createsubmarine.submarine.block.entity.PumpControllerBlockEntity;
import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class PumpControllerRenderer extends KineticBlockEntityRenderer<PumpControllerBlockEntity> {

    public PumpControllerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(PumpControllerBlockEntity be, BlockState state) {
        Direction front = state.getValue(PumpBlock.FACING);
        float offset = PumpControllerVisual.COG_OFFSET;
        return CachedBuffers.partialFacing(AllPartialModels.PUMP_CONTROLLER_COG, state)
                .translate(front.getStepX() * offset, front.getStepY() * offset, front.getStepZ() * offset);
    }
}
