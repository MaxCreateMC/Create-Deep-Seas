package com.maxenonyme.createsubmarine.submarine.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.entity.IndustrialAlarmBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class IndustrialAlarmBlock extends DirectionalBlock implements SimpleWaterloggedBlock, EntityBlock {

    private static final DustParticleOptions PARTICLE = new DustParticleOptions(new Vector3f(1.0f, 0.0f, 0.0f), 0.3f);

    protected static final VoxelShape AABB_UP    = Block.box(5, 0, 5, 11, 8, 11);
    protected static final VoxelShape AABB_DOWN  = Block.box(5, 8, 5, 11, 16, 11);
    protected static final VoxelShape AABB_EAST  = Block.box(0, 5, 5, 8, 11, 11);
    protected static final VoxelShape AABB_WEST  = Block.box(8, 5, 5, 16, 11, 11);
    protected static final VoxelShape AABB_SOUTH = Block.box(5, 5, 0, 11, 11, 8);
    protected static final VoxelShape AABB_NORTH = Block.box(5, 5, 8, 11, 11, 16);

    public IndustrialAlarmBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(BlockStateProperties.LIT, false)
                .setValue(BlockStateProperties.FACING, Direction.UP)
                .setValue(BlockStateProperties.WATERLOGGED, false));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        FluidState fluidstate = ctx.getLevel().getFluidState(ctx.getClickedPos());
        return defaultBlockState()
                .setValue(BlockStateProperties.FACING, ctx.getClickedFace())
                .setValue(BlockStateProperties.LIT, false)
                .setValue(BlockStateProperties.WATERLOGGED, fluidstate.is(net.minecraft.tags.FluidTags.WATER));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(BlockStateProperties.FACING)) {
            case UP    -> AABB_UP;
            case DOWN  -> AABB_DOWN;
            case EAST  -> AABB_EAST;
            case WEST  -> AABB_WEST;
            case SOUTH -> AABB_SOUTH;
            case NORTH -> AABB_NORTH;
        };
    }

    @Override
    public BlockState updateShape(BlockState state, Direction from, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(BlockStateProperties.WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return !this.canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, from, neighbor, level, pos, neighborPos);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (state.getValue(BlockStateProperties.LIT) && (level.getGameTime() / 20L) % 2L == 0L) {
            makeParticle(state, level, pos);
        }
    }

    private void makeParticle(BlockState state, LevelAccessor level, BlockPos pos) {
        Direction direction = state.getValue(BlockStateProperties.FACING);
        level.addParticle(PARTICLE,
                pos.getX() + 0.5f + 0.1f * direction.getStepX(),
                pos.getY() + 0.5f + 0.1f * direction.getStepY(),
                pos.getZ() + 0.5f + 0.1f * direction.getStepZ(),
                0d, 0d, 0d);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.LIT, BlockStateProperties.FACING, BlockStateProperties.WATERLOGGED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IndustrialAlarmBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof IndustrialAlarmBlockEntity alarm) {
                alarm.tickClient(lvl, pos, st);
            }
        };
    }

    @SuppressWarnings("null")
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(BlockStateProperties.FACING);
        return Block.canSupportCenter(level, pos.relative(facing.getOpposite()), facing.getOpposite());
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        throw new UnsupportedOperationException();
    }
}