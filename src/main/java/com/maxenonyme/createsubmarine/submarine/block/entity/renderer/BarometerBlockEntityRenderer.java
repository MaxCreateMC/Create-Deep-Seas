package com.maxenonyme.createsubmarine.submarine.block.entity.renderer;

import com.maxenonyme.createsubmarine.submarine.block.BarometerBlock;
import com.maxenonyme.createsubmarine.submarine.block.entity.BarometerBlockEntity;
import com.maxenonyme.createsubmarine.submarine.client.DeviceGlitch;
import net.minecraft.core.Direction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.entity.animal.Pufferfish;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;

public class BarometerBlockEntityRenderer implements BlockEntityRenderer<BarometerBlockEntity> {
    public BarometerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BarometerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        Direction facing = be.getBlockState().hasProperty(BarometerBlock.FACING) ? be.getBlockState().getValue(BarometerBlock.FACING) : Direction.NORTH;
        float f = facing.toYRot();

        ms.pushPose();
        ms.translate(0.5f, 5.01f / 16f, 0.5f);
        ms.mulPose(Axis.YP.rotationDegrees(-f));

        // Sand
        ms.pushPose();
        ms.translate(0, 0.05f / 2f, 0);
        ms.scale(0.99f, 0.05f, 0.99f);
        ms.translate(-0.5f, -0.5f, -0.5f);
        net.createmod.catnip.render.CachedBuffers.block(Blocks.SAND.defaultBlockState())
            .light(light)
            .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
        ms.popPose();

        // Stone
        ms.pushPose();
        ms.translate(-0.3f, 0.05f, 0.1f);
        ms.scale(0.15f, 0.10f, 0.15f);
        ms.mulPose(Axis.YP.rotationDegrees(45f));
        ms.translate(-0.5f, 0, -0.5f);
        net.createmod.catnip.render.CachedBuffers.block(Blocks.STONE.defaultBlockState())
            .light(light)
            .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
        ms.popPose();

        // Seagrass
        ms.pushPose();
        ms.translate(-0.25f, 0.05f, 0.25f);
        ms.scale(0.4f, 0.5f, 0.4f);
        ms.translate(-0.5f, 0, -0.5f);
        net.createmod.catnip.render.CachedBuffers.block(Blocks.SEAGRASS.defaultBlockState())
            .light(light)
            .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
        ms.popPose();

        ms.pushPose();
        ms.translate(0.3f, 0.05f, 0.3f);
        ms.scale(0.3f, 0.4f, 0.3f);
        ms.translate(-0.5f, 0, -0.5f);
        net.createmod.catnip.render.CachedBuffers.block(Blocks.SEAGRASS.defaultBlockState())
            .light(light)
            .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
        ms.popPose();

        ms.pushPose();
        ms.translate(0.2f, 0.05f, -0.2f);
        ms.scale(0.35f, 0.35f, 0.35f);
        ms.translate(-0.5f, 0, -0.5f);
        net.createmod.catnip.render.CachedBuffers.block(Blocks.SEAGRASS.defaultBlockState())
            .light(light)
            .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
        ms.popPose();

        ms.popPose();

        Pufferfish pufferfish = be.getPufferfish();
        if (pufferfish != null) {
            boolean isCritical = be.syncedDepth > 0 && be.syncedWeakest != -1 && be.syncedDepth > be.syncedWeakest;
            float pufferScale = isCritical ? 0.65f : 0.5f;

            ms.pushPose();
            ms.translate(0.5f, 6.5f / 16f, 0.5f);
            ms.mulPose(Axis.YP.rotationDegrees(-f));
            ms.scale(pufferScale, pufferScale, pufferScale);

            float time = be.getLevel() != null ? be.getLevel().getGameTime() + partialTicks : 0;
            ms.translate(0, Mth.sin(time * 0.05f) * 0.05f, 0);

            float glitch = DeviceGlitch.at(be.getLevel(), be.getBlockPos());
            if (glitch > 0f)
                ms.mulPose(Axis.YP.rotationDegrees(glitch * DeviceGlitch.spinDegrees(partialTicks) * 1.5f));
            ms.mulPose(Axis.XP.rotationDegrees(Mth.sin(time * 0.15f) * 25f));
            ms.mulPose(Axis.YP.rotationDegrees(Mth.cos(time * 0.11f) * 25f));
            ms.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 0.17f) * 25f));

            try {
                Minecraft.getInstance().getEntityRenderDispatcher().render(pufferfish, 0, 0, 0, 0, partialTicks, ms, buffer, light);
            } catch (RuntimeException e) {
            }
            ms.popPose();
        }
        


        FluidStack water = new FluidStack(Fluids.WATER, 1000);
        renderForcedFluid(water, 0.01f / 16f, 4.01f / 16f, 0.01f / 16f, 15.99f / 16f, 15.99f / 16f, 15.99f / 16f, ms, buffer, light, overlay);
    }

    private void renderForcedFluid(FluidStack fluid, float xMin, float yMin, float zMin, float xMax, float yMax, float zMax, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        IClientFluidTypeExtensions props = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(props.getStillTexture(fluid));
        int color = props.getTintColor(fluid);
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF, a = (color >> 24) & 0xFF;

        Matrix4f mat = ms.last().pose();
        float u0 = sprite.getU0(), v0 = sprite.getV0(), u1 = sprite.getU1(), v1 = sprite.getV1();

        addQuad(vc, mat, xMin, yMax, zMax, xMax, yMax, zMax, xMax, yMax, zMin, xMin, yMax, zMin, u0, v1, u1, v1, u1, v0, u0, v0, r, g, b, a, light, overlay, 0, 1, 0);
        addQuad(vc, mat, xMin, yMin, zMin, xMax, yMin, zMin, xMax, yMin, zMax, xMin, yMin, zMax, u0, v0, u1, v0, u1, v1, u0, v1, r, g, b, a, light, overlay, 0, -1, 0);
        addQuad(vc, mat, xMax, yMax, zMin, xMax, yMin, zMin, xMin, yMin, zMin, xMin, yMax, zMin, u0, v0, u0, v1, u1, v1, u1, v0, r, g, b, a, light, overlay, 0, 0, -1);
        addQuad(vc, mat, xMin, yMax, zMax, xMin, yMin, zMax, xMax, yMin, zMax, xMax, yMax, zMax, u0, v0, u0, v1, u1, v1, u1, v0, r, g, b, a, light, overlay, 0, 0, 1);
        addQuad(vc, mat, xMin, yMax, zMin, xMin, yMin, zMin, xMin, yMin, zMax, xMin, yMax, zMax, u0, v0, u0, v1, u1, v1, u1, v0, r, g, b, a, light, overlay, -1, 0, 0);
        addQuad(vc, mat, xMax, yMax, zMax, xMax, yMin, zMax, xMax, yMin, zMin, xMax, yMax, zMin, u0, v0, u0, v1, u1, v1, u1, v0, r, g, b, a, light, overlay, 1, 0, 0);
    }

    private void addQuad(VertexConsumer vc, Matrix4f mat, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float u1, float v1, float u2, float v2, float u3, float v3, float u4, float v4, int r, int g, int b, int a, int light, int overlay, float nx, float ny, float nz) {
        vc.addVertex(mat, x4, y4, z4).setColor(r, g, b, a).setUv(u4, v4).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
        vc.addVertex(mat, x3, y3, z3).setColor(r, g, b, a).setUv(u3, v3).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
        vc.addVertex(mat, x2, y2, z2).setColor(r, g, b, a).setUv(u2, v2).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
    }
}
