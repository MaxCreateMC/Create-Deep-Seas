package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.submarine.system.LightFailure;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class LightFailureRenderer {
    private LightFailureRenderer() {
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !LightFailure.any())
            return;
        Map<Long, LightFailure.Failure> failures = LightFailure.clientFailures(mc.level);
        if (failures.isEmpty())
            return;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean drew = false;

        for (Map.Entry<Long, LightFailure.Failure> entry : failures.entrySet()) {
            LightFailure.Failure f = entry.getValue();
            boolean trembling = f.trembling(now);
            boolean dark = f.shownDark(now);
            if (!trembling && !dark)
                continue;
            BlockPos pos = BlockPos.of(entry.getKey());
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir())
                continue;
            SubLevel sub = Sable.HELPER.getContaining(mc.level, pos);
            Vector3d corner = new Vector3d(pos.getX(), pos.getY(), pos.getZ());
            Quaternionf rotation = new Quaternionf();
            int light = dark ? ambient(mc, pos) : LevelRenderer.getLightColor(mc.level, pos);
            if (sub instanceof ClientSubLevel csl) {
                Pose3dc pose = csl.renderPose(partialTick);
                pose.transformPosition(corner);
                rotation.set((float) pose.orientation().x(), (float) pose.orientation().y(),
                        (float) pose.orientation().z(), (float) pose.orientation().w());
                light = csl.scaleLightColor(light);
            }
            poseStack.pushPose();
            poseStack.translate(corner.x - cam.x, corner.y - cam.y, corner.z - cam.z);
            poseStack.mulPose(rotation);
            poseStack.translate(0.5f, 0.5f, 0.5f);
            if (trembling) {
                float t = Mth.clamp((now - f.start - f.flicker + partialTick) / f.tremble, 0f, 1f);
                float jitter = 0.02f + 0.06f * t;
                float wobble = (1.5f + 3f * t) * Mth.DEG_TO_RAD;
                ThreadLocalRandom random = ThreadLocalRandom.current();
                poseStack.translate((random.nextFloat() - 0.5f) * 2f * jitter, (random.nextFloat() - 0.5f) * 2f * jitter,
                        (random.nextFloat() - 0.5f) * 2f * jitter);
                poseStack.mulPose(new Quaternionf().rotateXYZ((random.nextFloat() - 0.5f) * 2f * wobble,
                        (random.nextFloat() - 0.5f) * 2f * wobble, (random.nextFloat() - 0.5f) * 2f * wobble));
            }
            float scale = trembling ? 1.02f : 1.006f;
            poseStack.scale(scale, scale, scale);
            poseStack.translate(-0.5f, -0.5f, -0.5f);
            mc.getBlockRenderer().renderSingleBlock(state, poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
            drew = true;
        }
        if (drew)
            buffers.endBatch();
    }

    private static int ambient(Minecraft mc, BlockPos pos) {
        int block = 0;
        int sky = 0;
        for (Direction d : Direction.values()) {
            BlockPos next = pos.relative(d);
            block = Math.max(block, mc.level.getBrightness(LightLayer.BLOCK, next));
            sky = Math.max(sky, mc.level.getBrightness(LightLayer.SKY, next));
        }
        return LightTexture.pack(Math.max(0, block - 1), sky);
    }
}
