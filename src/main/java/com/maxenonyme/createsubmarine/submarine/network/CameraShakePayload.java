package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.AbyssDimension.client.CameraShake;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CameraShakePayload(float intensity, int ticks) implements CustomPacketPayload {
    public static final Type<CameraShakePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "camera_shake"));

    public static final StreamCodec<FriendlyByteBuf, CameraShakePayload> CODEC = CustomPacketPayload.codec(
            CameraShakePayload::write, CameraShakePayload::new);

    public CameraShakePayload(FriendlyByteBuf buf) {
        this(buf.readFloat(), buf.readInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeFloat(intensity);
        buf.writeInt(ticks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CameraShakePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientHandler.handle(payload));
    }

    private static class ClientHandler {
        private static void handle(CameraShakePayload payload) {
            CameraShake.shake(payload.intensity(), payload.ticks());
        }
    }
}
