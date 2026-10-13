package com.maxenonyme.createsubmarine.submarine.client;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class WardingEffectHandler {
    private static final int EFFECT_DURATION = 40;

    private static int activeTicks = 0;
    private static int targetPlayerId = -1;
    private static PointLightData lightData;
    private static LightRenderHandle lightHandle;
    private static boolean registered = false;

    private WardingEffectHandler() {
    }

    public static void trigger(int playerId) {
        targetPlayerId = playerId;
        activeTicks = EFFECT_DURATION;
        if (!registered) {
            registered = true;
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, WardingEffectHandler::onClientTick);
        }
    }

    private static void onClientTick(ClientTickEvent.Pre event) {
        if (activeTicks <= 0) {
            cleanup();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Player player = (Player) (level != null ? level.getEntity(targetPlayerId) : null);
        if (player == null) {
            cleanup();
            return;
        }

        double px = player.getX();
        double py = player.getY() + 1.0;
        double pz = player.getZ();

        if (lightData == null) {
            lightData = new PointLightData();
            lightData.setColor(0.6F, 0.8F, 1.0F);
            lightData.setBrightness(12.0F);
            lightData.setOcclusionEnabled(false);
        }

        float rad = Math.min(20.0F - (20.0F - activeTicks * 0.5F), 20.0F);
        lightData.setPosition((float) px, (float) py, (float) pz);
        lightData.setRadius(rad);
        if (lightHandle == null && VeilRenderSystem.renderer() != null) {
            lightHandle = VeilRenderSystem.renderer().getLightRenderer().addLight(lightData);
        }

        float progress = 1.0F - ((float) activeTicks / EFFECT_DURATION);

        for (int ring = 0; ring < 4; ring++) {
            float phase = (progress + (float) ring / 4) % 1.0F;
            double radius = 2.0 + phase * 12.0;
            int count = 8 + (int) (phase * 24);
            double yOff = Math.sin(phase * Math.PI * 2) * 1.5;
            for (int i = 0; i < count; i++) {
                double angle = (i / (double) count) * Math.PI * 2 + activeTicks * 0.15;
                double x = px + Math.cos(angle) * radius;
                double z = pz + Math.sin(angle) * radius;
                double y = py + yOff;

                float age = (float) i / count;
                if (ring % 2 == 0) {
                    level.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0.02, 0);
                } else {
                    level.addParticle(ParticleTypes.CLOUD, x, y, z, 0, 0.01, 0);
                }
            }
        }

        if (activeTicks > EFFECT_DURATION - 8) {
            for (int i = 0; i < 3; i++) {
                double a = player.getRandom().nextDouble() * Math.PI * 2;
                double r = 0.5 + player.getRandom().nextDouble() * 1.5;
                level.addParticle(ParticleTypes.WITCH, px + Math.cos(a) * r, py + 0.5, pz + Math.sin(a) * r, 0, 0.05, 0);
            }
        }

        activeTicks--;
    }

    private static void cleanup() {
        activeTicks = 0;
        targetPlayerId = -1;
        if (lightHandle != null) {
            lightHandle.free();
            lightHandle = null;
        }
    }

    public static void clearOnLogout() {
        cleanup();
    }
}
