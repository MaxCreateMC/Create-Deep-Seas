package com.maxenonyme.vortexengine.mist;

import com.maxenonyme.highseas.compat.iris.IrisCompat;
import com.maxenonyme.vortexengine.VortexConfig;
import com.maxenonyme.vortexengine.VortexEngine;
import com.maxenonyme.vortexengine.light.LightField;
import com.maxenonyme.vortexengine.light.VortexLights;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.ArrayList;
import java.util.List;

public class MistField {

    private static final List<MistVolume> VOLUMES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    public static boolean usable() {
        return VortexEngine.enabled() && VortexConfig.MIST.get() && !IrisCompat.isShaderPackActive();
    }

    public static boolean voxel() {
        return usable() && VortexConfig.MIST_STYLE.get() == VortexConfig.MistStyle.VOXEL && VortexLights.usable()
                && LightField.ready();
    }

    public static int add(ClientLevel level, Vec3 center, float radius, float density, float red, float green,
            float blue) {
        int budget = VortexConfig.MAX_PUFFS.get() - puffCount();
        int wanted = Math.max(12, Math.round(density * radius * radius * 2));
        int count = Math.min(wanted, budget);
        if (count <= 0)
            return 0;

        VOLUMES.add(new MistVolume(level, RANDOM, center.x, center.y, center.z, radius, density, red, green, blue,
                count));
        return count;
    }

    public static int clear() {
        int count = VOLUMES.size();
        VOLUMES.clear();
        return count;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            VOLUMES.clear();
            return;
        }

        if (VOLUMES.isEmpty() || mc.isPaused() || !usable() || voxel())
            return;

        for (MistVolume volume : VOLUMES) {
            AABB area = new AABB(volume.x - volume.radius, volume.y - 32, volume.z - volume.radius,
                    volume.x + volume.radius, volume.y + 16, volume.z + volume.radius);
            List<Entity> movers = level.getEntities((Entity) null, area,
                    entity -> entity.isAlive() && !entity.isSpectator());

            volume.tick(level, RANDOM, movers);
            volume.light(level);
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide())
            VOLUMES.clear();
    }

    public static List<MistVolume> volumes() {
        return VOLUMES;
    }

    private static int puffCount() {
        int count = 0;

        for (MistVolume volume : VOLUMES)
            count += volume.puffs.size();

        return count;
    }
}
