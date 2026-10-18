package com.maxenonyme.vortexengine.light;

import com.maxenonyme.vortexengine.VortexConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

public class Wakes {

    private static final int LIFE = 30;
    private static final int MAX = 96;
    private static final List<Wake> WAKES = new ArrayList<>();

    private static class Wake {
        final double x, y, z;
        final float strength;
        int age;

        Wake(double x, double y, double z, float strength) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.strength = strength;
        }
    }

    static boolean active() {
        return VortexLights.usable() && VortexConfig.WAKE_STYLE.get() == VortexConfig.WakeStyle.GLOW
                && VortexConfig.LIGHT_STYLE.get() != VortexConfig.LightStyle.VOLUMES;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            WAKES.clear();
            return;
        }

        if (mc.isPaused())
            return;

        WAKES.removeIf(wake -> ++wake.age >= LIFE);
        if (!active())
            return;

        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        Entity viewer = mc.getCameraEntity();

        for (Entity entity : level.entitiesForRendering()) {
            if (WAKES.size() >= MAX)
                break;
            if (entity.tickCount % 5 != 0 || !entity.isInWater() || entity.isSpectator())
                continue;
            if (entity == mc.player || entity == viewer || viewer != null && viewer.getVehicle() == entity)
                continue;
            if (entity.distanceToSqr(camera) > 48 * 48)
                continue;

            double dx = entity.getX() - entity.xo, dy = entity.getY() - entity.yo, dz = entity.getZ() - entity.zo;
            double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (speed < 0.06)
                continue;

            WAKES.add(new Wake(entity.xo, entity.yo + entity.getBbHeight() * 0.5, entity.zo,
                    (float) Math.min(1, speed * 3)));
        }
    }

    static int collect(List<Light> out) {
        for (Wake wake : WAKES) {
            float fade = 1 - wake.age / (float) LIFE;
            float strength = wake.strength * fade * fade * 1.4f;
            out.add(Light.omni(wake.x, wake.y, wake.z, 0.25f, 0.85f, 1f, 5.5f, strength, 0));
        }

        return WAKES.size();
    }
}
