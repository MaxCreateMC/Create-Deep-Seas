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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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

public class BoatSailBlock extends RotatedPillarBlock implements IWrenchable, BlockSubLevelLiftProvider, SpecialBlockItemRequirement {
    public static final TagKey<Block> SAILS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(CreateHighSeas.MOD_ID, "sails"));

    private static final VoxelShape SHAPE_X = Block.box(6, 0, 0, 10, 16, 16);
    private static final VoxelShape SHAPE_Y = Block.box(0, 6, 0, 16, 10, 16);
    private static final VoxelShape SHAPE_Z = Block.box(0, 0, 6, 16, 16, 10);

    private static final int placementHelperId = PlacementHelpers.register(
            new BoatSailPlacementHelper(BoatSailBlock::checkItem, BoatSailBlock::checkState));

    protected final DyeColor color;

    public BoatSailBlock(Properties properties, DyeColor color) {
        super(properties);
        this.color = color;
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
        return defaultBlockState().setValue(AXIS, context.getNearestLookingDirection().getAxis());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction.Axis axis = state.getValue(AXIS);
        return switch (axis) {
            case X -> Shapes.or(SHAPE_X, bulge(axis, level, pos));
            case Z -> Shapes.or(SHAPE_Z, bulge(axis, level, pos));
            default -> SHAPE_Y;
        };
    }

    private static VoxelShape bulge(Direction.Axis axis, BlockGetter level, BlockPos pos) {
        Direction plus = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        boolean supPlus = isSupport(level, pos.relative(plus));
        boolean supMinus = isSupport(level, pos.relative(plus.getOpposite()));

        double lo = 0.1;
        double hi = 0.9;
        if (supMinus && !supPlus) {
            lo = 0.0;
        } else if (supPlus && !supMinus) {
            hi = 1.0;
        }

        return axis == Direction.Axis.X
                ? Shapes.box(lo, 0.0, 0.0, hi, 1.0, 1.0)
                : Shapes.box(0.0, 0.0, lo, 1.0, 1.0, hi);
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
