package com.maxenonyme.AbyssDimension.system;

import com.maxenonyme.AbyssDimension.LianaRegistry;
import com.maxenonyme.AbyssDimension.block.SubmarineLianaBlock;
import com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3dc;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

public class LianaLODOptimizer {

    public static final TagKey<Block> LIANA_LOD_TAG = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("create_submarine", "liana_lod")
    );

    private static int tickCounter = 0;
    private static final Set<UUID> awakeSubLevels = new HashSet<>();
    private static final Map<UUID, Boolean> isLianaOrPlantCache = new HashMap<>();

    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter % 10 != 0)
            return;

        MinecraftServer server = event.getServer();
        Set<UUID> presentSubLevels = new HashSet<>();

        for (ServerLevel level : server.getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null)
                continue;

            List<ServerPlayer> players = level.players();
            for (SubLevel subLevel : container.getAllSubLevels()) {
                if (!(subLevel instanceof ServerSubLevel serverSub))
                    continue;

                UUID id = serverSub.getUniqueId();
                presentSubLevels.add(id);

                Boolean isPlant = isLianaOrPlantCache.get(id);
                if (isPlant == null) {
                    isPlant = isLianaOrPlantSubLevel(serverSub);
                    if (isPlant == null)
                        continue;
                    isLianaOrPlantCache.put(id, isPlant);
                    if (isPlant)
                        SablePhysicsHelper.setAsleep(SablePhysicsHelper.getHandle(serverSub), true);
                }
                if (!isPlant)
                    continue;

                Vector3dc subPos = serverSub.logicalPose().position();
                double closest = Double.MAX_VALUE;
                for (ServerPlayer player : players)
                    closest = Math.min(closest, player.distanceToSqr(subPos.x(), subPos.y(), subPos.z()));

                Object handle = SablePhysicsHelper.getHandle(serverSub);
                if (handle == null)
                    continue;

                if (closest > 15 * 15) {
                    if (awakeSubLevels.remove(id))
                        SablePhysicsHelper.setAsleep(handle, true);
                } else if (awakeSubLevels.add(id)) {
                    SablePhysicsHelper.setAsleep(handle, false);
                }
            }
        }

        for (ServerLevel level : server.getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null)
                continue;

            for (SubLevel subLevel : container.getAllSubLevels()) {
                if (!(subLevel instanceof ServerSubLevel serverSub))
                    continue;
                if (!isLianaOrPlantCache.getOrDefault(serverSub.getUniqueId(), false))
                    continue;

                BlockPos anchor = serverSub.getPlot().getCenterBlock();
                if (anchor == null)
                    continue;
                LevelChunk anchorChunk = serverSub.getPlot().getChunk(serverSub.getPlot().toLocal(new ChunkPos(anchor)));
                if (anchorChunk == null)
                    continue;

                BlockState state = anchorChunk.getBlockState(anchor);
                if (!state.is(LianaRegistry.LIANA_BLOCK.get()))
                    continue;

                Vector3dc pos = serverSub.logicalPose().position();
                int blockLight = level.getBrightness(LightLayer.BLOCK, BlockPos.containing(pos.x(), pos.y(), pos.z()));
                if (state.getValue(SubmarineLianaBlock.LIGHT) != blockLight)
                    level.setBlock(anchor, state.setValue(SubmarineLianaBlock.LIGHT, blockLight), Block.UPDATE_CLIENTS);
            }
        }

        isLianaOrPlantCache.keySet().retainAll(presentSubLevels);
        awakeSubLevels.retainAll(presentSubLevels);
    }

    private static Boolean isLianaOrPlantSubLevel(SubLevel subLevel) {
        if (subLevel.getPlot() == null)
            return null;
        BlockPos anchor = subLevel.getPlot().getCenterBlock();
        if (anchor == null)
            return null;
        LevelChunk chunk = subLevel.getPlot().getChunk(subLevel.getPlot().toLocal(new ChunkPos(anchor)));
        if (chunk == null)
            return null;

        BlockState state = chunk.getBlockState(anchor);
        return state.is(LIANA_LOD_TAG)
                || state.is(LianaRegistry.LIANA_BLOCK.get())
                || state.is(LianaRegistry.CREEPVINE_SEED.get());
    }
}
