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

    record Layer(RenderTarget scene, int opaqueDepth, float share, boolean translucent) {
    }

    private static ShaderInstance shader;
    private static ShaderInstance composite;
    private static TextureTarget target;
    private static ByteBufferBuilder bytes;
    private static VertexBuffer movingBuffer;
    private static int movingCount;
    private static List<Light> movingLights = List.of();
    private static float fadeRange = 64;
    private static List<Light> movingAll = List.of();
    private static int anchorX, anchorY, anchorZ;
    private static boolean drawn;

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
        return movingLights;
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            boolean lit = movingCount > 0 || LightField.ready() || PixelLights.ready();
            Layer layer = translucentLayer();
            if (drawn && lit && layer != null)
                draw(event, event.getCamera().getPosition(), layer);

            if (VortexLights.usable())
                Reflections.draw(event);

            drawn = false;
            return;
        }

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES)
            return;

        drawn = false;

        boolean usable = VortexLights.usable();
        Tuning.refresh();
        boolean rays = usable && VortexConfig.WATER_STYLE.get() != VortexConfig.WaterStyle.NONE;
        LightIndex.sync(usable, usable, usable && VortexConfig.AUTO_GLOW.get());

        if (!usable || shader == null || composite == null) {
            movingLights = List.of();
            LightField.forget();
            return;
        }

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (!Minecraft.useShaderTransparency())
            OpaqueDepth.copy(main);

        LightHistory.advance();

        Vec3 camera = event.getCamera().getPosition();
        int distance = VortexConfig.LIGHT_DISTANCE.get();
        boolean pixel = VortexConfig.lightStyle() == VortexConfig.LightStyle.PIXEL;

        List<Light> spread = buildMoving(event, camera, distance);
        boolean underwater = event.getCamera().getFluidInCamera() == FogType.WATER;
        boolean needed = !movingAll.isEmpty() || !spread.isEmpty() || rays && underwater
                || LightIndex.emittersWithin(camera, Math.max(distance, 64) + 16);

        VortexProfiler.begin(VortexProfiler.Pass.UPLOAD);
        if (needed) {
            ShadowGrid.update(camera);
            EntityShadows.update(camera, event.getPartialTick().getGameTimeDeltaPartialTick(true));
        }
        VortexProfiler.end(VortexProfiler.Pass.UPLOAD);

        boolean field = needed && (ShadowGrid.lit() || !spread.isEmpty());

        if (field) {
            VortexProfiler.begin(VortexProfiler.Pass.INJECTION);
            int writes = ShadowGrid.inject(spread);
            VortexProfiler.end(VortexProfiler.Pass.INJECTION);
            VortexProfiler.count(VortexProfiler.Counter.TEXEL_WRITES, writes);

            VortexProfiler.begin(VortexProfiler.Pass.PROPAGATION);
            LightField.step(pixel ? 2 : 1);
            VortexProfiler.end(VortexProfiler.Pass.PROPAGATION);
        } else {
            LightField.forget();
        }

        if (pixel) {
            PixelLights.build(camera, spread, distance, event.getModelViewMatrix(), event.getProjectionMatrix(), main);
            VortexProfiler.count(VortexProfiler.Counter.PIXEL_LIGHTS, PixelLights.count());
        } else {
            PixelLights.forget();
        }

        VortexProfiler.count(VortexProfiler.Counter.BEAMS, movingCount);
        VortexProfiler.count(VortexProfiler.Counter.SPREAD, spread.size());

        if (movingCount > 0 || LightField.ready() || PixelLights.ready()
                || rays && underwater && ShadowGrid.ready()) {
            draw(event, camera, new Layer(main, main.getDepthTextureId(), 1, false));
            drawn = true;
        }
    }

    private static List<Light> buildMoving(RenderLevelStageEvent event, Vec3 camera, int distance) {
        long section = SectionPos.asLong(Mth.floor(camera.x) >> 4, Mth.floor(camera.y) >> 4, Mth.floor(camera.z) >> 4);
        anchorX = SectionPos.x(section) << 4;
        anchorY = SectionPos.y(section) << 4;
        anchorZ = SectionPos.z(section) << 4;
        fadeRange = distance;

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

        int max = VortexConfig.MAX_LIGHTS.get();
        if (near.size() > max) {
            near.sort(Comparator.comparingDouble(light -> light.distanceSqr(camera)));
            near = near.subList(0, max);
        }

        List<Light> spread = new ArrayList<>();
        List<Light> beams = new ArrayList<>();

        for (Light light : near) {
            if (light.cone() <= -0.99f)
                spread.add(light);
            else
                beams.add(light);
        }

        movingLights = beams;
        movingCount = beams.size();
        movingBuffer = upload(movingBuffer, beams);
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

    private static Layer translucentLayer() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();

        if (Minecraft.useShaderTransparency()) {
            RenderTarget layer = mc.levelRenderer.getTranslucentTarget();
            return layer == null ? null : new Layer(layer, main.getDepthTextureId(), 1, true);
        }

        return OpaqueDepth.ready() ? new Layer(main, OpaqueDepth.texture(), 0.5f, true) : null;
    }

    private static void draw(RenderLevelStageEvent event, Vec3 camera, Layer layer) {
        RenderTarget scene = layer.scene();
        boolean translucent = layer.translucent();
        prepareTarget(scene);

        Matrix4f view = event.getModelViewMatrix();
        Matrix4f projection = event.getProjectionMatrix();
        float offsetX = (float) (anchorX - camera.x);
        float offsetY = (float) (anchorY - camera.y);
        float offsetZ = (float) (anchorZ - camera.z);
        Matrix4f modelView = new Matrix4f(view).translate(offsetX, offsetY, offsetZ);
        boolean shadows = ShadowGrid.ready() && VortexConfig.SHADOWS.get();
        boolean haze = !translucent && VortexConfig.HAZE.get()
                && VortexConfig.HALO_STYLE.get() != VortexConfig.HaloStyle.NONE;
        boolean underwater = event.getCamera().getFluidInCamera() == FogType.WATER;

        shader.setSampler("DepthSampler", scene.getDepthTextureId());
        shader.setSampler("OpaqueDepth", layer.opaqueDepth());
        shader.safeGetUniform("Translucent").set(translucent ? 1f : 0f);
        shader.safeGetUniform("LightScale").set((float) lightScale());
        shader.safeGetUniform("Jitter").set(jitterX(translucent), jitterY(translucent));
        shader.safeGetUniform("InvProjMat").set(new Matrix4f(projection).invert());
        shader.safeGetUniform("InvViewMat").set(new Matrix4f(view).invert());
        shader.safeGetUniform("ScreenSize").set((float) scene.width, (float) scene.height);
        shader.safeGetUniform("CameraFract").set((float) Mth.frac(camera.x), (float) Mth.frac(camera.y),
                (float) Mth.frac(camera.z));
        shader.safeGetUniform("AnchorOffset").set(offsetX, offsetY, offsetZ);
        shader.safeGetUniform("WorldWrap").set((float) Math.floorMod(Mth.floor(camera.x), 1024),
                (float) Math.floorMod(Mth.floor(camera.y), 1024), (float) Math.floorMod(Mth.floor(camera.z), 1024));

        shader.safeGetUniform("Pixelated").set(VortexConfig.PIXEL_LIGHTING.get() ? 1f : 0f);
        shader.safeGetUniform("Shadows").set(shadows ? 1f : 0f);
        shader.safeGetUniform("Haze").set(haze ? 1f : 0f);
        shader.safeGetUniform("Strength").set(layer.share() * VortexConfig.BRIGHTNESS.get() / 100f);
        shader.safeGetUniform("HaloScale").set(0.35f);
        shader.safeGetUniform("HaloCap").set(2.5f);
        shader.safeGetUniform("HaloGain").set(VortexConfig.HALO_STRENGTH.get() / 100f);
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
        boolean caustics = !translucent && VortexConfig.WATER_STYLE.get() != VortexConfig.WaterStyle.NONE
                && underwater && ShadowGrid.ready();

        if (PixelLights.ready()) {
            VortexProfiler.begin(VortexProfiler.Pass.SURFACE);
            PixelLights.draw(camera, layer, view, projection);
            VortexProfiler.end(VortexProfiler.Pass.SURFACE);
        }

        if (fieldSurface || caustics) {
            VortexProfiler.begin(VortexProfiler.Pass.SURFACE);
            drawField(event, camera, layer, underwater, fieldSurface);
            VortexProfiler.end(VortexProfiler.Pass.SURFACE);
        }

        VortexProfiler.begin(VortexProfiler.Pass.COMPOSITE);
        if (!translucent && LightHistory.active()) {
            int history = LightHistory.accumulate(scene, target.getColorTextureId(), camera, view, projection);
            scene.bindWrite(true);
            composite(scene, projection, true, history, 1);
        } else {
            scene.bindWrite(true);
            composite(scene, projection, !translucent, target.getColorTextureId(), lightScale());
        }
        VortexProfiler.end(VortexProfiler.Pass.COMPOSITE);

        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    public static List<Light> shaftCandidates() {
        return movingAll;
    }

    private static void drawField(RenderLevelStageEvent event, Vec3 camera, Layer layer, boolean underwater,
            boolean light) {
        RenderTarget scene = layer.scene();
        boolean translucent = layer.translucent();

        ShaderInstance surface = LightField.surface();
        ClientLevel level = Minecraft.getInstance().level;
        if (surface == null || level == null)
            return;

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Sunlight sun = Sunlight.of(level, partialTick, camera);
        boolean rays = VortexConfig.WATER_STYLE.get() != VortexConfig.WaterStyle.NONE;

        surface.setSampler("DepthSampler", scene.getDepthTextureId());
        surface.setSampler("OpaqueDepth", layer.opaqueDepth());
        surface.setSampler("Heights", ShadowGrid.heights());
        surface.safeGetUniform("Translucent").set(translucent ? 1f : 0f);
        surface.safeGetUniform("LightScale").set((float) lightScale());
        surface.safeGetUniform("Jitter").set(jitterX(translucent), jitterY(translucent));
        surface.safeGetUniform("InvProjMat").set(new Matrix4f(event.getProjectionMatrix()).invert());
        surface.safeGetUniform("InvViewMat").set(new Matrix4f(event.getModelViewMatrix()).invert());
        surface.safeGetUniform("CameraFract").set((float) Mth.frac(camera.x), (float) Mth.frac(camera.y),
                (float) Mth.frac(camera.z));
        surface.safeGetUniform("WorldWrap").set((float) Math.floorMod(Mth.floor(camera.x), 1024),
                (float) Math.floorMod(Mth.floor(camera.y), 1024), (float) Math.floorMod(Mth.floor(camera.z), 1024));
        surface.safeGetUniform("Pixelated").set(VortexConfig.PIXEL_LIGHTING.get() ? 1f : 0f);
        surface.safeGetUniform("Murk").set(underwater ? 1f : 0.2f);
        float caustics = rays && !translucent && sun.strength() > 0 ? 0.55f : 0f;
        surface.safeGetUniform("Caustics").set(caustics * VortexConfig.SUNLIGHT.get() / 100f);
        surface.safeGetUniform("SunDir").set(sun.x(), sun.y(), 0f);
        surface.safeGetUniform("SunColor").set(sun.red(), sun.green(), sun.blue());
        surface.safeGetUniform("SurfaceY").set((float) (sun.surface() - camera.y));
        float gain = 0.5f * layer.share();
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

    private static void composite(RenderTarget scene, Matrix4f projection, boolean withBloom, int light, int scale) {
        boolean blooming = withBloom && VortexConfig.BLOOM.get() && VortexConfig.BLOOM_STRENGTH.get() > 0;
        GlStateManager._bindTexture(light);
        if (blooming) {
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 5);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        }
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
                blooming ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._bindTexture(0);

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        composite.setSampler("LightSampler", light);
        composite.setSampler("DepthSampler", scene.getDepthTextureId());
        composite.safeGetUniform("InvProjMat").set(new Matrix4f(projection).invert());
        composite.safeGetUniform("LightScale").set((float) scale);
        RenderSystem.setShader(() -> composite);

        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        composite.safeGetUniform("Strength").set(1f);
        composite.safeGetUniform("Bloom").set(0f);
        BufferUploader.drawWithShader(screenQuad());

        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        composite.safeGetUniform("Strength").set(0.28f);
        float bloom = 0.5f * VortexConfig.BLOOM_STRENGTH.get() / 100f;
        composite.safeGetUniform("Bloom").set(blooming ? bloom : 0f);
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

    static int lightScale() {
        return 1 << VortexConfig.LIGHT_RESOLUTION.get().ordinal();
    }

    static float jitterX(boolean translucent) {
        return translucent ? 0 : LightHistory.jitterX();
    }

    static float jitterY(boolean translucent) {
        return translucent ? 0 : LightHistory.jitterY();
    }

    private static void prepareTarget(RenderTarget scene) {
        int width = Math.max(1, scene.width / lightScale());
        int height = Math.max(1, scene.height / lightScale());

        if (target == null) {
            target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            target.setClearColor(0, 0, 0, 0);
        }

        if (target.width != width || target.height != height)
            target.resize(width, height, Minecraft.ON_OSX);

        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
    }
}
