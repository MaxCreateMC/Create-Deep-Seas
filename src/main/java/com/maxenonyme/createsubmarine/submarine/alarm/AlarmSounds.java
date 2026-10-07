package com.maxenonyme.createsubmarine.submarine.alarm;

import com.maxenonyme.createsubmarine.submarine.network.AlarmSoundChunkPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.CRC32;

public final class AlarmSounds {
    private AlarmSounds() {
    }

    public static final long MAX_SIZE = 8L * 1024 * 1024;
    public static final int MAX_FILES = 128;
    public static final int CHUNK = 256 * 1024;
    public static final int MAX_CHUNKS = (int) (MAX_SIZE / CHUNK) + 1;
    private static final long RESEND = 5000L;

    private static final Map<String, Long> SENT = new ConcurrentHashMap<>();

    public static Path folder() {
        Path path = FMLPaths.CONFIGDIR.get().resolve("create_submarine").resolve("alarms");
        try {
            Files.createDirectories(path);
        } catch (IOException ignored) {
        }
        return path;
    }

    public static List<String> list() {
        try (Stream<Path> files = Files.list(folder())) {
            return files.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(AlarmSounds::valid)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .limit(MAX_FILES)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public static boolean valid(String name) {
        return !name.isEmpty() && name.length() <= 128 && !name.contains("/") && !name.contains("\\")
                && !name.contains("..") && name.toLowerCase(Locale.ROOT).endsWith(".ogg");
    }

    public static byte[] read(String name) {
        if (!valid(name))
            return null;
        Path path = folder().resolve(name);
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > MAX_SIZE)
                return null;
            return Files.readAllBytes(path);
        } catch (IOException e) {
            return null;
        }
    }

    public static long hash(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue() ^ ((long) data.length << 32);
    }

    public static void send(ServerPlayer player, String name, long hash) {
        String key = player.getUUID() + "|" + name + "|" + hash;
        long now = System.currentTimeMillis();
        Long last = SENT.get(key);
        if (last != null && now - last < RESEND)
            return;
        SENT.put(key, now);
        byte[] data = read(name);
        if (data == null)
            return;
        int total = Math.max(1, (data.length + CHUNK - 1) / CHUNK);
        for (int i = 0; i < total; i++) {
            int from = i * CHUNK;
            int to = Math.min(data.length, from + CHUNK);
            PacketDistributor.sendToPlayer(player, new AlarmSoundChunkPayload(name, hash, i, total, Arrays.copyOfRange(data, from, to)));
        }
    }
}
