package com.maxenonyme.highseas.item;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class FluentWatersDiscItem extends Item {
    private static final int CREATE_YELLOW = 0xEBC255;
    private static final int DEEP_GOLD = 0xC9962E;
    private static final int SILVER = 0xE4E9F0;
    private static final String SONG = "jukebox_song.create_high_seas.hidden_between_fluent_waters";

    public FluentWatersDiscItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(title());
        if (Screen.hasShiftDown()) {
            tooltip.add(Component.empty());
            addLines(tooltip, "item.create_high_seas.music_disc_hidden_between_fluent_waters.tooltip.summary", CREATE_YELLOW);

            tooltip.add(Component.empty());
            addLines(tooltip, "item.create_high_seas.music_disc_hidden_between_fluent_waters.tooltip.condition1",
                    ChatFormatting.GRAY.getColor());
            addLines(tooltip, "item.create_high_seas.music_disc_hidden_between_fluent_waters.tooltip.behaviour1", CREATE_YELLOW);
        } else {
            tooltip.add(Component.translatable("create_submarine.tooltip.holdForInfo",
                    Component.translatable("create_submarine.tooltip.keyShift").withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }

    private static Component title() {
        String text = Component.translatable(SONG).getString();
        double t = Util.getMillis() / 1000.0;
        int len = text.length();
        double head = (t * 14.0) % (len + 16) - 8;
        MutableComponent out = Component.empty();
        for (int i = 0; i < len; i++) {
            int base = mix(DEEP_GOLD, CREATE_YELLOW, 0.5 + 0.5 * Math.sin(i * 0.45 - t * 3.0));
            double d = (i - head) / 3.0;
            int colour = mix(base, SILVER, Math.exp(-d * d));
            out.append(Component.literal(String.valueOf(text.charAt(i))).withStyle(s -> s.withColor(colour)));
        }
        return out;
    }

    private static int mix(int a, int b, double f) {
        int r = (int) Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * f);
        int g = (int) Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * f);
        int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * f);
        return r << 16 | g << 8 | bl;
    }

    private static void addLines(List<Component> tooltip, String key, int colorHex) {
        for (String line : Component.translatable(key).getString().split("\n")) {
            tooltip.add(Component.literal(line).withStyle(style -> style.withColor(colorHex)));
        }
    }
}
