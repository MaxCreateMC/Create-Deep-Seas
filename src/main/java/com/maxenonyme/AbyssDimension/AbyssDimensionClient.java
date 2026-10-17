package com.maxenonyme.AbyssDimension;

import com.maxenonyme.AbyssDimension.client.AbyssSpecialEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = "create_abyss", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class AbyssDimensionClient {
    private static final ResourceLocation ABYSS_DIM = ResourceLocation.fromNamespaceAndPath("create_abyss", "abyss");

    /** Set by /submarine abyss fog off (synced from the server). Skips the dense abyss fog. */
    public static volatile boolean fogDisabled = false;

    @SubscribeEvent
    public static void onRegisterDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(
            ABYSS_DIM,
            new AbyssSpecialEffects()
        );
    }

    @EventBusSubscriber(modid = "create_abyss", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static class GameEvents {

        @SubscribeEvent
        public static void onRenderFog(ViewportEvent.RenderFog event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.dimension().location().equals(ABYSS_DIM)) {
                if (fogDisabled) {
                    if (event.getCamera().getFluidInCamera() == FogType.WATER) {
                        event.setNearPlaneDistance(-4.0F);
                        event.setFarPlaneDistance(256.0F);
                    } else {
                        event.setNearPlaneDistance(0.0F);
                        event.setFarPlaneDistance(1024.0F);
                    }
                    event.setCanceled(true);
                    return;
                }
                if (event.getCamera().getFluidInCamera() == FogType.WATER) {
                    event.setNearPlaneDistance(-4.0F);
                    event.setFarPlaneDistance(32.0F);
                    event.setCanceled(true);
                } else {
                    event.setNearPlaneDistance(0.0F);
                    event.setFarPlaneDistance(64.0F);
                    event.setCanceled(true);
                }
            }
        }

        @SubscribeEvent
        public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.dimension().location().equals(ABYSS_DIM)) {
                if (event.getCamera().getFluidInCamera() == FogType.WATER) {
                    event.setRed(0.0F);
                    event.setGreen(0.0627F);
                    event.setBlue(0.1882F);
                }
            }
        }
    }
}
