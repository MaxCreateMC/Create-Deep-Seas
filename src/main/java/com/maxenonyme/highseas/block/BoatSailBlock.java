package com.maxenonyme.highseas.block;

import com.maxenonyme.highseas.CreateHighSeas;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement;
import com.simibubi.create.content.contraptions.bearing.SailBlock;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.utility.BlockHelper;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntityTicker;
import com.maxenonyme.highseas.block.entity.HalyardBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BoatSailBlock extends RotatedPillarBlock implements IWrenchable, BlockSubLevelLiftProvider, SpecialBlockItemRequirement, EntityBlock {
    public static final TagKey<Block> SAILS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(CreateHighSeas.MOD_ID, "sails"));

    public static final EnumProperty<SailCorner> CORNER = EnumProperty.create("corner", SailCorner.class);
    public static final BooleanProperty RIGGED = BooleanProperty.create("rigged");

    private static final VoxelShape SHAPE_Y = Block.box(0, 6, 0, 16, 10, 16);
    private static final double[][] DEPTHS = { { 0.1, 0.9 }, { 0.0, 0.9 }, { 0.1, 1.0 } };
    private static final VoxelShape[][][] SHAPES = new VoxelShape[2][SailCorner.values().length][DEPTHS.length];

    private static final int placementHelperId = PlacementHelpers.register(
            new BoatSailPlacementHelper(BoatSailBlock::checkItem, BoatSailBlock::checkState));

    protected final DyeColor color;

    public BoatSailBlock(Properties properties, DyeColor color) {
        super(properties);
        this.color = color;
        registerDefaultState(defaultBlockState().setValue(CORNER, SailCorner.NONE).setValue(RIGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CORNER, RIGGED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(RIGGED) ? new HalyardBlockEntity(CreateHighSeas.HALYARD_BE.get(), pos, state) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!state.getValue(RIGGED) || type != CreateHighSeas.HALYARD_BE.get())
            return null;
        return (BlockEntityTicker<T>) new SmartBlockEntityTicker<HalyardBlockEntity>();
    }

    public static Direction horizontal(Direction.Axis axis) {
        return axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
    }

    private static boolean sail(BlockGetter level, BlockPos pos, Direction.Axis axis) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof BoatSailBlock && s.getValue(AXIS) == axis;
    }

    public static SailCorner cornerFor(BlockGetter level, BlockPos pos, Direction.Axis axis) {
        if (axis == Direction.Axis.Y)
            return SailCorner.NONE;
        Direction across = horizontal(axis);
        SailCorner found = SailCorner.NONE;
        for (SailCorner c : SailCorner.CUTS) {
            Direction dh = c.h > 0 ? across : across.getOpposite();
            Direction dv = c.v > 0 ? Direction.UP : Direction.DOWN;
            if (sail(level, pos.relative(dh), axis) || sail(level, pos.relative(dv), axis))
                continue;
            if (!sail(level, pos.relative(dh.getOpposite()), axis) && !sail(level, pos.relative(dv.getOpposite()), axis))
                continue;
            if (!sail(level, pos.relative(dh.getOpposite()).relative(dv), axis)
                    && !sail(level, pos.relative(dh).relative(dv.getOpposite()), axis))
                continue;
            if (found != SailCorner.NONE)
                return SailCorner.NONE;
            found = c;
        }
        return found;
    }

    private static void refresh(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof BoatSailBlock))
            return;
        SailCorner c = cornerFor(level, pos, s.getValue(AXIS));
        if (s.getValue(CORNER) != c)
            level.setBlock(pos, s.setValue(CORNER, c), Block.UPDATE_CLIENTS);
    }

    private static void refreshDiagonals(Level level, BlockPos pos, Direction.Axis axis) {
        if (axis == Direction.Axis.Y)
            return;
        Direction across = horizontal(axis);
        for (int h = -1; h <= 1; h += 2)
            for (int v = -1; v <= 1; v += 2)
                refresh(level, pos.relative(across, h).relative(Direction.UP, v));
    }

    private static boolean sameSail(BlockState a, BlockState b) {
        return a.getBlock() instanceof BoatSailBlock && b.getBlock() instanceof BoatSailBlock
                && a.getValue(AXIS) == b.getValue(AXIS);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide || sameSail(state, oldState))
            return;
        refresh(level, pos);
        refreshDiagonals(level, pos, state.getValue(AXIS));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (level.isClientSide || sameSail(state, newState))
            return;
        refreshDiagonals(level, pos, state.getValue(AXIS));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return state.setValue(CORNER, cornerFor(level, pos, state.getValue(AXIS)));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState turned = super.rotate(state, rotation);
        if (state.getValue(AXIS) == Direction.Axis.Y || state.getValue(CORNER) == SailCorner.NONE)
            return turned;
        Direction across = rotation.rotate(horizontal(state.getValue(AXIS)));
        return turned.setValue(CORNER, flip(state.getValue(CORNER), across != horizontal(turned.getValue(AXIS))));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (state.getValue(AXIS) == Direction.Axis.Y || state.getValue(CORNER) == SailCorner.NONE)
            return state;
        Direction across = mirror.mirror(horizontal(state.getValue(AXIS)));
        return state.setValue(CORNER, flip(state.getValue(CORNER), across != horizontal(state.getValue(AXIS))));
    }

    private static SailCorner flip(SailCorner c, boolean flip) {
        if (!flip)
            return c;
        for (SailCorner o : SailCorner.CUTS)
            if (o.h == -c.h && o.v == c.v)
                return o;
        return c;
    }

    private static boolean checkItem(ItemStack i) {
        return i.getItem() instanceof BlockItem bi && bi.getBlock() instanceof BoatSailBlock;
    }

    private static boolean checkState(BlockState state) {
        return state.getBlock() instanceof BoatSailBlock;
    }

    public static void registerMovementCheck() {
        BlockMovementChecks.registerAttachedCheck((state, world, pos, direction) -> {
            Block block = state.getBlock();
            Block relative = world.getBlockState(pos.relative(direction)).getBlock();
            if (block instanceof BoatSailBlock && relative instanceof SailBlock)
                return BlockMovementChecks.CheckResult.FAIL;
            if (block instanceof SailBlock && relative instanceof BoatSailBlock)
                return BlockMovementChecks.CheckResult.FAIL;
            if (block instanceof BoatSailBlock)
                return direction.getAxis() == state.getValue(AXIS)
                        ? BlockMovementChecks.CheckResult.FAIL
                        : BlockMovementChecks.CheckResult.SUCCESS;
            return BlockMovementChecks.CheckResult.PASS;
        });
    }

    public void applyDye(BlockState state, Level world, BlockPos pos, Vec3 hit, @Nullable DyeColor color) {
        BlockState newState = CreateHighSeas.SAILS.get(color).get().defaultBlockState();
        newState = BlockHelper.copyProperties(state, newState);

        if (state != newState) {
            world.setBlockAndUpdate(pos, newState);
            return;
        }

        List<Direction> directions = IPlacementHelper.orderedByDistanceExceptAxis(pos, hit, state.getValue(AXIS));
        for (Direction d : directions) {
            BlockPos offset = pos.relative(d);
            BlockState adjacentState = world.getBlockState(offset);
            if (!(adjacentState.getBlock() instanceof BoatSailBlock))
                continue;
            if (state.getValue(AXIS) != adjacentState.getValue(AXIS))
                continue;
            if (state == adjacentState)
                continue;
            world.setBlockAndUpdate(offset, newState);
            return;
        }

        List<BlockPos> frontier = new ArrayList<>();
        frontier.add(pos);
        Set<BlockPos> visited = new HashSet<>();
        int timeout = 100;
        while (!frontier.isEmpty()) {
            if (timeout-- < 0)
                break;

            BlockPos currentPos = frontier.removeFirst();
            visited.add(currentPos);

            for (Direction d : Iterate.directions) {
                if (d.getAxis() == state.getValue(AXIS))
                    continue;
                BlockPos offset = currentPos.relative(d);
                if (visited.contains(offset))
                    continue;
                BlockState adjacentState = world.getBlockState(offset);
                if (!(adjacentState.getBlock() instanceof BoatSailBlock))
                    continue;
                if (adjacentState.getValue(AXIS) != state.getValue(AXIS))
                    continue;
                if (state != adjacentState)
                    world.setBlockAndUpdate(offset, newState);
                frontier.add(offset);
                visited.add(offset);
            }
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos,
                                              Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);

        if (heldItem.getItem() instanceof DyeItem dye) {
            if (!level.isClientSide)
                level.playSound(null, blockPos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0f, 1.1f - level.random.nextFloat() * .2f);
            applyDye(blockState, level, blockPos, blockHitResult.getLocation(), dye.getDyeColor());
            return ItemInteractionResult.SUCCESS;
        }

        IPlacementHelper placementHelper = PlacementHelpers.get(placementHelperId);
        if (placementHelper.matchesItem(heldItem)) {
            placementHelper.getOffset(player, level, blockState, blockPos, blockHitResult)
                    .placeInWorld(level, (BlockItem) heldItem.getItem(), player, interactionHand, blockHitResult);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    public DyeColor getColor() {
        return color;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis axis = context.getNearestLookingDirection().getAxis();
        return defaultBlockState().setValue(AXIS, axis)
                .setValue(CORNER, cornerFor(context.getLevel(), context.getClickedPos(), axis));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction.Axis axis = state.getValue(AXIS);
        if (axis == Direction.Axis.Y)
            return SHAPE_Y;
        Direction plus = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        boolean supPlus = isSupport(level, pos.relative(plus));
        boolean supMinus = isSupport(level, pos.relative(plus.getOpposite()));
        int depth = supMinus && !supPlus ? 1 : supPlus && !supMinus ? 2 : 0;
        int a = axis == Direction.Axis.X ? 0 : 1;
        SailCorner corner = state.getValue(CORNER);
        VoxelShape shape = SHAPES[a][corner.ordinal()][depth];
        if (shape == null) {
            shape = shape(axis, corner, DEPTHS[depth][0], DEPTHS[depth][1]);
            SHAPES[a][corner.ordinal()][depth] = shape;
        }
        return shape;
    }

    private static VoxelShape shape(Direction.Axis axis, SailCorner corner, double lo, double hi) {
        if (corner == SailCorner.NONE)
            return axis == Direction.Axis.X ? Shapes.box(lo, 0.0, 0.0, hi, 1.0, 1.0) : Shapes.box(0.0, 0.0, lo, 1.0, 1.0, hi);
        VoxelShape shape = Shapes.empty();
        for (int j = 0; j < 4; j++) {
            double reach = 1.0 - j / 4.0;
            double h0 = corner.h > 0 ? 0.0 : 1.0 - reach;
            double h1 = corner.h > 0 ? reach : 1.0;
            double v0 = corner.v > 0 ? j / 4.0 : 0.75 - j / 4.0;
            double v1 = v0 + 0.25;
            shape = Shapes.or(shape, axis == Direction.Axis.X
                    ? Shapes.box(lo, v0, h0, hi, v1, h1)
                    : Shapes.box(h0, v0, lo, h1, v1, hi));
        }
        return shape;
    }

    private static boolean isSupport(BlockGetter level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return !s.isAir() && s.getFluidState().isEmpty() && !s.is(SAILS);
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        super.fallOn(level, state, pos, entity, 0);
    }

    @Override
    public void updateEntityAfterFallOn(BlockGetter level, Entity entity) {
        if (entity.isSuppressingBounce()) {
            super.updateEntityAfterFallOn(level, entity);
        } else {
            bounce(entity);
        }
    }

    private void bounce(Entity entity) {
        Vec3 motion = entity.getDeltaMovement();
        if (motion.y < 0.0D) {
            double d0 = entity instanceof LivingEntity ? 1.0D : 0.8D;
            entity.setDeltaMovement(motion.x, -motion.y * (double) 0.26F * d0, motion.z);
        }
    }

    @Override
    public float sable$getLiftScalar() {
        return 0;
    }

    @Override
    public float sable$getParallelDragScalar() {
        return 1.75f;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(CreateHighSeas.SAIL_ITEM.get());
    }

    @Override
    public @NotNull Direction sable$getNormal(BlockState blockState) {
        return Direction.get(Direction.AxisDirection.POSITIVE, blockState.getValue(AXIS));
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState state, @Nullable BlockEntity blockEntity) {
        return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, new ItemStack(CreateHighSeas.SAIL_ITEM.get()));
    }
}
