package com.maxenonyme.highseas.helm;

import com.maxenonyme.highseas.CreateHighSeas;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HelmInputPayload(BlockPos engine, byte keys) implements CustomPacketPayload {

    public static final Type<HelmInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateHighSeas.MOD_ID, "helm_input"));

    public static final StreamCodec<ByteBuf, HelmInputPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HelmInputPayload::engine,
            ByteBufCodecs.BYTE, HelmInputPayload::keys,
            HelmInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HelmInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player)
                HelmServer.acceptInput(player, payload.engine(), payload.keys());
        });
    }
}
