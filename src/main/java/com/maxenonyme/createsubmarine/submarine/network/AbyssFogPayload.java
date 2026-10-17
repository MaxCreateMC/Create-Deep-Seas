package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.BiConsumer;

public record AbyssFogPayload(boolean fogDisabled) implements CustomPacketPayload {
    public static final Type<AbyssFogPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "abyss_fog"));

    public static final StreamCodec<FriendlyByteBuf, AbyssFogPayload> CODEC = CustomPacketPayload.codec(
            AbyssFogPayload::write, AbyssFogPayload::new);

    public AbyssFogPayload(FriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(fogDisabled);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static BiConsumer<AbyssFogPayload, IPayloadContext> handler = (payload, context) -> {
    };

    public static void handle(AbyssFogPayload payload, IPayloadContext context) {
        handler.accept(payload, context);
    }
}
