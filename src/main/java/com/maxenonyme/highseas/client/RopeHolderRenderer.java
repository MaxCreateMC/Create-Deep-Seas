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
import com.maxenonyme.highseas.block.BoatSailBlock;
import com.maxenonyme.highseas.block.entity.HalyardBlockEntity;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Supplier;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class RopeHolderRenderer<T extends SmartBlockEntity & RopeStrandHolderBlockEntity> implements BlockEntityRenderer<T> {

    private static final float BOLT_ROPE = 4.5f / 3.0f;

    private static final Supplier<BlockState> ROPE_BLOCK = () -> BuiltInRegistries.BLOCK
            .get(ResourceLocation.fromNamespaceAndPath("create", "rope")).defaultBlockState();

    public RopeHolderRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(T be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (be instanceof HalyardBlockEntity bridle && bridle.halyard && !bridle.isRemoved()
                && bridle.getBehavior() != null && bridle.getBehavior().isAttached()
                && be.getLevel() != null && be.getLevel().getBlockState(be.getBlockPos()).getBlock() instanceof BoatSailBlock
                && bridle.halyardFoot(bridle.getBlockState()).distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) < 64 * 64)
            luff(bridle, ms, buffer, light);
        if (be.getBehavior() == null || !be.getBehavior().ownsRope()) {
            return;
        }
        RopeStrandRenderer.render(be, be.getBehavior(), partialTicks, ms, buffer);
    }

    private static void luff(HalyardBlockEntity bridle, PoseStack ms, MultiBufferSource buffer, int light) {
        BlockState state = bridle.getBlockState();
        float reef = bridle.shownReef();
        Vec3 top = bridle.halyardPoint(state, reef);
        segment(bridle.getBlockPos(), bridle.halyardFoot(state), top, BOLT_ROPE, ms, buffer, light);
        segment(bridle.getBlockPos(), top, bridle.halyardAnchor(state, reef), 1.0f, ms, buffer, light);
    }

    private static void segment(BlockPos pos, Vec3 foot, Vec3 top, float width, PoseStack ms, MultiBufferSource buffer, int light) {
        Vec3 span = top.subtract(foot);
        double length = span.length();
        if (length < 0.05)
            return;
        SuperByteBuffer rope = CachedBuffers.partialFacing(SimPartialModels.ROPE, ROPE_BLOCK.get(), Direction.NORTH);
        VertexConsumer vb = buffer.getBuffer(RenderType.solid());
        ms.pushPose();
        ms.translate(foot.x - pos.getX(), foot.y - pos.getY(), foot.z - pos.getZ());
        ms.mulPose(new Quaternionf().rotationTo(new Vector3f(0.0f, 1.0f, 0.0f), span.toVector3f().normalize()));
        ms.scale(width, (float) length, width);
        ms.translate(-0.5, 0.0, -0.5);
        rope.light(light).renderInto(ms, vb);
        ms.popPose();
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
        if (be.getBehavior() != null && be.getBehavior().ownsRope()
                || be instanceof HalyardBlockEntity bridle && bridle.halyard) {
            return AABB.INFINITE;
        }
        return new AABB(be.getBlockPos());
    }
}
