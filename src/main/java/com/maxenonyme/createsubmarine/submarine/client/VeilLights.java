package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import foundry.veil.Veil;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType;
import foundry.veil.api.client.render.light.data.LightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashSet;
import java.util.Set;

public final class VeilLights {
    private static final ResourceLocation BUFFERS = ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "lights");
    private static final int LINGER_TICKS = 200;
    private static final Set<LightRenderHandle<?>> HELD = new HashSet<>();
    private static boolean pinned;
    private static int idle;

    private VeilLights() {
    }

    public static boolean usable() {
        if (Veil.IRIS)
            return false;
        return !SubmarineConfig.CLIENT_SPEC.isLoaded() || SubmarineConfig.VEIL_LIGHTS.get();
    }

    public static boolean usableForAlarm() {
        if (!usable())
            return false;
        return !SubmarineConfig.CLIENT_SPEC.isLoaded() || !SubmarineConfig.PHOTOSENSITIVE_MODE.get();
    }

    public static <T extends LightData> LightRenderHandle<T> add(T light) {
        LightRenderHandle<T> handle = VeilRenderSystem.renderer().getLightRenderer().addLight(light);
        HELD.add(handle);
        idle = 0;
        if (!pinned) {
            pinned = true;
            VeilRenderSystem.renderer().enableBuffers(BUFFERS, DynamicBufferType.ALBEDO, DynamicBufferType.NORMAL);
        }
        return handle;
    }

    public static void free(Object handle) {
        if (handle instanceof LightRenderHandle<?> light) {
            HELD.remove(light);
            light.free();
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (!pinned)
            return;
        HELD.removeIf(light -> !light.isValid());
        if (!HELD.isEmpty()) {
            idle = 0;
            return;
        }
        if (++idle < LINGER_TICKS)
            return;
        pinned = false;
        VeilRenderSystem.renderer().disableBuffers(BUFFERS, DynamicBufferType.ALBEDO, DynamicBufferType.NORMAL);
    }
}
