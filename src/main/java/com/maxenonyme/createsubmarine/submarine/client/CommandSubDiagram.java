package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.submarine.block.entity.CommandSubBlockEntity;
import com.maxenonyme.createsubmarine.submarine.block.entity.renderer.CommandSubRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.entities.diagram.screen.DiagramScreen;
import dev.simulated_team.simulated.util.SimpleSubLevelGroupRenderer;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class CommandSubDiagram {
    private CommandSubDiagram() {
    }

    private static final int PIXELS_PER_UNIT = 4;
    private static final long FRAME_NANOS = 100_000_000L;
    private static final long FORGET_NANOS = 5_000_000_000L;

    public static boolean drawing;

    private static final class Frames {
        AdvancedFbo fbo;
        AdvancedFbo outline;
        AdvancedFbo result;
        long lastDraw;
        long lastSeen;

        void free() {
            if (result != null)
                CommandSubRenderTypes.forget(result.getColorTextureAttachment(0).getId());
            if (fbo != null)
                fbo.free();
            if (outline != null)
                outline.free();
            if (result != null)
                result.free();
            fbo = outline = result = null;
        }
    }

    private static final Map<CommandSubBlockEntity, Frames> FRAMES = new HashMap<>();

    public static RenderType textureFor(CommandSubBlockEntity be) {
        Frames frames = FRAMES.computeIfAbsent(be, k -> new Frames());
        frames.lastSeen = System.nanoTime();
        return frames.result == null ? null
                : CommandSubRenderTypes.diagram(frames.result.getColorTextureAttachment(0).getId());
    }

    public static void onFrameEnd(RenderFrameEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        boolean drew = false;

        Iterator<Map.Entry<CommandSubBlockEntity, Frames>> it = FRAMES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<CommandSubBlockEntity, Frames> entry = it.next();
            CommandSubBlockEntity be = entry.getKey();
            Frames frames = entry.getValue();
            if (mc.level == null || be.isRemoved() || be.getLevel() != mc.level || now - frames.lastSeen > FORGET_NANOS) {
                frames.free();
                it.remove();
                continue;
            }
            if (now - frames.lastDraw < FRAME_NANOS)
                continue;
            SubLevel sub = Sable.HELPER.getContaining(be);
            if (!(sub instanceof ClientSubLevel csl) || csl.isRemoved() || csl.getPlot() == null)
                continue;
            frames.lastDraw = now;
            draw(csl, frames, event.getPartialTick().getGameTimeDeltaPartialTick(true));
            drew = true;
        }

        if (drew)
            mc.getMainRenderTarget().bindWrite(true);
    }

    private static void draw(ClientSubLevel sub, Frames frames, float partialTicks) {
        float[] area = CommandSubRenderer.DIAGRAM;
        int width = Math.round((area[2] - area[0]) * PIXELS_PER_UNIT);
        int height = Math.round((area[3] - area[1]) * PIXELS_PER_UNIT);
        if (frames.fbo == null) {
            frames.fbo = AdvancedFbo.withSize(width, height).addColorTextureBuffer().setDepthTextureBuffer().build(true);
            frames.outline = AdvancedFbo.withSize(width, height).addColorTextureBuffer().build(true);
            frames.result = AdvancedFbo.withSize(width, height).addColorTextureBuffer().build(true);
        }

        Pose3dc pose = sub.renderPose(partialTicks);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        Vector3d corner = new Vector3d();
        for (ClientSubLevel member : SimpleSubLevelGroupRenderer.getRenderedChain(sub)) {
            if (member.getPlot() == null)
                continue;
            BoundingBox3ic box = member.getPlot().getBoundingBox();
            Pose3dc memberPose = member.renderPose(partialTicks);
            for (int i = 0; i < 8; i++) {
                corner.set((i & 1) == 0 ? box.minX() : box.maxX() + 1,
                        (i & 2) == 0 ? box.minY() : box.maxY() + 1,
                        (i & 4) == 0 ? box.minZ() : box.maxZ() + 1);
                memberPose.transformPosition(corner);
                pose.transformPositionInverse(corner);
                minX = Math.min(minX, corner.x);
                minY = Math.min(minY, corner.y);
                minZ = Math.min(minZ, corner.z);
                maxX = Math.max(maxX, corner.x);
                maxY = Math.max(maxY, corner.y);
                maxZ = Math.max(maxZ, corner.z);
            }
        }
        if (minX > maxX)
            return;

        boolean alongX = maxX - minX >= maxZ - minZ;
        Quaternionf side = alongX ? new Quaternionf() : new Quaternionf().rotateY((float) Math.toRadians(90));
        double length = alongX ? maxX - minX : maxZ - minZ;
        double thickness = alongX ? maxZ - minZ : maxX - minX;

        float aspect = (float) width / height;
        float halfHeight = (float) Math.max((maxY - minY) / 2, length / 2 / aspect) * 1.08f + 0.25f;
        float reach = (float) thickness / 2 + 2;
        Matrix4f projection = new Matrix4f().ortho(-halfHeight * aspect, halfHeight * aspect, -halfHeight, halfHeight,
                0.1f, reach + (float) thickness + 2);

        Vector3d camera = new Vector3d((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2)
                .add(side.transform(new Vector3d(0, 0, reach)));
        pose.transformPosition(camera);

        drawing = true;
        try {
            DiagramScreen.draw(sub, partialTicks, side, projection, camera, width, height,
                    frames.fbo, frames.outline, frames.result, 0.25f, 1.0f, 0x2E3032, 0x696965);
        } finally {
            drawing = false;
            AdvancedFbo.unbind();
        }
    }
}
