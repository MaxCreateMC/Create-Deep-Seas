package com.maxenonyme.highseas.sail;

import com.maxenonyme.highseas.CreateHighSeas;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;

public record FurlSyncPayload(UUID subId, List<Long> keys) implements CustomPacketPayload {

    public static final Type<FurlSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateHighSeas.MOD_ID, "furl_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FurlSyncPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, FurlSyncPayload::subId,
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), FurlSyncPayload::keys,
            FurlSyncPayload::new);

    @Override
    public Type<FurlSyncPayload> type() {
        return TYPE;
    }

    public static void handle(FurlSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> FurlState.applyClient(payload.subId(), payload.keys()));
    }
}
