package com.maxenonyme.createsubmarine.submarine.compartment;

import com.maxenonyme.createsubmarine.submarine.network.BreachSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BreachLedger {

    private static final String NAME = "create_submarine_breaches";
    private static final Map<UUID, Set<BlockPos>> BREACHES = new ConcurrentHashMap<>();
    private static Data data;

    public static void changed(UUID id, Set<BlockPos> plugs) {
        Set<BlockPos> next = Set.copyOf(plugs);
        if (next.equals(BREACHES.getOrDefault(id, Set.of())))
            return;
        if (next.isEmpty())
            BREACHES.remove(id);
        else
            BREACHES.put(id, next);
        if (data != null)
            data.setDirty();
        PacketDistributor.sendToAllPlayers(new BreachSyncPayload(id, List.copyOf(next)));
    }

    public static void onServerStarted(ServerStartedEvent event) {
        BREACHES.clear();
        data = event.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Data::new, Data::load), NAME);
        BREACHES.forEach(CompartmentTracker::restorePlugs);
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;
        BREACHES.forEach((id, plugs) ->
                PacketDistributor.sendToPlayer(player, new BreachSyncPayload(id, List.copyOf(plugs))));
    }

    private static class Data extends SavedData {
        static Data load(CompoundTag tag, HolderLookup.Provider provider) {
            for (String key : tag.getAllKeys()) {
                UUID id;
                try {
                    id = UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                Set<BlockPos> plugs = new HashSet<>();
                for (Tag entry : tag.getList(key, Tag.TAG_LONG))
                    plugs.add(BlockPos.of(((LongTag) entry).getAsLong()));
                if (!plugs.isEmpty())
                    BREACHES.put(id, Set.copyOf(plugs));
            }
            return new Data();
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            BREACHES.forEach((id, plugs) -> {
                ListTag list = new ListTag();
                for (BlockPos pos : plugs)
                    list.add(LongTag.valueOf(pos.asLong()));
                tag.put(id.toString(), list);
            });
            return tag;
        }
    }
}
