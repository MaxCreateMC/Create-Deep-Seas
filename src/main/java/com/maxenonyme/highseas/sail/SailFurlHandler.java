package com.maxenonyme.highseas.sail;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public final class SailFurlHandler {
    private SailFurlHandler() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        FurlState.clearAll();
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        for (UUID sub : FurlState.reefSubs()) {
            PacketDistributor.sendToPlayer(player, ReefSyncPayload.of(sub));
        }
    }
}
