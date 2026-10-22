package com.maxenonyme.vortexengine.light;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.client.VeilUploadGuard;
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
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PixelLights {

    private static final int TILE = 32;
    private static final int PER_TILE = 32;
    private static final int MAX = 4096;
    private static final int INDEX_WIDTH = 256;

    private record Source(double x, double y, double z, int packed, float seed) {
    }

    private static ShaderInstance shader;
    private static List<Source> fixed = List.of();
    private static long builtSection = Long.MIN_VALUE;
    private static int builtVersion = -1;
    private static int builtDistance = -1;
    private static FloatBuffer data;
    private static IntBuffer tiles;
    private static ShortBuffer indices;
    private static int dataTexture, tileTexture, indexTexture;
    private static int tileWidth, tileHeight, indexRows;
    private static int count;
    private static boolean ready;

    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "vortex_pixel"),
                    DefaultVertexFormat.POSITION), loaded -> shader = loaded);
        } catch (IOException e) {
            CreateSubmarine.LOGGER.warn("Couldn't load the Vortex pixel light shader", e);
        }
    }

    static boolean ready() {
        return ready && shader != null;
    }

    static int count() {
        return count;
    }

    static void forget() {
        ready = false;
        builtSection = Long.MIN_VALUE;
    }

    static void build(Vec3 camera, List<Light> moving, int distance, Matrix4f view, Matrix4f projection,
            RenderTarget main) {
        if (shader == null) {
            ready = false;
            return;
        }

        refreshFixed(camera, distance);
        List<Source> sources = new ArrayList<>(fixed.size() + moving.size());
        sources.addAll(fixed);

        for (Light light : moving) {
            int packed = ShadowGrid.pack(light.red(), light.green(), light.blue(), light.radius(), light.intensity(),
                    light.behavior());
            sources.add(new Source(light.x(), light.y(), light.z(), packed, light.seed()));
        }

        sources.sort(Comparator.comparingDouble(source -> camera.distanceToSqr(source.x(), source.y(), source.z())));
        if (sources.size() > MAX)
            sources = sources.subList(0, MAX);

        count = sources.size();
        bin(sources, camera, view, projection, main);
        ready = true;
    }

    private static void refreshFixed(Vec3 camera, int distance) {
        long section = SectionPos.asLong(Mth.floor(camera.x) >> 4, Mth.floor(camera.y) >> 4, Mth.floor(camera.z) >> 4);
        int version = LightIndex.glowVersion();
        if (section == builtSection && version == builtVersion && distance == builtDistance)
            return;

        builtSection = section;
        builtVersion = version;
        builtDistance = distance;

        List<Source> found = new ArrayList<>();
        for (LightIndex.Emitter emitter : LightIndex.emittersNear(camera, distance))
            found.add(new Source(emitter.x(), emitter.y(), emitter.z(), emitter.packed(),
                    Light.seedOf(emitter.x(), emitter.y(), emitter.z())));
        fixed = found;
    }

    private static void bin(List<Source> sources, Vec3 camera, Matrix4f view, Matrix4f projection,
            RenderTarget main) {
        tileWidth = (main.width + TILE - 1) / TILE;
        tileHeight = (main.height + TILE - 1) / TILE;
        int tileCount = tileWidth * tileHeight;
        int[] filled = new int[tileCount];
        short[] lists = new short[tileCount * PER_TILE];

        Matrix4f clip = new Matrix4f(projection).mul(view);
        Vector4f corner = new Vector4f();
        prepareData(sources.size());

        for (int i = 0; i < sources.size(); i++) {
            Source source = sources.get(i);
            float x = (float) (source.x() - camera.x);
            float y = (float) (source.y() - camera.y);
            float z = (float) (source.z() - camera.z);
            int code = (source.packed() & 255) - 1;
            float level = (code >> 2) / 62f * 15;
            write(i, x, y, z, level, source.packed(), code & 3, source.seed());

            float reach = level + 0.5f;
            float minX = 1, minY = 1, maxX = -1, maxY = -1;
            boolean whole = false;

            for (int c = 0; c < 8 && !whole; c++) {
                corner.set(x + ((c & 1) == 0 ? -reach : reach), y + ((c & 2) == 0 ? -reach : reach),
                        z + ((c & 4) == 0 ? -reach : reach), 1);
                clip.transform(corner);
                if (corner.w < 0.05f) {
                    whole = true;
                    continue;
                }

                minX = Math.min(minX, corner.x / corner.w);
                maxX = Math.max(maxX, corner.x / corner.w);
                minY = Math.min(minY, corner.y / corner.w);
                maxY = Math.max(maxY, corner.y / corner.w);
            }

            if (!whole && (maxX < -1 || minX > 1 || maxY < -1 || minY > 1))
                continue;

            int x0 = whole ? 0 : tileOf(minX, main.width, tileWidth);
            int x1 = whole ? tileWidth - 1 : tileOf(maxX, main.width, tileWidth);
            int y0 = whole ? 0 : tileOf(minY, main.height, tileHeight);
            int y1 = whole ? tileHeight - 1 : tileOf(maxY, main.height, tileHeight);

            for (int ty = y0; ty <= y1; ty++) {
                for (int tx = x0; tx <= x1; tx++) {
                    int tile = tx + ty * tileWidth;
                    if (filled[tile] < PER_TILE)
                        lists[tile * PER_TILE + filled[tile]++] = (short) i;
                }
            }
        }

        upload(sources.size(), filled, lists);
    }

    private static int tileOf(float ndc, int size, int tiles) {
        return Mth.clamp((int) ((ndc * 0.5f + 0.5f) * size) / TILE, 0, tiles - 1);
    }

    private static void prepareData(int lights) {
        int needed = Math.max(lights, 1) * 3 * 4;
        if (data == null || data.capacity() < needed) {
            if (data != null)
                MemoryUtil.memFree(data);
            data = MemoryUtil.memAllocFloat(MAX * 3 * 4);
        }

        data.clear();
        data.limit(needed);
    }

    private static void write(int i, float x, float y, float z, float level, int packed, int rhythm, float seed) {
        int width = data.limit() / 12;
        int row0 = i * 4, row1 = (width + i) * 4, row2 = (width * 2 + i) * 4;

        data.put(row0, x).put(row0 + 1, y).put(row0 + 2, z).put(row0 + 3, level);
        data.put(row1, (packed >>> 24) / 255f).put(row1 + 1, (packed >>> 16 & 255) / 255f)
                .put(row1 + 2, (packed >>> 8 & 255) / 255f).put(row1 + 3, rhythm);
        data.put(row2, seed).put(row2 + 1, 0).put(row2 + 2, 0).put(row2 + 3, 0);
    }

    private static void upload(int lights, int[] filled, short[] lists) {
        int tileCount = filled.length;
        int total = 0;
        for (int amount : filled)
            total += amount;

        int rows = Math.max(1, (total + INDEX_WIDTH - 1) / INDEX_WIDTH);
        if (tiles == null || tiles.capacity() < tileCount) {
            if (tiles != null)
                MemoryUtil.memFree(tiles);
            tiles = MemoryUtil.memAllocInt(tileCount);
        }
        if (indices == null || indices.capacity() < rows * INDEX_WIDTH) {
            if (indices != null)
                MemoryUtil.memFree(indices);
            indices = MemoryUtil.memAllocShort(rows * INDEX_WIDTH);
        }

        tiles.clear();
        indices.clear();
        int offset = 0;

        for (int tile = 0; tile < tileCount; tile++) {
            tiles.put(offset * 64 + filled[tile]);
            for (int k = 0; k < filled[tile]; k++)
                indices.put(lists[tile * PER_TILE + k]);
            offset += filled[tile];
        }

        while (indices.position() < rows * INDEX_WIDTH)
            indices.put((short) 0);

        tiles.flip();
        indices.flip();
        data.position(0);

        VeilUploadGuard.reset();
        int width = Math.max(lights, 1);

        if (dataTexture == 0)
            dataTexture = create(GL30.GL_RGBA32F, MAX, 3, GL11.GL_RGBA, GL11.GL_FLOAT);
        GlStateManager._bindTexture(dataTexture);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, 3, GL11.GL_RGBA, GL11.GL_FLOAT, data);

        if (tileTexture == 0)
            tileTexture = GlStateManager._genTexture();
        GlStateManager._bindTexture(tileTexture);
        nearest();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32I, tileWidth, tileHeight, 0, GL30.GL_RED_INTEGER,
                GL11.GL_INT, tiles);

        if (indexTexture == 0)
            indexTexture = GlStateManager._genTexture();
        GlStateManager._bindTexture(indexTexture);
        nearest();
        if (rows != indexRows) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R16I, INDEX_WIDTH, rows, 0, GL30.GL_RED_INTEGER,
                    GL11.GL_SHORT, indices);
            indexRows = rows;
        } else {
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, INDEX_WIDTH, rows, GL30.GL_RED_INTEGER, GL11.GL_SHORT,
                    indices);
        }

        GlStateManager._bindTexture(0);
    }

    private static int create(int internal, int width, int height, int format, int type) {
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        nearest();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internal, width, height, 0, format, type, (ByteBuffer) null);
        return id;
    }

    private static void nearest() {
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }

    static void draw(Vec3 camera, LightVolumes.Layer layer, Matrix4f view, Matrix4f projection) {
        shader.setSampler("DepthSampler", layer.scene().getDepthTextureId());
        shader.setSampler("OpaqueDepth", layer.opaqueDepth());
        shader.safeGetUniform("Translucent").set(layer.translucent() ? 1f : 0f);
        shader.setSampler("LightData", dataTexture);
        shader.setSampler("Tiles", tileTexture);
        shader.setSampler("Indices", indexTexture);
        shader.safeGetUniform("InvProjMat").set(new Matrix4f(projection).invert());
        shader.safeGetUniform("InvViewMat").set(new Matrix4f(view).invert());
        shader.safeGetUniform("CameraFract").set((float) Mth.frac(camera.x), (float) Mth.frac(camera.y),
                (float) Mth.frac(camera.z));
        shader.safeGetUniform("GridOffset").set(Mth.floor(camera.x) - ShadowGrid.originX(),
                Mth.floor(camera.y) - ShadowGrid.originY(), Mth.floor(camera.z) - ShadowGrid.originZ());
        shader.safeGetUniform("ShadowGrid").set(LightVolumes.SHADOW_UNIT);
        shader.safeGetUniform("TileSize").set(TILE);
        shader.safeGetUniform("Shadows").set(ShadowGrid.ready() && VortexConfig.SHADOWS.get() ? 1f : 0f);
        shader.safeGetUniform("Pixelated").set(VortexConfig.PIXEL_LIGHTING.get() ? 1f : 0f);
        shader.safeGetUniform("Strength").set(0.7f * layer.share() * VortexConfig.BRIGHTNESS.get() / 100f);

        LightField.bind(LightVolumes.SHADOW_UNIT, ShadowGrid.texture());
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(screen());
        RenderSystem.enableCull();
        LightField.bind(LightVolumes.SHADOW_UNIT, 0);
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
