package com.maxenonyme.createsubmarine.submarine.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;

public class WatermarkOverlay {
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath("create_submarine", "watermark"), (guiGraphics, partialTick) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.hideGui || FMLEnvironment.production)
                return;

            String text = "[Create Deep Seas: In development]";
            int x = 10;
            int y = guiGraphics.guiHeight() - 15;

            guiGraphics.drawString(mc.font, text, x, y, 0xAAFFFFFF, true);
        });
    }
}
