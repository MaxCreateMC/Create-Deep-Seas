package com.maxenonyme.createsubmarine.submarine.block;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.entity.WaterThrusterBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class WaterThrusterBlock extends DirectionalBlock implements EntityBlock {
    public static final MapCodec<WaterThrusterBlock> CODEC = simpleCodec(WaterThrusterBlock::new);
    private static final Map<Direction, VoxelShape> SHAPES = new HashMap<>();

    static {
        VoxelShape base = Shapes.or(
            Block.box(2, 0, 1, 14, 16, 13.5),
            Block.box(4, 16, 3.25, 12, 18, 11.25),
            Block.box(5, 18, 4.25, 11, 20, 10.25),
            Block.box(6, 20, 5.25, 10, 23, 9.25)
        );

        SHAPES.put(Direction.UP, base);
        SHAPES.put(Direction.DOWN, rotateShape(base, Direction.UP, Direction.DOWN));
        SHAPES.put(Direction.NORTH, rotateShape(base, Direction.UP, Direction.NORTH));
        SHAPES.put(Direction.SOUTH, rotateShape(base, Direction.UP, Direction.SOUTH));
        SHAPES.put(Direction.EAST, rotateShape(base, Direction.UP, Direction.EAST));
        SHAPES.put(Direction.WEST, rotateShape(base, Direction.UP, Direction.WEST));
    }

    private static VoxelShape rotateShape(VoxelShape shape, Direction from, Direction to) {
        if (from == to)
            return shape;
        switch (to) {
            case DOWN:
                return Shapes.or(
                    Block.box(2, 0, 2.5, 14, 16, 15),
                    Block.box(4, -2, 4.75, 12, 0, 12.75),
                    Block.box(5, -4, 5.75, 11, -2, 11.75),
                    Block.box(6, -7, 6.75, 10, -4, 10.75)
                );
            case NORTH:
                return Shapes.or(
                    Block.box(2, 1, 0, 14, 13.5, 16),
                    Block.box(4, 3.25, -2, 12, 11.25, 0),
                    Block.box(5, 4.25, -4, 11, 10.25, -2),
                    Block.box(6, 5.25, -7, 10, 9.25, -4)
                );
            case SOUTH:
                return Shapes.or(
                    Block.box(2, 1, 0, 14, 13.5, 16),
                    Block.box(4, 3.25, 16, 12, 11.25, 18),
                    Block.box(5, 4.25, 18, 11, 10.25, 20),
                    Block.box(6, 5.25, 20, 10, 9.25, 23)
                );
            case EAST:
                return Shapes.or(
                    Block.box(0, 1, 2, 16, 13.5, 14),
                    Block.box(16, 3.25, 4, 18, 11.25, 12),
                    Block.box(18, 4.25, 5, 20, 10.25, 11),
                    Block.box(20, 5.25, 6, 23, 9.25, 10)
                );
            case WEST:
                return Shapes.or(
                    Block.box(0, 1, 2, 16, 13.5, 14),
                    Block.box(-2, 3.25, 4, 0, 11.25, 12),
                    Block.box(-4, 4.25, 5, -2, 10.25, 11),
                    Block.box(-7, 5.25, 6, -4, 9.25, 10)
                );
            default:
                return shape;
        }
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    public WaterThrusterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.UP));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WaterThrusterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == CreateSubmarine.WATER_THRUSTER_BE.get()
                ? (l, p, s, be) -> WaterThrusterBlockEntity.serverTick(l, p, s, (WaterThrusterBlockEntity) be)
                : null;
    }
}