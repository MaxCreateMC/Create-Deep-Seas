package com.maxenonyme.createsubmarine.submarine.block;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class PumpControllerBlock extends PumpBlock {
    public static final BooleanProperty SEIZED = BooleanProperty.create("seized");

    public PumpControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SEIZED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(SEIZED));
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
