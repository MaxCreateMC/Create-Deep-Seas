package com.maxenonyme.highseas.item;

import com.maxenonyme.highseas.CreateHighSeas;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SeaglideDrainPayload(byte ticks) implements CustomPacketPayload {

    public static final float EXHAUSTION_PER_TICK = 0.02f;
    public static final int MAX_TICKS = 40;

    public static final Type<SeaglideDrainPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateHighSeas.MOD_ID, "seaglide_drain"));

    public static final StreamCodec<FriendlyByteBuf, SeaglideDrainPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeByte(payload.ticks()),
            buf -> new SeaglideDrainPayload(buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SeaglideDrainPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            if (!player.getMainHandItem().is(CreateHighSeas.SEAGLIDE.get())
                    && !player.getOffhandItem().is(CreateHighSeas.SEAGLIDE.get()))
                return;
            int ticks = Math.min(Math.max(payload.ticks(), 0), MAX_TICKS);
            player.causeFoodExhaustion(EXHAUSTION_PER_TICK * ticks);
        });
    }
}
