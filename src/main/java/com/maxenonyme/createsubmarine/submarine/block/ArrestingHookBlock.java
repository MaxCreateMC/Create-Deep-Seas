package com.maxenonyme.createsubmarine.submarine.block;

import com.maxenonyme.createsubmarine.submarine.block.entity.ArrestingHookBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ArrestingHookBlock extends HorizontalDirectionalBlock implements EntityBlock, SimpleWaterloggedBlock {

    public static final MapCodec<ArrestingHookBlock> CODEC = simpleCodec(ArrestingHookBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty CEILING = BooleanProperty.create("ceiling");

    private static final VoxelShape SHAPE_NS = Block.box(4, 0, 3, 12, 5, 13);
    private static final VoxelShape SHAPE_EW = Block.box(3, 0, 4, 13, 5, 12);
    private static final VoxelShape SHAPE_NS_CEILING = Block.box(4, 11, 3, 12, 16, 13);
    private static final VoxelShape SHAPE_EW_CEILING = Block.box(3, 11, 4, 13, 16, 12);

    public ArrestingHookBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false)
                .setValue(CEILING, false)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, CEILING, WATERLOGGED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        boolean ceiling = context.getClickedFace() == Direction.DOWN;
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection())
                .setValue(POWERED, level.hasNeighborSignal(pos))
                .setValue(CEILING, ceiling)
                .setValue(WATERLOGGED, level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide) {
            boolean signal = level.hasNeighborSignal(pos);
            if (signal != state.getValue(POWERED)) {
                level.setBlock(pos, state.setValue(POWERED, signal), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean ew = state.getValue(FACING).getAxis() == Direction.Axis.X;
        if (state.getValue(CEILING)) {
            return ew ? SHAPE_EW_CEILING : SHAPE_NS_CEILING;
        }
        return ew ? SHAPE_EW : SHAPE_NS;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArrestingHookBlockEntity(pos, state);
    }
}
