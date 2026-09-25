package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.block.entity.BuoySeatEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class BuoySeatRenderer extends EntityRenderer<BuoySeatEntity> {

    public BuoySeatRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
        this.shadowStrength = 0.0f;
    }

    @Override
    public boolean shouldRender(BuoySeatEntity entity, Frustum frustum, double x, double y, double z) {
        return false;
    }

    @Override
    public void render(BuoySeatEntity entity, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int light) {
    }

    @Override
    public ResourceLocation getTextureLocation(BuoySeatEntity entity) {
        return null;
    }
}
