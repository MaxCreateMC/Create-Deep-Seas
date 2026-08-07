package com.maxenonyme.AbyssDimension;

import com.maxenonyme.AbyssDimension.client.AbyssSpecialEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = "create_submarine", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class AbyssDimensionClient {
    private static final ResourceLocation ABYSS_DIM = ResourceLocation.fromNamespaceAndPath("create_abyss", "abyss");

    @SubscribeEvent
    public static void onRegisterDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(
            ABYSS_DIM,
            new AbyssSpecialEffects()
        );
    }

    @EventBusSubscriber(modid = "create_submarine", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static class GameEvents {

        @SubscribeEvent
        public static void onRenderFog(ViewportEvent.RenderFog event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.dimension().location().equals(ABYSS_DIM)) {
                if (event.getCamera().getFluidInCamera() == net.minecraft.world.level.material.FogType.WATER) {
                    if (mc.player != null && mc.player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)) {
                        event.setNearPlaneDistance(-1.0E5F);
                        event.setFarPlaneDistance(1.0E6F);
                    } else {
                        event.setNearPlaneDistance(-4.0F);
                        event.setFarPlaneDistance(32.0F);
                    }
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
                if (event.getCamera().getFluidInCamera() == net.minecraft.world.level.material.FogType.WATER) {
                    event.setRed(0.0F);
                    event.setGreen(0.0627F);
                    event.setBlue(0.1882F);
                }
            }
        }
    }
}
