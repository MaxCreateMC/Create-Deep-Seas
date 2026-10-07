package com.maxenonyme.AbyssDimension.client.renderer;

import com.maxenonyme.AbyssDimension.CreateAbyss;
import com.maxenonyme.AbyssDimension.client.model.Isopod;
import com.maxenonyme.AbyssDimension.entities.IsopodEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;

public class IsopodRenderer extends MobRenderer<IsopodEntity, Isopod<IsopodEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        CreateAbyss.MOD_ID, "textures/entity/giant_isopod.png");

    public IsopodRenderer(EntityRendererProvider.Context context) {
        super(context, new Isopod<>(context.bakeLayer(Isopod.LAYER_LOCATION)), 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(IsopodEntity entity) {
        return TEXTURE;
    }

    @Override
    protected float getShadowRadius(IsopodEntity entity) {
        return 0.3F * (float)entity.getSize();
    }

    @Override
    protected void scale(IsopodEntity entity, PoseStack poseStack, float partialTick) {
        float size = (float)entity.getSize();
        poseStack.scale(size, size, size);
    }
}
