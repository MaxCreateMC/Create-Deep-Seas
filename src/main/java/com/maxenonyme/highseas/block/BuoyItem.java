package com.maxenonyme.highseas.block;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class BuoyItem extends BlockItem {
    private static final int CREATE_YELLOW = 0xEBC255;

    private static final float CYCLE_MILLIS = 2600.0f;
    private static final float HUE_STEP = 0.055f;
    private static final float SATURATION = 0.82f;

    public BuoyItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        String plain = Component.translatable(getDescriptionId(stack)).getString();
        float sweep = System.currentTimeMillis() % (long) CYCLE_MILLIS / CYCLE_MILLIS;
        MutableComponent painted = Component.empty();
        for (int i = 0; i < plain.length(); i++) {
            float hue = sweep - i * HUE_STEP;
            int rgb = Mth.hsvToRgb(hue - Mth.floor(hue), SATURATION, 1.0f);
            painted.append(Component.literal(String.valueOf(plain.charAt(i)))
                    .withStyle(style -> style.withColor(rgb)));
        }
        return painted;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (Screen.hasShiftDown()) {
            tooltip.add(Component.empty());
            addLines(tooltip, "block.create_high_seas.buoy.tooltip.summary", CREATE_YELLOW);
            addLines(tooltip, "block.create_high_seas.buoy.tooltip.behaviour1", CREATE_YELLOW);

            tooltip.add(Component.empty());
            addLines(tooltip, "block.create_high_seas.buoy.tooltip.condition1", ChatFormatting.GRAY.getColor());
            addLines(tooltip, "block.create_high_seas.buoy.tooltip.behaviour2", CREATE_YELLOW);

            tooltip.add(Component.empty());
            addLines(tooltip, "block.create_high_seas.buoy.tooltip.condition2", ChatFormatting.GRAY.getColor());
            addLines(tooltip, "block.create_high_seas.buoy.tooltip.behaviour3", CREATE_YELLOW);

            tooltip.add(Component.empty());
            addLines(tooltip, "block.create_high_seas.buoy.tooltip.condition3", ChatFormatting.GRAY.getColor());
            addLines(tooltip, "block.create_high_seas.buoy.tooltip.behaviour4", CREATE_YELLOW);
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
