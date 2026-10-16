package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.submarine.client.renderer.SubmarineWaterCullBuffer;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.UUID;

public class SubmarineFogHandler {

    private static final double PROBE_OFFSET = 0.35;

    private static boolean cachedShouldFog = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null) {
            cachedShouldFog = false;
            return;
        }

        SubmarineWaterCullBuffer.syncSubmarinePoses();
        cachedShouldFog = computeShouldFog(mc.level, player.getEyePosition());
    }

    public static boolean shouldFog() {
        return cachedShouldFog;
    }

    private static boolean computeShouldFog(Level level, Vec3 probePos) {
        if (findSubmarineContainingPos(level, probePos) == null)
            return false;

        return isWaterAt(level, Mth.floor(probePos.x), Mth.floor(probePos.y), Mth.floor(probePos.z))
                || isSubmerged(level, probePos);
    }

    private static UUID findSubmarineContainingPos(Level level, Vec3 probePos) {
        for (double dx = -PROBE_OFFSET; dx <= PROBE_OFFSET; dx += PROBE_OFFSET) {
            for (double dy = -PROBE_OFFSET; dy <= PROBE_OFFSET; dy += PROBE_OFFSET) {
                for (double dz = -PROBE_OFFSET; dz <= PROBE_OFFSET; dz += PROBE_OFFSET) {
                    BlockPos pos = BlockPos.containing(probePos.x + dx, probePos.y + dy, probePos.z + dz);
                    UUID id = CompartmentTracker.findSealedSublevel(level, pos);
                    if (id != null)
                        return id;
                }
            }
        }

        return null;
    }

    private static boolean isSubmerged(Level level, Vec3 probePos) {
        int x = (int) Math.floor(probePos.x);
        int z = (int) Math.floor(probePos.z);
        int startY = (int) Math.floor(probePos.y);
        int endY = level.getMaxBuildHeight();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = startY; y <= endY; y++) {
            cursor.set(x, y, z);
            if (CompartmentTracker.isOccluded(level, cursor))
                continue;

            return isWaterAt(level, x, y, z);
        }

        return false;
    }

    private static boolean isWaterAt(Level level, int x, int y, int z) {
        try {
            LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (chunk == null)
                return false;

            int sectionIndex = chunk.getSectionIndex(y);
            LevelChunkSection[] sections = chunk.getSections();
            if (sectionIndex < 0 || sectionIndex >= sections.length)
                return false;

            LevelChunkSection section = sections[sectionIndex];
            if (section == null || section.hasOnlyAir())
                return false;

            BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
            return state.getFluidState().is(FluidTags.WATER);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
