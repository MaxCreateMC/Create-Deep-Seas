package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.CreateHighSeas;
import com.maxenonyme.highseas.sail.RudderDetector;
import com.maxenonyme.highseas.sail.SailDetector;
import com.maxenonyme.highseas.sail.SailForce;
import com.maxenonyme.highseas.sail.SailGroup;
import com.maxenonyme.highseas.wind.WindManager;
import com.maxenonyme.highseas.wind.WindSample;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.Direction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = CreateHighSeas.MOD_ID, value = Dist.CLIENT)
public final class WindDebugRenderer {
    private WindDebugRenderer() {
    }

    private static final int SCAN_INTERVAL = 5;
    private static long lastScan = Long.MIN_VALUE;
    private static final Map<UUID, List<SailGroup>> CACHE = new HashMap<>();

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!mc.getEntityRenderDispatcher().shouldRenderHitBoxes()) {
            return;
        }
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }

        long now = level.getGameTime();
        if (now - lastScan >= SCAN_INTERVAL) {
            lastScan = now;
            CACHE.clear();
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = poseStack.last().pose();

        for (SubLevel ship : container.getAllSubLevels()) {
            if (ship.getPlot() == null || ship.getLevel() == null) {
                continue;
            }
            List<SailGroup> groups = CACHE.computeIfAbsent(ship.getUniqueId(),
                    k -> SailDetector.detect(ship.getLevel(), ship.getPlot().getBoundingBox()));
            if (groups.isEmpty()) {
                continue;
            }

            Pose3dc pose = ship.logicalPose();
            Quaterniondc orientation = pose.orientation();
            BoundingBox3ic bb = ship.getPlot().getBoundingBox();
            Vec3 rudder = RudderDetector.centroid(ship.getLevel(), bb);
            Vec3 rcenter = new Vec3((bb.minX() + bb.maxX()) * 0.5, (bb.minY() + bb.maxY()) * 0.5, (bb.minZ() + bb.maxZ()) * 0.5);
            Vector3d forward = SailForce.forward(orientation, rudder, rcenter, bb.maxX() - bb.minX(), bb.maxZ() - bb.minZ(), groups);

            for (SailGroup group : groups) {
                Vector3d wc = new Vector3d(group.localCenter().x, group.localCenter().y, group.localCenter().z);
                pose.transformPosition(wc);
                Vec3 start = new Vec3(wc.x, wc.y, wc.z);

                WindSample wind = WindManager.getWind(level, wc.x, wc.y, wc.z);
                Vec3 windVec = wind.vector();
                double strength = wind.strength();

                if (strength >= 1.0e-4) {
                    Vec3 windDir = windVec.normalize();
                    drawArrow(lines, matrix, start, start.add(windDir.scale(1.0 + strength * 3.0)), windDir,
                            0.25f, 0.85f, 1.0f);
                }

                if (forward != null && group.axis() != Direction.Axis.Y) {
                    Vec3 fwd = new Vec3(forward.x, forward.y, forward.z);

                    Vec3 ln = group.localNormal();
                    Vector3d wn = new Vector3d(ln.x, ln.y, ln.z);
                    orientation.transform(wn);
                    if (wn.lengthSquared() > 1.0e-9) {
                        wn.normalize();
                        double power = SailForce.power(windVec, wn.x, wn.y, wn.z, forward.x, forward.y, forward.z, group.area());
                        double len = Mth.clamp(power * 0.15, -6.0, 6.0);
                        if (Math.abs(len) < 0.5) {
                            len = 0.5 * Math.signum(power);
                        }
                        Vec3 tipDir = len >= 0 ? fwd : new Vec3(-fwd.x, -fwd.y, -fwd.z);
                        drawArrow(lines, matrix, start, start.add(fwd.scale(len)), tipDir, 1.0f, 0.2f, 0.2f);
                    }

                    if (strength >= 1.0e-4) {
                        Vec3 windDir = windVec.normalize();
                        double boost = Math.max(0.0, windDir.x * forward.x + windDir.y * forward.y + windDir.z * forward.z);
                        if (boost > 0.01) {
                            drawArrow(lines, matrix, start, start.add(fwd.scale(boost * 6.0)), fwd, 0.2f, 1.0f, 0.3f);
                        }
                    }
                }
            }
        }

        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void drawArrow(VertexConsumer vc, Matrix4f mat, Vec3 a, Vec3 b, Vec3 dir, float r, float g, float bl) {
        line(vc, mat, a, b, r, g, bl);

        Vec3 up = Math.abs(dir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 side = dir.cross(up).normalize();
        Vec3 back = b.subtract(dir.scale(0.3));
        line(vc, mat, b, back.add(side.scale(0.2)), r, g, bl);
        line(vc, mat, b, back.subtract(side.scale(0.2)), r, g, bl);
    }

    private static void line(VertexConsumer vc, Matrix4f mat, Vec3 a, Vec3 b, float r, float g, float bl) {
        float nx = (float) (b.x - a.x);
        float ny = (float) (b.y - a.y);
        float nz = (float) (b.z - a.z);
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0e-5f) {
            return;
        }
        nx /= len;
        ny /= len;
        nz /= len;
        vc.addVertex(mat, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, 1.0f).setNormal(nx, ny, nz);
        vc.addVertex(mat, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, 1.0f).setNormal(nx, ny, nz);
    }
}
