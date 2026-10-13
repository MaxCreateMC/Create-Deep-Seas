package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.submarine.block.entity.UnderwaterMineBlockEntity;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.compartment.FloodSystem;
import com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig;
import com.maxenonyme.createsubmarine.submarine.network.HullConfigSyncPayload;
import com.maxenonyme.createsubmarine.submarine.network.SubCrackPayload;
import com.maxenonyme.createsubmarine.submarine.network.SubLevelBoundsPayload;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;

public class SubmarineLifecycleHandler {

    public static void onServerStopping(ServerStoppingEvent event) {
        SubmarineSinkingSystem.clearCrashed();
        ImplosionSequence.clearAll();
        LightFailure.clear(false);
        FloodSystem.clearAll();
        SubmarinePressureSystem.clearAll();
        SubLevelRegistry.clearAll();
        CompartmentTracker.clearAll();
        SubmarineDriverRegistry.clearAll();
        SubmarineInteractionSystem.clearAll();
        UnderwaterMineBlockEntity.clearAll();
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel sl) {
            SubLevelRegistry.clearForLevel(sl);
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;

        PacketDistributor.sendToPlayer(player,
                new HullConfigSyncPayload(
                        HullStrengthConfig.getValues()));

        for (Map.Entry<UUID, SubLevelAccess> entry : SubLevelRegistry.getAll().entrySet()) {
            UUID subId = entry.getKey();

            SubLevelRegistry.PlotBounds bounds = SubLevelRegistry.getBounds(subId);
            if (bounds != null) {
                PacketDistributor.sendToPlayer(player,
                        new SubLevelBoundsPayload(subId, bounds.minY(), bounds.maxY()));
            }

            Map<BlockPos, Integer> cracks = SubmarinePressureSystem.getAllCracks().get(subId);
            if (cracks == null)
                continue;
            for (Map.Entry<BlockPos, Integer> c : cracks.entrySet()) {
                PacketDistributor.sendToPlayer(player,
                        new SubCrackPayload(subId, c.getKey(), c.getValue(), 0));
            }
        }
    }
}
