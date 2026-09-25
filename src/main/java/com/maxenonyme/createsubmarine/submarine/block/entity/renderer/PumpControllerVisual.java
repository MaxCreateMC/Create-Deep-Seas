package com.maxenonyme.createsubmarine.submarine.block.entity.renderer;

import com.maxenonyme.createsubmarine.submarine.block.entity.PumpControllerBlockEntity;
import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import net.minecraft.core.Direction;

public class PumpControllerVisual extends SingleAxisRotatingVisual<PumpControllerBlockEntity> {
    public static final float COG_OFFSET = 0.6f / 16f;

    public PumpControllerVisual(VisualizationContext context, PumpControllerBlockEntity be, float partialTick) {
        super(context, be, partialTick, Direction.SOUTH, Models.partial(AllPartialModels.PUMP_CONTROLLER_COG));
        Direction front = be.getBlockState().getValue(PumpBlock.FACING);
        rotatingModel.nudge(front.getStepX() * COG_OFFSET, front.getStepY() * COG_OFFSET, front.getStepZ() * COG_OFFSET)
                .setChanged();
    }
}
