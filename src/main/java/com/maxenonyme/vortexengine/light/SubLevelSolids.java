package com.maxenonyme.vortexengine.light;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SubLevelSolids {

    record Hull(Pose3d pose, long[] cells, int[] filters) {
    }

    record Snapshot(List<Hull> hulls, long signature) {
    }

    private record Cells(long[] cells, int[] filters) {
    }

    private record Cached(Set<BlockPos> source, Cells cells) {
    }

    private static final Map<UUID, Cached> CELLS = new HashMap<>();

    static Snapshot snapshot(ClientLevel level, int x, int y, int z, int size) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null)
            return new Snapshot(List.of(), 0L);

        List<Hull> hulls = new ArrayList<>();
        long signature = 1L;

        for (SubLevel entry : container.getAllSubLevels()) {
            if (!(entry instanceof ClientSubLevel subLevel) || subLevel.isRemoved())
                continue;

            BoundingBox3dc box = subLevel.boundingBox();
            if (box == null || box.maxX() < x || box.maxY() < y || box.maxZ() < z || box.minX() > x + size
                    || box.minY() > y + size || box.minZ() > z + size)
                continue;

            Cells cells = cells(level, subLevel.getUniqueId());
            if (cells.cells().length == 0)
                continue;

            Pose3dc pose = subLevel.logicalPose();
            hulls.add(new Hull(new Pose3d(pose), cells.cells(), cells.filters()));

            signature = signature * 31 + System.identityHashCode(cells);
            signature = signature * 31 + quantize(pose);
        }

        return new Snapshot(hulls, signature);
    }

    static void stamp(byte[] grid, List<Hull> hulls, int x, int y, int z, int size) {
        Vector3d point = new Vector3d();

        for (Hull hull : hulls) {
            long[] cells = hull.cells();

            for (int i = 0; i < cells.length; i++) {
                int bx = BlockPos.getX(cells[i]), by = BlockPos.getY(cells[i]), bz = BlockPos.getZ(cells[i]);
                int filter = hull.filters()[i];

                hull.pose().transformPosition(point.set(bx + 0.5, by + 0.5, bz + 0.5));
                int cx = Mth.floor(point.x) - x, cy = Mth.floor(point.y) - y, cz = Mth.floor(point.z) - z;
                if (cx < 0 || cy < 0 || cz < 0 || cx >= size || cy >= size || cz >= size)
                    continue;

                int index = (cx + cy * size + cz * size * size) * 4;
                if (filter == 0)
                    ShadowGrid.block(grid, index);
                else
                    ShadowGrid.filter(grid, index, filter);
            }
        }
    }

    static void clear() {
        CELLS.clear();
    }

    private static Cells cells(ClientLevel level, UUID id) {
        Set<BlockPos> solid = CompartmentTracker.solidBlocks(id);
        Cached known = CELLS.get(id);
        if (known != null && known.source() == solid)
            return known.cells();

        LongArrayList cells = new LongArrayList(solid.size());
        IntArrayList filters = new IntArrayList();

        for (BlockPos pos : solid) {
            BlockState state = level.getBlockState(pos);
            int filter = LightIndex.filter(state);

            if (filter != 0) {
                cells.add(pos.asLong());
                filters.add(filter);
            } else if ((state.canOcclude() || state.is(Blocks.TINTED_GLASS)) && state.getLightEmission() == 0) {
                cells.add(pos.asLong());
                filters.add(0);
            }
        }

        Cells found = new Cells(cells.toLongArray(), filters.toIntArray());
        CELLS.put(id, new Cached(solid, found));
        return found;
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
