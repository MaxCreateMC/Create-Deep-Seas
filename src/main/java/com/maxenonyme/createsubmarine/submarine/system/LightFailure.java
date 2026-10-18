package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.submarine.network.LightFailurePayload;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LightFailure {
    private static final int FLICKER_MIN = 80;
    private static final int FLICKER_SPREAD = 80;
    private static final int TREMBLE = 40;
    private static final int RESCAN = 200;
    private static final Random RAND = new Random();

    public static class Failure {
        public final long start;
        public final int flicker;
        public final int tremble;
        boolean dark;
        boolean settled;

        Failure(long start, int flicker, int tremble) {
            this.start = start;
            this.flicker = flicker;
            this.tremble = tremble;
        }

        public long deadAt() {
            return start + flicker + tremble;
        }

        public boolean shownDark(long now) {
            return now >= deadAt() || (now >= start && dark);
        }

        public boolean trembling(long now) {
            return now >= start + flicker && now < deadAt();
        }
    }

    private record Lights(long scanned, List<BlockPos> positions) {
    }

    private static final Map<ResourceKey<Level>, Map<Long, Failure>> SERVER = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Map<Long, Failure>> CLIENT = new ConcurrentHashMap<>();
    private static final Map<UUID, Lights> LIGHTS = new ConcurrentHashMap<>();
    private static volatile boolean any;

    public static boolean any() {
        return any;
    }

    private static Map<Long, Failure> of(Level level) {
        return (level.isClientSide ? CLIENT : SERVER).computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>());
    }

    public static Map<Long, Failure> clientFailures(Level level) {
        Map<Long, Failure> map = CLIENT.get(level.dimension());
        return map != null ? map : Map.of();
    }

    public static boolean isDark(Level level, long pos) {
        Map<Long, Failure> map = (level.isClientSide ? CLIENT : SERVER).get(level.dimension());
        if (map == null || map.isEmpty())
            return false;
        Failure f = map.get(pos);
        if (f == null)
            return false;
        long now = level.getGameTime();
        if (now >= f.deadAt())
            return true;
        return level.isClientSide && now >= f.start && f.dark;
    }

    public static void onHullDamage(ServerLevel ocean, UUID id, Level plot, float severity) {
        int wanted = 0;
        for (float roll = severity; roll > 0f; roll -= 1f)
            if (RAND.nextFloat() < Math.min(1f, roll) * 0.6f)
                wanted++;
        if (wanted == 0)
            return;
        List<BlockPos> lights = lights(id, plot, ocean.getGameTime());
        if (lights.isEmpty())
            return;
        Map<Long, Failure> failures = of(plot);
        for (int tries = 0; tries < wanted * 4 && wanted > 0; tries++) {
            BlockPos pos = lights.get(RAND.nextInt(lights.size()));
            if (failures.containsKey(pos.asLong()) || plot.getBlockState(pos).getLightEmission() <= 0)
                continue;
            start(plot, pos, ocean.getGameTime());
            wanted--;
        }
    }

    private static List<BlockPos> lights(UUID id, Level plot, long now) {
        Lights cached = LIGHTS.get(id);
        if (cached != null && now - cached.scanned() < RESCAN)
            return cached.positions();
        List<BlockPos> found = new ArrayList<>();
        SubLevelRegistry.PlotBounds bounds = SubLevelRegistry.getBounds(id);
        if (bounds != null) {
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = bounds.minX(); x <= bounds.maxX(); x++)
                for (int y = bounds.minY(); y <= bounds.maxY(); y++)
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++)
                        if (plot.getBlockState(cursor.set(x, y, z)).getLightEmission() > 0)
                            found.add(cursor.immutable());
        }
        LIGHTS.put(id, new Lights(now, found));
        return found;
    }

    private static void start(Level plot, BlockPos pos, long now) {
        int flicker = FLICKER_MIN + RAND.nextInt(FLICKER_SPREAD);
        of(plot).put(pos.asLong(), new Failure(now, flicker, TREMBLE));
        any = true;
        if (plot instanceof ServerLevel server)
            PacketDistributor.sendToPlayersInDimension(server, new LightFailurePayload(pos, now, flicker, TREMBLE));
    }

    public static void accept(Level clientLevel, LightFailurePayload payload) {
        of(clientLevel).put(payload.pos().asLong(), new Failure(payload.start(), payload.flicker(), payload.tremble()));
        any = true;
    }

    public static void forget(Level level, BlockPos pos) {
        if (!any)
            return;
        Map<Long, Failure> map = (level.isClientSide ? CLIENT : SERVER).get(level.dimension());
        if (map != null && map.remove(pos.asLong()) != null)
            level.getLightEngine().checkBlock(pos);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!any)
            return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Map<Long, Failure> map = SERVER.get(level.dimension());
            if (map == null)
                continue;
            long now = level.getGameTime();
            for (Map.Entry<Long, Failure> entry : map.entrySet()) {
                Failure f = entry.getValue();
                if (!f.settled && now >= f.deadAt()) {
                    f.settled = true;
                    level.getLightEngine().checkBlock(BlockPos.of(entry.getKey()));
                }
            }
        }
    }

    public static void tickClient(Level level) {
        Map<Long, Failure> map = CLIENT.get(level.dimension());
        if (map == null || map.isEmpty())
            return;
        long now = level.getGameTime();
        for (Map.Entry<Long, Failure> entry : map.entrySet()) {
            Failure f = entry.getValue();
            if (f.settled || now < f.start)
                continue;
            boolean dead = now >= f.deadAt();
            boolean dark = dead || flickerDark(entry.getKey(), now, f);
            if (dead)
                f.settled = true;
            if (dark != f.dark || dead) {
                f.dark = dark;
                level.getLightEngine().checkBlock(BlockPos.of(entry.getKey()));
            }
        }
    }

    private static boolean flickerDark(long key, long now, Failure f) {
        if (f.trembling(now))
            return hash(key, now) < 0.65f;
        float progress = (now - f.start) / (float) f.flicker;
        long slot = progress < 0.4f ? now / 3 : now;
        return hash(key, slot) < 0.12f + 0.5f * Mth.clamp(progress, 0f, 1f);
    }

    private static float hash(long key, long tick) {
        long h = key * 0x9E3779B97F4A7C15L + tick * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        sync(event.getEntity());
    }

    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sync(event.getEntity());
    }

    private static void sync(Player player) {
        if (!(player instanceof ServerPlayer server))
            return;
        Map<Long, Failure> map = SERVER.get(server.level().dimension());
        if (map == null)
            return;
        for (Map.Entry<Long, Failure> entry : map.entrySet()) {
            Failure f = entry.getValue();
            PacketDistributor.sendToPlayer(server, new LightFailurePayload(BlockPos.of(entry.getKey()), f.start, f.flicker, f.tremble));
        }
    }

    public static void forgetSub(UUID id) {
        LIGHTS.remove(id);
    }

    public static void clear(boolean client) {
        (client ? CLIENT : SERVER).clear();
        if (!client)
            LIGHTS.clear();
        any = !SERVER.isEmpty() || !CLIENT.isEmpty();
    }
}
