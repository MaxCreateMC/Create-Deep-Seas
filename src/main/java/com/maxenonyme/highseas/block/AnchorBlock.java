package com.maxenonyme.highseas.block;

import com.maxenonyme.highseas.block.entity.AnchorBlockEntity;
import com.maxenonyme.highseas.CreateHighSeas;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.createmod.catnip.math.VoxelShaper;
import com.mojang.serialization.MapCodec;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
import dev.simulated_team.simulated.index.SimDataComponents;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.SteelCableItem;
import com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper;
import com.maxenonyme.createsubmarine.submarine.util.SteelCableHolderAccessor;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.shapes.BooleanOp;

public class AnchorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<AnchorBlock> CODEC = simpleCodec(AnchorBlock::new);
    public static final BooleanProperty HAS_CABLE = BooleanProperty.create("has_cable");
    public static final BooleanProperty GROUNDED = BooleanProperty.create("grounded");

    public static final VoxelShaper SHAPE = VoxelShaper.forHorizontal(Shapes.or(
        Block.box(8.650, 22.334,  13.560,  9.150, 23.584,  16.010),
        Block.box(6.900, 23.334,  13.560,  9.150, 23.834,  16.010),
        Block.box(6.900, 21.834,  13.560,  9.150, 22.334,  16.010),
        Block.box(6.900, 22.334,  13.560,  7.400, 23.584,  16.010),
        Block.box(-0.927, 0.000,  13.762,  8.891, 5.997,  15.762),
        Block.box(7.200, 0.000,  13.761,  17.018, 5.997,  15.763),
        Block.box(-2.323, 7.832,  14.012,  -0.013, 9.694,  15.514),
        Block.box(-3.224, 4.121,  14.011,  0.646, 9.692,  15.513),
        Block.box(15.354, 4.121,  14.011,  19.224, 9.692,  15.513),
        Block.box(16.013, 7.832,  14.012,  18.323, 9.694,  15.514),
        Block.box(5.000, 18.834,  14.260,  7.450, 19.834,  15.210),
        Block.box(11.000, 18.584,  14.010,  12.500, 20.084,  15.510),
        Block.box(3.500, 18.584,  14.010,  5.000, 20.084,  15.510),
        Block.box(8.650, 18.834,  14.260,  11.250, 19.834,  15.210),
        Block.box(7.050, 1.834,  13.510,  9.050, 10.834,  16.010),
        Block.box(7.450, 10.834,  13.760,  8.650, 21.834,  15.760)
    ), Direction.NORTH);

    public AnchorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HAS_CABLE, false)
                .setValue(GROUNDED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_CABLE, GROUNDED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.join(SHAPE.get(state.getValue(FACING)), Shapes.block(), BooleanOp.AND);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AnchorBlockEntity(CreateHighSeas.ANCHOR_BE.get(), pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        if (type != CreateHighSeas.ANCHOR_BE.get()) return null;
        return (lvl, pos, st, be) -> ((AnchorBlockEntity) be).tick();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(CreateSubmarine.STEEL_CABLE.get())) {
            if (player.isShiftKeyDown()) {
                stack.remove(SimDataComponents.ROPE_FIRST_CONNECTION);
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }

            if (stack.has(SimDataComponents.ROPE_FIRST_CONNECTION)) {
                if (!level.isClientSide) {
                    BlockPos firstPos = stack.get(SimDataComponents.ROPE_FIRST_CONNECTION);
                    boolean success = attachCable(level, firstPos, pos);
                    if (success) {
                        if (!player.isCreative()) {
                            stack.shrink(1);
                        }
                    }
                }
                stack.remove(SimDataComponents.ROPE_FIRST_CONNECTION);
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }

            if (RopeItem.isValidRopeAttachment(level, pos)) {
                stack.set(SimDataComponents.ROPE_FIRST_CONNECTION, pos);
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        if (stack.getItem() instanceof RopeItem && !stack.is(CreateSubmarine.STEEL_CABLE.get())) {
            if (!level.isClientSide && player != null) {
                player.displayClientMessage(Component.literal("✖ ")
                        .append(Component.translatable("create_high_seas.anchor.steel_cable_only"))
                        .withStyle(ChatFormatting.RED), true);
                level.playSound(null, pos, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.BLOCKS, 0.6F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    public static BlockPos extractAnchorToSubLevel(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockState(pos).getBlock() instanceof AnchorBlock)) {
            return pos;
        }
        
        SubLevelAccess currentSub = SableCompanion.INSTANCE.getContaining(level, pos);
        if (currentSub instanceof ServerSubLevel serverSub) {
            BoundingBox3ic bb = serverSub.getPlot().getBoundingBox();
            if (bb != null && bb.minX() == bb.maxX() && bb.minY() == bb.maxY() && bb.minZ() == bb.maxZ()) {
                return pos; 
            }
        }
        
        try {
            Set<BlockPos> segmentBlocks = Set.of(pos);
            BoundingBox3i bounds = new BoundingBox3i(
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX(), pos.getY(), pos.getZ()
            );
            
            ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, pos, segmentBlocks, bounds);

            if (subLevel != null) {
                Object handle = SablePhysicsHelper.getHandle(subLevel);
                if (handle != null) {
                    SablePhysicsHelper.wakeUp(handle);
                }
                return subLevel.getPlot().getCenterBlock();
            }
        } catch (RuntimeException e) {
            CreateHighSeas.LOGGER.error("Could not turn the anchor at {} into its own sub-level", pos, e);
        }

        return pos;
    }

    private boolean attachCable(Level level, BlockPos posA, BlockPos posB) {
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            posA = extractAnchorToSubLevel(serverLevel, posA);
            posB = extractAnchorToSubLevel(serverLevel, posB);
        }

        RopeStrandHolderBehavior holderA = RopeItem.getRopeHolder(level, posA);
        RopeStrandHolderBehavior holderB = RopeItem.getRopeHolder(level, posB);

        if (holderA == null || holderB == null) return false;

        if (SteelCableItem.createSteelRope(holderA, holderB)) {
            if (level.getBlockState(posA).getBlock() instanceof AnchorBlock) {
                level.setBlock(posA, level.getBlockState(posA).setValue(HAS_CABLE, true), 3);
            }
            if (level.getBlockState(posB).getBlock() instanceof AnchorBlock) {
                level.setBlock(posB, level.getBlockState(posB).setValue(HAS_CABLE, true), 3);
            }
            holderA.blockEntity.notifyUpdate();
            holderB.blockEntity.notifyUpdate();
            level.playSound(null, posB, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.7F, 0.9F);
            return true;
        }
        return false;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!isMoving && !level.isClientSide()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof AnchorBlockEntity anchorBlockEntity && anchorBlockEntity.getBehavior() != null) {
                    RopeStrandHolderBehavior mine = anchorBlockEntity.getBehavior();
                    RopeStrandHolderBehavior owner = mine.ownsRope() ? mine : findRopeOwner(level, mine);
                    if (owner != null) {
                        ServerRopeStrand strand = owner.getOwnedStrand();
                        owner.destroyRope(null, null, true);
                        if (strand != null) {
                            for (RopeAttachment attachment : strand.getAttachments()) {
                                BlockPos otherPos = attachment.blockAttachment();
                                if (!otherPos.equals(pos) && level.getBlockState(otherPos).getBlock() instanceof AnchorBlock) {
                                    level.setBlock(otherPos, level.getBlockState(otherPos).setValue(HAS_CABLE, false), 3);
                                }
                            }
                        }
                    }
                }
            }
            level.removeBlockEntity(pos);
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    private static RopeStrandHolderBehavior findRopeOwner(Level level, RopeStrandHolderBehavior mine) {
        ServerRopeStrand strand = mine.getAttachedStrand();
        if (strand == null) {
            return null;
        }
        for (RopeAttachment attachment : strand.getAttachments()) {
            BlockEntity other = level.getBlockEntity(attachment.blockAttachment());
            if (other instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity smart) {
                RopeStrandHolderBehavior candidate = smart.getBehaviour(RopeStrandHolderBehavior.TYPE);
                if (candidate != null && candidate != mine && candidate.ownsRope()) {
                    return candidate;
                }
            }
        }
        return null;
    }
}

