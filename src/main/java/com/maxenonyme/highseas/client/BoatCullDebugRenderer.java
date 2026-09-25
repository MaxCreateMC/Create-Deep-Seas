package com.maxenonyme.highseas.client;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.highseas.CreateHighSeas;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = CreateHighSeas.MOD_ID, value = Dist.CLIENT)
public final class BoatCullDebugRenderer {
    private BoatCullDebugRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.getEntityRenderDispatcher().shouldRenderHitBoxes() || mc.level == null)
            return;

        Map<UUID, SubLevelAccess> subs = CompartmentTracker.getSubsSnapshot();
        if (subs.isEmpty())
            return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(DebugRenderType.XRAY_LINES);

        for (Map.Entry<UUID, SubLevelAccess> e : subs.entrySet()) {
            UUID id = e.getKey();
            SubLevelAccess sub = e.getValue();

            List<CompartmentDetector.Component> comps = CompartmentTracker.getCompartments(id);
            Set<BlockPos> union = new HashSet<>();
            Set<BlockPos> air = new HashSet<>();
            if (comps != null) {
                for (CompartmentDetector.Component c : comps) {
                    if (!c.sealed())
                        continue;
                    union.addAll(c.internal());
                    union.addAll(c.hull());
                    air.addAll(c.internal());
                }
            }

            AABB worldAABB = CompartmentTracker.getWorldAABB(id);
            if (worldAABB != null) {
                poseStack.pushPose();
                poseStack.translate(-cam.x, -cam.y, -cam.z);
                aabb(lines, poseStack.last().pose(), worldAABB, 0.7f, 0.2f, 0.9f);
                poseStack.popPose();
            }

            if (union.isEmpty())
                continue;

            Pose3dc pose = (sub instanceof ClientSubLevel csl) ? csl.renderPose() : sub.logicalPose();
            Quaterniondc q = pose.orientation();
            poseStack.pushPose();
            poseStack.translate(pose.position().x() - cam.x, pose.position().y() - cam.y, pose.position().z() - cam.z);
            poseStack.mulPose(new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w()));
            Matrix4f mat = poseStack.last().pose();
            for (BlockPos p : union)
                faces(lines, mat, union, p, 0.25f, 0.85f, 1.0f);
            for (BlockPos p : air)
                marker(lines, mat, p, 1.0f, 0.35f, 0.7f);
            poseStack.popPose();
        }

        buffers.endBatch(DebugRenderType.XRAY_LINES);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.getEntityRenderDispatcher().shouldRenderHitBoxes() || mc.level == null)
            return;
        Map<UUID, SubLevelAccess> subs = CompartmentTracker.getSubsSnapshot();
        GuiGraphics g = event.getGuiGraphics();
        g.drawString(mc.font, "[Water Cull] tracked=" + subs.size(), 4, 4, 0x55FFFF);
    }

    private static void faces(VertexConsumer vc, Matrix4f mat, Set<BlockPos> union, BlockPos p,
            float r, float g, float b) {
        float x = p.getX(), y = p.getY(), z = p.getZ();
        float x1 = x + 1f, y1 = y + 1f, z1 = z + 1f;
        if (!union.contains(p.above()))
            quad(vc, mat, x, y1, z, x1, y1, z, x1, y1, z1, x, y1, z1, r, g, b);
        if (!union.contains(p.below()))
            quad(vc, mat, x, y, z, x1, y, z, x1, y, z1, x, y, z1, r, g, b);
        if (!union.contains(p.north()))
            quad(vc, mat, x, y, z, x1, y, z, x1, y1, z, x, y1, z, r, g, b);
        if (!union.contains(p.south()))
            quad(vc, mat, x, y, z1, x1, y, z1, x1, y1, z1, x, y1, z1, r, g, b);
        if (!union.contains(p.west()))
            quad(vc, mat, x, y, z, x, y, z1, x, y1, z1, x, y1, z, r, g, b);
        if (!union.contains(p.east()))
            quad(vc, mat, x1, y, z, x1, y, z1, x1, y1, z1, x1, y1, z, r, g, b);
    }

    private static void marker(VertexConsumer vc, Matrix4f mat, BlockPos p, float r, float g, float b) {
        float h = 0.18f;
        float x0 = p.getX() + 0.5f - h, y0 = p.getY() + 0.5f - h, z0 = p.getZ() + 0.5f - h;
        float x1 = p.getX() + 0.5f + h, y1 = p.getY() + 0.5f + h, z1 = p.getZ() + 0.5f + h;
        seg(vc, mat, x0, y0, z0, x1, y0, z0, r, g, b);
        seg(vc, mat, x1, y0, z0, x1, y0, z1, r, g, b);
        seg(vc, mat, x1, y0, z1, x0, y0, z1, r, g, b);
        seg(vc, mat, x0, y0, z1, x0, y0, z0, r, g, b);
        seg(vc, mat, x0, y1, z0, x1, y1, z0, r, g, b);
        seg(vc, mat, x1, y1, z0, x1, y1, z1, r, g, b);
        seg(vc, mat, x1, y1, z1, x0, y1, z1, r, g, b);
        seg(vc, mat, x0, y1, z1, x0, y1, z0, r, g, b);
        seg(vc, mat, x0, y0, z0, x0, y1, z0, r, g, b);
        seg(vc, mat, x1, y0, z0, x1, y1, z0, r, g, b);
        seg(vc, mat, x1, y0, z1, x1, y1, z1, r, g, b);
        seg(vc, mat, x0, y0, z1, x0, y1, z1, r, g, b);
    }

    private static void quad(VertexConsumer vc, Matrix4f mat, float ax, float ay, float az, float bx, float by,
            float bz, float cx, float cy, float cz, float dx, float dy, float dz, float r, float g, float b) {
        seg(vc, mat, ax, ay, az, bx, by, bz, r, g, b);
        seg(vc, mat, bx, by, bz, cx, cy, cz, r, g, b);
        seg(vc, mat, cx, cy, cz, dx, dy, dz, r, g, b);
        seg(vc, mat, dx, dy, dz, ax, ay, az, r, g, b);
    }

    private static void aabb(VertexConsumer vc, Matrix4f mat, AABB box, float r, float g, float b) {
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
        seg(vc, mat, x0, y0, z0, x1, y0, z0, r, g, b);
        seg(vc, mat, x1, y0, z0, x1, y0, z1, r, g, b);
        seg(vc, mat, x1, y0, z1, x0, y0, z1, r, g, b);
        seg(vc, mat, x0, y0, z1, x0, y0, z0, r, g, b);
        seg(vc, mat, x0, y1, z0, x1, y1, z0, r, g, b);
        seg(vc, mat, x1, y1, z0, x1, y1, z1, r, g, b);
        seg(vc, mat, x1, y1, z1, x0, y1, z1, r, g, b);
        seg(vc, mat, x0, y1, z1, x0, y1, z0, r, g, b);
        seg(vc, mat, x0, y0, z0, x0, y1, z0, r, g, b);
        seg(vc, mat, x1, y0, z0, x1, y1, z0, r, g, b);
        seg(vc, mat, x1, y0, z1, x1, y1, z1, r, g, b);
        seg(vc, mat, x0, y0, z1, x0, y1, z1, r, g, b);
    }

    private static void seg(VertexConsumer vc, Matrix4f mat, float ax, float ay, float az, float bx, float by, float bz,
            float r, float g, float b) {
        float nx = bx - ax, ny = by - ay, nz = bz - az;
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0e-5f)
            return;
        nx /= len;
        ny /= len;
        nz /= len;
        vc.addVertex(mat, ax, ay, az).setColor(r, g, b, 1.0f).setNormal(nx, ny, nz);
        vc.addVertex(mat, bx, by, bz).setColor(r, g, b, 1.0f).setNormal(nx, ny, nz);
    }
}
