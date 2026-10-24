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
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.AABB;
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
import java.util.function.Predicate;

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

    private static final Map<Long, long[]> SOLIDS = new ConcurrentHashMap<>();
    private static final Map<Long, int[]> FILTERS = new ConcurrentHashMap<>();
    private static final Map<Long, int[]> PARTS = new ConcurrentHashMap<>();
    private static final Map<BlockState, Integer> SHAPES = new ConcurrentHashMap<>();
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

            Predicate<Long> inChunk = key -> SectionPos.x(key) == pos.x && SectionPos.z(key) == pos.z;

            if (EMITTERS.keySet().removeIf(inChunk))
                GLOW_VERSION.incrementAndGet();

            boolean solids = SOLIDS.keySet().removeIf(inChunk);
            boolean filters = FILTERS.keySet().removeIf(inChunk);
            boolean parts = PARTS.keySet().removeIf(inChunk);
            if (solids || filters || parts)
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
        boolean solid = shadows && (cover(old, glows) != cover(state, glows) || filter(old) != filter(state));
        if (!glow && !solid)
            return;

        if (Sable.HELPER.getContaining(chunk.getLevel(), chunk.getPos()) instanceof SubLevel subLevel) {
            if (glow)
                SubLevelGlows.markDirty(subLevel.getUniqueId());
            if (solid)
                SubLevelSolids.markDirty(subLevel.getUniqueId());
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

    static int[] filters(long section) {
        return FILTERS.get(section);
    }

    static int[] parts(long section) {
        return PARTS.get(section);
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

        return new Census(EMITTERS.size(), SOLIDS.size(), emitters);
    }

    private static void clear() {
        SubLevelGlows.clear();
        SubLevelSolids.clear();
        GENERATION.incrementAndGet();
        SOLIDS.clear();
        FILTERS.clear();
        PARTS.clear();
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
        boolean solid = shadows && !empty && section.maybeHas(state -> cover(state, glows) != 0);
        boolean tinted = shadows && !empty && section.maybeHas(state -> filter(state) != 0);
        boolean withShadows = shadows;
        PalettedContainer<BlockState> states = lit || solid || tinted ? section.getStates().copy() : null;
        int generation = GENERATION.get();

        WORKER.execute(() -> {
            try {
                int[] emitters = lit ? collectEmitters(states, glows) : null;
                long[] bits = solid ? collectSolids(states, glows) : null;
                int[] parts = solid ? collectParts(states, glows) : null;
                int[] filters = tinted ? collectFilters(states) : null;
                if (generation != GENERATION.get())
                    return;

                if (!Arrays.equals(EMITTERS.get(key), emitters)) {
                    if (emitters == null)
                        EMITTERS.remove(key);
                    else
                        EMITTERS.put(key, emitters);
                    GLOW_VERSION.incrementAndGet();
                }

                if (!withShadows)
                    return;

                boolean changed = false;

                if (!Arrays.equals(SOLIDS.get(key), bits)) {
                    if (bits == null)
                        SOLIDS.remove(key);
                    else
                        SOLIDS.put(key, bits);
                    changed = true;
                }

                if (!Arrays.equals(FILTERS.get(key), filters)) {
                    if (filters == null)
                        FILTERS.remove(key);
                    else
                        FILTERS.put(key, filters);
                    changed = true;
                }

                if (!Arrays.equals(PARTS.get(key), parts)) {
                    if (parts == null)
                        PARTS.remove(key);
                    else
                        PARTS.put(key, parts);
                    changed = true;
                }

                if (changed)
                    SOLID_VERSION.incrementAndGet();
            } catch (RuntimeException e) {
                CreateSubmarine.LOGGER.warn("Couldn't index the light of section {}", SectionPos.of(key), e);
            }
        });
    }

    static int cover(BlockState state, Map<BlockState, Glow> glows) {
        if (state.isAir() || state.getLightEmission() != 0 || glows.containsKey(state))
            return 0;

        boolean opaque = state.canOcclude() || state.is(Blocks.TINTED_GLASS)
                || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock;
        if (!opaque)
            return 0;

        return SHAPES.computeIfAbsent(state, LightIndex::octants);
    }

    private static int octants(BlockState state) {
        try {
            int mask = 0;

            for (AABB box : state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs())
                mask |= octants(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            return mask;
        } catch (RuntimeException e) {
            return 255;
        }
    }

    static int octants(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        int mask = 0;

        for (int i = 0; i < 8; i++) {
            double x = (i & 1) * 0.5, y = (i >> 1 & 1) * 0.5, z = (i >> 2) * 0.5;
            if (covers(minX, maxX, x) && covers(minY, maxY, y) && covers(minZ, maxZ, z))
                mask |= 1 << i;
        }

        return mask;
    }

    private static boolean covers(double min, double max, double start) {
        return Math.min(max, start + 0.5) - Math.max(min, start) >= 3 / 16.0;
    }

    static int filter(BlockState state) {
        if (!(state.getBlock() instanceof BeaconBeamBlock beam) || state.canOcclude())
            return 0;

        int color = beam.getColor().getTextureDiffuseColor();
        int r = 255 - (color >> 16 & 255);
        int g = 255 - (color >> 8 & 255);
        int b = 255 - (color & 255);
        return 1 << 24 | r << 16 | g << 8 | b;
    }

    private static int[] collectFilters(PalettedContainer<BlockState> states) {
        int[] found = new int[32];
        int count = 0;

        for (int i = 0; i < 4096; i++) {
            int filter = filter(states.get(i & 15, i >> 8, i >> 4 & 15));
            if (filter == 0)
                continue;

            if (count + 2 > found.length)
                found = Arrays.copyOf(found, found.length * 2);
            found[count++] = i;
            found[count++] = filter;
        }

        return count == 0 ? null : Arrays.copyOf(found, count);
    }

    private static long[] collectSolids(PalettedContainer<BlockState> states, Map<BlockState, Glow> glows) {
        long[] bits = new long[64];
        boolean any = false;

        for (int i = 0; i < 4096; i++) {
            if (cover(states.get(i & 15, i >> 8, i >> 4 & 15), glows) == 255) {
                bits[i >> 6] |= 1L << (i & 63);
                any = true;
            }
        }

        return any ? bits : null;
    }

    private static int[] collectParts(PalettedContainer<BlockState> states, Map<BlockState, Glow> glows) {
        int[] found = new int[32];
        int count = 0;

        for (int i = 0; i < 4096; i++) {
            int mask = cover(states.get(i & 15, i >> 8, i >> 4 & 15), glows);
            if (mask == 0 || mask == 255)
                continue;

            if (count + 2 > found.length)
                found = Arrays.copyOf(found, found.length * 2);
            found[count++] = i;
            found[count++] = mask;
        }

        return count == 0 ? null : Arrays.copyOf(found, count);
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
}
