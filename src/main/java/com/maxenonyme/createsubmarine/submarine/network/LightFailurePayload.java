package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.BiConsumer;

public record LightFailurePayload(BlockPos pos, long start, int flicker, int tremble) implements CustomPacketPayload {
    public static final Type<LightFailurePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "light_failure"));

    public static final StreamCodec<FriendlyByteBuf, LightFailurePayload> CODEC = CustomPacketPayload.codec(
            LightFailurePayload::write, LightFailurePayload::new);

    public LightFailurePayload(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarLong(start);
        buf.writeVarInt(flicker);
        buf.writeVarInt(tremble);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static BiConsumer<LightFailurePayload, IPayloadContext> handler = (payload, context) -> {
    };

    public static void handle(LightFailurePayload payload, IPayloadContext context) {
        handler.accept(payload, context);
    }
}
