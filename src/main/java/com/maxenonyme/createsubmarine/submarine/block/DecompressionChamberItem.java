package com.maxenonyme.createsubmarine.submarine.block;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class DecompressionChamberItem extends BlockItem {
    public DecompressionChamberItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (Screen.hasShiftDown()) {
            tooltipComponents.add(Component.empty());
            BallastTankItem.addTranslatableLines(tooltipComponents,
                    "item.create_submarine.decompression_chamber.tooltip.summary", 0xEBC255);
            tooltipComponents.add(Component.empty());
            BallastTankItem.addTranslatableLines(tooltipComponents,
                    "item.create_submarine.decompression_chamber.tooltip.condition1", ChatFormatting.GRAY.getColor());
            BallastTankItem.addTranslatableLines(tooltipComponents,
                    "item.create_submarine.decompression_chamber.tooltip.behaviour1", 0xEBC255);
            BallastTankItem.addTranslatableLines(tooltipComponents,
                    "item.create_submarine.decompression_chamber.tooltip.condition2", ChatFormatting.GRAY.getColor());
            BallastTankItem.addTranslatableLines(tooltipComponents,
                    "item.create_submarine.decompression_chamber.tooltip.behaviour2", 0xEBC255);
        } else {
            tooltipComponents.add(Component.translatable("create_submarine.tooltip.holdForInfo",
                    Component.translatable("create_submarine.tooltip.keyShift").withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
