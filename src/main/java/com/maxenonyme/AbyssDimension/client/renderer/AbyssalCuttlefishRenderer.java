package com.maxenonyme.AbyssDimension.client.renderer;

import com.maxenonyme.AbyssDimension.CreateAbyss;
import com.maxenonyme.AbyssDimension.client.model.AbyssalCuttlefish;
import com.maxenonyme.AbyssDimension.entities.AbyssalCuttlefishEntity;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class AbyssalCuttlefishRenderer extends MobRenderer<AbyssalCuttlefishEntity, AbyssalCuttlefish<AbyssalCuttlefishEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        CreateAbyss.MOD_ID, "textures/entity/abyssal_cuttlefish.png");

    public AbyssalCuttlefishRenderer(EntityRendererProvider.Context context) {
        super(context, new AbyssalCuttlefish<>(context.bakeLayer(AbyssalCuttlefish.LAYER_LOCATION)), 0.6F);
    }

    @Override
    public ResourceLocation getTextureLocation(AbyssalCuttlefishEntity entity) {
        return TEXTURE;
    }

    @Override
    protected RenderType getRenderType(AbyssalCuttlefishEntity entity, boolean bodyVisible, boolean translucent, boolean glint) {
        return RenderType.entityTranslucent(TEXTURE);
    }

    @Override
    public void render(AbyssalCuttlefishEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float alpha = entity.camouflageAlpha;
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
