package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.block.BoatEngineBlock;
import com.maxenonyme.highseas.block.entity.BoatEngineBlockEntity;
import com.maxenonyme.highseas.helm.HelmClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;

public class BoatEngineRenderer implements BlockEntityRenderer<BoatEngineBlockEntity> {

    private static final float PROP_IDLE_SPEED = 0.3f;
    private static final float PROP_MAX_SPEED = 3.5f;
    private static final float MAX_LEVER_RAD = (float) Math.toRadians(20.0);
    private static final float STEER_RESPONSE = 10.0f;
    private static final float MAX_FRAME_SECONDS = 0.1f;

    public BoatEngineRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static boolean flywheelHandlesIt(BoatEngineBlockEntity be) {
        return be.getLevel() != null
                && VisualizationManager.supportsVisualization(be.getLevel())
                && !IrisCompat.isShaderPackActive();
    }

    @Override
    public void render(BoatEngineBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
            int light, int overlay) {
        if (flywheelHandlesIt(be)) {
            return;
        }

        boolean powered = be.getBlockState().getValue(BoatEngineBlock.POWERED);
        long gameTime = be.getLevel() == null ? 0L : be.getLevel().getGameTime();
        float throttle = HelmClient.throttleFor(be.getBlockPos(), gameTime);

        long nanos = System.nanoTime();
        float dt = be.renderLastNanos == 0L ? 0.0f
                : Math.min((nanos - be.renderLastNanos) / 1.0e9f, MAX_FRAME_SECONDS);
        be.renderLastNanos = nanos;

        float target = HelmClient.steerFor(be.getBlockPos(), gameTime);
        be.renderSteer += (target - be.renderSteer) * (1.0f - (float) Math.exp(-STEER_RESPONSE * dt));
        float lever = MAX_LEVER_RAD * be.renderSteer;

        float speed = PROP_IDLE_SPEED + Math.abs(throttle) * (PROP_MAX_SPEED - PROP_IDLE_SPEED);
        be.renderPropAngle += speed * dt * 20.0f * (throttle < -0.02f ? -1.0f : 1.0f);

        float angle = facingAngle(be);

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotation(angle));
        ms.translate(-0.5, -0.5, -0.5);

        if (!powered) {
            draw(AllHighSeasPartialModels.BOAT_ENGINE_BODY_OFF, be, ms, buffer, light);
            swivel(ms, lever);
            draw(AllHighSeasPartialModels.BOAT_ENGINE_LEVER_OFF, be, ms, buffer, light);
            unswivel(ms, lever);
            legSwivel(ms, lever);
            draw(AllHighSeasPartialModels.BOAT_ENGINE_LEG_OFF, be, ms, buffer, light);
            ms.popPose();
            ms.popPose();
            return;
        }

        draw(AllHighSeasPartialModels.BOAT_ENGINE_BODY, be, ms, buffer, light);
        draw(AllHighSeasPartialModels.BOAT_ENGINE_BODY_GLOW, be, ms, buffer, LightTexture.FULL_BRIGHT);

        float pulse = 1.05f - 0.05f * Mth.cos(Mth.PI * (gameTime % 100000L + partialTicks) / 20.0f);
        exhaust(ms, buffer, be, light, pulse, 0.25f, AllHighSeasPartialModels.BOAT_ENGINE_EXHAUST_1,
                AllHighSeasPartialModels.BOAT_ENGINE_EXHAUST_1_GLOW);
        exhaust(ms, buffer, be, light, pulse, 0.75f, AllHighSeasPartialModels.BOAT_ENGINE_EXHAUST_2,
                AllHighSeasPartialModels.BOAT_ENGINE_EXHAUST_2_GLOW);

        swivel(ms, lever);
        draw(AllHighSeasPartialModels.BOAT_ENGINE_LEVER, be, ms, buffer, light);
        unswivel(ms, lever);

        legSwivel(ms, lever);
        draw(AllHighSeasPartialModels.BOAT_ENGINE_LEG, be, ms, buffer, light);
        ms.translate(0.5, -0.65625, 0.90625);
        ms.mulPose(Axis.ZP.rotation(be.renderPropAngle));
        ms.translate(-0.5, 0.65625, -0.90625);
        draw(AllHighSeasPartialModels.BOAT_ENGINE_PROP, be, ms, buffer, light);
        ms.popPose();

        ms.popPose();
    }

    private void exhaust(PoseStack ms, MultiBufferSource buffer, BoatEngineBlockEntity be, int light, float scale,
            float px, PartialModel model, PartialModel glow) {
        ms.pushPose();
        ms.translate(px, 0.6875f, 1.0625f);
        ms.scale(scale, scale, scale);
        ms.translate(-px, -0.6875f, -1.0625f);
        draw(model, be, ms, buffer, light);
        draw(glow, be, ms, buffer, LightTexture.FULL_BRIGHT);
        ms.popPose();
    }

    private void swivel(PoseStack ms, float lever) {
        ms.pushPose();
        ms.translate(0.5, 1.1875, 0.31875);
        ms.mulPose(Axis.YP.rotation(lever));
        ms.translate(-0.5, -1.1875, -0.31875);
    }

    private void unswivel(PoseStack ms, float lever) {
        ms.popPose();
    }

    private void legSwivel(PoseStack ms, float lever) {
        ms.pushPose();
        ms.translate(0.5, 0.0, 0.6875);
        ms.mulPose(Axis.YP.rotation(lever));
        ms.translate(-0.5, 0.0, -0.6875);
    }

    private void draw(PartialModel model, BoatEngineBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        CachedBuffers.partial(model, be.getBlockState())
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS)));
    }

    private static float facingAngle(BoatEngineBlockEntity be) {
        Direction facing = be.getBlockState().getValue(BoatEngineBlock.FACING);
        int yaw = switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        return -Mth.DEG_TO_RAD * yaw;
    }

    @Override
    public AABB getRenderBoundingBox(BoatEngineBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(2.5);
    }
}
