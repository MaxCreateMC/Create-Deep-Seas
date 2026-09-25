package com.maxenonyme.createsubmarine.submarine.item;

import com.maxenonyme.createsubmarine.submarine.system.SubmarineInfoCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public class SubmarineStaffItem extends Item {
    public static final int CHARGE_TICKS = 60;

    public SubmarineStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        int elapsed = getUseDuration(stack, entity) - remainingUseDuration;

        if ((elapsed == 20 || elapsed == 40 || elapsed == 60) && !level.isClientSide) {
            float pitch = elapsed == 20 ? 1.0F : elapsed == 40 ? 1.5F : 2.0F;
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 2.0F, pitch);
        }

        if (elapsed >= CHARGE_TICKS) {
            if (entity instanceof ServerPlayer player) {
                SubmarineInfoCommand.findHoles(player.createCommandSourceStack());
            }
            entity.stopUsingItem();
        }
    }
}
