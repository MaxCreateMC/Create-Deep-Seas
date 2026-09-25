package com.maxenonyme.createsubmarine.submarine.block;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class PumpControllerBlock extends PumpBlock {

    public PumpControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntityType<? extends PumpBlockEntity> getBlockEntityType() {
        return CreateSubmarine.PUMP_CONTROLLER_BE.get();
    }
}
