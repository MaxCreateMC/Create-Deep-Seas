package com.maxenonyme.createsubmarine.submarine.block;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.entity.ElectrolyzerBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.maxenonyme.createsubmarine.submarine.gui.ElectrolyzerMenu;
import com.maxenonyme.createsubmarine.submarine.system.DiffuserZoneProtection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;

public class ElectrolyzerBlock extends KineticBlock implements IBE<ElectrolyzerBlockEntity> {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty ALTERNATOR = BooleanProperty.create("alternator");

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 13, 16),
            Block.box(2, 13, 2, 14, 27, 14)
    );

    public ElectrolyzerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(POWERED, false)
                .setValue(ALTERNATOR, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, ALTERNATOR);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return DiffuserZoneProtection.canPlaceMachine(context)
                ? super.getStateForPlacement(context)
                : null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ElectrolyzerBlockEntity be) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new ElectrolyzerMenu(id, inv, be, be.data),
                    Component.translatable("block.create_submarine.electrolyzer")), pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Z;
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return state.getValue(ALTERNATOR) && face.getAxis() == Direction.Axis.Z;
    }

    @Override
    protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
        return super.areStatesKineticallyEquivalent(oldState, newState)
                && oldState.getValue(ALTERNATOR) == newState.getValue(ALTERNATOR);
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        return originalState;
    }

    @Override
    public Class<ElectrolyzerBlockEntity> getBlockEntityClass() {
        return ElectrolyzerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ElectrolyzerBlockEntity> getBlockEntityType() {
        return CreateSubmarine.ELECTROLYZER_BE.get();
    }
}
