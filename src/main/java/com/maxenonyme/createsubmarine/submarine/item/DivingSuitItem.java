package com.maxenonyme.createsubmarine.submarine.item;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;

public class DivingSuitItem extends ArmorItem {
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
}
