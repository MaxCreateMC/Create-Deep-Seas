package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.submarine.client.renderer.SubLevelRenderPoseCapture;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.client.Minecraft;
import com.maxenonyme.createsubmarine.submarine.system.ImplosionSequence;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@OnlyIn(Dist.CLIENT)
public class SubLevelCrackRenderer {
    private static final Map<UUID, Map<BlockPos, long[]>> CLIENT_CRACKS = new ConcurrentHashMap<>();
    private static final int BURSTING = 4;

    public static void updateCrack(UUID subId, BlockPos plotPos, int crackLevel, int blockId) {
        if (crackLevel < 0) {
            Map<BlockPos, long[]> m = CLIENT_CRACKS.get(subId);
            if (m != null)
                m.remove(plotPos);
        } else {
            Minecraft mc = Minecraft.getInstance();
            long now = mc.level == null ? 0 : mc.level.getGameTime();
            CLIENT_CRACKS.computeIfAbsent(subId, k -> new ConcurrentHashMap<>())
                    .put(plotPos, new long[]{crackLevel, now});
        }
    }

    public static boolean hasCrack(UUID subId, BlockPos plotPos) {
        Map<BlockPos, long[]> m = CLIENT_CRACKS.get(subId);
        return m != null && m.containsKey(plotPos);
    }

    public static void clearSub(UUID subId) {
        CLIENT_CRACKS.remove(subId);
    }

    public static void clearAll() {
        CLIENT_CRACKS.clear();
        SubLevelRenderPoseCapture.clearAll();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        if (CLIENT_CRACKS.isEmpty())
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        boolean shook = false;

        for (Map.Entry<UUID, Map<BlockPos, long[]>> subEntry : CLIENT_CRACKS.entrySet()) {
            UUID subId = subEntry.getKey();
            Map<BlockPos, long[]> cracks = subEntry.getValue();
            if (cracks.isEmpty())
                continue;

            SubLevelAccess subLevel = CompartmentTracker.getSub(subId);
            if (subLevel == null)
                continue;

            Pose3dc renderPose =
                    SubLevelRenderPoseCapture.get(subId);
            if (renderPose == null) {
                if (subLevel instanceof ClientSubLevel clientSub) {
                    renderPose = clientSub.renderPose();
                } else {
                    renderPose = subLevel.logicalPose();
                }
            }

            if (renderPose == null)
                continue;
            Quaterniondc subRot = renderPose.orientation();

            for (Map.Entry<BlockPos, long[]> crackEntry : cracks.entrySet()) {
                BlockPos plotPos = crackEntry.getKey();
                int crackLevel = (int) crackEntry.getValue()[0];

                int stage = 0;
                if (crackLevel == 1)
                    stage = 2;
                else if (crackLevel == 2)
                    stage = 5;
                else if (crackLevel == 3)
                    stage = 8;
                else if (crackLevel >= BURSTING)
                    stage = 9;
                ResourceLocation tex = ResourceLocation.withDefaultNamespace(
                        "textures/block/destroy_stage_" + stage + ".png");

                BlockState state = mc.level.getBlockState(plotPos);
                if (state.isAir())
                    continue;

                Vector3d worldVec = new Vector3d(plotPos.getX(), plotPos.getY(), plotPos.getZ());
                renderPose.transformPosition(worldVec);

                poseStack.pushPose();
                poseStack.translate(worldVec.x - camPos.x, worldVec.y - camPos.y, worldVec.z - camPos.z);
                poseStack.mulPose(new Quaternionf(
                        (float) subRot.x(), (float) subRot.y(), (float) subRot.z(), (float) subRot.w()));

                poseStack.translate(0.5f, 0.5f, 0.5f);
                if (crackLevel >= BURSTING) {
                    shake(poseStack, mc.level.getGameTime() - crackEntry.getValue()[1] + partialTick);
                    poseStack.pushPose();
                    poseStack.scale(1.02f, 1.02f, 1.02f);
                    poseStack.translate(-0.5f, -0.5f, -0.5f);
                    int light = LevelRenderer.getLightColor(mc.level, plotPos);
                    if (subLevel instanceof ClientSubLevel clientSub)
                        light = clientSub.scaleLightColor(light);
                    mc.getBlockRenderer().renderSingleBlock(state, poseStack, bufferSource, light, OverlayTexture.NO_OVERLAY);
                    poseStack.popPose();
                    shook = true;
                }
                poseStack.scale(1.03f, 1.03f, 1.03f);
                poseStack.translate(-0.5f, -0.5f, -0.5f);

                VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(tex));
                renderCube(poseStack.last().pose(), consumer);

                poseStack.popPose();
            }
        }

        if (shook)
            bufferSource.endBatch();
        for (int s = 0; s <= 9; s++) {
            ResourceLocation tex = ResourceLocation.withDefaultNamespace(
                    "textures/block/destroy_stage_" + s + ".png");
            bufferSource.endBatch(RenderType.entityTranslucent(tex));
        }
    }

    private static void shake(PoseStack poseStack, float age) {
        float t = Mth.clamp(age / ImplosionSequence.BUILD_UP, 0f, 1f);
        float jitter = 0.015f + 0.075f * t;
        float wobble = (1.0f + 4.0f * t) * Mth.DEG_TO_RAD;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        poseStack.translate((random.nextFloat() - 0.5f) * 2f * jitter, (random.nextFloat() - 0.5f) * 2f * jitter,
                (random.nextFloat() - 0.5f) * 2f * jitter);
        poseStack.mulPose(new Quaternionf().rotateXYZ((random.nextFloat() - 0.5f) * 2f * wobble,
                (random.nextFloat() - 0.5f) * 2f * wobble, (random.nextFloat() - 0.5f) * 2f * wobble));
    }

    private static void renderCube(Matrix4f mat, VertexConsumer v) {
        quad(v, mat, 0,0,1, 1,0,1, 1,0,0, 0,0,0,  0,-1,0);
        quad(v, mat, 0,1,0, 1,1,0, 1,1,1, 0,1,1,  0,1,0);
        quad(v, mat, 1,1,0, 0,1,0, 0,0,0, 1,0,0,  0,0,-1);
        quad(v, mat, 0,1,1, 1,1,1, 1,0,1, 0,0,1,  0,0,1);
        quad(v, mat, 0,1,0, 0,1,1, 0,0,1, 0,0,0,  -1,0,0);
        quad(v, mat, 1,1,1, 1,1,0, 1,0,0, 1,0,1,  1,0,0);
    }

    private static void quad(VertexConsumer v, Matrix4f mat,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float nx, float ny, float nz) {
        vert(v, mat, x3, y3, z3, 0, 1, nx, ny, nz);
        vert(v, mat, x2, y2, z2, 1, 1, nx, ny, nz);
        vert(v, mat, x1, y1, z1, 1, 0, nx, ny, nz);
        vert(v, mat, x0, y0, z0, 0, 0, nx, ny, nz);
    }

    private static void vert(VertexConsumer v, Matrix4f mat,
            float x, float y, float z, float u, float w,
            float nx, float ny, float nz) {
        v.addVertex(mat, x, y, z)
                .setColor(-1)
                .setUv(u, w)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(nx, ny, nz);
    }
}
