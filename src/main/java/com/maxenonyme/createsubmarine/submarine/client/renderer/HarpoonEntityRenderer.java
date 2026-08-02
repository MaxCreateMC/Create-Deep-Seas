package com.maxenonyme.createsubmarine.submarine.client.renderer;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.client.model.HarpoonModel;
import com.maxenonyme.createsubmarine.submarine.item.harpoon_gun.HarpoonEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class HarpoonEntityRenderer extends EntityRenderer<HarpoonEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        CreateSubmarine.MOD_ID, "textures/entity/harpoon.png");

    private final HarpoonModel<HarpoonEntity> model;

    public HarpoonEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new HarpoonModel<>(context.bakeLayer(HarpoonModel.LAYER_LOCATION));
    }

    @Override
    public void render(HarpoonEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        float yRot = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        float xRot = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        poseStack.mulPose(Axis.YP.rotationDegrees(yRot - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-xRot));

        float scale = 0.8F;
        poseStack.scale(scale, scale, scale);

        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutout(TEXTURE));
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();

        renderRope(entity, partialTick, poseStack, buffer);
    }

    private void renderRope(HarpoonEntity entity, float partialTick,
                            PoseStack poseStack, MultiBufferSource buffer) {
        Player owner = entity.getOwner() instanceof Player p ? p : null;
        if (owner == null) return;

        Vec3 harpoonPos = entity.getPosition(partialTick);
        Vec3 relative = owner.getPosition(partialTick).add(0, owner.getEyeHeight() * 0.8, 0)
            .subtract(harpoonPos);

        float segments = (float) Math.ceil(relative.length() * 2);
        if (segments < 2) segments = 2;

        VertexConsumer consumer = buffer.getBuffer(RenderType.lineStrip());
        Matrix4f mat = poseStack.last().pose();

        for (int i = 0; i <= (int) segments; i++) {
            float t = i / segments;
            float x = Mth.lerp(t, 0, (float) relative.x);
            float y = Mth.lerp(t, 0, (float) relative.y)
                + (float) (Math.sin(t * Math.PI) * 0.5);
            float z = Mth.lerp(t, 0, (float) relative.z);
            consumer.addVertex(mat, x, y, z)
                .setColor(0.3F, 0.2F, 0.1F, 1.0F)
                .setNormal(0.0F, 1.0F, 0.0F)
                .setLight(15728880);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(HarpoonEntity entity) {
        return TEXTURE;
    }
}
