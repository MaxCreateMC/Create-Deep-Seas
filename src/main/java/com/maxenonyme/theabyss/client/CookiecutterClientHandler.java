package com.maxenonyme.theabyss.client;

import com.maxenonyme.theabyss.entities.CookiecutterSharkEntity;
import com.maxenonyme.theabyss.network.StruggleSharkPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class CookiecutterClientHandler {

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        if (player == null || !player.level().isClientSide)
            return;

        List<CookiecutterSharkEntity> sharks = player.level().getEntitiesOfClass(
                CookiecutterSharkEntity.class,
                player.getBoundingBox().inflate(3.0),
                shark -> shark.isLatchedTo(player));
        if (!sharks.isEmpty()) {
            PacketDistributor.sendToServer(new StruggleSharkPayload(sharks.get(0).getId()));
        }
    }
}
