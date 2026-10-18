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
    public static final BooleanProperty INVERTED = BooleanProperty.create("inverted");

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    public PulleyBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CONNECTED, false).setValue(INVERTED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONNECTED, INVERTED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite().getCounterClockWise();
        Direction clicked = context.getClickedFace();
        boolean inverted = clicked == Direction.DOWN
                || (clicked != Direction.UP && context.getClickLocation().y - context.getClickedPos().getY() > 0.5);
        return this.defaultBlockState().setValue(FACING, facing).setValue(CONNECTED, false).setValue(INVERTED, inverted);
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
        boolean inverted = state.getValue(INVERTED);
        double wheelBottom = inverted ? -13 : 7;
        double wheelTop = inverted ? 9 : 29;
        VoxelShape base = Block.box(0, 0, 0, 16, 16, 16);
        VoxelShape wheel = switch (facing) {
            case NORTH, UP -> Block.box(3, wheelBottom, 2, 25, wheelTop, 14);
            case SOUTH -> Block.box(-9, wheelBottom, 2, 13, wheelTop, 14);
            case EAST -> Block.box(2, wheelBottom, 3, 14, wheelTop, 25);
            case WEST -> Block.box(2, wheelBottom, -9, 14, wheelTop, 13);
            case DOWN -> Block.box(3, -13, 2, 25, 9, 14);
        };
        return Shapes.or(base, wheel);
    }
}
