package com.maxenonyme.highseas.client;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.highseas.BoatManager;
import com.maxenonyme.highseas.CreateHighSeas;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = CreateHighSeas.MOD_ID, value = Dist.CLIENT)
public final class BoatBuoyancyDebugRenderer {
    private BoatBuoyancyDebugRenderer() {
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
        var buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(DebugRenderType.XRAY_LINES);

        Vector3d w = new Vector3d();
        for (Map.Entry<UUID, SubLevelAccess> e : subs.entrySet()) {
            UUID id = e.getKey();
            if (!BoatManager.boatSubs().containsKey(id))
                continue;
            SubLevelAccess sub = e.getValue();
            List<CompartmentDetector.Component> comps = CompartmentTracker.getCompartments(id);
            if (comps == null || comps.isEmpty())
                continue;

            Pose3dc pose = (sub instanceof ClientSubLevel csl) ? csl.renderPose() : sub.logicalPose();
            Quaterniondc q = pose.orientation();
            poseStack.pushPose();
            poseStack.translate(pose.position().x() - cam.x, pose.position().y() - cam.y, pose.position().z() - cam.z);
            poseStack.mulPose(new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w()));
            Matrix4f mat = poseStack.last().pose();

            for (CompartmentDetector.Component c : comps) {
                if (!c.sealed())
                    continue;
                for (BlockPos cell : c.internal()) {
                    w.set(cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5);
                    pose.transformPosition(w);
                    if (CompartmentTracker.realFluidState(mc.level, BlockPos.containing(w.x, w.y, w.z)).is(FluidTags.WATER))
                        cube(lines, mat, cell, 0.2f, 0.2f, 1.0f, 0.35f);
                }
            }
            poseStack.popPose();
        }

        buffers.endBatch(DebugRenderType.XRAY_LINES);
    }

    private static void cube(VertexConsumer vc, Matrix4f mat, BlockPos p, float h, float r, float g, float b) {
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
