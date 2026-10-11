package com.maxenonyme.createsubmarine.submarine.block.propeller.submarine_propeller;

import dev.eriksonn.aeronautics.content.blocks.propeller.small.BasePropellerBlock;
import dev.eriksonn.aeronautics.content.blocks.propeller.small.BasePropellerBlockEntity;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.BallastTankItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import java.util.List;

public class SubmarinePropellerBlock extends BasePropellerBlock {
    public SubmarinePropellerBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends BasePropellerBlockEntity> getBlockEntityType() {
        return CreateSubmarine.SUBMARINE_PROPELLER_BE.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (Screen.hasShiftDown()) {
            tooltip.add(Component.empty());
            BallastTankItem.addTranslatableLines(tooltip, "item.create_submarine.submarine_propeller.tooltip.summary", 0xEBC255);
        } else {
            tooltip.add(Component.translatable("create_submarine.tooltip.holdForInfo",
                Component.translatable("create_submarine.tooltip.keyShift").withStyle(ChatFormatting.GRAY))
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
