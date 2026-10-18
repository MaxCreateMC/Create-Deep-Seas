package com.maxenonyme.vortexengine.light;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.vortexengine.VortexConfig;
import com.maxenonyme.vortexengine.VortexProfiler;
import com.maxenonyme.vortexengine.atmosphere.Sunlight;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LightVolumes {

    private static final int[][] FACES = {
            { 0, 4, 6, 2 }, { 5, 1, 3, 7 },
            { 0, 1, 5, 4 }, { 3, 2, 6, 7 },
            { 1, 0, 2, 3 }, { 4, 5, 7, 6 }
    };
    private static final VertexFormat FORMAT = DefaultVertexFormat.NEW_ENTITY;
    public static final int SHADOW_UNIT = 5;

    private static ShaderInstance shader;
    private static ShaderInstance composite;
    private static TextureTarget target;
    private static ByteBufferBuilder bytes;
    private static VertexBuffer fixedBuffer;
    private static VertexBuffer movingBuffer;
    private static int fixedCount;
    private static int movingCount;
    private static List<Light> fixedLights = List.of();
    private static List<Light> movingLights = List.of();
    private static int builtVersion = -1;
    private static int builtDistance = -1;
    private static long builtSection = Long.MIN_VALUE;
    private static float fadeRange = 64;
    private static List<Light> nearGlows = List.of();
    private static List<Light> movingAll = List.of();
    private static long glowSection = Long.MIN_VALUE;
    private static int glowSeen = -1;
    private static int anchorX, anchorY, anchorZ;

    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "vortex_light"), FORMAT),
                    loaded -> shader = loaded);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "vortex_composite"),
                    DefaultVertexFormat.POSITION), loaded -> composite = loaded);
        } catch (IOException e) {
            CreateSubmarine.LOGGER.warn("Couldn't load the Vortex light shader", e);
        }
    }

    public static List<Light> activeLights() {
        List<Light> lights = new ArrayList<>(fixedLights);
        lights.addAll(movingLights);
        return lights;
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES)
            return;

        boolean usable = VortexLights.usable();
        Tuning.refresh();
        VortexConfig.LightStyle style = VortexConfig.LIGHT_STYLE.get();
        boolean field = usable && style != VortexConfig.LightStyle.VOLUMES;
        boolean shadows = usable && VortexConfig.SHADOWS.get();
        boolean rays = usable && VortexConfig.WATER_STYLE.get() != VortexConfig.WaterStyle.NONE;
        LightIndex.sync(usable, shadows || field || rays, usable && VortexConfig.AUTO_GLOW.get());

        if (!usable || shader == null || composite == null) {
            fixedLights = movingLights = List.of();
            LightField.forget();
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        int distance = VortexConfig.LIGHT_DISTANCE.get();
        boolean hybrid = style == VortexConfig.LightStyle.HYBRID;
        boolean pixel = style == VortexConfig.LightStyle.PIXEL;

        if (field && !hybrid) {
            fixedLights = List.of();
            fixedCount = 0;
            builtSection = Long.MIN_VALUE;
        } else {
            buildFixed(camera, hybrid ? Math.min(distance, 24) : distance);
        }

        List<Light> spread = buildMoving(event, camera, distance, field);

        VortexProfiler.begin(VortexProfiler.Pass.UPLOAD);
        if (shadows || field || rays)
            ShadowGrid.update(camera, field);
        else
            ShadowGrid.forget();
        VortexProfiler.end(VortexProfiler.Pass.UPLOAD);

        if (field) {
            VortexProfiler.begin(VortexProfiler.Pass.INJECTION);
            int writes = ShadowGrid.inject(spread);
            VortexProfiler.end(VortexProfiler.Pass.INJECTION);
            VortexProfiler.count(VortexProfiler.Counter.TEXEL_WRITES, writes);

            VortexProfiler.begin(VortexProfiler.Pass.PROPAGATION);
            LightField.step(style == VortexConfig.LightStyle.VANILLA || pixel ? 2 : 1);
            VortexProfiler.end(VortexProfiler.Pass.PROPAGATION);
        } else {
            LightField.forget();
        }

        if (pixel) {
            PixelLights.build(camera, spread, distance, event.getModelViewMatrix(), event.getProjectionMatrix(),
                    Minecraft.getInstance().getMainRenderTarget());
            VortexProfiler.count(VortexProfiler.Counter.PIXEL_LIGHTS, PixelLights.count());
        } else {
            PixelLights.forget();
        }

        VortexProfiler.count(VortexProfiler.Counter.FIXED_LIGHTS, fixedCount);
        VortexProfiler.count(VortexProfiler.Counter.BEAMS, movingCount);
        VortexProfiler.count(VortexProfiler.Counter.SPREAD, spread.size());

        if (fixedCount > 0 || movingCount > 0 || LightField.ready() || PixelLights.ready()
                || rays && ShadowGrid.ready())
            draw(event, camera);
    }

    private static void buildFixed(Vec3 camera, int distance) {
        long section = SectionPos.asLong(Mth.floor(camera.x) >> 4, Mth.floor(camera.y) >> 4, Mth.floor(camera.z) >> 4);
        int version = LightIndex.glowVersion();
        if (section == builtSection && version == builtVersion && distance == builtDistance)
            return;

        builtSection = section;
        builtVersion = version;
        builtDistance = distance;
        anchorX = SectionPos.x(section) << 4;
        anchorY = SectionPos.y(section) << 4;
        anchorZ = SectionPos.z(section) << 4;

        List<Light> near = LightIndex.near(camera, distance);
        int max = VortexConfig.MAX_LIGHTS.get();
        fadeRange = distance;

        if (near.size() > max) {
            near.sort(Comparator.comparingDouble(light -> light.distanceSqr(camera)));
            near = near.subList(0, max);
            fadeRange = (float) Math.sqrt(near.get(max - 1).distanceSqr(camera));
        }

        fixedLights = near;
        fixedCount = near.size();
        fixedBuffer = upload(fixedBuffer, near);
    }

    private static List<Light> buildMoving(RenderLevelStageEvent event, Vec3 camera, int distance, boolean field) {
        List<Light> near = new ArrayList<>();
        ClientLevel level = Minecraft.getInstance().level;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);

        if (level != null)
            SubLevelGlows.collect(level, camera, distance, partialTick, near);

        for (PointLight point : VortexLights.moving()) {
            Light light = point.snapshot();
            if (light.intensity() > 0 && light.distanceSqr(camera) <= distance * distance)
                near.add(light);
        }

        movingAll = new ArrayList<>(near);
        if (Wakes.active())
            VortexProfiler.count(VortexProfiler.Counter.WAKES, Wakes.collect(near));

        int max = VortexConfig.MAX_LIGHTS.get();
        if (near.size() > max) {
            near.sort(Comparator.comparingDouble(light -> light.distanceSqr(camera)));
            near = near.subList(0, max);
        }

        List<Light> spread = new ArrayList<>();
        if (field) {
            List<Light> beams = new ArrayList<>();

            for (Light light : near) {
                if (light.cone() <= -0.99f)
                    spread.add(light);
                else
                    beams.add(light);
            }

            near = beams;
        }

        movingLights = near;
        movingCount = near.size();
        movingBuffer = upload(movingBuffer, near);
        return spread;
    }

    private static VertexBuffer upload(VertexBuffer buffer, List<Light> lights) {
        if (lights.isEmpty())
            return buffer;

        if (bytes == null)
            bytes = new ByteBufferBuilder(FORMAT.getVertexSize() * 24 * 64);
        BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, FORMAT);

        for (Light light : lights)
            cube(builder, light);

        if (buffer == null)
            buffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        buffer.bind();
        buffer.upload(builder.buildOrThrow());
        VertexBuffer.unbind();
        return buffer;
    }

    private static void cube(BufferBuilder builder, Light light) {
        float x = (float) (light.x() - anchorX);
        float y = (float) (light.y() - anchorY);
        float z = (float) (light.z() - anchorZ);
        float radius = light.radius() * Tuning.reach();
        float top = Math.max(Math.max(light.red(), light.green()), Math.max(light.blue(), 0.001f));
        int dirX = Math.round(light.dirX() * Short.MAX_VALUE);
        int dirY = Math.round(light.dirY() * Short.MAX_VALUE);
        int dirZ = Math.round(light.dirZ() * Short.MAX_VALUE);
        int cone = Math.round(light.cone() * Short.MAX_VALUE);
        float seed = 0.25f + 0.75f * light.seed();

        for (int[] face : FACES) {
            for (int corner : face) {
                float sx = (corner & 1) == 0 ? -1 : 1;
                float sy = (corner & 2) == 0 ? -1 : 1;
                float sz = (corner & 4) == 0 ? -1 : 1;
                builder.addVertex(x + sx * radius, y + sy * radius, z + sz * radius)
                        .setColor(Tuning.saturate(light.red(), top), Tuning.saturate(light.green(), top),
                                Tuning.saturate(light.blue(), top), Tuning.behavior(light.behavior()) / 255f)
                        .setUv(radius, light.intensity())
                        .setUv1(dirX, dirY)
                        .setUv2(dirZ, cone)
                        .setNormal(sx * seed, sy * seed, sz * seed);
            }
        }
    }

    private static void draw(RenderLevelStageEvent event, Vec3 camera) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        prepareTarget(main);

        Matrix4f view = event.getModelViewMatrix();
        Matrix4f projection = event.getProjectionMatrix();
        float offsetX = (float) (anchorX - camera.x);
        float offsetY = (float) (anchorY - camera.y);
        float offsetZ = (float) (anchorZ - camera.z);
        Matrix4f modelView = new Matrix4f(view).translate(offsetX, offsetY, offsetZ);
        boolean shadows = ShadowGrid.ready() && VortexConfig.SHADOWS.get();
        boolean hybrid = VortexConfig.LIGHT_STYLE.get() == VortexConfig.LightStyle.HYBRID;
        boolean volumes = VortexConfig.LIGHT_STYLE.get() == VortexConfig.LightStyle.VOLUMES;
        boolean haze = VortexConfig.HAZE.get() && VortexConfig.HALO_STYLE.get() != VortexConfig.HaloStyle.NONE
                && !(hybrid && VortexConfig.HALO_STYLE.get() == VortexConfig.HaloStyle.GLOW_FOG);
        boolean underwater = event.getCamera().getFluidInCamera() == FogType.WATER;

        float strength = 1;
        if (hybrid)
            strength = 0.6f;
        else if (volumes)
            strength = 1.3f;

        shader.setSampler("DepthSampler", main.getDepthTextureId());
        shader.safeGetUniform("InvProjMat").set(new Matrix4f(projection).invert());
        shader.safeGetUniform("InvViewMat").set(new Matrix4f(view).invert());
        shader.safeGetUniform("ScreenSize").set((float) main.width, (float) main.height);
        shader.safeGetUniform("CameraFract").set((float) Mth.frac(camera.x), (float) Mth.frac(camera.y),
                (float) Mth.frac(camera.z));
        shader.safeGetUniform("AnchorOffset").set(offsetX, offsetY, offsetZ);
        shader.safeGetUniform("WorldWrap").set((float) Math.floorMod(Mth.floor(camera.x), 1024),
                (float) Math.floorMod(Mth.floor(camera.y), 1024), (float) Math.floorMod(Mth.floor(camera.z), 1024));

        shader.safeGetUniform("Pixelated").set(VortexConfig.PIXEL_LIGHTING.get() ? 1f : 0f);
        shader.safeGetUniform("Shadows").set(shadows ? 1f : 0f);
        shader.safeGetUniform("Haze").set(haze ? 1f : 0f);
        shader.safeGetUniform("Strength").set(strength * VortexConfig.BRIGHTNESS.get() / 100f);
        shader.safeGetUniform("HaloScale").set(volumes ? 0.6f : 0.35f);
        shader.safeGetUniform("HaloCap").set(volumes ? 4.5f : 2.5f);
        float halo = volumes ? 1.6f : 1f;
        shader.safeGetUniform("HaloGain").set(halo * VortexConfig.HALO_STRENGTH.get() / 100f);
        shader.safeGetUniform("FadeRange").set(Math.max(fadeRange, 12));
        shader.safeGetUniform("Murk").set(underwater ? 1f : 0.2f);

        shader.safeGetUniform("ShadowGrid").set(SHADOW_UNIT);
        shader.safeGetUniform("GridOffset").set(Mth.floor(camera.x) - ShadowGrid.originX(),
                Mth.floor(camera.y) - ShadowGrid.originY(), Mth.floor(camera.z) - ShadowGrid.originZ());

        if (shadows) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + SHADOW_UNIT);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, ShadowGrid.texture());
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        }

        VortexProfiler.begin(VortexProfiler.Pass.LIGHTS);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableCull();
        GL11.glCullFace(GL11.GL_FRONT);

        if (fixedCount > 0) {
            fixedBuffer.bind();
            fixedBuffer.drawWithShader(modelView, projection, shader);
        }

        if (movingCount > 0) {
            movingBuffer.bind();
            movingBuffer.drawWithShader(modelView, projection, shader);
        }

        GL11.glCullFace(GL11.GL_BACK);
        VertexBuffer.unbind();
        VortexProfiler.end(VortexProfiler.Pass.LIGHTS);

        if (shadows) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + SHADOW_UNIT);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        }

        boolean fieldSurface = LightField.ready() && !PixelLights.ready();
        boolean caustics = VortexConfig.WATER_STYLE.get() != VortexConfig.WaterStyle.NONE && underwater
                && ShadowGrid.ready();

        if (PixelLights.ready()) {
            VortexProfiler.begin(VortexProfiler.Pass.SURFACE);
            PixelLights.draw(camera, main, view, projection);
            VortexProfiler.end(VortexProfiler.Pass.SURFACE);
        }

        if (fieldSurface || caustics) {
            VortexProfiler.begin(VortexProfiler.Pass.SURFACE);
            drawField(event, camera, main, underwater, fieldSurface);
            VortexProfiler.end(VortexProfiler.Pass.SURFACE);
        }

        main.bindWrite(false);
        VortexProfiler.begin(VortexProfiler.Pass.COMPOSITE);
        composite();
        VortexProfiler.end(VortexProfiler.Pass.COMPOSITE);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    public static List<Light> shaftCandidates(Vec3 camera) {
        long section = SectionPos.asLong(Mth.floor(camera.x) >> 4, Mth.floor(camera.y) >> 4, Mth.floor(camera.z) >> 4);
        int version = LightIndex.glowVersion();
        if (section != glowSection || version != glowSeen) {
            nearGlows = LightIndex.near(camera, 32);
            glowSection = section;
            glowSeen = version;
        }

        List<Light> all = new ArrayList<>(nearGlows);
        all.addAll(movingAll);
        return all;
    }

    private static void drawField(RenderLevelStageEvent event, Vec3 camera, RenderTarget main, boolean underwater,
            boolean light) {
        ShaderInstance surface = LightField.surface();
        ClientLevel level = Minecraft.getInstance().level;
        if (surface == null || level == null)
            return;

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Sunlight sun = Sunlight.of(level, partialTick, camera);
        boolean rays = VortexConfig.WATER_STYLE.get() != VortexConfig.WaterStyle.NONE;

        surface.setSampler("DepthSampler", main.getDepthTextureId());
        surface.setSampler("Heights", ShadowGrid.heights());
        surface.safeGetUniform("InvProjMat").set(new Matrix4f(event.getProjectionMatrix()).invert());
        surface.safeGetUniform("InvViewMat").set(new Matrix4f(event.getModelViewMatrix()).invert());
        surface.safeGetUniform("CameraFract").set((float) Mth.frac(camera.x), (float) Mth.frac(camera.y),
                (float) Mth.frac(camera.z));
        surface.safeGetUniform("WorldWrap").set((float) Math.floorMod(Mth.floor(camera.x), 1024),
                (float) Math.floorMod(Mth.floor(camera.y), 1024), (float) Math.floorMod(Mth.floor(camera.z), 1024));
        surface.safeGetUniform("Pixelated").set(VortexConfig.PIXEL_LIGHTING.get() ? 1f : 0f);
        surface.safeGetUniform("Murk").set(underwater ? 1f : 0.2f);
        float caustics = rays && sun.strength() > 0 ? 0.55f : 0f;
        surface.safeGetUniform("Caustics").set(caustics * VortexConfig.SUNLIGHT.get() / 100f);
        surface.safeGetUniform("SunDir").set(sun.x(), sun.y(), 0f);
        surface.safeGetUniform("SunColor").set(sun.red(), sun.green(), sun.blue());
        surface.safeGetUniform("SurfaceY").set((float) (sun.surface() - camera.y));
        boolean hybrid = VortexConfig.LIGHT_STYLE.get() == VortexConfig.LightStyle.HYBRID;
        float gain = hybrid ? 0.3f : 0.5f;
        surface.safeGetUniform("Gain").set(gain * VortexConfig.BRIGHTNESS.get() / 100f);
        surface.safeGetUniform("FieldLight").set(light ? 1f : 0f);
        surface.safeGetUniform("GridOffset").set(Mth.floor(camera.x) - ShadowGrid.originX(),
                Mth.floor(camera.y) - ShadowGrid.originY(), Mth.floor(camera.z) - ShadowGrid.originZ());
        if (light)
            LightField.setGrid(surface, camera.x, camera.y, camera.z);

        LightField.bind(SHADOW_UNIT, ShadowGrid.texture());
        LightField.bind(LightField.UNIT, light ? LightField.texture() : 0);

        RenderSystem.disableCull();
        RenderSystem.setShader(() -> surface);
        BufferUploader.drawWithShader(screenQuad());
        RenderSystem.enableCull();

        LightField.bind(SHADOW_UNIT, 0);
        LightField.bind(LightField.UNIT, 0);
    }

    private static void composite() {
        GlStateManager._bindTexture(target.getColorTextureId());
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._bindTexture(0);

        RenderSystem.disableDepthTest();
        composite.setSampler("LightSampler", target.getColorTextureId());
        RenderSystem.setShader(() -> composite);

        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        composite.safeGetUniform("Strength").set(1f);
        composite.safeGetUniform("Bloom").set(0f);
        BufferUploader.drawWithShader(screenQuad());

        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        composite.safeGetUniform("Strength").set(0.28f);
        boolean volumes = VortexConfig.LIGHT_STYLE.get() == VortexConfig.LightStyle.VOLUMES;
        float bloom = volumes ? 0.8f : 0.5f;
        bloom *= VortexConfig.BLOOM_STRENGTH.get() / 100f;
        composite.safeGetUniform("Bloom").set(VortexConfig.BLOOM.get() ? bloom : 0f);
        BufferUploader.drawWithShader(screenQuad());
    }

    private static MeshData screenQuad() {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.addVertex(-1, -1, 0);
        builder.addVertex(1, -1, 0);
        builder.addVertex(1, 1, 0);
        builder.addVertex(-1, 1, 0);
        return builder.buildOrThrow();
    }

    private static void prepareTarget(RenderTarget main) {
        if (target == null) {
            target = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            target.setClearColor(0, 0, 0, 0);
        }

        if (target.width != main.width || target.height != main.height)
            target.resize(main.width, main.height, Minecraft.ON_OSX);

        target.clear(Minecraft.ON_OSX);
        target.bindWrite(false);
    }
}
