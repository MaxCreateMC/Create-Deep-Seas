package com.maxenonyme.createsubmarine.submarine.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.Minecraft;
import com.simibubi.create.content.equipment.goggles.GogglesItem;

@EventBusSubscriber(modid = CreateSubmarine.MOD_ID, value = Dist.CLIENT)
public class CreateSubmarineClientEvents {

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (event.getEntity() == null)
            return;
        if (!(event.getEntity() instanceof Player player) || !GogglesItem.isWearingGoggles(player))
            return;
            return;

        if (event.getItemStack().getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            BlockState state = block.defaultBlockState();
            var propOpt = HullStrengthConfig.getFor(state);
            if (propOpt.isPresent()) {
                int maxDepth = propOpt.get().maxWaterDepth();
                if (maxDepth <= 0)
                    return;

                ChatFormatting color;
                String tierKey;
                if (maxDepth < 100) {
                    color = ChatFormatting.RED;
                    tierKey = "create_submarine.gui.goggles.hull_tier.fragile";
                } else if (maxDepth < 250) {
                    color = ChatFormatting.GOLD;
                    tierKey = "create_submarine.gui.goggles.hull_tier.standard";
                } else {
                    color = ChatFormatting.GREEN;
                    tierKey = "create_submarine.gui.goggles.hull_tier.reinforced";
                }

                event.getToolTip()
                        .add(Component.literal(" ")
                                .append(Component.translatable("create_submarine.gui.goggles.max_depth")
                                        .withStyle(ChatFormatting.DARK_GRAY))
                                .append(Component.literal(": " + maxDepth).withStyle(color)));
            }
        }
    }

}
