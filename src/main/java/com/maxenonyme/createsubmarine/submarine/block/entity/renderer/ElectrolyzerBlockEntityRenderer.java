package com.maxenonyme.createsubmarine.submarine.block.entity.renderer;

import com.maxenonyme.createsubmarine.submarine.block.ElectrolyzerBlock;
import com.maxenonyme.createsubmarine.submarine.block.entity.ElectrolyzerBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;

public class ElectrolyzerBlockEntityRenderer implements BlockEntityRenderer<ElectrolyzerBlockEntity> {
    public ElectrolyzerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ElectrolyzerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        boolean isPowered = be.getBlockState().getValue(BlockStateProperties.POWERED);
        FluidStack water = be.waterTank.getFluid();

        if (isPowered && !water.isEmpty()) {
            float fillRatio = (float) be.waterTank.getFluidAmount() / be.waterTank.getCapacity();
            if (fillRatio > 0) {
                renderForcedFluid(water, 2.1f / 16f, 13.1f / 16f, 2.1f / 16f, 13.9f / 16f, 13.1f / 16f + (fillRatio * 13.7f / 16f), 13.9f / 16f, ms, buffer, light, overlay);
            }
        }

        BlockState state = be.getBlockState();
        if (state.getValue(ElectrolyzerBlock.ALTERNATOR)) {
            ms.pushPose();
            ms.translate(0.5, 0.5 - 1 / 16f, 0.5);
            ms.scale(1f, 1f, 18 / 16f);
            ms.translate(-0.5, -0.5, -0.5);
            KineticBlockEntityRenderer.renderRotatingKineticBlock(be, KineticBlockEntityRenderer.shaft(Direction.Axis.Z), ms,
                    buffer.getBuffer(RenderType.solid()), light);
            ms.popPose();
        }

        if (AllPartialModels.ELECTROLYZER_GLASS.get() != null) {
            CachedBuffers.partial(AllPartialModels.ELECTROLYZER_GLASS, be.getBlockState())
                    .light(light)
                    .renderInto(ms, buffer.getBuffer(RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS)));
        }
    }

    private void renderForcedFluid(FluidStack fluid, float xMin, float yMin, float zMin, float xMax, float yMax, float zMax, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        IClientFluidTypeExtensions props = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(props.getStillTexture(fluid));
        int color = props.getTintColor(fluid);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        Matrix4f mat = ms.last().pose();

        drawFace(vc, mat, xMin, yMin, zMin, xMax, yMin, zMax, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), r, g, b, a, light, overlay, 0, -1, 0);
        drawFace(vc, mat, xMin, yMax, zMin, xMax, yMax, zMax, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), r, g, b, a, light, overlay, 0, 1, 0);
        drawFace(vc, mat, xMin, yMin, zMin, xMin, yMax, zMax, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), r, g, b, a, light, overlay, -1, 0, 0);
        drawFace(vc, mat, xMax, yMin, zMin, xMax, yMax, zMax, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), r, g, b, a, light, overlay, 1, 0, 0);
        drawFace(vc, mat, xMin, yMin, zMin, xMax, yMax, zMin, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), r, g, b, a, light, overlay, 0, 0, -1);
        drawFace(vc, mat, xMin, yMin, zMax, xMax, yMax, zMax, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), r, g, b, a, light, overlay, 0, 0, 1);
    }

    private void drawFace(VertexConsumer vc, Matrix4f mat, float x1, float y1, float z1, float x2, float y2, float z2, float u1, float v1, float u2, float v2, int r, int g, int b, int a, int light, int overlay, float nx, float ny, float nz) {
        if (nx != 0) {
            addV(vc, mat, x1, y1, z1, u1, v1, r, g, b, a, light, overlay, nx, ny, nz);
            addV(vc, mat, x1, y2, z1, u1, v2, r, g, b, a, light, overlay, nx, ny, nz);
            addV(vc, mat, x1, y2, z2, u2, v2, r, g, b, a, light, overlay, nx, ny, nz);
            addV(vc, mat, x1, y1, z2, u2, v1, r, g, b, a, light, overlay, nx, ny, nz);
        } else if (ny != 0) {
            addV(vc, mat, x1, y1, z1, u1, v1, r, g, b, a, light, overlay, nx, ny, nz);
            addV(vc, mat, x2, y1, z1, u2, v1, r, g, b, a, light, overlay, nx, ny, nz);
            addV(vc, mat, x2, y1, z2, u2, v2, r, g, b, a, light, overlay, nx, ny, nz);
            addV(vc, mat, x1, y1, z2, u1, v2, r, g, b, a, light, overlay, nx, ny, nz);
        } else {
            addV(vc, mat, x1, y1, z1, u1, v1, r, g, b, a, light, overlay, nx, ny, nz);
            addV(vc, mat, x2, y1, z1, u2, v1, r, g, b, a, light, overlay, nx, ny, nz);
            addV(vc, mat, x2, y2, z1, u2, v2, r, g, b, a, light, overlay, nx, ny, nz);
            addV(vc, mat, x1, y2, z1, u1, v2, r, g, b, a, light, overlay, nx, ny, nz);
        }
    }

    private void addV(VertexConsumer vc, Matrix4f mat, float x, float y, float z, float u, float v, int r, int g, int b, int a, int light, int overlay, float nx, float ny, float nz) {
        vc.addVertex(mat, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
    }
}