package com.maxenonyme.createsubmarine.submarine.item;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.network.PressureModePayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = CreateSubmarine.MOD_ID)
public final class PressureGogglesEvents {
    private PressureGogglesEvents() {
    }

    private static boolean wantsToggle(Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && player.getMainHandItem().isEmpty()
                && PressureGogglesItem.isWearing(player);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!wantsToggle(player, event.getHand()))
            return;
        if (!event.getLevel().isClientSide)
            PressureGogglesItem.toggle(player, player.getItemBySlot(EquipmentSlot.HEAD));
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
    }

    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (wantsToggle(event.getEntity(), event.getHand()))
            PacketDistributor.sendToServer(new PressureModePayload());
    }
}
