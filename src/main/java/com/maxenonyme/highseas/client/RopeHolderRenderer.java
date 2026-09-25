package com.maxenonyme.highseas.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.RopeStrandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class RopeHolderRenderer<T extends SmartBlockEntity & RopeStrandHolderBlockEntity> implements BlockEntityRenderer<T> {

    public RopeHolderRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(T be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (be.getBehavior() == null || !be.getBehavior().ownsRope()) {
            return;
        }
        RopeStrandRenderer.render(be, be.getBehavior(), partialTicks, ms, buffer);
    }

    @Override
    public boolean shouldRenderOffScreen(T be) {
        return true;
    }

    @Override
    public boolean shouldRender(T be, Vec3 cameraPos) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        if (be.getBehavior() != null && be.getBehavior().ownsRope()) {
            return AABB.INFINITE;
        }
        return new AABB(be.getBlockPos());
    }
}
