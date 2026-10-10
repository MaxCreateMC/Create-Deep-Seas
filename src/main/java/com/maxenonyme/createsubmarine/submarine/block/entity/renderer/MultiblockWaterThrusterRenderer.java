package com.maxenonyme.createsubmarine.submarine.block.entity.renderer;

import com.maxenonyme.createsubmarine.submarine.block.entity.WaterThrusterBlockEntity;
import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Renders the multiblock model (static base + spinning propeller) for a water
 * thruster that is the corner of a formed 2x2 / 3x3 arrangement. Single thrusters
 * keep their normal blockstate model; formed multiblocks hide their members and
 * only the corner draws here.
 */
public class MultiblockWaterThrusterRenderer implements BlockEntityRenderer<WaterThrusterBlockEntity> {

    private static final PartialModel[] BASES = {null, null, AllPartialModels.WATER_THRUSTER_2X2_BASE, AllPartialModels.WATER_THRUSTER_3X3_BASE};
    private static final PartialModel[] PROPS = {null, null, AllPartialModels.WATER_THRUSTER_2X2_PROP, AllPartialModels.WATER_THRUSTER_3X3_PROP};
    /**
     * Propeller axle positions in BLOCK units (px/16), read off the converted source
     * models: 2x2 prop axle (9.114, 3.445, 7.5) px, 3x3 prop axle (6.123, 4.621, 8.987) px.
     */
    private static final float[] AXLE_X = {0f, 0f, 9.114f / 16f, 6.123f / 16f};
    private static final float[] AXLE_Y = {0f, 0f, 3.445f / 16f, 4.621f / 16f};
    private static final float[] AXLE_Z = {0f, 0f, 7.500f / 16f, 8.987f / 16f};

    public MultiblockWaterThrusterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(WaterThrusterBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        final int w = be.getMultiblockWidth();
        if (w < 2 || w > 3) return;
        if (!be.isMultiblockCorner()) return;

        final BlockState state = be.getBlockState();
        final PartialModel base = BASES[w];
        final PartialModel prop = PROPS[w];
        if (base == null) return;

        final Direction facing = state.getValue(BlockStateProperties.FACING);
        // Baked model geometry is in BLOCK units at render time (the JSON is in
        // pixels but the model baker converts it), so one block = 1.0 here. The axle
        // sits at (axleX, axleY, axleZ) in that space and scaling by w puts the disc
        // centre at axle*w, which is the footprint-relative position to spin about.
        final float cx = AXLE_X[w];
        final float cz = AXLE_Z[w];
        final float hubY = AXLE_Y[w] > 0 ? AXLE_Y[w] : 0.25f;

        final VertexConsumer vb = buffer.getBuffer(RenderType.cutoutMipped());

        // static housing
        final SuperByteBuffer baseBuffer = CachedBuffers.partial(base, state);
        ms.pushPose();
        applyFacingRotation(ms, facing);
        ms.scale(w, w, w);
        baseBuffer.light(light).overlay(overlay).renderInto(ms, vb);
        ms.popPose();

        // spinning propeller, pivoted about its own hub in model space
        if (prop != null) {
            final float time = (be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 360)) + partialTicks;
            // thrust magnitude is server state; on the client a formed thruster spins at
            // full speed, otherwise idle.
            final boolean active = be.isActive()
                    || (be.getLevel() != null && be.getLevel().isClientSide() && w > 0);
            final float angle = time * (active ? 7.0f : 0.6f);

            final SuperByteBuffer propBuffer = CachedBuffers.partial(prop, state);
            // Geometry is in block units at render time; the axle sits at
            // (0.5, hubY, 0.5) and scaling by w puts it at the footprint centre,
            // so the pivot must be expressed in that scaled space.
            final float px = cx * w, py = hubY * w, pz = cz * w;
            ms.pushPose();
            ms.translate(px, py, pz);
            applyFacingRotation(ms, facing);
            ms.mulPose(Axis.YP.rotationDegrees(angle));
            ms.translate(-px, -py, -pz);
            ms.scale(w, w, w);
            propBuffer.light(light).overlay(overlay).renderInto(ms, vb);
            ms.popPose();
        }
    }

    /**
     * Rotates the model (authored facing UP, exhaust along +Y) so the exhaust points
     * along the block's facing direction.
     */
    private static void applyFacingRotation(PoseStack ms, Direction facing) {
        switch (facing) {
            case UP -> { }
            case DOWN -> ms.mulPose(Axis.XP.rotationDegrees(180));
            case NORTH -> ms.mulPose(Axis.XP.rotationDegrees(-90));
            case SOUTH -> ms.mulPose(Axis.XP.rotationDegrees(90));
            case EAST -> ms.mulPose(Axis.ZP.rotationDegrees(-90));
            case WEST -> ms.mulPose(Axis.ZP.rotationDegrees(90));
        }
    }
}
