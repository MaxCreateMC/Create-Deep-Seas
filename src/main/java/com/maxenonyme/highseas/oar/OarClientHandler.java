package com.maxenonyme.highseas.oar;

import com.maxenonyme.highseas.CreateHighSeas;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class OarClientHandler {
    private OarClientHandler() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || mc.screen != null)
            return;
        if (!mc.options.keyUse.isDown())
            return;
        if (!isHoldingOar(player))
            return;
        PacketDistributor.sendToServer(OarRowPayload.INSTANCE);
    }

    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND)
            return;
        Player player = Minecraft.getInstance().player;
        if (player == null || !OarRowAnimator.isRowing(player.getUUID()))
            return;
        if (!event.getItemStack().is(CreateHighSeas.OAR_OF_BOAT.get()))
            return;
        OarRowAnimator.applyFirstPerson(event.getPoseStack());
    }

    private static boolean isHoldingOar(Player player) {
        return player.getMainHandItem().is(CreateHighSeas.OAR_OF_BOAT.get())
                || player.getOffhandItem().is(CreateHighSeas.OAR_OF_BOAT.get());
    }
}
