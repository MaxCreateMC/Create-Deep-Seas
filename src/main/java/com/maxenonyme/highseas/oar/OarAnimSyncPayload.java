package com.maxenonyme.highseas.oar;

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

public record OarAnimSyncPayload(List<UUID> rowers) implements CustomPacketPayload {

    public static final Type<OarAnimSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateHighSeas.MOD_ID, "oar_anim_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OarAnimSyncPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), OarAnimSyncPayload::rowers,
            OarAnimSyncPayload::new);

    @Override
    public Type<OarAnimSyncPayload> type() {
        return TYPE;
    }

    public static void handle(OarAnimSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> OarRowAnimator.updateRowers(payload.rowers()));
    }
}
