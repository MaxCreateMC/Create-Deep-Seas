package com.maxenonyme.theabyss;

import com.maxenonyme.theabyss.client.AbyssSpecialEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = "create_abyss", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class TheAbyssClient {
    private static final ResourceLocation ABYSS_DIM = ResourceLocation.fromNamespaceAndPath("create_abyss", "abyss");

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
                if (event.getCamera().getFluidInCamera() == FogType.WATER) {
                    float light = AbyssSpecialEffects.daylight(mc.level, (float) event.getPartialTick());
                    event.setNearPlaneDistance(-4.0F);
                    event.setFarPlaneDistance(Mth.lerp(light, 28.0F, 112.0F));
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
                    float light = AbyssSpecialEffects.daylight(mc.level, (float) event.getPartialTick());
                    event.setRed(Mth.lerp(light, 0.0F, 0.1F));
                    event.setGreen(Mth.lerp(light, 0.04F, 0.42F));
                    event.setBlue(Mth.lerp(light, 0.12F, 0.56F));
                }
            }
        }
    }
}
