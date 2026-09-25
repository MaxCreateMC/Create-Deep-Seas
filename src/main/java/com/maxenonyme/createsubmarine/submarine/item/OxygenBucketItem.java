package com.maxenonyme.createsubmarine.submarine.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class OxygenBucketItem extends BucketItem {
    public OxygenBucketItem(java.util.function.Supplier<? extends Fluid> supplier, Item.Properties builder) {
        super(supplier.get(), builder);
    }

    @Override
    public net.minecraft.network.chat.Component getName(ItemStack pStack) {
        return super.getName(pStack).copy().withStyle(net.minecraft.ChatFormatting.GOLD);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        BlockHitResult blockhitresult = getPlayerPOVHitResult(level, player, net.minecraft.world.level.ClipContext.Fluid.NONE);

        if (blockhitresult.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(itemstack);
        } else if (blockhitresult.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(itemstack);
        }

        BlockPos blockpos = blockhitresult.getBlockPos();
        net.minecraft.core.Direction direction = blockhitresult.getDirection();
        BlockPos blockpos1 = blockpos.relative(direction);

        if (!level.mayInteract(player, blockpos) || !player.mayUseItemAt(blockpos1, direction, itemstack)) {
            return InteractionResultHolder.fail(itemstack);
        }

        if (!level.isClientSide && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            for (int i = 0; i < 15; i++) {
                double x = blockpos1.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5;
                double y = blockpos1.getY() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5;
                double z = blockpos1.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5;
                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 1, 0, 0.05, 0, 0.05);
            }
        } else {
            level.playLocalSound(blockpos1.getX() + 0.5, blockpos1.getY() + 0.5, blockpos1.getZ() + 0.5,
                    net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.BLOCKS, 0.5F,
                    2.6F + (level.random.nextFloat() - level.random.nextFloat()) * 0.8F, false);
        }

        if (!player.isCreative()) {
            itemstack.shrink(1);
            if (itemstack.isEmpty()) {
                return InteractionResultHolder.sidedSuccess(new ItemStack(Items.BUCKET), level.isClientSide());
            }
            if (!player.getInventory().add(new ItemStack(Items.BUCKET))) {
                player.drop(new ItemStack(Items.BUCKET), false);
            }
        }

        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }
}
