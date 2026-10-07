package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.submarine.block.entity.BarometerBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;

import java.util.Arrays;

public class BarometerDisplaySource extends SingleLineDisplaySource {

    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        if (!(context.getSourceBlockEntity() instanceof BarometerBlockEntity be)) {
            return EMPTY_LINE;
        }

        int depth = be.syncedDepth;
        int weakest = be.syncedWeakest;

        if (depth <= 0) {
            return Component.translatable("create_submarine.gui.goggles.barometer.no_pressure")
                    .withStyle(ChatFormatting.GRAY);
        }

        int state = 1;
        if (weakest != -1) {
            if (depth > weakest) {
                state = 3;
            } else if (depth >= weakest * 0.80) {
                state = 2;
            }
        }

        int mode = context.sourceConfig().getInt("DisplayMode");

        MutableComponent dangerText;
        if (state == 3)
            dangerText = Component.translatable("create_submarine.gui.goggles.barometer.critical")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        else if (state == 2)
            dangerText = Component.translatable("create_submarine.gui.goggles.barometer.warning")
                    .withStyle(ChatFormatting.YELLOW);
        else
            dangerText = Component.translatable("create_submarine.gui.goggles.barometer.acceptable")
                    .withStyle(ChatFormatting.GREEN);

        MutableComponent depthText = Component.literal(depth + "m").withStyle(ChatFormatting.AQUA);

        if (mode == 0) {
            return Component.translatable("create_submarine.display_source.depth", depthText);
        } else if (mode == 1) {
            return dangerText;
        } else {
            return Component.literal("").append(depthText)
                    .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY)).append(dangerText);
        }
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return true;
    }

    @Override
    public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder,
            boolean isFirstLine) {
        super.initConfigurationWidgets(context, builder, isFirstLine);
        if (isFirstLine)
            return;

        builder.addSelectionScrollInput(0, 100, (si, label) -> {
            si.forOptions(Arrays.asList(
                    Component.translatable("create_submarine.display_source.mode.depth_only"),
                    Component.translatable("create_submarine.display_source.mode.danger_only"),
                    Component.translatable("create_submarine.display_source.mode.both")))
                    .titled(Component.translatable("create_submarine.display_source.mode.title"));
        }, "DisplayMode");
    }

    @Override
    public Component getName() {
        return Component.translatable("create_submarine.display_source.barometer");
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 20;
    }
}
