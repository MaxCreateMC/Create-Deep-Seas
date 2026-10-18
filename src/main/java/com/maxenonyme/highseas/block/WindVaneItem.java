package com.maxenonyme.highseas.block;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class WindVaneItem extends BlockItem {
    private static final int CREATE_YELLOW = 0xEBC255;

    public WindVaneItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (Screen.hasShiftDown()) {
            tooltip.add(Component.empty());
            addLines(tooltip, "item.create_high_seas.wind_vane.tooltip.summary", CREATE_YELLOW);
            addLines(tooltip, "item.create_high_seas.wind_vane.tooltip.behaviour1", CREATE_YELLOW);
            addLines(tooltip, "item.create_high_seas.wind_vane.tooltip.behaviour2", CREATE_YELLOW);
        } else {
            tooltip.add(Component.translatable("create_submarine.tooltip.holdForInfo",
                    Component.translatable("create_submarine.tooltip.keyShift").withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }

    private static void addLines(List<Component> tooltip, String key, int colorHex) {
        for (String line : Component.translatable(key).getString().split("\n")) {
            tooltip.add(Component.literal(line).withStyle(style -> style.withColor(colorHex)));
        }
    }
}
