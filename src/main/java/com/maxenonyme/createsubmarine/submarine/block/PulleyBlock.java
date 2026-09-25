package com.maxenonyme.createsubmarine.submarine.block;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.entity.PulleyBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class PulleyBlock extends DirectionalBlock implements EntityBlock {
    public static final MapCodec<PulleyBlock> CODEC = simpleCodec(PulleyBlock::new);
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    public PulleyBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CONNECTED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONNECTED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite().getCounterClockWise();
        return this.defaultBlockState().setValue(FACING, facing).setValue(CONNECTED, false);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PulleyBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        VoxelShape base = Block.box(0, 0, 0, 16, 16, 16);
        VoxelShape wheel = switch (facing) {
            case NORTH, UP -> Block.box(3, 7, 2, 25, 29, 14);
            case SOUTH -> Block.box(-9, 7, 2, 13, 29, 14);
            case EAST -> Block.box(2, 7, 3, 14, 29, 25);
            case WEST -> Block.box(2, 7, -9, 14, 29, 13);
            case DOWN -> Block.box(3, -13, 2, 25, 9, 14);
        };
        return Shapes.or(base, wheel);
    }
}
