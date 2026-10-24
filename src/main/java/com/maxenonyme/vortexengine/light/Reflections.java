package com.maxenonyme.vortexengine.light;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.vortexengine.VortexConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;

public class Reflections {

    private static ShaderInstance shader;
    private static TextureTarget copy;

    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "vortex_reflect"),
                    DefaultVertexFormat.POSITION), loaded -> shader = loaded);
        } catch (IOException e) {
            CreateSubmarine.LOGGER.warn("Couldn't load the Vortex reflection shader", e);
        }
    }

    static void draw(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!VortexConfig.REFLECTIONS.get() || shader == null || level == null)
            return;

        RenderTarget main = mc.getMainRenderTarget();
        boolean fabulous = Minecraft.useShaderTransparency();
        RenderTarget surface = fabulous ? mc.levelRenderer.getTranslucentTarget() : main;
        if (surface == null || !fabulous && !OpaqueDepth.ready())
            return;

        copyScene(main);

        Camera camera = event.getCamera();
        Vec3 position = camera.getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 sky = level.getSkyColor(position, partialTick);

        boolean underwater = camera.getFluidInCamera() == FogType.WATER;
        float skyLight = underwater ? 0 : level.getBrightness(LightLayer.SKY, BlockPos.containing(position)) / 15f;

        surface.bindWrite(true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                fabulous ? GlStateManager.SourceFactor.ONE : GlStateManager.SourceFactor.ZERO,
                fabulous ? GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA : GlStateManager.DestFactor.ONE);

        shader.setSampler("SurfaceDepth", surface.getDepthTextureId());
        shader.setSampler("OpaqueDepth", fabulous ? main.getDepthTextureId() : OpaqueDepth.texture());
        shader.setSampler("SceneDepth", main.getDepthTextureId());
        shader.setSampler("SceneSampler", copy.getColorTextureId());

        shader.safeGetUniform("Projection").set(event.getProjectionMatrix());
        shader.safeGetUniform("InvProjMat").set(new Matrix4f(event.getProjectionMatrix()).invert());
        shader.safeGetUniform("InvViewMat").set(new Matrix4f(event.getModelViewMatrix()).invert());
        shader.safeGetUniform("SkyColor").set((float) sky.x, (float) sky.y, (float) sky.z);
        shader.safeGetUniform("SkyLight").set(skyLight);

        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(screen());

        main.bindWrite(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void copyScene(RenderTarget main) {
        if (copy == null)
            copy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        if (copy.width != main.width || copy.height != main.height)
            copy.resize(main.width, main.height, Minecraft.ON_OSX);

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, copy.width, copy.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
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
