package com.maxenonyme.vortexengine.atmosphere;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.client.VeilUploadGuard;
import com.maxenonyme.vortexengine.VortexConfig;
import com.maxenonyme.vortexengine.VortexProfiler;
import com.maxenonyme.vortexengine.light.Light;
import com.maxenonyme.vortexengine.light.LightField;
import com.maxenonyme.vortexengine.light.LightVolumes;
import com.maxenonyme.vortexengine.light.ShadowGrid;
import com.maxenonyme.vortexengine.light.VortexLights;
import com.maxenonyme.vortexengine.mist.MistField;
import com.maxenonyme.vortexengine.mist.MistVolume;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.FloatBuffer;

public class Atmosphere {

    private static final int VOLUMES = 8;
    private static final int BEAMS = 4;

    private static ShaderInstance shader;
    private static ShaderInstance blit;
    private static TextureTarget target;
    private static FloatBuffer mistData;
    private static FloatBuffer beamData;
    private static int mistTexture;
    private static int beamTexture;

    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "vortex_atmosphere"),
                    DefaultVertexFormat.POSITION), loaded -> shader = loaded);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "vortex_blit"),
                    DefaultVertexFormat.POSITION), loaded -> blit = loaded);
        } catch (IOException e) {
            CreateSubmarine.LOGGER.warn("Couldn't load the Vortex atmosphere shaders", e);
        }
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || shader == null || blit == null || !VortexLights.usable() || !ShadowGrid.ready())
            return;

        Vec3 eye = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        boolean underwater = event.getCamera().getFluidInCamera() == FogType.WATER;
        Sunlight sun = Sunlight.of(level, partialTick, eye);

        boolean field = LightField.ready();
        boolean glowing = field && VortexConfig.HAZE.get()
                && VortexConfig.HALO_STYLE.get() == VortexConfig.HaloStyle.GLOW_FOG;
        boolean blocky = VortexConfig.LIGHT_STYLE.get() == VortexConfig.LightStyle.PIXEL;
        float glow = 0;

        if (glowing)
            glow = (underwater ? 0.05f : 0.0125f) * (blocky ? 1.6f : 1f) * VortexConfig.HALO_STRENGTH.get() / 100f;
        
        VortexConfig.WaterStyle water = VortexConfig.WATER_STYLE.get();
        float shafts = water != VortexConfig.WaterStyle.NONE && underwater && sun.strength() > 0 ? 0.035f : 0;
        shafts *= VortexConfig.SUNLIGHT.get() / 100f;
        int beams = water == VortexConfig.WaterStyle.ALL_RAYS && underwater ? uploadBeams(event, eye) : 0;
        int mists = MistField.voxel() ? uploadMist(level, eye) : 0;
        int motes = underwater ? VortexConfig.MOTES_STYLE.get().ordinal() : 0;

        if (glow <= 0 && shafts <= 0 && mists == 0 && motes == 0 && beams == 0)
            return;

        VortexProfiler.count(VortexProfiler.Counter.MIST_VOLUMES, mists);
        VortexProfiler.begin(VortexProfiler.Pass.ATMOSPHERE);
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        prepareTarget(main);

        shader.setSampler("DepthSampler", main.getDepthTextureId());
        shader.setSampler("Heights", ShadowGrid.heights());
        shader.setSampler("MistData", mistTexture);
        shader.setSampler("BeamData", beamTexture);
        shader.safeGetUniform("InvProjMat").set(new Matrix4f(event.getProjectionMatrix()).invert());
        shader.safeGetUniform("InvViewMat").set(new Matrix4f(event.getModelViewMatrix()).invert());
        shader.safeGetUniform("TargetSize").set((float) target.width, (float) target.height);
        shader.safeGetUniform("CameraFract").set((float) Mth.frac(eye.x), (float) Mth.frac(eye.y),
                (float) Mth.frac(eye.z));
        shader.safeGetUniform("WorldWrap").set((float) Math.floorMod(Mth.floor(eye.x), 1024),
                (float) Math.floorMod(Mth.floor(eye.y), 1024), (float) Math.floorMod(Mth.floor(eye.z), 1024));

        shader.safeGetUniform("GlowFog").set(glow);
        shader.safeGetUniform("Shafts").set(shafts);
        shader.safeGetUniform("SunDir").set(sun.x(), sun.y(), 0f);
        shader.safeGetUniform("SunColor").set(sun.red(), sun.green(), sun.blue());
        shader.safeGetUniform("SurfaceY").set((float) (sun.surface() - eye.y));
        shader.safeGetUniform("BeamCount").set(beams);

        shader.safeGetUniform("MistCount").set(mists);
        shader.safeGetUniform("MistScatter").set(underwater ? 0.5f : 0.15f);
        shader.safeGetUniform("Motes").set((float) motes);
        shader.safeGetUniform("Blocky").set(blocky ? 1f : 0f);
        shader.safeGetUniform("MoteDensity").set(underwater ? 1f : 0.25f);

        shader.safeGetUniform("FieldReady").set(field ? 1f : 0f);
        shader.safeGetUniform("ShadowGrid").set(LightVolumes.SHADOW_UNIT);
        shader.safeGetUniform("GridOffset").set(Mth.floor(eye.x) - ShadowGrid.originX(),
                Mth.floor(eye.y) - ShadowGrid.originY(), Mth.floor(eye.z) - ShadowGrid.originZ());
        if (field)
            LightField.setGrid(shader, eye.x, eye.y, eye.z);

        LightField.bind(LightVolumes.SHADOW_UNIT, ShadowGrid.texture());
        LightField.bind(LightField.UNIT, field ? LightField.texture() : 0);

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(screenQuad());

        LightField.bind(LightVolumes.SHADOW_UNIT, 0);
        LightField.bind(LightField.UNIT, 0);

        main.bindWrite(true);
        blit.setSampler("AtmosphereSampler", target.getColorTextureId());
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.SRC_ALPHA,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(() -> blit);
        BufferUploader.drawWithShader(screenQuad());

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        VortexProfiler.end(VortexProfiler.Pass.ATMOSPHERE);
    }

    private static int uploadBeams(RenderLevelStageEvent event, Vec3 eye) {
        Matrix4f clip = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        Vector4f point = new Vector4f();
        Light[] picked = new Light[BEAMS];
        float[] scores = new float[BEAMS];
        float[] places = new float[BEAMS * 4];

        for (Light light : LightVolumes.shaftCandidates(eye)) {
            if (light.cone() > -0.99f || light.intensity() <= 0)
                continue;

            float x = (float) (light.x() - eye.x), y = (float) (light.y() - eye.y), z = (float) (light.z() - eye.z);
            float distance = Mth.sqrt(x * x + y * y + z * z);
            if (distance < 0.5f || distance > 32)
                continue;

            point.set(x, y, z, 1);
            clip.transform(point);
            if (point.w < 0.1f)
                continue;

            float u = point.x / point.w * 0.5f + 0.5f, v = point.y / point.w * 0.5f + 0.5f;
            if (u < -0.3f || u > 1.3f || v < -0.3f || v > 1.3f)
                continue;

            float score = light.intensity() * Math.min(1, light.radius() / 8) * (1 - distance / 32);

            for (int i = 0; i < BEAMS; i++) {
                if (picked[i] != null && score <= scores[i])
                    continue;

                for (int k = BEAMS - 1; k > i; k--) {
                    picked[k] = picked[k - 1];
                    scores[k] = scores[k - 1];
                    System.arraycopy(places, (k - 1) * 4, places, k * 4, 4);
                }

                picked[i] = light;
                scores[i] = score;
                places[i * 4] = u;
                places[i * 4 + 1] = v;
                places[i * 4 + 2] = distance;
                places[i * 4 + 3] = light.radius();
                break;
            }
        }

        if (beamData == null)
            beamData = MemoryUtil.memAllocFloat(BEAMS * 2 * 4);
        beamData.clear();
        beamData.put(places);

        int count = 0;
        for (int i = 0; i < BEAMS; i++) {
            Light light = picked[i];
            if (light == null) {
                beamData.put(0).put(0).put(0).put(0);
                continue;
            }

            float top = Math.max(Math.max(light.red(), light.green()), Math.max(light.blue(), 0.001f));
            float strength = scores[i] * 0.35f;
            beamData.put(light.red() / top * strength).put(light.green() / top * strength)
                    .put(light.blue() / top * strength).put(0);
            count++;
        }

        beamData.flip();
        VeilUploadGuard.reset();

        if (beamTexture == 0) {
            beamTexture = GlStateManager._genTexture();
            GlStateManager._bindTexture(beamTexture);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, BEAMS, 2, 0, GL11.GL_RGBA, GL11.GL_FLOAT,
                    (FloatBuffer) null);
        } else {
            GlStateManager._bindTexture(beamTexture);
        }

        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, BEAMS, 2, GL11.GL_RGBA, GL11.GL_FLOAT, beamData);
        GlStateManager._bindTexture(0);
        return count;
    }

    private static int uploadMist(ClientLevel level, Vec3 eye) {
        if (mistData == null)
            mistData = MemoryUtil.memAllocFloat(VOLUMES * 2 * 4);
        mistData.clear();

        float[] shapes = new float[VOLUMES * 4];
        float[] tones = new float[VOLUMES * 4];
        int count = 0;

        for (MistVolume volume : MistField.volumes()) {
            double reach = 48 + volume.radius;
            if (count == VOLUMES || eye.distanceToSqr(volume.x, volume.y, volume.z) > reach * reach)
                continue;

            float ambient = Math.max(0.2f, LightTexture.getBrightness(level.dimensionType(),
                    level.getMaxLocalRawBrightness(BlockPos.containing(volume.x, volume.y + 1, volume.z))));
            int i = count * 4;

            shapes[i] = (float) (volume.x - eye.x);
            shapes[i + 1] = (float) (volume.y - eye.y);
            shapes[i + 2] = (float) (volume.z - eye.z);
            shapes[i + 3] = volume.radius;
            tones[i] = volume.red * ambient;
            tones[i + 1] = volume.green * ambient;
            tones[i + 2] = volume.blue * ambient;
            tones[i + 3] = volume.density;
            count++;
        }

        mistData.put(shapes).put(tones).flip();
        VeilUploadGuard.reset();

        if (mistTexture == 0) {
            mistTexture = GlStateManager._genTexture();
            GlStateManager._bindTexture(mistTexture);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, VOLUMES, 2, 0, GL11.GL_RGBA, GL11.GL_FLOAT,
                    (FloatBuffer) null);
        } else {
            GlStateManager._bindTexture(mistTexture);
        }

        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, VOLUMES, 2, GL11.GL_RGBA, GL11.GL_FLOAT, mistData);
        GlStateManager._bindTexture(0);
        return count;
    }

    private static void prepareTarget(RenderTarget main) {
        int width = Math.max(1, main.width / 2);
        int height = Math.max(1, main.height / 2);

        if (target == null) {
            target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            target.setClearColor(0, 0, 0, 1);
            target.setFilterMode(GL11.GL_LINEAR);
        }

        if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
        }

        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
    }

    private static MeshData screenQuad() {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.addVertex(-1, -1, 0);
        builder.addVertex(1, -1, 0);
        builder.addVertex(1, 1, 0);
        builder.addVertex(-1, 1, 0);
        return builder.buildOrThrow();
    }
}
