package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.alarm.AlarmSounds;
import com.maxenonyme.createsubmarine.submarine.client.alarm.AlarmClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AlarmSoundChunkPayload(String name, long hash, int index, int total, byte[] data) implements CustomPacketPayload {
    public static final Type<AlarmSoundChunkPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "alarm_sound_chunk"));

    public static final StreamCodec<FriendlyByteBuf, AlarmSoundChunkPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.name(), 128);
                buf.writeLong(p.hash());
                buf.writeVarInt(p.index());
                buf.writeVarInt(p.total());
                buf.writeByteArray(p.data());
            },
            buf -> new AlarmSoundChunkPayload(buf.readUtf(128), buf.readLong(), buf.readVarInt(), buf.readVarInt(),
                    buf.readByteArray(AlarmSounds.CHUNK)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final AlarmSoundChunkPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> AlarmClient.receive(payload));
    }
}
