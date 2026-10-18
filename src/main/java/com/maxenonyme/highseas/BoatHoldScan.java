package com.maxenonyme.highseas;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.maxenonyme.createsubmarine.submarine.compartment.HullSeal;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class BoatHoldScan {

    private static final int WEST = 1, EAST = 2, NORTH = 4, SOUTH = 8, FLOOR = 16,
            BOXED = WEST | EAST | NORTH | SOUTH | FLOOR;
    private static final byte HULL = 1, CUT_X = 2, CUT_Y = 4, CUT_Z = 8, SOLID = HULL | CUT_X | CUT_Y | CUT_Z;
    private static final long MAX_VOLUME = 4_000_000L;
    private static final int MEND_PASSES = 3;

    record Glue(double[] key, Set<BlockPos> cells, int hash) {
    }

    static final Glue LOOSE = new Glue(new double[0], Set.of(), 0);

    static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "High Seas boat holds");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private final int minX, minY, minZ, dx, dy, dz;
    private final int sectionX, sectionY, sectionZ, sectionsX, sectionsZ;
    private final List<PalettedContainer<BlockState>> sections = new ArrayList<>();
    private final boolean[] loaded;
    private final Map<BlockState, Byte> seals = new IdentityHashMap<>();
    private final boolean[] glued;

    private BoatHoldScan(LevelPlot plot, BoundingBox3ic box, Set<BlockPos> contraptions) {
        minX = box.minX();
        minY = box.minY();
        minZ = box.minZ();
        dx = box.maxX() - minX + 1;
        dy = box.maxY() - minY + 1;
        dz = box.maxZ() - minZ + 1;

        glued = contraptions.isEmpty() ? null : new boolean[dx * dy * dz];

        for (BlockPos p : contraptions) {
            int ix = p.getX() - minX, iy = p.getY() - minY, iz = p.getZ() - minZ;
            if (ix >= 0 && ix < dx && iy >= 0 && iy < dy && iz >= 0 && iz < dz)
                glued[(iy * dz + iz) * dx + ix] = true;
        }

        sectionX = minX >> 4;
        sectionY = minY >> 4;
        sectionZ = minZ >> 4;
        sectionsX = (box.maxX() >> 4) - sectionX + 1;
        sectionsZ = (box.maxZ() >> 4) - sectionZ + 1;
        int sectionsY = (box.maxY() >> 4) - sectionY + 1;
        loaded = new boolean[sectionsX * sectionsY * sectionsZ];

        for (int sy = 0; sy < sectionsY; sy++) {
            for (int sz = 0; sz < sectionsZ; sz++) {
                for (int sx = 0; sx < sectionsX; sx++) {
                    LevelChunk chunk = plot.getChunk(plot.toLocal(new ChunkPos(sectionX + sx, sectionZ + sz)));
                    int index = chunk == null ? -1 : chunk.getSectionIndexFromSectionY(sectionY + sy);
                    if (chunk == null || index < 0 || index >= chunk.getSectionsCount()) {
                        sections.add(null);
                        continue;
                    }

                    LevelChunkSection section = chunk.getSection(index);
                    loaded[sections.size()] = true;

                    if (section.hasOnlyAir()) {
                        sections.add(null);
                        continue;
                    }

                    PalettedContainer<BlockState> states = section.getStates().copy();
                    states.count((state, count) -> seals.computeIfAbsent(state, BoatHoldScan::seal));
                    sections.add(states);
                }
            }
        }
    }

    private static byte seal(BlockState state) {
        HullSeal.Shape shape = HullSeal.of(state);
        if (shape == HullSeal.OPEN)
            return 0;

        byte mask = HULL;
        if (!shape.crosses(Direction.Axis.X))
            mask |= CUT_X;
        if (!shape.crosses(Direction.Axis.Y))
            mask |= CUT_Y;
        if (!shape.crosses(Direction.Axis.Z))
            mask |= CUT_Z;
        return mask;
    }

    static Glue contraptions(Level level, SubLevel sub, Glue previous) {
        LevelPlot plot = sub.getPlot();
        if (plot == null)
            return LOOSE;

        BoundingBox3ic box = plot.getBoundingBox();
        AABB area = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
        List<AbstractContraptionEntity> entities = level.getEntitiesOfClass(AbstractContraptionEntity.class, area);
        if (entities.isEmpty())
            return LOOSE;

        double[] key = new double[entities.size() * 11];
        int k = 0;

        for (AbstractContraptionEntity entity : entities) {
            Contraption contraption = entity.getContraption();
            Vec3 origin = entity.toGlobalVector(Vec3.ZERO, 1.0f);
            Vec3 east = entity.toGlobalVector(new Vec3(1, 0, 0), 1.0f);
            Vec3 up = entity.toGlobalVector(new Vec3(0, 1, 0), 1.0f);
            key[k++] = entity.getId();
            key[k++] = contraption == null ? -1 : layout(contraption);
            key[k++] = origin.x;
            key[k++] = origin.y;
            key[k++] = origin.z;
            key[k++] = east.x;
            key[k++] = east.y;
            key[k++] = east.z;
            key[k++] = up.x;
            key[k++] = up.y;
            key[k++] = up.z;
        }

        if (previous != null && Arrays.equals(previous.key(), key))
            return previous;

        Set<BlockPos> cells = new HashSet<>();

        for (AbstractContraptionEntity entity : entities) {
            Contraption contraption = entity.getContraption();
            if (contraption == null)
                continue;

            for (Map.Entry<BlockPos, StructureTemplate.StructureBlockInfo> e : contraption.getBlocks().entrySet()) {
                if (CompartmentDetector.isPermeable(e.getValue().state()))
                    continue;

                cells.add(BlockPos.containing(entity.toGlobalVector(Vec3.atCenterOf(e.getKey()), 1.0f)));
            }
        }

        return new Glue(key, cells, cells.hashCode());
    }

    private static int layout(Contraption contraption) {
        int hash = 1;

        for (StructureTemplate.StructureBlockInfo info : contraption.getBlocks().values())
            hash = 31 * hash + System.identityHashCode(info.state());

        return hash;
    }

    static CompletableFuture<CompartmentDetector.Result> start(SubLevel sub, Set<BlockPos> contraptions) {
        LevelPlot plot = sub.getPlot();
        if (plot == null)
            return CompletableFuture.completedFuture(null);

        BoundingBox3ic box = plot.getBoundingBox();
        long volume = (long) (box.maxX() - box.minX() + 1) * (box.maxY() - box.minY() + 1)
                * (box.maxZ() - box.minZ() + 1);
        if (volume <= 0 || volume > MAX_VOLUME)
            return CompletableFuture.completedFuture(null);

        BoatHoldScan scan = new BoatHoldScan(plot, box, contraptions);
        UUID id = sub.getUniqueId();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return scan.holds();
            } catch (RuntimeException e) {
                CreateHighSeas.LOGGER.warn("Couldn't scan the hold of {}", id, e);
                return null;
            }
        }, WORKER);
    }

    private CompartmentDetector.Result holds() {
        byte[] wall = new byte[dx * dy * dz];
        boolean[] air = new boolean[wall.length];
        Set<BlockPos> solid = new HashSet<>();
        int scanned = 0;

        for (int iy = 0; iy < dy; iy++) {
            for (int iz = 0; iz < dz; iz++) {
                for (int ix = 0; ix < dx; ix++) {
                    int x = minX + ix, y = minY + iy, z = minZ + iz;
                    int section = (((y >> 4) - sectionY) * sectionsZ + (z >> 4) - sectionZ) * sectionsX
                            + (x >> 4) - sectionX;
                    if (!loaded[section])
                        continue;

                    scanned++;

                    PalettedContainer<BlockState> states = sections.get(section);
                    int i = (iy * dz + iz) * dx + ix;
                    byte seal = glued != null && glued[i] ? SOLID
                            : states == null ? 0 : seals.get(states.get(x & 15, y & 15, z & 15));
                    if (seal == 0) {
                        air[i] = true;
                    } else {
                        wall[i] = seal;
                        solid.add(new BlockPos(x, y, z));
                    }
                }
            }
        }

        byte[] cover = mend(sweep(wall), wall);

        for (int i = 0; i < air.length; i++)
            air[i] &= cover[i] == BOXED;

        List<CompartmentDetector.Component> comps = new ArrayList<>();

        for (Set<BlockPos> hold : split(air)) {
            BlockPos anchor = null;

            for (BlockPos p : hold) {
                if (anchor == null || lex(p, anchor) < 0)
                    anchor = p;
            }

            Set<BlockPos> group = brim(hold, solid);

            Set<BlockPos> skin = new HashSet<>();

            for (BlockPos p : group) {
                for (Direction dir : Direction.values()) {
                    BlockPos n = p.relative(dir);
                    if (solid.contains(n))
                        skin.add(n);
                }
            }

            comps.add(new CompartmentDetector.Component(group, skin, true, anchor));
        }

        if (comps.isEmpty())
            return null;

        return new CompartmentDetector.Result(comps, scanned, Collections.unmodifiableSet(solid));
    }

    private List<Set<BlockPos>> split(boolean[] cells) {
        List<Set<BlockPos>> groups = new ArrayList<>();
        int[] queue = new int[cells.length];
        int layer = dx * dz;

        for (int seed = 0; seed < cells.length; seed++) {
            if (!cells[seed])
                continue;

            cells[seed] = false;

            Set<BlockPos> group = new HashSet<>();
            int head = 0, tail = 0;
            queue[tail++] = seed;

            while (head < tail) {
                int i = queue[head++];
                int ix = i % dx, iz = i / dx % dz, iy = i / layer;
                group.add(new BlockPos(minX + ix, minY + iy, minZ + iz));

                for (Direction dir : Direction.values()) {
                    int nx = ix + dir.getStepX(), ny = iy + dir.getStepY(), nz = iz + dir.getStepZ();
                    if (nx < 0 || nx >= dx || ny < 0 || ny >= dy || nz < 0 || nz >= dz)
                        continue;

                    int n = (ny * dz + nz) * dx + nx;
                    if (cells[n]) {
                        cells[n] = false;
                        queue[tail++] = n;
                    }
                }
            }

            groups.add(group);
        }

        return groups;
    }

    private static Set<BlockPos> brim(Set<BlockPos> hold, Set<BlockPos> walls) {
        int top = Integer.MIN_VALUE;
        Map<Long, Integer> columns = new HashMap<>();

        for (BlockPos p : hold) {
            top = Math.max(top, p.getY());
            columns.merge(BlockPos.asLong(p.getX(), 0, p.getZ()), p.getY(), Math::min);
        }

        Map<Long, Integer> rim = new HashMap<>();

        for (Map.Entry<Long, Integer> e : columns.entrySet()) {
            int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey());

            for (Direction dir : Direction.Plane.HORIZONTAL) {
                long key = BlockPos.asLong(x + dir.getStepX(), 0, z + dir.getStepZ());
                if (!columns.containsKey(key))
                    rim.merge(key, e.getValue(), Math::min);
            }
        }

        Set<BlockPos> out = new HashSet<>(hold);
        raise(out, columns, top, walls, true);
        raise(out, rim, top, walls, false);
        return out;
    }

    private static void raise(Set<BlockPos> out, Map<Long, Integer> columns, int top, Set<BlockPos> walls, boolean open) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        for (Map.Entry<Long, Integer> e : columns.entrySet()) {
            int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey());
            boolean held = open;

            for (int y = e.getValue(); y <= top; y++) {
                p.set(x, y, z);

                if (walls.contains(p)) {
                    held = true;
                    continue;
                }

                if (held)
                    out.add(p.immutable());
            }
        }
    }

    private byte[] sweep(byte[] wall) {
        byte[] cover = new byte[wall.length];

        for (int iy = 0; iy < dy; iy++) {
            for (int iz = 0; iz < dz; iz++) {
                int row = (iy * dz + iz) * dx;
                boolean seen = false;

                for (int ix = 0; ix < dx; ix++) {
                    if ((wall[row + ix] & CUT_X) != 0)
                        seen = true;
                    else if (seen)
                        cover[row + ix] |= WEST;
                }

                seen = false;

                for (int ix = dx - 1; ix >= 0; ix--) {
                    if ((wall[row + ix] & CUT_X) != 0)
                        seen = true;
                    else if (seen)
                        cover[row + ix] |= EAST;
                }
            }

            for (int ix = 0; ix < dx; ix++) {
                boolean seen = false;

                for (int iz = 0; iz < dz; iz++) {
                    int i = (iy * dz + iz) * dx + ix;
                    if ((wall[i] & CUT_Z) != 0)
                        seen = true;
                    else if (seen)
                        cover[i] |= NORTH;
                }

                seen = false;

                for (int iz = dz - 1; iz >= 0; iz--) {
                    int i = (iy * dz + iz) * dx + ix;
                    if ((wall[i] & CUT_Z) != 0)
                        seen = true;
                    else if (seen)
                        cover[i] |= SOUTH;
                }
            }
        }

        int layer = dx * dz;

        for (int column = 0; column < layer; column++) {
            boolean floored = false;

            for (int iy = 0; iy < dy; iy++) {
                int i = iy * layer + column;
                if ((wall[i] & CUT_Y) != 0)
                    floored = true;
                else if (floored)
                    cover[i] |= FLOOR;
            }
        }

        return cover;
    }

    private byte[] mend(byte[] cover, byte[] wall) {
        for (int pass = 0; pass < MEND_PASSES; pass++) {
            byte[] next = cover.clone();
            boolean changed = false;

            for (int iy = 0; iy < dy; iy++) {
                for (int iz = 0; iz < dz; iz++) {
                    for (int ix = 0; ix < dx; ix++) {
                        int i = (iy * dz + iz) * dx + ix;
                        if (wall[i] != 0 || cover[i] == BOXED)
                            continue;

                        int gained = 0;
                        if (iz > 0)
                            gained |= cover[i - dx] & (WEST | EAST);
                        if (iz < dz - 1)
                            gained |= cover[i + dx] & (WEST | EAST);
                        if (ix > 0)
                            gained |= cover[i - 1] & (NORTH | SOUTH);
                        if (ix < dx - 1)
                            gained |= cover[i + 1] & (NORTH | SOUTH);
                        if ((gained & ~cover[i]) != 0) {
                            next[i] = (byte) (cover[i] | gained);
                            changed = true;
                        }
                    }
                }
            }

            cover = next;
            if (!changed)
                break;
        }

        return cover;
    }

    private static int lex(BlockPos a, BlockPos b) {
        int d = Integer.compare(a.getX(), b.getX());
        if (d != 0)
            return d;

        d = Integer.compare(a.getY(), b.getY());
        return d != 0 ? d : Integer.compare(a.getZ(), b.getZ());
    }
}
