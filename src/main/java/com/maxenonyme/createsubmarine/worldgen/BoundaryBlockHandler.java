package com.maxenonyme.createsubmarine.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.HashSet;
import java.util.Set;

public class BoundaryBlockHandler {

    private static final ResourceKey<Level> ABYSS_KEY = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("create_submarine", "abyss"));

    private static final int GRADIENT_BLOCKS = 16;
    private static final Set<Long> processed = new HashSet<>();

    // Disabled for now — causes "Too many chained neighbor updates" due to setBlockState triggering fluid cascades.
    // TODO: rewrite using Level.setBlock(pos, state, 0) with flags=0 to suppress updates, or use chunk section storage directly.
    //@SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        Level level = chunk.getLevel();
        if (level == null || level.isClientSide) return;
        if (!level.dimension().equals(ABYSS_KEY)) return;

        long cpos = chunk.getPos().toLong();
        if (!processed.add(cpos)) return;

        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = minX + lx;
                int wz = minZ + lz;

                double strength = SeafloorGenerator.getBoundaryStrength(wx, wz);
                if (strength < 0.3) continue;

                int topY = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, lx, lz);
                if (topY <= level.getMinBuildHeight()) continue;

                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(wx, topY, wz);

                if (strength > 0.8) {
                    chunk.setBlockState(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), false);
                }

                for (int dy = 1; dy <= GRADIENT_BLOCKS; dy++) {
                    pos.setY(topY - dy);
                    if (pos.getY() <= level.getMinBuildHeight()) break;
                    if (chunk.getBlockState(pos).isAir()) break;

                    double t = (double) dy / GRADIENT_BLOCKS;
                    BlockState bs;
                    if (t < 0.25) {
                        bs = Blocks.COBBLESTONE.defaultBlockState();
                    } else if (t < 0.55) {
                        bs = Blocks.STONE.defaultBlockState();
                    } else {
                        bs = Blocks.DEEPSLATE.defaultBlockState();
                    }
                    chunk.setBlockState(pos, bs, false);
                }
            }
        }
    }
}
