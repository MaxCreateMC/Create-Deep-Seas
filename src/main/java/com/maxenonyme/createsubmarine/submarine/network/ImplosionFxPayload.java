package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.BiConsumer;

public record ImplosionFxPayload(int kind, float strength, int ticks) implements CustomPacketPayload {
    public static final int STRESS = 0;
    public static final int BLAST = 1;
    public static final int FATAL = 2;
    public static final int STRAIN = 3;
    public static final int PANIC = 4;
    public static final int SILENCE = 5;
    public static final int END = 6;
    public static final int SQUEEZE = 7;
    public static final int THOUGHT = 8;
    public static final int DEPTH = 9;

    public static final Type<ImplosionFxPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "implosion_fx"));

    public static final StreamCodec<FriendlyByteBuf, ImplosionFxPayload> CODEC = CustomPacketPayload.codec(
            ImplosionFxPayload::write, ImplosionFxPayload::new);

    public ImplosionFxPayload(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readFloat(), buf.readVarInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(kind);
        buf.writeFloat(strength);
        buf.writeVarInt(ticks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static BiConsumer<ImplosionFxPayload, IPayloadContext> handler = (payload, context) -> {
    };

    public static void handle(ImplosionFxPayload payload, IPayloadContext context) {
        handler.accept(payload, context);
    }
}
