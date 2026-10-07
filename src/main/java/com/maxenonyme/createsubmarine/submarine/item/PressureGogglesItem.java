package com.maxenonyme.createsubmarine.submarine.item;

import com.maxenonyme.createsubmarine.submarine.block.BallastTankItem;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

public class PressureGogglesItem extends GogglesItem {
    private static final String MODE_TAG = "PressureMode";
    private static final int GOLD = 0xEBC255;

    public enum Mode {
        ABSOLUTE, DISTRIBUTION, NONE;

        public String key() {
            return "create_submarine.pressure_goggles.mode." + name().toLowerCase();
        }
    }

    public PressureGogglesItem(Properties properties) {
        super(properties);
    }

    public static boolean isWearing(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof PressureGogglesItem;
    }

    public static Mode mode(ItemStack stack) {
        int i = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(MODE_TAG);
        return Mode.values()[Math.floorMod(i, Mode.values().length)];
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown())
            return super.use(level, player, hand);
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide)
            toggle(player, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown())
            return super.useOn(context);
        if (!context.getLevel().isClientSide)
            toggle(player, context.getItemInHand());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    public static void toggle(Player player, ItemStack stack) {
        Mode next = Mode.values()[(mode(stack).ordinal() + 1) % Mode.values().length];
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(MODE_TAG, next.ordinal()));
        AllSoundEvents.CONFIRM.playOnServer(player.level(), player.blockPosition());
        player.displayClientMessage(Component.translatable("create_submarine.pressure_goggles.switched",
                Component.translatable(next.key()).withStyle(ChatFormatting.WHITE))
                .withStyle(style -> style.withColor(GOLD)), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("create_submarine.pressure_goggles.current",
                Component.translatable(mode(stack).key()).withStyle(style -> style.withColor(GOLD)))
                .withStyle(ChatFormatting.GRAY));
        if (Screen.hasShiftDown()) {
            tooltip.add(Component.empty());
            BallastTankItem.addTranslatableLines(tooltip, "item.create_submarine.pressure_goggles.tooltip.summary", GOLD);
            tooltip.add(Component.empty());
            BallastTankItem.addTranslatableLines(tooltip, "item.create_submarine.pressure_goggles.tooltip.condition1",
                    ChatFormatting.GRAY.getColor());
            BallastTankItem.addTranslatableLines(tooltip, "item.create_submarine.pressure_goggles.tooltip.behaviour1", GOLD);
            BallastTankItem.addTranslatableLines(tooltip, "item.create_submarine.pressure_goggles.tooltip.condition2",
                    ChatFormatting.GRAY.getColor());
            BallastTankItem.addTranslatableLines(tooltip, "item.create_submarine.pressure_goggles.tooltip.behaviour2", GOLD);
            BallastTankItem.addTranslatableLines(tooltip, "item.create_submarine.pressure_goggles.tooltip.condition3",
                    ChatFormatting.GRAY.getColor());
            BallastTankItem.addTranslatableLines(tooltip, "item.create_submarine.pressure_goggles.tooltip.behaviour3", GOLD);
        } else {
            tooltip.add(Component.translatable("create_submarine.tooltip.holdForInfo",
                    Component.translatable("create_submarine.tooltip.keyShift").withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
