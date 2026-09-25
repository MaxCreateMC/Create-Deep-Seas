package com.maxenonyme.highseas.block;

import com.maxenonyme.highseas.CreateHighSeas;
import com.maxenonyme.highseas.block.entity.BoatEngineBlockEntity;
import com.maxenonyme.highseas.helm.HelmServer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;
import com.maxenonyme.highseas.gui.BoatEngineMenu;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.SimpleMenuProvider;

public class BoatEngineBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<BoatEngineBlock> CODEC = simpleCodec(BoatEngineBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final VoxelShape SHAPE_NORTH = Shapes.or(
            Block.box(0, 4, 0, 16, 18, 16),
            Block.box(2, 6, 16, 6, 16, 18),
            Block.box(10, 6, 16, 14, 16, 18),
            Block.box(5, 0, 4, 11, 4, 14),
            Block.box(6.5, -12, 9.5, 9.5, 0, 12.5),
            Block.box(7, -11.5, 12.5, 9, -9.5, 15.5));

    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            SHAPES[dir.get2DDataValue()] = rotate(Direction.NORTH, dir, SHAPE_NORTH);
        }
    }

    public BoatEngineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos below = context.getClickedPos().below();
        if (!context.getLevel().getBlockState(below).canBeReplaced()) {
            if (!context.getLevel().isClientSide && context.getPlayer() instanceof ServerPlayer player) {
                notifyObstructed(player);
            }
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoatEngineBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide)
            return ticker(type, CreateHighSeas.BOAT_ENGINE_BE.get(), BoatEngineBlockEntity::clientTick);
        return ticker(type, CreateHighSeas.BOAT_ENGINE_BE.get(), BoatEngineBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (stack.getBurnTime(null) > 0 && level.getBlockEntity(pos) instanceof BoatEngineBlockEntity be && !be.hasFuel()) {
            if (!level.isClientSide)
                be.insertFuel(player, hand);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof BoatEngineBlockEntity be))
            return InteractionResult.PASS;

        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide) {
                player.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new BoatEngineMenu(id, inv, pos, be.getFuelSlot(),
                                be.data),
                        Component.translatable("block.create_high_seas.boat_engine")), pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide && player instanceof ServerPlayer sp)
            HelmServer.tryMount(sp, be);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BoatEngineBlockEntity be) {
            HelmServer.releaseAt(level, pos);
            be.dropFuel();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        LevelAccessor level = event.getLevel();
        if (!(level.getBlockState(event.getPos().above()).getBlock() instanceof BoatEngineBlock))
            return;
        event.setCanceled(true);
        Entity entity = event.getEntity();
        if (entity instanceof ServerPlayer player) {
            notifyObstructed(player);
            player.inventoryMenu.sendAllDataToRemote();
            player.containerMenu.sendAllDataToRemote();
        }
    }

    private static void notifyObstructed(ServerPlayer player) {
        player.displayClientMessage(
                Component.literal("✖ ")
                        .append(Component.translatable("message.create_high_seas.boat_engine.obstructed"))
                        .withStyle(ChatFormatting.RED),
                true);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <T extends BlockEntity> BlockEntityTicker<T> ticker(BlockEntityType<T> given,
            BlockEntityType<BoatEngineBlockEntity> expected, BlockEntityTicker<BoatEngineBlockEntity> ticker) {
        return expected == given ? (BlockEntityTicker<T>) ticker : null;
    }

    private static VoxelShape rotate(Direction from, Direction to, VoxelShape shape) {
        VoxelShape[] buffer = { shape, Shapes.empty() };
        int steps = (to.get2DDataValue() - from.get2DDataValue() + 4) % 4;
        for (int i = 0; i < steps; i++) {
            buffer[0].forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> buffer[1] = Shapes.or(buffer[1],
                    Shapes.box(1 - maxZ, minY, minX, 1 - minZ, maxY, maxX)));
            buffer[0] = buffer[1];
            buffer[1] = Shapes.empty();
        }
        return buffer[0];
    }
}
