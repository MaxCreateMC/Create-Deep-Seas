package com.maxenonyme.AbyssDimension.client.renderer;

import com.maxenonyme.AbyssDimension.CreateAbyss;
import com.maxenonyme.AbyssDimension.client.model.MagmaticSnail;
import com.maxenonyme.AbyssDimension.entities.MagmaticSnailEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class MagmaticSnailRenderer extends MobRenderer<MagmaticSnailEntity, MagmaticSnail<MagmaticSnailEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        CreateAbyss.MOD_ID, "textures/entity/magmatic_snail.png");

    public MagmaticSnailRenderer(EntityRendererProvider.Context context) {
        super(context, new MagmaticSnail<>(context.bakeLayer(MagmaticSnail.LAYER_LOCATION)), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(MagmaticSnailEntity entity) {
        return TEXTURE;
    }
}
