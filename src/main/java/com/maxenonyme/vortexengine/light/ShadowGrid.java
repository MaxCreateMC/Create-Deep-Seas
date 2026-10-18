package com.maxenonyme.vortexengine.light;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.client.VeilUploadGuard;
import com.maxenonyme.vortexengine.VortexProfiler;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ShadowGrid {

    static final int SIZE = 128;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Vortex shadow grid");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private record Built(int x, int y, int z, int version, int glowVersion, byte[] cells, long[][] sections,
            byte[] emission, int[][] emitters, byte[] heights) {
    }

    private static CompletableFuture<Built> pending;
    private static Built incoming;
    private static int incomingSlice;
    private static ByteBuffer staging;
    private static ByteBuffer emissionStaging;
    private static ByteBuffer heightStaging;
    private static ByteBuffer texel;
    private static int texture;
    private static int backTexture;
    private static int emissionTexture;
    private static int backEmission;
    private static int heightTexture;
    private static int builtVersion = -1;
    private static int builtGlowVersion = -1;
    private static long[][] builtSections;
    private static int[][] builtEmitters;
    private static byte[] builtCells;
    private static byte[] builtEmission;
    private static int[] injected = new int[256];
    private static int injectedCount;
    private static int originX, originY, originZ;
    private static boolean withField;
    private static boolean ready;

    static void update(Vec3 camera, boolean field) {
        if (incoming != null) {
            uploadSlices();
            return;
        }

        if (pending != null && pending.isDone()) {
            Built built = pending.join();
            pending = null;
            if (built != null)
                receive(built);
            return;
        }

        if (pending != null)
            return;

        int x = (Mth.floor(camera.x) >> 4 << 4) - SIZE / 2;
        int y = (Mth.floor(camera.y) >> 4 << 4) - SIZE / 2;
        int z = (Mth.floor(camera.z) >> 4 << 4) - SIZE / 2;
        int version = LightIndex.solidVersion();
        int glowVersion = LightIndex.glowVersion();
        boolean moved = !ready || x != originX || y != originY || z != originZ;

        boolean stale = moved || field != withField
                || version != builtVersion && touched(false)
                || field && glowVersion != builtGlowVersion && touched(true);

        if (stale) {
            pending = CompletableFuture.supplyAsync(() -> build(x, y, z, version, glowVersion, field), WORKER);
        } else {
            builtVersion = version;
            builtGlowVersion = glowVersion;
        }
    }

    private static boolean touched(boolean glows) {
        int i = 0;

        for (int sx = 0; sx < SIZE / 16; sx++) {
            for (int sy = 0; sy < SIZE / 16; sy++) {
                for (int sz = 0; sz < SIZE / 16; sz++) {
                    long section = SectionPos.asLong((originX >> 4) + sx, (originY >> 4) + sy, (originZ >> 4) + sz);
                    boolean changed = glows
                            ? LightIndex.emitters(section) != builtEmitters[i]
                            : LightIndex.solids(section) != builtSections[i];
                    if (changed)
                        return true;

                    i++;
                }
            }
        }

        return false;
    }

    public static boolean ready() {
        return ready;
    }

    public static boolean hasField() {
        return ready && builtEmission != null;
    }

    public static int texture() {
        return texture;
    }

    static int emission() {
        return emissionTexture;
    }

    public static int heights() {
        return heightTexture;
    }

    public static int originX() {
        return originX;
    }

    public static int originY() {
        return originY;
    }

    public static int originZ() {
        return originZ;
    }

    static void forget() {
        ready = false;
        builtVersion = -1;
        builtGlowVersion = -1;
    }

    static int pack(float red, float green, float blue, float radius, float intensity, int behavior) {
        float top = Math.max(Math.max(red, green), Math.max(blue, 0.001f));
        int r = Math.round(Mth.clamp(Tuning.saturate(red, top) / top, 0, 1) * 255);
        int g = Math.round(Mth.clamp(Tuning.saturate(green, top) / top, 0, 1) * 255);
        int b = Math.round(Mth.clamp(Tuning.saturate(blue, top) / top, 0, 1) * 255);

        float reach = Mth.clamp(radius * Tuning.reach() * (1.0f + 0.55f * intensity), 1, 15);
        behavior = Tuning.behavior(behavior);
        int rhythm = ((behavior >> 4) > 0 ? 1 : 0) | ((behavior & 15) > 0 ? 2 : 0);
        int code = Math.round(reach / 15 * 62) << 2 | rhythm;
        return r << 24 | g << 16 | b << 8 | code + 1;
    }

    static int inject(List<Light> lights) {
        if (!hasField() || injectedCount == 0 && lights.isEmpty())
            return 0;

        if (texel == null)
            texel = MemoryUtil.memAlloc(4);

        VeilUploadGuard.reset();
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, emissionTexture);

        int writes = injectedCount;

        for (int i = 0; i < injectedCount; i++) {
            int index = injected[i] * 4;
            write(injected[i], (builtEmission[index] & 255) << 24 | (builtEmission[index + 1] & 255) << 16
                    | (builtEmission[index + 2] & 255) << 8 | builtEmission[index + 3] & 255);
        }

        injectedCount = 0;

        for (Light light : lights) {
            int cx = Mth.floor(light.x()) - originX;
            int cy = Mth.floor(light.y()) - originY;
            int cz = Mth.floor(light.z()) - originZ;
            if (cx < 0 || cy < 0 || cz < 0 || cx >= SIZE || cy >= SIZE || cz >= SIZE)
                continue;

            int index = cx + cy * SIZE + cz * SIZE * SIZE;
            if (builtCells[index] != 0 || injectedCount == injected.length)
                continue;

            write(index, pack(light.red(), light.green(), light.blue(), light.radius(), light.intensity(),
                    light.behavior()));
            injected[injectedCount++] = index;
            writes++;
        }

        GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        return writes;
    }

    private static void write(int index, int packed) {
        texel.clear();
        texel.put((byte) (packed >>> 24)).put((byte) (packed >>> 16)).put((byte) (packed >>> 8)).put((byte) packed);
        texel.flip();

        int cx = index % SIZE, cy = index / SIZE % SIZE, cz = index / (SIZE * SIZE);
        GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, cx, cy, cz, 1, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texel);
    }

    private static Built build(int x, int y, int z, int version, int glowVersion, boolean field) {
        long started = System.nanoTime();

        try {
            byte[] cells = new byte[SIZE * SIZE * SIZE];
            byte[] emission = field ? new byte[SIZE * SIZE * SIZE * 4] : null;
            long[][] sections = new long[SIZE * SIZE * SIZE / 4096][];
            int[][] emitters = field ? new int[sections.length][] : null;
            int i = 0;

            for (int sx = 0; sx < SIZE / 16; sx++) {
                for (int sy = 0; sy < SIZE / 16; sy++) {
                    for (int sz = 0; sz < SIZE / 16; sz++) {
                        long section = SectionPos.asLong((x >> 4) + sx, (y >> 4) + sy, (z >> 4) + sz);
                        long[] bits = LightIndex.solids(section);
                        sections[i] = bits;
                        if (bits != null)
                            fill(cells, bits, sx << 4, sy << 4, sz << 4);

                        if (field) {
                            int[] found = LightIndex.emitters(section);
                            emitters[i] = found;
                            if (found != null)
                                splat(emission, found, sx << 4, sy << 4, sz << 4);
                        }

                        i++;
                    }
                }
            }

            byte[] heights = heights(cells);
            VortexProfiler.recordBuild(System.nanoTime() - started);
            return new Built(x, y, z, version, glowVersion, cells, sections, emission, emitters, heights);
        } catch (RuntimeException e) {
            CreateSubmarine.LOGGER.warn("Couldn't build the Vortex shadow grid", e);
            return null;
        }
    }

    private static void fill(byte[] cells, long[] bits, int baseX, int baseY, int baseZ) {
        for (int i = 0; i < 4096; i++) {
            if ((bits[i >> 6] >>> (i & 63) & 1) == 0)
                continue;

            int cx = baseX + (i & 15), cy = baseY + (i >> 8), cz = baseZ + (i >> 4 & 15);
            cells[cx + cy * SIZE + cz * SIZE * SIZE] = (byte) 255;
        }
    }

    private static void splat(byte[] emission, int[] found, int baseX, int baseY, int baseZ) {
        for (int k = 0; k < found.length; k += 2) {
            int local = found[k];
            int packed = found[k + 1];
            int cx = baseX + (local & 15), cy = baseY + (local >> 8), cz = baseZ + (local >> 4 & 15);
            int index = (cx + cy * SIZE + cz * SIZE * SIZE) * 4;

            emission[index] = (byte) (packed >>> 24);
            emission[index + 1] = (byte) (packed >>> 16);
            emission[index + 2] = (byte) (packed >>> 8);
            emission[index + 3] = (byte) packed;
        }
    }

    private static byte[] heights(byte[] cells) {
        byte[] heights = new byte[SIZE * SIZE];

        for (int cz = 0; cz < SIZE; cz++) {
            for (int cx = 0; cx < SIZE; cx++) {
                for (int cy = SIZE - 1; cy >= 0; cy--) {
                    if (cells[cx + cy * SIZE + cz * SIZE * SIZE] != 0) {
                        heights[cx + cz * SIZE] = (byte) (cy + 1);
                        break;
                    }
                }
            }
        }

        return heights;
    }

    private static void receive(Built built) {
        if (staging == null)
            staging = MemoryUtil.memAlloc(SIZE * SIZE * SIZE);
        staging.clear();
        staging.put(built.cells()).flip();

        if (built.emission() != null) {
            if (emissionStaging == null)
                emissionStaging = MemoryUtil.memAlloc(SIZE * SIZE * SIZE * 4);
            emissionStaging.clear();
            emissionStaging.put(built.emission()).flip();
        }

        incoming = built;
        incomingSlice = 0;
        uploadSlices();
    }

    private static void uploadSlices() {
        Built built = incoming;
        int count = Math.min(16, SIZE - incomingSlice);
        int slab = SIZE * SIZE;

        VeilUploadGuard.reset();
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);

        if (backTexture == 0)
            backTexture = create3D(GL30.GL_R8, GL11.GL_RED, GL11.GL_LINEAR);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, backTexture);
        staging.limit((incomingSlice + count) * slab).position(incomingSlice * slab);
        GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, 0, 0, incomingSlice, SIZE, SIZE, count, GL11.GL_RED,
                GL11.GL_UNSIGNED_BYTE, staging);
        long bytes = (long) count * slab;

        if (built.emission() != null) {
            if (backEmission == 0)
                backEmission = create3D(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_NEAREST);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, backEmission);
            emissionStaging.limit((incomingSlice + count) * slab * 4).position(incomingSlice * slab * 4);
            GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, 0, 0, incomingSlice, SIZE, SIZE, count, GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE, emissionStaging);
            bytes += (long) count * slab * 4;
        }

        GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);

        incomingSlice += count;
        boolean complete = incomingSlice >= SIZE;
        VortexProfiler.recordUpload(bytes, complete);
        if (complete)
            swap(built);
    }

    private static void swap(Built built) {
        int solids = texture;
        texture = backTexture;
        backTexture = solids;

        if (built.emission() != null) {
            int emitters = emissionTexture;
            emissionTexture = backEmission;
            backEmission = emitters;
        }

        uploadHeights(built.heights());

        incoming = null;
        originX = built.x();
        originY = built.y();
        originZ = built.z();
        builtVersion = built.version();
        builtGlowVersion = built.glowVersion();
        builtSections = built.sections();
        builtEmitters = built.emitters();
        builtCells = built.cells();
        builtEmission = built.emission();
        withField = built.emission() != null;
        injectedCount = 0;
        ready = true;
    }

    private static void uploadHeights(byte[] heights) {
        if (heightStaging == null)
            heightStaging = MemoryUtil.memAlloc(SIZE * SIZE);
        heightStaging.clear();
        heightStaging.put(heights).flip();

        if (heightTexture == 0) {
            heightTexture = GlStateManager._genTexture();
            GlStateManager._bindTexture(heightTexture);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, SIZE, SIZE, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE,
                    (ByteBuffer) null);
        } else {
            GlStateManager._bindTexture(heightTexture);
        }

        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, SIZE, SIZE, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE,
                heightStaging);
        GlStateManager._bindTexture(0);
    }

    private static int create3D(int internal, int format, int filter) {
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, id);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, internal, SIZE, SIZE, SIZE, 0, format, GL11.GL_UNSIGNED_BYTE,
                (ByteBuffer) null);
        return id;
    }
}
