package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.submarine.block.entity.CommandSubBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class CommandSubDisplaySource extends SingleLineDisplaySource {

    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        if (!(context.getSourceBlockEntity() instanceof CommandSubBlockEntity be))
            return EMPTY_LINE;
        String[] states = { "drain", "hold", "fill" };
        return Component.translatable("create_submarine.command_sub.status." + states[Math.max(0, Math.min(2, be.syncedStatus))],
                be.syncedDepth, be.targetDepth);
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return true;
    }

    @Override
    public Component getName() {
        return Component.translatable("create_submarine.display_source.command_sub");
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 10;
    }
}
