package com.maxenonyme.vortexengine.light;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.nio.ByteBuffer;

public class LightField {

    public static final int UNIT = 8;
    static final int EMISSION_UNIT = 9;
    private static final int SIZE = ShadowGrid.SIZE;

    private static ShaderInstance propagate;
    private static ShaderInstance surface;
    private static VertexBuffer quad;
    private static final int[] textures = new int[2];
    private static int framebuffer;
    private static int current;
    private static int originX, originY, originZ;
    private static int mode = -1;
    private static boolean ready;

    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "vortex_propagate"),
                    DefaultVertexFormat.POSITION), loaded -> propagate = loaded);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "vortex_field"),
                    DefaultVertexFormat.POSITION), loaded -> surface = loaded);
        } catch (IOException e) {
            CreateSubmarine.LOGGER.warn("Couldn't load the Vortex light field shaders", e);
        }
    }

    public static boolean ready() {
        return ready && ShadowGrid.hasField();
    }

    public static int texture() {
        return textures[current];
    }

    static ShaderInstance surface() {
        return surface;
    }

    static VertexBuffer quad() {
        if (quad == null) {
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION);
            builder.addVertex(-1, -1, 0);
            builder.addVertex(1, -1, 0);
            builder.addVertex(1, 1, 0);
            builder.addVertex(-1, 1, 0);

            quad = new VertexBuffer(VertexBuffer.Usage.STATIC);
            quad.bind();
            quad.upload(builder.buildOrThrow());
            VertexBuffer.unbind();
        }

        return quad;
    }

    static void forget() {
        ready = false;
    }

    static void step(int wanted) {
        if (propagate == null || surface == null || !ShadowGrid.hasField()) {
            ready = false;
            return;
        }

        if (framebuffer == 0)
            create();

        boolean fresh = !ready || wanted != mode;
        if (fresh)
            clear();

        int shiftX = fresh ? 0 : ShadowGrid.originX() - originX;
        int shiftY = fresh ? 0 : ShadowGrid.originY() - originY;
        int shiftZ = fresh ? 0 : ShadowGrid.originZ() - originZ;
        mode = wanted;
        originX = ShadowGrid.originX();
        originY = ShadowGrid.originY();
        originZ = ShadowGrid.originZ();

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GlStateManager._viewport(0, 0, SIZE, SIZE);

        bind(LightVolumes.SHADOW_UNIT, ShadowGrid.texture());
        bind(UNIT, textures[current]);
        bind(EMISSION_UNIT, ShadowGrid.emission());

        propagate.safeGetUniform("Shift").set(shiftX, shiftY, shiftZ);
        propagate.safeGetUniform("Mode").set((float) wanted);
        propagate.safeGetUniform("GameTime").set(RenderSystem.getShaderGameTime());
        propagate.safeGetUniform("OriginWrap").set((float) Math.floorMod(originX, 1024),
                (float) Math.floorMod(originY, 1024), (float) Math.floorMod(originZ, 1024));
        propagate.apply();

        int slice = Uniform.glGetUniformLocation(propagate.getId(), "Slice");
        int target = textures[1 - current];
        VertexBuffer screen = quad();
        screen.bind();

        for (int z = 0; z < SIZE; z++) {
            GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, target, 0, z);
            GL20.glUniform1i(slice, z);
            screen.draw();
        }

        VertexBuffer.unbind();
        propagate.clear();

        bind(LightVolumes.SHADOW_UNIT, 0);
        bind(UNIT, 0);
        bind(EMISSION_UNIT, 0);

        current = 1 - current;
        ready = true;
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    public static void bind(int unit, int texture) {
        RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
    }

    public static void setGrid(ShaderInstance shader, double cameraX, double cameraY, double cameraZ) {
        shader.safeGetUniform("ShadowGrid").set(LightVolumes.SHADOW_UNIT);
        shader.safeGetUniform("Field").set(UNIT);
        shader.safeGetUniform("FieldMode").set((float) mode);
        shader.safeGetUniform("GridOffset").set(Mth.floor(cameraX) - originX, Mth.floor(cameraY) - originY,
                Mth.floor(cameraZ) - originZ);
    }

    private static void create() {
        for (int i = 0; i < 2; i++) {
            textures[i] = GL11.glGenTextures();
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, textures[i]);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
            GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_RGBA16F, SIZE, SIZE, SIZE, 0, GL11.GL_RGBA,
                    GL11.GL_FLOAT, (ByteBuffer) null);
        }

        GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        framebuffer = GlStateManager.glGenFramebuffers();
    }

    private static void clear() {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GlStateManager._clearColor(0, 0, 0, 0);

        for (int texture : textures) {
            for (int z = 0; z < SIZE; z++) {
                GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, texture, 0, z);
                GlStateManager._clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
            }
        }

        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }
}
