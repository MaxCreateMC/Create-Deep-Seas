package com.maxenonyme.createsubmarine.submarine.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopePoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostProcessingManager;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;

class SonarOverlay {

    private static final int ROPE = 0xFF6B5A45;
    private static final int WAVE = 0x3F86E0;
    private static final int LINE = 0x2E3032;
    private static final int LINE_SHADOW = 0x696965;
    private static final int WAVE_SEGMENTS = 72;

    static void drawRopes(Level level, Vector3d sonar, Vector3d center, Quaternionf view, Matrix4f projection,
            SonarScan scan, float partialTicks) {
        AABB zone = SonarView.zone(sonar);
        float half = Math.max(0.08f, scan.unitsPerPixel * 0.8f);
        Matrix4f toView = new Matrix4f().rotation(view);
        Vector3d world = new Vector3d();
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();

        BufferBuilder builder = null;
        for (ClientRopeStrand strand : ClientLevelRopeManager.getOrCreate(level).getAllStrands()) {
            List<ClientRopePoint> points = strand.getPoints();
            if (points.size() < 2 || !strand.getBounds().intersects(zone))
                continue;
            if (builder == null)
                builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            points.get(0).renderPos(partialTicks, world);
            toView.transformPosition(relative(a, world, center));
            for (int i = 1; i < points.size(); i++) {
                points.get(i).renderPos(partialTicks, world);
                toView.transformPosition(relative(b, world, center));
                segment(builder, a, b, half, ROPE);
                a.set(b);
            }
        }
        if (builder != null)
            drawFlat(scan.fbo, projection, builder.build(), false);
    }

    static void drawWave(SonarScan scan, Matrix4f projection, Quaternionf view, Vector3d sonar, Vector3d center,
            float front) {
        if (front >= SonarView.SHELLS || front <= 0)
            return;
        float fade = 1f - front / SonarView.SHELLS;
        float half = Math.max(0.12f, scan.unitsPerPixel * 1.1f);
        Matrix4f toView = new Matrix4f().rotation(view);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        Vector3f middle = toView.transformPosition(relative(new Vector3f(), sonar, center));
        drawRing(builder, middle, front, half, alpha(fade * 0.95f) | WAVE);
        drawRing(builder, middle, Math.max(0f, front - 1.4f), half, alpha(fade * 0.45f) | WAVE);
        drawEquator(builder, toView, sonar, center, front, half * 0.8f, alpha(fade * 0.6f) | WAVE);

        drawFlat(scan.result, projection, builder.build(), true);
    }

    private static void drawRing(BufferBuilder builder, Vector3f middle, float radius, float half, int color) {
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        for (int i = 0; i < WAVE_SEGMENTS; i++) {
            double t0 = Math.PI * 2 * i / WAVE_SEGMENTS;
            double t1 = Math.PI * 2 * (i + 1) / WAVE_SEGMENTS;
            a.set(middle.x + (float) Math.cos(t0) * radius, middle.y + (float) Math.sin(t0) * radius, 0);
            b.set(middle.x + (float) Math.cos(t1) * radius, middle.y + (float) Math.sin(t1) * radius, 0);
            segment(builder, a, b, half, color);
        }
    }

    private static void drawEquator(BufferBuilder builder, Matrix4f toView, Vector3d sonar, Vector3d center,
            float radius, float half, int color) {
        float x = (float) (sonar.x - center.x);
        float y = (float) (sonar.y - center.y);
        float z = (float) (sonar.z - center.z);
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        for (int i = 0; i < WAVE_SEGMENTS; i++) {
            double t0 = Math.PI * 2 * i / WAVE_SEGMENTS;
            double t1 = Math.PI * 2 * (i + 1) / WAVE_SEGMENTS;
            toView.transformPosition(a.set(x + (float) Math.cos(t0) * radius, y, z + (float) Math.sin(t0) * radius));
            toView.transformPosition(b.set(x + (float) Math.cos(t1) * radius, y, z + (float) Math.sin(t1) * radius));
            a.z = 0;
            b.z = 0;
            segment(builder, a, b, half, color);
        }
    }

    private static int alpha(float opacity) {
        return (int) (255 * opacity) << 24;
    }

    private static Vector3f relative(Vector3f out, Vector3d world, Vector3d center) {
        return out.set((float) (world.x - center.x), (float) (world.y - center.y), (float) (world.z - center.z));
    }

    private static void segment(BufferBuilder builder, Vector3f a, Vector3f b, float half, int color) {
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0e-4f)
            return;
        float nx = -dy / length * half;
        float ny = dx / length * half;
        builder.addVertex(a.x + nx, a.y + ny, a.z).setColor(color);
        builder.addVertex(a.x - nx, a.y - ny, a.z).setColor(color);
        builder.addVertex(b.x - nx, b.y - ny, b.z).setColor(color);
        builder.addVertex(b.x + nx, b.y + ny, b.z).setColor(color);
    }

    private static void drawFlat(AdvancedFbo target, Matrix4f projection, MeshData mesh, boolean onTop) {
        if (mesh == null)
            return;
        target.bind(true);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.identity();
        RenderSystem.applyModelViewMatrix();
        try {
            if (onTop) {
                RenderSystem.disableDepthTest();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            } else {
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
            }
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            BufferUploader.drawWithShader(mesh);
        } finally {
            RenderSystem.enableCull();
            if (onTop) {
                RenderSystem.disableBlend();
                RenderSystem.enableDepthTest();
            }
            stack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    static void outline(SonarScan scan) {
        PostProcessingManager post = VeilRenderSystem.renderer().getPostProcessingManager();
        PostPipeline pipeline = post.getPipeline(Simulated.path("diagram"));
        if (pipeline == null)
            return;
        setColor(pipeline, "LineColor", LINE);
        setColor(pipeline, "LineShadowColor", LINE_SHADOW);
        pipeline.getUniformSafe("InSize").setVector((float) scan.width + SonarView.PAD, (float) scan.height);
        pipeline.getUniformSafe("PaletteOffset").setFloat(0.25f);
        pipeline.getUniformSafe("FadeScale").setFloat(0.3f);

        PostPipeline.Context context = post.getPostPipelineContext();
        context.setFramebuffer(Simulated.path("diagram"), scan.fbo);
        context.setFramebuffer(Simulated.path("diagram_outlined"), scan.outline);
        context.setFramebuffer(Simulated.path("diagram_final"), scan.result);
        post.runPipeline(pipeline, false);
    }

    private static void setColor(PostPipeline pipeline, String uniform, int rgb) {
        pipeline.getUniformSafe(uniform).setVector(
                ((rgb >> 16) & 0xFF) / 255f,
                ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f,
                1f);
    }
}
