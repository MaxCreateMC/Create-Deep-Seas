package com.maxenonyme.createsubmarine.submarine.block;

import com.maxenonyme.createsubmarine.submarine.block.entity.BarometerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class BarometerBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public BarometerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BarometerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide)
            return null;
        return (lvl, p, st, be) -> {
            if (be instanceof BarometerBlockEntity bbe) {
                bbe.tick();
            }
        };
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos,
            RandomSource random) {
        if (random.nextFloat() < 0.40f) {
            Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;

            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT,
                    SoundSource.BLOCKS,
                    2.0F, 0.8F + random.nextFloat() * 0.4F, false);

            int count = random.nextInt(3) + 2;
            for (int i = 0; i < count; i++) {
                double x = pos.getX() + 0.5 + facing.getStepX() * 0.15 + (random.nextDouble() - 0.5) * 0.15;
                double y = pos.getY() + 6.5 / 16.0 + random.nextDouble() * 0.2;
                double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.15 + (random.nextDouble() - 0.5) * 0.15;

                double speedY = 0.01 + random.nextDouble() * 0.02;

                level.addParticle(ParticleTypes.BUBBLE, x, y, z, 0.0, speedY, 0.0);
            }
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack,
            BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        if (stack.is(Items.NAME_TAG)
                && stack.has(DataComponents.CUSTOM_NAME)) {
            if (level.getBlockEntity(pos) instanceof BarometerBlockEntity be) {
                be.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }
}
