package com.maxenonyme.highseas.helm;

import com.maxenonyme.highseas.CreateHighSeas;
import com.maxenonyme.highseas.block.entity.BoatEngineBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ThrottleCapPayload(BlockPos engine, byte cap) implements CustomPacketPayload {

    public static final Type<ThrottleCapPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateHighSeas.MOD_ID, "throttle_cap"));

    public static final StreamCodec<ByteBuf, ThrottleCapPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ThrottleCapPayload::engine,
            ByteBufCodecs.BYTE, ThrottleCapPayload::cap,
            ThrottleCapPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ThrottleCapPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            if (!(player.getVehicle() instanceof HelmSeatEntity seat))
                return;
            if (!seat.getEnginePos().equals(payload.engine()))
                return;
            if (player.level().getBlockEntity(payload.engine()) instanceof BoatEngineBlockEntity be)
                be.setThrottleCap(payload.cap());
        });
    }
}
