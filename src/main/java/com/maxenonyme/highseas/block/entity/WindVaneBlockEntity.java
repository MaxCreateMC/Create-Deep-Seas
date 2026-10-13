package com.maxenonyme.highseas.block.entity;

import com.maxenonyme.highseas.CreateHighSeas;
import com.maxenonyme.highseas.client.WindVaneGoggle;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class WindVaneBlockEntity extends BlockEntity implements IHaveGoggleInformation {

    public float renderAngle;
    public boolean hasRenderAngle;
    public long renderLastNanos;

    public WindVaneBlockEntity(BlockPos pos, BlockState state) {
        super(CreateHighSeas.WIND_VANE_BE.get(), pos, state);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Component label = Component.translatable("create_high_seas.gui.goggles.wind_alignment")
                .withStyle(ChatFormatting.GRAY);
        double alignment = WindVaneGoggle.alignment(level, worldPosition);

        if (Double.isNaN(alignment)) {
            tooltip.add(Component.literal("    ").append(label)
                    .append(Component.literal(": —").withStyle(ChatFormatting.DARK_GRAY)));
            return true;
        }

        int percent = (int) Math.round((alignment + 1.0) * 50.0);
        tooltip.add(Component.literal("    ").append(label)
                .append(Component.literal(": " + percent + "%")
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(gradient(percent))))));
        return true;
    }

    private static int gradient(int percent) {
        float t = Mth.clamp(percent / 100.0f, 0.0f, 1.0f);
        int r, g;
        int b = 85;
        if (t < 0.5f) {
            r = 255;
            g = (int) (85 + (t / 0.5f) * (255 - 85));
        } else {
            r = (int) (255 + ((t - 0.5f) / 0.5f) * (85 - 255));
            g = 255;
        }
        return (r << 16) | (g << 8) | b;
    }
}
