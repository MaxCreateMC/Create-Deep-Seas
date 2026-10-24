package com.maxenonyme.vortexengine.light;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.nio.FloatBuffer;

public class OpaqueDepth {

    private static ShaderInstance shader;
    private static int framebuffer;
    private static int texture;
    private static int width, height;
    private static boolean ready;

    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "vortex_depth"),
                    DefaultVertexFormat.POSITION), loaded -> shader = loaded);
        } catch (IOException e) {
            CreateSubmarine.LOGGER.warn("Couldn't load the Vortex depth copy shader", e);
        }
    }

    static boolean ready() {
        return ready;
    }

    static int texture() {
        return texture;
    }

    static void copy(RenderTarget main) {
        ready = false;
        if (shader == null)
            return;

        if (framebuffer == 0)
            framebuffer = GlStateManager.glGenFramebuffers();
        if (texture == 0 || width != main.width || height != main.height)
            allocate(main.width, main.height);

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GlStateManager._viewport(0, 0, width, height);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        shader.setSampler("DepthSampler", main.getDepthTextureId());
        shader.apply();
        VertexBuffer screen = LightField.quad();
        screen.bind();
        screen.draw();
        VertexBuffer.unbind();
        shader.clear();

        main.bindWrite(true);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        ready = true;
    }

    private static void allocate(int newWidth, int newHeight) {
        width = newWidth;
        height = newHeight;

        if (texture == 0)
            texture = GlStateManager._genTexture();

        GlStateManager._bindTexture(texture);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, width, height, 0, GL11.GL_RED, GL11.GL_FLOAT,
                (FloatBuffer) null);
        GlStateManager._bindTexture(0);

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
    }
}
