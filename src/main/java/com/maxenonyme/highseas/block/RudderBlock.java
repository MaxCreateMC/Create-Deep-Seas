package com.maxenonyme.highseas.block;

import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Block;
import net.createmod.catnip.math.VoxelShaper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.decoration.copycat.WaterloggedCopycatBlock;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import com.maxenonyme.highseas.CreateHighSeas;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public class RudderBlock extends WaterloggedCopycatBlock implements BlockSubLevelLiftProvider {
    private static final int placementHelperId = PlacementHelpers.register(new RudderPlacementHelper(RudderBlock::checkItem, RudderBlock::checkState));
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final VoxelShaper SHAPE = VoxelShaper.forAxis(Block.box(0, 6, 0, 16, 10, 16), Direction.Axis.Y);

    public RudderBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(AXIS, Direction.Axis.Y));
    }

    private static boolean checkItem(ItemStack i) {
        return i.getItem() instanceof BlockItem bi && bi.getBlock() instanceof RudderBlock;
    }

    private static boolean checkState(BlockState state) {
        return state.getBlock() instanceof RudderBlock;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        IPlacementHelper placementHelper = PlacementHelpers.get(placementHelperId);
        if (placementHelper.matchesItem(heldItem)) {
            placementHelper.getOffset(player, level, blockState, blockPos, blockHitResult).placeInWorld(level, (BlockItem) heldItem.getItem(), player, interactionHand, blockHitResult);
            return ItemInteractionResult.SUCCESS;
        }
        
        ItemInteractionResult result = super.useItemOn(itemStack, blockState, level, blockPos, player, interactionHand, blockHitResult);

        return result;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder.add(AXIS));
    }

    @Override
    public BlockEntityType<? extends CopycatBlockEntity> getBlockEntityType() {
        return CreateHighSeas.RUDDER_COPYCAT_BE.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return CreateHighSeas.RUDDER_COPYCAT_BE.get().create(pPos, pState);
    }

    @Override
    public boolean canConnectTexturesToward(BlockAndTintGetter reader, BlockPos fromPos, BlockPos toPos, BlockState state) {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return this.defaultBlockState().setValue(AXIS, pContext.getNearestLookingDirection().getAxis());
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext ctx) {
        return SHAPE.get(pState.getValue(AXIS));
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
            this.bounce(entity);
        }
    }

    private void bounce(Entity pEntity) {
        Vec3 Vec3 = pEntity.getDeltaMovement();
        if (Vec3.y < 0.0D) {
            double d0 = pEntity instanceof LivingEntity ? 1.0D : 0.8D;
            pEntity.setDeltaMovement(Vec3.x, -Vec3.y * 0.26F * d0, Vec3.z);
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
    public @NotNull Direction sable$getNormal(BlockState blockState) {
        return Direction.get(Direction.AxisDirection.POSITIVE, blockState.getValue(RudderBlock.AXIS));
    }

    @Override
    public void sable$contributeLiftAndDrag(LiftProviderContext ctx, ServerSubLevel subLevel,
                                            @NotNull Pose3d localPose, double timeStep,
                                            Vector3dc linearVelocity, Vector3dc angularVelocity,
                                            Vector3d linearImpulse, Vector3d angularImpulse,
                                            @Nullable LiftProviderGroup group) {
        BlockSubLevelLiftProvider.resetVectors();
        LevelPlot plot = subLevel.getPlot();
        if (plot != null) {
            BoundingBox3ic bb = plot.getBoundingBox();
            long volume = (long) (bb.maxX() - bb.minX() + 1) * (bb.maxY() - bb.minY() + 1) * (bb.maxZ() - bb.minZ() + 1);
            if (volume < 6)
                return;
        }
        LIFT_NORMAL.set(ctx.dir().x(), ctx.dir().y(), ctx.dir().z());
        LIFT_POS.set(ctx.pos().getX() + 0.5, ctx.pos().getY() + 0.5, ctx.pos().getZ() + 0.5);

        localPose.transformNormal(LIFT_NORMAL);
        localPose.transformPosition(LIFT_POS);

        Pose3d pose = subLevel.logicalPose();

        pose.transformPosition(LIFT_POS, TEMP);
        BlockPos globalPos = BlockPos.containing(TEMP.x(), TEMP.y(), TEMP.z());

        if (!subLevel.getLevel().getFluidState(globalPos).is(FluidTags.WATER)) {
            BlockSubLevelLiftProvider.resetVectors();
            return;
        }

        double pressure = 50.0;
        TEMP.sub(pose.position());
        LIFT_VELO.set(linearVelocity);
        pose.transformNormalInverse(LIFT_VELO);

        LIFT_FORCE.zero();

        if (this.sable$getParallelDragScalar() > 0) {
            double dragStrength = LIFT_NORMAL.dot(LIFT_VELO) * this.sable$getParallelDragScalar() * pressure * timeStep;
            Vector3d parallelDrag = LIFT_NORMAL.mul(dragStrength, DRAG);
            LIFT_FORCE.add(parallelDrag);

            if (group != null) {
                group.totalDrag().sub(parallelDrag);
                group.dragCenter().fma(Math.abs(dragStrength), LIFT_POS);
                group.totalDragStrength += Math.abs(dragStrength);
            }
        }

        if (this.sable$getDirectionlessDragScalar() > 0) {
            double dragStrength = this.sable$getDirectionlessDragScalar() * pressure * timeStep;
            Vector3d directionlessDrag = LIFT_VELO.mul(dragStrength, TEMP);
            LIFT_FORCE.add(directionlessDrag);

            if (group != null) {
                group.totalDrag().sub(directionlessDrag);
                group.dragCenter().fma(directionlessDrag.length(), LIFT_POS);
                group.totalDragStrength += directionlessDrag.length();
            }
        }

        if (this.sable$getLiftScalar() > 0) {
            double liftStrength = LIFT_VELO.sub(DRAG, TEMP).length() * this.sable$getLiftScalar() * pressure * timeStep;
            Vector3d lift = LIFT_NORMAL.mul(liftStrength, TEMP);
            LIFT_FORCE.add(lift);

            if (group != null) {
                group.totalLift().sub(lift);
                group.liftCenter().fma(Math.abs(liftStrength), LIFT_POS);
                group.totalLiftStrength += liftStrength;
            }
        }

        linearImpulse.sub(LIFT_FORCE);
        LIFT_POS.sub(subLevel.getMassTracker().getCenterOfMass(), TEMP);
        angularImpulse.sub(TEMP.cross(LIFT_FORCE));
        BlockSubLevelLiftProvider.resetVectors();
    }
}
