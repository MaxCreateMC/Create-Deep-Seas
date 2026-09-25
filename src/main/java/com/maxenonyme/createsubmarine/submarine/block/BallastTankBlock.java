package com.maxenonyme.createsubmarine.submarine.block;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.entity.BallastTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

public class BallastTankBlock extends Block implements EntityBlock {
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");

    public BallastTankBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(TOP, true).setValue(BOTTOM, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP, BOTTOM);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        return defaultBlockState()
                .setValue(TOP, !isTank(level.getBlockState(pos.above())))
                .setValue(BOTTOM, !isTank(level.getBlockState(pos.below())));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
            LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction == Direction.UP)
            return state.setValue(TOP, !isTank(neighbour));
        if (direction == Direction.DOWN)
            return state.setValue(BOTTOM, !isTank(neighbour));
        return state;
    }

    private static boolean isTank(BlockState state) {
        return state.getBlock() instanceof BallastTankBlock;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BallastTankBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return type == CreateSubmarine.BALLAST_TANK_BE.get() ? (lvl, p, st, be) -> BallastTankBlockEntity.serverTick(lvl, p, (BallastTankBlockEntity) be) : null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
