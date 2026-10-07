package com.maxenonyme.createsubmarine.submarine.client.alarm;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.alarm.AlarmSettings;
import com.maxenonyme.createsubmarine.submarine.alarm.AlarmSounds;
import com.maxenonyme.createsubmarine.submarine.network.AlarmSoundChunkPayload;
import com.maxenonyme.createsubmarine.submarine.network.AlarmSoundRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AlarmClient {
    private AlarmClient() {
    }

    private static final Map<String, byte[]> READY = new ConcurrentHashMap<>();
    private static final Map<String, byte[][]> PARTS = new ConcurrentHashMap<>();
    private static final Set<String> ASKED = ConcurrentHashMap.newKeySet();

    public static void open(BlockPos pos, AlarmSettings settings, List<String> files) {
        Minecraft.getInstance().setScreen(new AlarmScreen(pos, settings, files));
    }

    private static String key(String name, long hash) {
        return name + "#" + hash;
    }

    public static byte[] sound(String name, long hash) {
        String key = key(name, hash);
        byte[] data = READY.get(key);
        if (data == null && ASKED.add(key))
            PacketDistributor.sendToServer(new AlarmSoundRequestPayload(name, hash));
        return data;
    }

    public static void receive(AlarmSoundChunkPayload payload) {
        if (payload.total() <= 0 || payload.total() > AlarmSounds.MAX_CHUNKS || payload.index() < 0 || payload.index() >= payload.total())
            return;
        String key = key(payload.name(), payload.hash());
        byte[][] parts = PARTS.computeIfAbsent(key, k -> new byte[payload.total()][]);
        if (parts.length != payload.total())
            return;
        parts[payload.index()] = payload.data();
        int size = 0;
        for (byte[] part : parts) {
            if (part == null)
                return;
            size += part.length;
        }
        byte[] whole = new byte[size];
        int at = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, whole, at, part.length);
            at += part.length;
        }
        PARTS.remove(key);
        READY.put(key, whole);
    }

    public static SoundInstance play(String name, byte[] data, double x, double y, double z, float volume) {
        String path = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        SoundInstance sound = new AlarmSoundInstance(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "alarm/" + path),
                data, x, y, z, volume);
        Minecraft.getInstance().getSoundManager().play(sound);
        return sound;
    }

    public static void stop(Object sound) {
        if (sound instanceof SoundInstance instance)
            Minecraft.getInstance().getSoundManager().stop(instance);
    }
}
