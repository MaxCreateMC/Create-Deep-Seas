package com.maxenonyme.createsubmarine.submarine.compartment;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class PlotSnapshot {

    public final int minX, minY, minZ, maxX, maxY, maxZ;

    private final int sectionX, sectionY, sectionZ, sectionsX, sectionsY, sectionsZ;
    private final List<PalettedContainer<BlockState>> sections = new ArrayList<>();
    private final boolean[] loaded;

    public PlotSnapshot(LevelPlot plot) {
        BoundingBox3ic box = plot.getBoundingBox();
        minX = box.minX();
        minY = box.minY();
        minZ = box.minZ();
        maxX = box.maxX();
        maxY = box.maxY();
        maxZ = box.maxZ();

        sectionX = minX >> 4;
        sectionY = minY >> 4;
        sectionZ = minZ >> 4;
        sectionsX = (maxX >> 4) - sectionX + 1;
        sectionsY = (maxY >> 4) - sectionY + 1;
        sectionsZ = (maxZ >> 4) - sectionZ + 1;
        loaded = new boolean[sectionsX * sectionsY * sectionsZ];

        for (int sy = 0; sy < sectionsY; sy++) {
            for (int sz = 0; sz < sectionsZ; sz++) {
                for (int sx = 0; sx < sectionsX; sx++) {
                    LevelChunk chunk = plot.getChunk(plot.toLocal(new ChunkPos(sectionX + sx, sectionZ + sz)));
                    if (chunk == null) {
                        sections.add(null);
                        continue;
                    }

                    loaded[sections.size()] = true;
                    int index = chunk.getSectionIndexFromSectionY(sectionY + sy);
                    if (index < 0 || index >= chunk.getSectionsCount()) {
                        sections.add(null);
                        continue;
                    }

                    LevelChunkSection section = chunk.getSection(index);
                    sections.add(section.hasOnlyAir() ? null : section.getStates().copy());
                }
            }
        }
    }

    public BlockState get(int x, int y, int z) {
        int sx = (x >> 4) - sectionX, sy = (y >> 4) - sectionY, sz = (z >> 4) - sectionZ;
        if (sx < 0 || sx >= sectionsX || sy < 0 || sy >= sectionsY || sz < 0 || sz >= sectionsZ)
            return null;

        int index = (sy * sectionsZ + sz) * sectionsX + sx;
        if (!loaded[index])
            return null;

        PalettedContainer<BlockState> states = sections.get(index);
        return states == null ? Blocks.AIR.defaultBlockState() : states.get(x & 15, y & 15, z & 15);
    }

    public void palette(Consumer<BlockState> action) {
        action.accept(Blocks.AIR.defaultBlockState());

        for (PalettedContainer<BlockState> states : sections) {
            if (states != null)
                states.count((state, count) -> action.accept(state));
        }
    }
}
