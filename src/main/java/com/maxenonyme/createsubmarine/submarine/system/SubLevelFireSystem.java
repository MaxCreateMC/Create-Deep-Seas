package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;

import java.util.UUID;

public final class SubLevelFireSystem {
    private SubLevelFireSystem() {
    }

    private static final int INTERVAL = 10;
    private static int ticks;

    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks % INTERVAL != 0)
            return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null)
                continue;
            for (SubLevel sub : container.getAllSubLevels()) {
                if (!sub.isRemoved() && sub.getPlot() != null)
                    douse(level, sub);
            }
        }
    }

    private static void douse(ServerLevel level, SubLevel sub) {
        LevelPlot plot = sub.getPlot();
        BoundingBox3ic b = plot.getBoundingBox();
        UUID id = sub.getUniqueId();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        Vector3d w = new Vector3d();
        for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) {
            for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) {
                LevelChunk chunk = plot.getChunk(plot.toLocal(new ChunkPos(cx, cz)));
                if (chunk == null)
                    continue;
                for (int cy = b.minY() >> 4; cy <= b.maxY() >> 4; cy++) {
                    int si = chunk.getSectionIndexFromSectionY(cy);
                    if (si < 0 || si >= chunk.getSections().length)
                        continue;
                    LevelChunkSection section = chunk.getSection(si);
                    if (section == null || section.hasOnlyAir()
                            || !section.maybeHas(state -> state.getBlock() instanceof BaseFireBlock))
                        continue;
                    for (int x = 0; x < 16; x++)
                        for (int y = 0; y < 16; y++)
                            for (int z = 0; z < 16; z++) {
                                if (!(section.getBlockState(x, y, z).getBlock() instanceof BaseFireBlock))
                                    continue;
                                m.set((cx << 4) + x, (cy << 4) + y, (cz << 4) + z);
                                if (CompartmentTracker.isWithinShip(id, m))
                                    continue;
                                sub.logicalPose().transformPosition(w.set(m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5));
                                BlockPos world = BlockPos.containing(w.x, w.y, w.z);
                                if (!CompartmentTracker.realFluidState(level, world).is(FluidTags.WATER))
                                    continue;
                                level.setBlock(m, Blocks.AIR.defaultBlockState(), 3);
                                level.playSound(null, world, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f,
                                        2.6f + (level.random.nextFloat() - level.random.nextFloat()) * 0.8f);
                                level.sendParticles(ParticleTypes.LARGE_SMOKE, w.x, w.y, w.z, 4, 0.2, 0.2, 0.2, 0.01);
                            }
                }
            }
        }
    }
}
