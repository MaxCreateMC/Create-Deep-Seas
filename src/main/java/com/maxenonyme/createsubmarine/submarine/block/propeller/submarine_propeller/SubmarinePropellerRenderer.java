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
        float angle = 2 * Mth.lerp(partialTicks, be.getPreviousAngle(), be.getAngle()) * Mth.DEG_TO_RAD;
        return angle + getRotationOffsetForPosition(be, be.getBlockPos(), dir.getAxis());
    }

    @Override
    protected void renderSafe(SubmarinePropellerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (VisualizationManager.supportsVisualization(be.getLevel()))
            return;

        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        if (be.isGroupMember())
            return;

        BlockState state = be.getBlockState();
        Direction dir = state.getValue(BlockStateProperties.FACING);
        VertexConsumer vb = buffer.getBuffer(RenderType.solid());
        float angle = getAngle(partialTicks, dir, be);

        ms.pushPose();
        if (be.isGroupMaster()) {
            Direction[] plane = SubmarinePropellerBlockEntity.planeAxes(dir);
            ms.translate((plane[0].getStepX() + plane[1].getStepX()) * 0.5,
                    (plane[0].getStepY() + plane[1].getStepY()) * 0.5,
                    (plane[0].getStepZ() + plane[1].getStepZ()) * 0.5);
            ms.translate(0.5, 0.5, 0.5);
            ms.scale(dir.getAxis() == Direction.Axis.X ? 1 : 2,
                    dir.getAxis() == Direction.Axis.Y ? 1 : 2,
                    dir.getAxis() == Direction.Axis.Z ? 1 : 2);
            ms.translate(-0.5, -0.5, -0.5);
        }
        renderBlades(CachedBuffers.partialFacing(getCurrentModel(be), state), be, dir, angle, light).renderInto(ms, vb);
        renderBlades(CachedBuffers.partialFacing(getContraModel(be), state), be, dir, -angle, light).renderInto(ms, vb);
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
