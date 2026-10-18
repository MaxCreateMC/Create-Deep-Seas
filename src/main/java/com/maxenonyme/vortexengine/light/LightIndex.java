package com.maxenonyme.vortexengine.light;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class LightIndex {

    public record Census(int glowSections, int solidSections, int emitters) {
    }

    record Emitter(double x, double y, double z, int packed) {
    }

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Vortex light index");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private static final Map<Long, Light[]> GLOWS = new ConcurrentHashMap<>();
    private static final Map<Long, long[]> SOLIDS = new ConcurrentHashMap<>();
    private static final Map<Long, int[]> EMITTERS = new ConcurrentHashMap<>();
    private static final Set<Long> CHUNKS = new HashSet<>();
    private static final AtomicInteger GLOW_VERSION = new AtomicInteger();
    private static final AtomicInteger SOLID_VERSION = new AtomicInteger();
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static boolean active;
    private static boolean shadows;
    private static boolean automatic;

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.getLevel().isClientSide() || !(event.getChunk() instanceof LevelChunk chunk))
            return;

        SubLevelContainer container = SubLevelContainer.getContainer(chunk.getLevel());
        if (container != null && container.inBounds(chunk.getPos().x, chunk.getPos().z))
            return;

        CHUNKS.add(chunk.getPos().toLong());
        if (active)
            scan(chunk);
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!event.getLevel().isClientSide())
            return;

        ChunkPos pos = event.getChunk().getPos();
        CHUNKS.remove(pos.toLong());
        int generation = GENERATION.get();

        WORKER.execute(() -> {
            if (generation != GENERATION.get())
                return;

            EMITTERS.keySet().removeIf(key -> SectionPos.x(key) == pos.x && SectionPos.z(key) == pos.z);
            if (GLOWS.keySet().removeIf(key -> SectionPos.x(key) == pos.x && SectionPos.z(key) == pos.z))
                GLOW_VERSION.incrementAndGet();
            if (SOLIDS.keySet().removeIf(key -> SectionPos.x(key) == pos.x && SectionPos.z(key) == pos.z))
                SOLID_VERSION.incrementAndGet();
        });
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide())
            return;

        CHUNKS.clear();
        clear();
        active = false;
        VortexLights.clear();
    }

    public static void onBlockChanged(LevelChunk chunk, BlockPos pos, BlockState old, BlockState state) {
        if (!active)
            return;

        boolean glow = GlowDefinitions.glows(old) || GlowDefinitions.glows(state);
        Map<BlockState, Glow> glows = GlowDefinitions.all();
        boolean solid = shadows && occludes(old, glows) != occludes(state, glows);
        if (!glow && !solid)
            return;

        if (Sable.HELPER.getContaining(chunk.getLevel(), chunk.getPos()) instanceof SubLevel subLevel) {
            if (glow)
                SubLevelGlows.markDirty(subLevel.getUniqueId());
            return;
        }

        int index = chunk.getSectionIndex(pos.getY());
        if (index >= 0 && index < chunk.getSectionsCount())
            scan(chunk, index, glows);
    }

    static void sync(boolean usable, boolean withShadows, boolean withAutomatic) {
        if (usable == active && withShadows == shadows && withAutomatic == automatic)
            return;

        active = usable;
        shadows = withShadows;
        automatic = withAutomatic;
        if (usable)
            rescanAll();
        else
            clear();
    }

    public static void rescanAll() {
        if (!active)
            return;

        ClientLevel level = Minecraft.getInstance().level;
        clear();
        if (level == null)
            return;

        for (long key : CHUNKS) {
            LevelChunk chunk = level.getChunkSource().getChunk(ChunkPos.getX(key), ChunkPos.getZ(key), false);
            if (chunk != null)
                scan(chunk);
        }
    }

    static int glowVersion() {
        return GLOW_VERSION.get();
    }

    static int solidVersion() {
        return SOLID_VERSION.get();
    }

    static long[] solids(long section) {
        return SOLIDS.get(section);
    }

    static int[] emitters(long section) {
        return EMITTERS.get(section);
    }

    static boolean emittersWithin(Vec3 camera, int distance) {
        double reach = (distance + 14) * (distance + 14);

        for (long key : EMITTERS.keySet()) {
            double dx = (SectionPos.x(key) << 4) + 8 - camera.x;
            double dy = (SectionPos.y(key) << 4) + 8 - camera.y;
            double dz = (SectionPos.z(key) << 4) + 8 - camera.z;
            if (dx * dx + dy * dy + dz * dz <= reach)
                return true;
        }

        return false;
    }

    static List<Emitter> emittersNear(Vec3 camera, int distance) {
        List<Emitter> near = new ArrayList<>();
        double reach = distance * distance;
        double sectionReach = (distance + 14) * (distance + 14);

        for (Map.Entry<Long, int[]> entry : EMITTERS.entrySet()) {
            long key = entry.getKey();
            int baseX = SectionPos.x(key) << 4, baseY = SectionPos.y(key) << 4, baseZ = SectionPos.z(key) << 4;
            double dx = baseX + 8 - camera.x, dy = baseY + 8 - camera.y, dz = baseZ + 8 - camera.z;
            if (dx * dx + dy * dy + dz * dz > sectionReach)
                continue;

            int[] found = entry.getValue();

            for (int k = 0; k < found.length; k += 2) {
                int local = found[k];
                double x = baseX + (local & 15) + 0.5;
                double y = baseY + (local >> 8) + 0.5;
                double z = baseZ + (local >> 4 & 15) + 0.5;
                if (camera.distanceToSqr(x, y, z) <= reach)
                    near.add(new Emitter(x, y, z, found[k + 1]));
            }
        }

        return near;
    }

    public static Census census() {
        int emitters = 0;

        for (int[] found : EMITTERS.values())
            emitters += found.length / 2;

        return new Census(GLOWS.size(), SOLIDS.size(), emitters);
    }

    static List<Light> near(Vec3 camera, int distance) {
        List<Light> near = new ArrayList<>();
        double reach = distance * distance;
        double sectionReach = (distance + 14) * (distance + 14);

        for (Map.Entry<Long, Light[]> entry : GLOWS.entrySet()) {
            long key = entry.getKey();
            double dx = (SectionPos.x(key) << 4) + 8 - camera.x;
            double dy = (SectionPos.y(key) << 4) + 8 - camera.y;
            double dz = (SectionPos.z(key) << 4) + 8 - camera.z;
            if (dx * dx + dy * dy + dz * dz > sectionReach)
                continue;

            for (Light light : entry.getValue()) {
                if (light.distanceSqr(camera) <= reach)
                    near.add(light);
            }
        }

        return near;
    }

    private static void clear() {
        SubLevelGlows.clear();
        GENERATION.incrementAndGet();
        GLOWS.clear();
        SOLIDS.clear();
        EMITTERS.clear();
        GLOW_VERSION.incrementAndGet();
        SOLID_VERSION.incrementAndGet();
    }

    private static void scan(LevelChunk chunk) {
        Map<BlockState, Glow> glows = GlowDefinitions.all();
        if (glows.isEmpty() && !shadows)
            return;

        for (int index = 0; index < chunk.getSectionsCount(); index++)
            scan(chunk, index, glows);
    }

    private static void scan(LevelChunk chunk, int index, Map<BlockState, Glow> glows) {
        LevelChunkSection section = chunk.getSection(index);
        long key = SectionPos.asLong(chunk.getPos().x, chunk.getSectionYFromSectionIndex(index), chunk.getPos().z);
        boolean empty = section.hasOnlyAir();
        boolean lit = !empty && section.maybeHas(glows::containsKey);
        boolean solid = shadows && !empty && section.maybeHas(state -> occludes(state, glows));
        boolean withShadows = shadows;
        PalettedContainer<BlockState> states = lit || solid ? section.getStates().copy() : null;
        int generation = GENERATION.get();

        WORKER.execute(() -> {
            try {
                Light[] lights = lit ? collectGlows(states, key, glows) : null;
                int[] emitters = lit ? collectEmitters(states, glows) : null;
                long[] bits = solid ? collectSolids(states, glows) : null;
                if (generation != GENERATION.get())
                    return;

                if (!Arrays.equals(EMITTERS.get(key), emitters)) {
                    if (emitters == null)
                        EMITTERS.remove(key);
                    else
                        EMITTERS.put(key, emitters);
                    GLOW_VERSION.incrementAndGet();
                }

                if (!Arrays.equals(GLOWS.get(key), lights)) {
                    if (lights == null)
                        GLOWS.remove(key);
                    else
                        GLOWS.put(key, lights);
                    GLOW_VERSION.incrementAndGet();
                }

                if (!withShadows || Arrays.equals(SOLIDS.get(key), bits))
                    return;

                if (bits == null)
                    SOLIDS.remove(key);
                else
                    SOLIDS.put(key, bits);
                SOLID_VERSION.incrementAndGet();
            } catch (RuntimeException e) {
                CreateSubmarine.LOGGER.warn("Couldn't index the light of section {}", SectionPos.of(key), e);
            }
        });
    }

    private static boolean occludes(BlockState state, Map<BlockState, Glow> glows) {
        return state.canOcclude() && state.getLightEmission() == 0 && !glows.containsKey(state);
    }

    private static long[] collectSolids(PalettedContainer<BlockState> states, Map<BlockState, Glow> glows) {
        long[] bits = new long[64];
        boolean any = false;

        for (int i = 0; i < 4096; i++) {
            if (occludes(states.get(i & 15, i >> 8, i >> 4 & 15), glows)) {
                bits[i >> 6] |= 1L << (i & 63);
                any = true;
            }
        }

        return any ? bits : null;
    }

    private static int[] collectEmitters(PalettedContainer<BlockState> states, Map<BlockState, Glow> glows) {
        int[] found = new int[32];
        int count = 0;

        for (int i = 0; i < 4096; i++) {
            Glow glow = glows.get(states.get(i & 15, i >> 8, i >> 4 & 15));
            if (glow == null)
                continue;

            if (count + 2 > found.length)
                found = Arrays.copyOf(found, found.length * 2);
            found[count++] = i;
            found[count++] = ShadowGrid.pack(glow.red(), glow.green(), glow.blue(), glow.radius(), glow.intensity(),
                    glow.behavior());
        }

        return count == 0 ? null : Arrays.copyOf(found, count);
    }

    private static Light[] collectGlows(PalettedContainer<BlockState> states, long key, Map<BlockState, Glow> glows) {
        int baseX = SectionPos.x(key) << 4, baseY = SectionPos.y(key) << 4, baseZ = SectionPos.z(key) << 4;
        Cluster[] clusters = new Cluster[64];

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = states.get(x, y, z);
                    Glow glow = glows.get(state);
                    if (glow == null)
                        continue;

                    int cell = (y >> 2) << 4 | (z >> 2) << 2 | x >> 2;
                    if (clusters[cell] == null)
                        clusters[cell] = new Cluster();
                    clusters[cell].add(baseX + x + 0.5, baseY + y + 0.5, baseZ + z + 0.5, glow);
                }
            }
        }

        List<Light> lights = new ArrayList<>();

        for (Cluster cluster : clusters) {
            if (cluster != null)
                lights.add(cluster.light());
        }

        return lights.isEmpty() ? null : lights.toArray(new Light[0]);
    }

    private static class Cluster {
        double x, y, z;
        float red, green, blue;
        float weight, radius, strongest;
        int pulse, flicker;

        void add(double px, double py, double pz, Glow glow) {
            float power = glow.intensity();
            x += px * power;
            y += py * power;
            z += pz * power;
            red += glow.red() * power;
            green += glow.green() * power;
            blue += glow.blue() * power;
            weight += power;
            radius = Math.max(radius, glow.radius());
            strongest = Math.max(strongest, power);
            pulse = Math.max(pulse, glow.behavior() >> 4);
            flicker = Math.max(flicker, glow.behavior() & 15);
        }

        Light light() {
            return Light.omni(x / weight, y / weight, z / weight, red / weight, green / weight, blue / weight,
                    radius, Math.min(weight, strongest * 1.5f), pulse << 4 | flicker);
        }
    }
}
