package com.maxenonyme.highseas.sail;

import com.maxenonyme.highseas.CreateHighSeas;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ReefSyncPayload(UUID subId, List<Long> keys, List<Float> values) implements CustomPacketPayload {

    public static final Type<ReefSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateHighSeas.MOD_ID, "reef_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReefSyncPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ReefSyncPayload::subId,
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), ReefSyncPayload::keys,
            ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()), ReefSyncPayload::values,
            ReefSyncPayload::new);

    public static ReefSyncPayload of(UUID sub) {
        List<Long> keys = new ArrayList<>();
        List<Float> values = new ArrayList<>();
        for (Map.Entry<Long, Float> e : FurlState.reefs(sub).entrySet()) {
            keys.add(e.getKey());
            values.add(e.getValue());
        }
        return new ReefSyncPayload(sub, keys, values);
    }

    public static void broadcast(UUID sub) {
        PacketDistributor.sendToAllPlayers(of(sub));
    }

    @Override
    public Type<ReefSyncPayload> type() {
        return TYPE;
    }

    public static void handle(ReefSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> FurlState.applyReefClient(payload.subId(), payload.keys(), payload.values()));
    }
}
