package com.maxenonyme.highseas.helm;

import com.maxenonyme.highseas.CreateHighSeas;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record EngineStatePayload(BlockPos pos, byte throttle, byte steer, byte cap) implements CustomPacketPayload {

    public static final Type<EngineStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateHighSeas.MOD_ID, "engine_state"));

    public static final StreamCodec<ByteBuf, EngineStatePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, EngineStatePayload::pos,
            ByteBufCodecs.BYTE, EngineStatePayload::throttle,
            ByteBufCodecs.BYTE, EngineStatePayload::steer,
            ByteBufCodecs.BYTE, EngineStatePayload::cap,
            EngineStatePayload::new);

    public static EngineStatePayload of(BlockPos pos, float throttle, float steer, int cap) {
        return new EngineStatePayload(pos, (byte) Math.round(throttle * 100.0f), (byte) Math.round(steer * 100.0f),
                (byte) cap);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EngineStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> HelmClient.put(payload.pos(), payload.throttle() / 100.0f,
                payload.steer() / 100.0f, payload.cap(), context.player().level().getGameTime()));
    }
}
