package com.maxenonyme.vortexengine.light;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compartment.PlotSnapshot;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SubLevelSolids {

    private static final long MAX_VOLUME = 2_000_000L;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Vortex sub-level solids");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    record Hull(Pose3d pose, long[] cells, int[] covers) {
    }

    record Snapshot(List<Hull> hulls, long signature) {
    }

    private record Cells(long[] cells, int[] covers) {
    }

    private static class Tracked {
        Cells cells = new Cells(new long[0], new int[0]);
        CompletableFuture<Cells> pending;
        boolean dirty = true;
    }

    private static final Map<UUID, Tracked> TRACKED = new HashMap<>();

    static Snapshot snapshot(ClientLevel level, int x, int y, int z, int size) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null)
            return new Snapshot(List.of(), 0L);

        List<Hull> hulls = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        long signature = 1L;

        for (SubLevel entry : container.getAllSubLevels()) {
            if (!(entry instanceof ClientSubLevel subLevel) || subLevel.isRemoved() || subLevel.getPlot() == null)
                continue;

            UUID id = subLevel.getUniqueId();
            seen.add(id);
            Cells cells = refresh(subLevel, TRACKED.computeIfAbsent(id, key -> new Tracked()));
            if (cells.cells().length == 0)
                continue;

            BoundingBox3dc box = subLevel.boundingBox();
            if (box == null || box.maxX() < x || box.maxY() < y || box.maxZ() < z || box.minX() > x + size
                    || box.minY() > y + size || box.minZ() > z + size)
                continue;

            Pose3dc pose = subLevel.logicalPose();
            hulls.add(new Hull(new Pose3d(pose), cells.cells(), cells.covers()));

            signature = signature * 31 + System.identityHashCode(cells);
            signature = signature * 31 + quantize(pose);
        }

        TRACKED.keySet().retainAll(seen);
        return new Snapshot(hulls, signature);
    }

    static void stamp(byte[] grid, List<Hull> hulls, int x, int y, int z, int size) {
        Vector3d point = new Vector3d();

        for (Hull hull : hulls) {
            long[] cells = hull.cells();

            for (int i = 0; i < cells.length; i++) {
                int bx = BlockPos.getX(cells[i]), by = BlockPos.getY(cells[i]), bz = BlockPos.getZ(cells[i]);
                int cover = hull.covers()[i];

                if (cover < 255) {
                    for (int bit = 0; bit < 8; bit++) {
                        if ((cover >> bit & 1) == 0)
                            continue;

                        hull.pose().transformPosition(point.set(bx + 0.25 + (bit & 1) * 0.5,
                                by + 0.25 + (bit >> 1 & 1) * 0.5, bz + 0.25 + (bit >> 2) * 0.5));
                        int index = indexOf(point, x, y, z, size);
                        if (index >= 0)
                            GridCells.cover(grid, index, octantOf(point));
                    }
                    continue;
                }

                hull.pose().transformPosition(point.set(bx + 0.5, by + 0.5, bz + 0.5));
                int index = indexOf(point, x, y, z, size);
                if (index < 0)
                    continue;

                if (cover == 255)
                    GridCells.block(grid, index);
                else
                    GridCells.filter(grid, index, cover);
            }
        }
    }

    private static int indexOf(Vector3d point, int x, int y, int z, int size) {
        int cx = Mth.floor(point.x) - x, cy = Mth.floor(point.y) - y, cz = Mth.floor(point.z) - z;
        if (cx < 0 || cy < 0 || cz < 0 || cx >= size || cy >= size || cz >= size)
            return -1;

        return (cx + cy * size + cz * size * size) * 4;
    }

    private static int octantOf(Vector3d point) {
        int bit = point.x - Math.floor(point.x) >= 0.5 ? 1 : 0;
        bit |= point.y - Math.floor(point.y) >= 0.5 ? 2 : 0;
        bit |= point.z - Math.floor(point.z) >= 0.5 ? 4 : 0;
        return 1 << bit;
    }

    static void markDirty(UUID id) {
        Tracked tracked = TRACKED.get(id);
        if (tracked != null)
            tracked.dirty = true;
    }

    static void clear() {
        TRACKED.clear();
    }

    private static Cells refresh(ClientSubLevel subLevel, Tracked tracked) {
        if (tracked.pending != null && tracked.pending.isDone()) {
            Cells found = tracked.pending.join();
            tracked.pending = null;
            if (found != null)
                tracked.cells = found;
        }

        if (!tracked.dirty || tracked.pending != null)
            return tracked.cells;

        tracked.dirty = false;
        BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        long volume = (long) (bounds.maxX() - bounds.minX() + 1) * (bounds.maxY() - bounds.minY() + 1)
                * (bounds.maxZ() - bounds.minZ() + 1);
        if (volume <= 0 || volume > MAX_VOLUME)
            return tracked.cells;

        PlotSnapshot plot = new PlotSnapshot(subLevel.getPlot());
        Map<BlockState, Glow> glows = GlowDefinitions.all();
        UUID id = subLevel.getUniqueId();
        tracked.pending = CompletableFuture.supplyAsync(() -> {
            try {
                return scan(plot, glows);
            } catch (RuntimeException e) {
                CreateSubmarine.LOGGER.warn("Couldn't find the solid blocks of sub-level {}", id, e);
                return null;
            }
        }, WORKER);
        return tracked.cells;
    }

    private static Cells scan(PlotSnapshot plot, Map<BlockState, Glow> glows) {
        LongArrayList cells = new LongArrayList();
        IntArrayList covers = new IntArrayList();

        for (int x = plot.minX; x <= plot.maxX; x++) {
            for (int y = plot.minY; y <= plot.maxY; y++) {
                for (int z = plot.minZ; z <= plot.maxZ; z++) {
                    BlockState state = plot.get(x, y, z);
                    if (state == null || state.isAir())
                        continue;

                    int cover = LightIndex.filter(state);
                    if (cover == 0)
                        cover = LightIndex.cover(state, glows);
                    if (cover == 0)
                        continue;

                    cells.add(BlockPos.asLong(x, y, z));
                    covers.add(cover);
                }
            }
        }

        return new Cells(cells.toLongArray(), covers.toIntArray());
    }

    private static long quantize(Pose3dc pose) {
        Vector3dc position = pose.position();
        Quaterniondc rotation = pose.orientation();

        long hash = Math.round(position.x() * 2);
        hash = hash * 31 + Math.round(position.y() * 2);
        hash = hash * 31 + Math.round(position.z() * 2);
        hash = hash * 31 + Math.round(rotation.x() * 100);
        hash = hash * 31 + Math.round(rotation.y() * 100);
        hash = hash * 31 + Math.round(rotation.z() * 100);
        return hash * 31 + Math.round(rotation.w() * 100);
    }
}
