package com.maxenonyme.highseas.oar;

import com.maxenonyme.highseas.CreateHighSeas;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OarRowPayload() implements CustomPacketPayload {
    public static final OarRowPayload INSTANCE = new OarRowPayload();
    public static final Type<OarRowPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateHighSeas.MOD_ID, "oar_row"));
    public static final StreamCodec<FriendlyByteBuf, OarRowPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final OarRowPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                OarPropulsionSystem.ping(sp);
            }
        });
    }
}
