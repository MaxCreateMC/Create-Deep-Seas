package com.maxenonyme.createsubmarine.submarine.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import java.util.function.Supplier;

public class OxygenBucketItem extends BucketItem {
    public OxygenBucketItem(Supplier<? extends Fluid> supplier, Item.Properties builder) {
        super(supplier.get(), builder);
    }

    @Override
    public Component getName(ItemStack pStack) {
        return super.getName(pStack).copy().withStyle(ChatFormatting.GOLD);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        BlockHitResult blockhitresult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);

        if (blockhitresult.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(itemstack);
        } else if (blockhitresult.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(itemstack);
        }

        BlockPos blockpos = blockhitresult.getBlockPos();
        Direction direction = blockhitresult.getDirection();
        BlockPos blockpos1 = blockpos.relative(direction);

        if (!level.mayInteract(player, blockpos) || !player.mayUseItemAt(blockpos1, direction, itemstack)) {
            return InteractionResultHolder.fail(itemstack);
        }

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            for (int i = 0; i < 15; i++) {
                double x = blockpos1.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5;
                double y = blockpos1.getY() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5;
                double z = blockpos1.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5;
                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 1, 0, 0.05, 0, 0.05);
            }
        } else {
            level.playLocalSound(blockpos1.getX() + 0.5, blockpos1.getY() + 0.5, blockpos1.getZ() + 0.5,
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
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
