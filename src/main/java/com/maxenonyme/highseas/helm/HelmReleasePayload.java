package com.maxenonyme.highseas.helm;

import com.maxenonyme.highseas.CreateHighSeas;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HelmReleasePayload() implements CustomPacketPayload {

    public static final HelmReleasePayload INSTANCE = new HelmReleasePayload();

    public static final Type<HelmReleasePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateHighSeas.MOD_ID, "helm_release"));

    public static final StreamCodec<ByteBuf, HelmReleasePayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HelmReleasePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player)
                HelmServer.release(player);
        });
    }
}
