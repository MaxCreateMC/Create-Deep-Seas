package com.maxenonyme.AbyssDimension.client.renderer;

import com.maxenonyme.AbyssDimension.CreateAbyss;
import com.maxenonyme.AbyssDimension.client.model.PelicanEel;
import com.maxenonyme.AbyssDimension.entities.PelicanEelEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class PelicanEelRenderer extends MobRenderer<PelicanEelEntity, PelicanEel<PelicanEelEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        CreateAbyss.MOD_ID, "textures/entity/pelican_eel.png");

    public PelicanEelRenderer(EntityRendererProvider.Context context) {
        super(context, new PelicanEel<>(context.bakeLayer(PelicanEel.LAYER_LOCATION)), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(PelicanEelEntity entity) {
        return TEXTURE;
    }
}
