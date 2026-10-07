package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.alarm.AlarmSounds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AlarmSoundRequestPayload(String name, long hash) implements CustomPacketPayload {
    public static final Type<AlarmSoundRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "alarm_sound_request"));

    public static final StreamCodec<FriendlyByteBuf, AlarmSoundRequestPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.name(), 128);
                buf.writeLong(p.hash());
            },
            buf -> new AlarmSoundRequestPayload(buf.readUtf(128), buf.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final AlarmSoundRequestPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player)
                AlarmSounds.send(player, payload.name(), payload.hash());
        });
    }
}
