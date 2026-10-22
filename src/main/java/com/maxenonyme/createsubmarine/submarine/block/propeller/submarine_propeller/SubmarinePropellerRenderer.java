package com.maxenonyme.createsubmarine.submarine.block.propeller.submarine_propeller;

import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Quaternionf;

import static dev.eriksonn.aeronautics.content.blocks.propeller.small.BasePropellerBlock.REVERSED;

public class SubmarinePropellerRenderer extends KineticBlockEntityRenderer<SubmarinePropellerBlockEntity> {

    public SubmarinePropellerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    public PartialModel getCurrentModel(SubmarinePropellerBlockEntity be) {
        return be.getBlockState().getValue(REVERSED) ? AllPartialModels.SUBMARINE_PROPELLER_REVERSED : AllPartialModels.SUBMARINE_PROPELLER;
    }

    public PartialModel getContraModel(SubmarinePropellerBlockEntity be) {
        return be.getBlockState().getValue(REVERSED) ? AllPartialModels.SUBMARINE_PROPELLER_REVERSED_CONTRA : AllPartialModels.SUBMARINE_PROPELLER_CONTRA;
    }

    @Override
    protected SuperByteBuffer getRotatedModel(SubmarinePropellerBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(com.simibubi.create.AllPartialModels.SHAFT_HALF, state,
                state.getValue(BlockStateProperties.FACING).getOpposite());
    }

    public float getAngle(float partialTicks, Direction dir, SubmarinePropellerBlockEntity be) {
        SubmarinePropellerBlockEntity source = be.clusterDriver();
        float angle = 2 * Mth.lerp(partialTicks, source.getPreviousAngle(), source.getAngle()) * Mth.DEG_TO_RAD;
        return angle + getRotationOffsetForPosition(be, be.getBlockPos(), dir.getAxis());
    }

    @Override
    protected void renderSafe(SubmarinePropellerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (VisualizationManager.supportsVisualization(be.getLevel()))
            return;

        int size = be.getClusterSize();
        if (size != 0) {
            if (size == 2 || be.isGroupMaster())
                super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
            if (be.isGroupMaster()) {
                renderCluster(be, size, partialTicks, ms, buffer, light);
            }
            return;
        }

        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        BlockState state = be.getBlockState();
        Direction dir = state.getValue(BlockStateProperties.FACING);
        VertexConsumer vb = buffer.getBuffer(RenderType.solid());
        float angle = getAngle(partialTicks, dir, be);
        renderBlades(CachedBuffers.partialFacing(getCurrentModel(be), state), be, dir, angle, light).renderInto(ms, vb);
        renderBlades(CachedBuffers.partialFacing(getContraModel(be), state), be, dir, -angle, light).renderInto(ms, vb);
    }

    private void renderCluster(SubmarinePropellerBlockEntity be, int size, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light) {
        BlockState state = be.getBlockState();
        Direction dir = state.getValue(BlockStateProperties.FACING);
        Quaternionf facing = dir.getRotation();
        float angle = getAngle(partialTicks, dir, be);
        Direction axis = Direction.get(Direction.AxisDirection.POSITIVE, dir.getAxis());

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        float halfWidth = 0.5f;
        if (size == 2) {
            Direction[] plane = SubmarinePropellerBlockEntity.planeAxes(dir);
            ms.translate((plane[0].getStepX() + plane[1].getStepX()) * 0.5,
                    (plane[0].getStepY() + plane[1].getStepY()) * 0.5,
                    (plane[0].getStepZ() + plane[1].getStepZ()) * 0.5);
            halfWidth = 1.0f;
        }

        ms.pushPose();
        ms.mulPose(facing);
        ms.translate(-halfWidth, -0.5f, -halfWidth);
        CachedBuffers.partial(size == 2 ? AllPartialModels.SUBMARINE_PROPELLER_2X2_BASE : AllPartialModels.SUBMARINE_PROPELLER_3X3_BASE, state)
                .light(light).renderInto(ms, buffer.getBuffer(size == 2 ? RenderType.cutout() : RenderType.solid()));
        ms.popPose();

        VertexConsumer vb = buffer.getBuffer(RenderType.solid());

        ms.mulPose(new Quaternionf().rotateAxis(angle, axis.step()).mul(facing));
        ms.translate(0, -0.5, 0);
        if (size == 2) {
            CachedBuffers.partial(AllPartialModels.SUBMARINE_PROPELLER_2X2_BLADES, state).light(light).renderInto(ms, vb);
        } else {
            CachedBuffers.partial(AllPartialModels.SUBMARINE_PROPELLER_3X3_GEAR, state).light(light).renderInto(ms, vb);
            CachedBuffers.partial(AllPartialModels.SUBMARINE_PROPELLER_3X3_BLADES, state).light(light).renderInto(ms, vb);
        }
        ms.popPose();
    }

    private SuperByteBuffer renderBlades(SuperByteBuffer blades, SubmarinePropellerBlockEntity be, Direction dir, float angle, int light) {
        kineticRotationTransform(blades, be, dir.getAxis(), angle, light);
        if (dir.getAxis().isHorizontal())
            blades.rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(dir.getOpposite())), Direction.UP);
        if (dir.getAxis().isVertical())
            blades.rotateCentered(AngleHelper.rad(AngleHelper.verticalAngle(dir.getOpposite())), Direction.EAST);
        blades.translate(0, 0, -3 / 16f).rotateCentered(AngleHelper.rad(-90 - AngleHelper.verticalAngle(dir)), Direction.EAST);
        return blades;
    }
}
