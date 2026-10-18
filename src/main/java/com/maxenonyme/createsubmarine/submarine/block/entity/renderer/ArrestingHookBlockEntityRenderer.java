package com.maxenonyme.createsubmarine.submarine.block.entity.renderer;

import com.maxenonyme.createsubmarine.submarine.block.ArrestingHookBlock;
import com.maxenonyme.createsubmarine.submarine.block.entity.ArrestingHookBlockEntity;
import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

public class ArrestingHookBlockEntityRenderer implements BlockEntityRenderer<ArrestingHookBlockEntity> {

    private static final float PIVOT_X = 8f / 16f;
    private static final float PIVOT_Y = 3f / 16f;
    private static final float PIVOT_Z = 8f / 16f;

 
    private static final float DEGREES_PER_SECOND =
            Math.abs(ArrestingHookBlockEntity.ACTIVE_ANGLE - ArrestingHookBlockEntity.REST_ANGLE) / 0.5f;
    private static final double MAX_FRAME_SECONDS = 0.1;

    public ArrestingHookBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ArrestingHookBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(ArrestingHookBlock.FACING))
            return;

        advanceArm(be, state);

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(mountRotation(state.getValue(ArrestingHookBlock.FACING), state.getValue(ArrestingHookBlock.CEILING)));
        ms.translate(-0.5, -0.5, -0.5);
        ms.translate(PIVOT_X, PIVOT_Y, PIVOT_Z);
        ms.mulPose(Axis.XP.rotationDegrees(be.clientArmAngle));
        ms.translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z);

        CachedBuffers.partial(AllPartialModels.ARRESTING_HOOK_ARM, state)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS)));

        ms.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(ArrestingHookBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(2.0);
    }

    private void advanceArm(ArrestingHookBlockEntity be, BlockState state) {
        long now = System.nanoTime();
        long last = be.clientLastNanos;
        be.clientLastNanos = now;

        float target = ArrestingHookBlockEntity.targetAngle(state);
        if (last == 0L) {
            be.clientArmAngle = target;
            return;
        }

        float frameSeconds = (float) Math.min((now - last) / 1_000_000_000.0, MAX_FRAME_SECONDS);
        float step = DEGREES_PER_SECOND * frameSeconds;
        float diff = target - be.clientArmAngle;
        if (Math.abs(diff) <= step) {
            be.clientArmAngle = target;
        } else {
            be.clientArmAngle += Math.signum(diff) * step;
        }
    }

    private static Quaternionf mountRotation(Direction facing, boolean ceiling) {
        int y = switch (facing) {
            case SOUTH -> 180;
            case EAST -> 90;
            case WEST -> 270;
            default -> 0;
        };
        int x = 0;
        if (ceiling) {
            x = 180;
            y = (y + 180) % 360;
        }
        return new Quaternionf().rotateYXZ((float) Math.toRadians(-y), (float) Math.toRadians(-x), 0f);
    }
}
