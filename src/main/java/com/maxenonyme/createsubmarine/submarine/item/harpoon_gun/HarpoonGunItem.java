package com.maxenonyme.createsubmarine.submarine.item.harpoon_gun;

import java.util.function.Consumer;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.item.harpoon_gun.HarpoonEntity;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

public class HarpoonGunItem extends Item {

    private static final int COOLDOWN = 40;

    public HarpoonGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        HarpoonEntity harpoon = new HarpoonEntity(level, player);
        harpoon.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 3.0F, 0.5F);
        level.addFreshEntity(harpoon);

        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void initializeClient(Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(com.simibubi.create.foundation.item.render.SimpleCustomRenderer.create(
            this, new HarpoonGunItemRenderer()));
    }
}
