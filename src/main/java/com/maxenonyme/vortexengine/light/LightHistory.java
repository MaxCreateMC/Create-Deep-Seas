package com.maxenonyme.vortexengine.light;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.vortexengine.VortexConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.nio.ByteBuffer;

public class LightHistory {

    private static final int[] ORDER = { 0, 10, 2, 8, 5, 15, 7, 13, 1, 11, 3, 9, 4, 14, 6, 12 };

    private static ShaderInstance shader;
    private static final int[] textures = new int[2];
    private static final int[] framebuffers = new int[2];
    private static int width, height;
    private static int current;
    private static long frame;
    private static long accumulated = -2;
    private static final Matrix4f previous = new Matrix4f();
    private static Vec3 previousCamera = Vec3.ZERO;
    private static float jitterX, jitterY;

    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "vortex_temporal"),
                    DefaultVertexFormat.POSITION), loaded -> shader = loaded);
        } catch (IOException e) {
            CreateSubmarine.LOGGER.warn("Couldn't load the Vortex temporal shader", e);
        }
    }

    static boolean active() {
        return shader != null && VortexConfig.TEMPORAL.get();
    }

    static float jitterX() {
        return jitterX;
    }

    static float jitterY() {
        return jitterY;
    }

    static void advance() {
        frame++;
        jitterX = 0;
        jitterY = 0;
        if (!active())
            return;

        int scale = LightVolumes.lightScale();
        int index = ORDER[(int) (frame % (scale * scale))];
        jitterX = (index & 3) * scale / 4;
        jitterY = (index >> 2) * scale / 4;
    }

    static int accumulate(RenderTarget scene, int light, Vec3 camera, Matrix4f view, Matrix4f projection) {
        if (width != scene.width || height != scene.height) {
            allocate(scene.width, scene.height);
            accumulated = -2;
        }

        boolean valid = accumulated == frame - 1;
        int last = current;
        current ^= 1;

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffers[current]);
        GlStateManager._viewport(0, 0, width, height);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();

        Vec3 motion = camera.subtract(previousCamera);
        shader.setSampler("LightSampler", light);
        shader.setSampler("DepthSampler", scene.getDepthTextureId());
        shader.setSampler("HistorySampler", textures[last]);
        shader.safeGetUniform("InvProjMat").set(new Matrix4f(projection).invert());
        shader.safeGetUniform("InvViewMat").set(new Matrix4f(view).invert());
        shader.safeGetUniform("PreviousMat").set(previous);
        shader.safeGetUniform("CameraMotion").set((float) motion.x, (float) motion.y, (float) motion.z);
        shader.safeGetUniform("LightScale").set((float) LightVolumes.lightScale());
        shader.safeGetUniform("Jitter").set(jitterX, jitterY);
        shader.safeGetUniform("HistoryValid").set(valid ? 1f : 0f);

        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(screen());

        previous.set(projection).mul(view);
        previousCamera = camera;
        accumulated = frame;
        return textures[current];
    }

    private static void allocate(int newWidth, int newHeight) {
        width = newWidth;
        height = newHeight;

        for (int i = 0; i < 2; i++) {
            if (textures[i] == 0)
                textures[i] = GlStateManager._genTexture();
            if (framebuffers[i] == 0)
                framebuffers[i] = GlStateManager.glGenFramebuffers();

            GlStateManager._bindTexture(textures[i]);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, width, height, 0, GL11.GL_RGBA, GL11.GL_FLOAT,
                    (ByteBuffer) null);

            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffers[i]);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
                    textures[i], 0);
        }

        GlStateManager._bindTexture(0);
    }

    private static MeshData screen() {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.addVertex(-1, -1, 0);
        builder.addVertex(1, -1, 0);
        builder.addVertex(1, 1, 0);
        builder.addVertex(-1, 1, 0);
        return builder.buildOrThrow();
    }
}
