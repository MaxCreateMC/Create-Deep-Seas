package com.maxenonyme.createsubmarine.submarine.block;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.entity.BallastVentBlockEntity;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
public class BallastVentBlock extends Block implements EntityBlock, IWrenchable {
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public BallastVentBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.defaultBlockState()
            .setValue(UP, false).setValue(DOWN, false)
            .setValue(NORTH, false).setValue(SOUTH, false)
            .setValue(EAST, false).setValue(WEST, false));
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UP, DOWN, NORTH, SOUTH, EAST, WEST);
    }
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BallastVentBlockEntity(pos, state);
    }
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide)
            return null;
        return type == CreateSubmarine.BALLAST_VENT_BE.get() ? (l, p, s, be) -> ((BallastVentBlockEntity) be).tick() : null;
    }
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return computeConnections(this.defaultBlockState(), context.getLevel(), context.getClickedPos());
    }
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(propertyForDirection(direction), canConnectTo(level, neighborPos, direction));
    }
    public static BlockState computeConnections(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            state = state.setValue(propertyForDirection(dir), canConnectTo(level, pos.relative(dir), dir));
        }
        return state;
    }
    private static boolean canConnectTo(BlockGetter level, BlockPos neighborPos, Direction faceTowardsNeighbor) {
        BlockEntity be = level.getBlockEntity(neighborPos);
        if (be == null) return false;
        if (be.getLevel() == null) return false;
        IFluidHandler handler = be.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, neighborPos, faceTowardsNeighbor.getOpposite());
        return handler != null;
    }
    public static BooleanProperty propertyForDirection(Direction dir) {
        return switch (dir) {
            case UP -> UP;
            case DOWN -> DOWN;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
        };
    }
}
