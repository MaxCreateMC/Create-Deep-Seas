package com.maxenonyme.highseas.sail;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import com.maxenonyme.highseas.block.BoatSailBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public final class SailFurlHandler {
    private SailFurlHandler() {
    }

    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(BoatSailBlock.SAILS)) {
            return;
        }

        SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(level, pos);
        if (ship == null) {
            return;
        }
        BlockPos groupMin = SailDetector.groupMin(level, pos);
        if (groupMin == null) {
            return;
        }
        UUID sub = ship.getUniqueId();
        FurlState.setFurled(sub, groupMin, !FurlState.isFurled(sub, groupMin));
        FurlSavedData.markDirty();
        PacketDistributor.sendToAllPlayers(new FurlSyncPayload(sub, FurlState.get(sub)));

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    public static void onServerStarted(ServerStartedEvent event) {
        FurlState.clearAll();
        FurlSavedData.get(event.getServer().overworld());
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        for (UUID sub : FurlState.subs()) {
            PacketDistributor.sendToPlayer(player, new FurlSyncPayload(sub, FurlState.get(sub)));
        }
    }
}
