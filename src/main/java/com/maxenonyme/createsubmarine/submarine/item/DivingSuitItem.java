package com.maxenonyme.createsubmarine.submarine.item;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.BallastTankItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

public class DivingSuitItem extends ArmorItem {
    private static Item backtank;

    private final ResourceLocation texture;

    public DivingSuitItem(Holder<ArmorMaterial> material, Type type, Properties properties, ResourceLocation texture) {
        super(material, type, properties);
        this.texture = texture;
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer,
            boolean innerModel) {
        return texture;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String key = getDescriptionId() + ".tooltip.";
        if (Screen.hasShiftDown()) {
            tooltip.add(Component.empty());
            BallastTankItem.addTranslatableLines(tooltip, key + "summary", 0xEBC255);
            for (int i = 1; Language.getInstance().has(key + "condition" + i); i++) {
                tooltip.add(Component.empty());
                BallastTankItem.addTranslatableLines(tooltip, key + "condition" + i, ChatFormatting.GRAY.getColor());
                BallastTankItem.addTranslatableLines(tooltip, key + "behaviour" + i, 0xEBC255);
            }
        } else {
            tooltip.add(Component.translatable("create_submarine.tooltip.holdForInfo",
                    Component.translatable("create_submarine.tooltip.keyShift").withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }

    public static List<ItemStack> carriedBacktanks(LivingEntity entity) {
        if (!(entity instanceof Player player) || !player.getItemBySlot(EquipmentSlot.CHEST)
                .is(CreateSubmarine.DIVING_CHESTPLATE.get()))
            return List.of();
        if (backtank == null)
            backtank = BuiltInRegistries.ITEM
                    .get(ResourceLocation.fromNamespaceAndPath("create", "copper_backtank"));
        List<ItemStack> carried = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(backtank))
                carried.add(stack);
        }
        if (player.getOffhandItem().is(backtank))
            carried.add(player.getOffhandItem());
        return carried;
    }
}
