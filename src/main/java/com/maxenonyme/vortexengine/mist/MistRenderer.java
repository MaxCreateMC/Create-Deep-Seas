package com.maxenonyme.vortexengine.mist;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.client.VeilUploadGuard;
import com.maxenonyme.vortexengine.VortexConfig;
import com.maxenonyme.vortexengine.VortexProfiler;
import com.maxenonyme.vortexengine.light.Light;
import com.maxenonyme.vortexengine.light.LightField;
import com.maxenonyme.vortexengine.light.LightVolumes;
import com.maxenonyme.vortexengine.light.ShadowGrid;
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
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MistRenderer {

    private static final int FRAMES = 6;
    private static final int LIGHTS = 8;
    private static final VertexFormat FORMAT = DefaultVertexFormat.NEW_ENTITY;

    private record Drawn(Puff puff, float alpha, float x, float y, float z, double distance) {
    }

    private static ShaderInstance shader;
    private static TextureTarget depth;
    private static FloatBuffer lightData;
    private static int lightTexture;

    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "vortex_mist"), FORMAT),
                    loaded -> shader = loaded);
        } catch (IOException e) {
            CreateSubmarine.LOGGER.warn("Couldn't load the Vortex mist shader", e);
        }
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        if (shader == null || MistField.volumes().isEmpty() || !MistField.usable() || MistField.voxel())
            return;

        VortexProfiler.begin(VortexProfiler.Pass.MIST);
        render(event);
        VortexProfiler.end(VortexProfiler.Pass.MIST);
    }

    private static void render(RenderLevelStageEvent event) {
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        List<Drawn> drawn = gather(eye, partialTick, event.getFrustum());
        if (drawn.isEmpty())
            return;

        VortexProfiler.count(VortexProfiler.Counter.PUFFS, drawn.size());
        drawn.sort(Comparator.comparingDouble(Drawn::distance).reversed());
        MeshData mesh = build(drawn, camera);
        if (mesh == null)
            return;

        int lights = uploadLights(eye);
        draw(mesh, event.getProjectionMatrix(), eye, lights, camera.getFluidInCamera() == FogType.WATER);
    }

    private static List<Drawn> gather(Vec3 eye, float partialTick, Frustum frustum) {
        List<Drawn> drawn = new ArrayList<>();

        for (MistVolume volume : MistField.volumes()) {
            for (Puff puff : volume.puffs) {
                double px = Mth.lerp(partialTick, puff.lastX, puff.x);
                double py = Mth.lerp(partialTick, puff.lastY, puff.y);
                double pz = Mth.lerp(partialTick, puff.lastZ, puff.z);
                double half = puff.size / 2;
                if (!frustum.isVisible(new AABB(px - half, py - half, pz - half, px + half, py + half, pz + half)))
                    continue;

                float alpha = volume.density * 0.3f * puff.fade() * volume.edge(px, pz);
                if (alpha <= 0.002f)
                    continue;

                float x = (float) (px - eye.x), y = (float) (py - eye.y), z = (float) (pz - eye.z);
                drawn.add(new Drawn(puff, alpha, x, y, z, x * x + y * y + z * z));
            }
        }

        return drawn;
    }

    private static MeshData build(List<Drawn> drawn, Camera camera) {
        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance().getTextureManager()
                .getTexture(TextureAtlas.LOCATION_PARTICLES);
        TextureAtlasSprite[] frames = new TextureAtlasSprite[FRAMES];

        for (int i = 0; i < FRAMES; i++)
            frames[i] = atlas.getSprite(ResourceLocation.withDefaultNamespace("big_smoke_" + i));

        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, FORMAT);

        for (Drawn entry : drawn) {
            Puff puff = entry.puff();
            TextureAtlasSprite sprite = frames[puff.frame];
            float half = puff.size / 2;
            float flat = half * 0.6f;

            float sideX = left.x() * half, sideY = left.y() * half, sideZ = left.z() * half;
            float topX = up.x() * flat, topY = up.y() * flat, topZ = up.z() * flat;

            vertex(builder, entry, sideX + topX, sideY + topY, sideZ + topZ, sprite.getU0(), sprite.getV0());
            vertex(builder, entry, topX - sideX, topY - sideY, topZ - sideZ, sprite.getU1(), sprite.getV0());
            vertex(builder, entry, -sideX - topX, -sideY - topY, -sideZ - topZ, sprite.getU1(), sprite.getV1());
            vertex(builder, entry, sideX - topX, sideY - topY, sideZ - topZ, sprite.getU0(), sprite.getV1());
        }

        return builder.build();
    }

    private static void vertex(BufferBuilder builder, Drawn entry, float dx, float dy, float dz, float u, float v) {
        Puff puff = entry.puff();
        builder.addVertex(entry.x() + dx, entry.y() + dy, entry.z() + dz)
                .setColor(puff.red, puff.green, puff.blue, entry.alpha())
                .setUv(u, v)
                .setUv1(Math.round(-dx * 1000), Math.round(-dy * 1000))
                .setUv2(Math.round(-dz * 1000), 0)
                .setNormal(0, 1, 0);
    }

    private static int uploadLights(Vec3 eye) {
        List<Light> near = new ArrayList<>();

        for (Light light : LightVolumes.activeLights()) {
            for (MistVolume volume : MistField.volumes()) {
                double reach = light.radius() + volume.radius + 2;
                double dx = light.x() - volume.x, dy = light.y() - volume.y, dz = light.z() - volume.z;
                if (dx * dx + dy * dy + dz * dz < reach * reach) {
                    near.add(light);
                    break;
                }
            }
        }

        near.sort(Comparator.comparingDouble(light -> eye.distanceToSqr(light.x(), light.y(), light.z())));
        int count = Math.min(near.size(), LIGHTS);

        if (lightData == null)
            lightData = MemoryUtil.memAllocFloat(LIGHTS * 4 * 4);
        lightData.clear();

        for (int row = 0; row < 4; row++) {
            for (int i = 0; i < LIGHTS; i++) {
                if (i >= count) {
                    lightData.put(0).put(0).put(0).put(0);
                    continue;
                }

                Light light = near.get(i);

                switch (row) {
                    case 0 -> lightData.put((float) (light.x() - eye.x)).put((float) (light.y() - eye.y))
                            .put((float) (light.z() - eye.z)).put(light.radius());
                    case 1 -> lightData.put(light.red()).put(light.green()).put(light.blue()).put(light.intensity());
                    case 2 -> lightData.put(light.dirX()).put(light.dirY()).put(light.dirZ()).put(light.cone());
                    default -> lightData.put(light.behavior()).put(light.seed()).put(0).put(0);
                }
            }
        }

        lightData.flip();
        VeilUploadGuard.reset();

        if (lightTexture == 0) {
            lightTexture = GlStateManager._genTexture();
            GlStateManager._bindTexture(lightTexture);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, LIGHTS, 4, 0, GL11.GL_RGBA, GL11.GL_FLOAT,
                    (FloatBuffer) null);
        } else {
            GlStateManager._bindTexture(lightTexture);
        }

        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, LIGHTS, 4, GL11.GL_RGBA, GL11.GL_FLOAT, lightData);
        GlStateManager._bindTexture(0);
        return count;
    }

    private static void draw(MeshData mesh, Matrix4f projection, Vec3 eye, int lights, boolean underwater) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        copyDepth(main);
        main.bindWrite(false);

        boolean shadows = ShadowGrid.ready() && VortexConfig.SHADOWS.get();
        boolean field = LightField.ready();

        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
        shader.setSampler("DepthSampler", depth.getDepthTextureId());
        shader.setSampler("LightData", lightTexture);
        shader.safeGetUniform("InvProjMat").set(new Matrix4f(projection).invert());
        shader.safeGetUniform("Scatter").set(underwater ? 0.5f : 0.15f);
        shader.safeGetUniform("LightCount").set(lights);
        shader.safeGetUniform("Shadows").set(shadows ? 1f : 0f);
        shader.safeGetUniform("Pixelated").set(VortexConfig.PIXEL_LIGHTING.get() ? 1f : 0f);
        shader.safeGetUniform("ShadowGrid").set(LightVolumes.SHADOW_UNIT);
        shader.safeGetUniform("CameraFract").set((float) Mth.frac(eye.x), (float) Mth.frac(eye.y),
                (float) Mth.frac(eye.z));
        shader.safeGetUniform("GridOffset").set(Mth.floor(eye.x) - ShadowGrid.originX(),
                Mth.floor(eye.y) - ShadowGrid.originY(), Mth.floor(eye.z) - ShadowGrid.originZ());
        shader.safeGetUniform("FieldMode").set(0f);

        if (shadows) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + LightVolumes.SHADOW_UNIT);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, ShadowGrid.texture());
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        }

        if (field) {
            LightField.setGrid(shader, eye.x, eye.y, eye.z);
            LightField.bind(LightField.UNIT, LightField.texture());
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        BufferUploader.drawWithShader(mesh);

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();

        if (field)
            LightField.bind(LightField.UNIT, 0);

        if (shadows) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + LightVolumes.SHADOW_UNIT);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        }
    }

    private static void copyDepth(RenderTarget main) {
        if (depth == null)
            depth = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        if (main.isStencilEnabled() && !depth.isStencilEnabled())
            depth.enableStencil();
        if (depth.width != main.width || depth.height != main.height)
            depth.resize(main.width, main.height, Minecraft.ON_OSX);

        depth.copyDepthFrom(main);
    }
}
