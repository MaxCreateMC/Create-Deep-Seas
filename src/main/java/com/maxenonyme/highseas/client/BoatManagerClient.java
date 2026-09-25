package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.BoatManager;
import com.maxenonyme.highseas.CreateHighSeas;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = CreateHighSeas.MOD_ID, value = Dist.CLIENT)
public final class BoatManagerClient {
    private BoatManagerClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!BoatManager.isEnabled() || mc.level == null) {
            BoatManager.clearSide(true);
            return;
        }
        BoatManager.tick(mc.level, true);
    }
}
