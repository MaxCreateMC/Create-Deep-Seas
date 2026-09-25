package com.maxenonyme.highseas.item;

import com.maxenonyme.highseas.CreateHighSeas;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class SeaglideAttackGuard {
    private SeaglideAttackGuard() {
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isHolding(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    public static void onAttackEntity(AttackEntityEvent event) {
        if (isHolding(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static boolean isHolding(Player player) {
        return player.getMainHandItem().is(CreateHighSeas.SEAGLIDE.get())
                || player.getOffhandItem().is(CreateHighSeas.SEAGLIDE.get());
    }
}
