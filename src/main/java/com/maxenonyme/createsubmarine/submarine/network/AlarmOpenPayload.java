package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.alarm.AlarmSettings;
import com.maxenonyme.createsubmarine.submarine.client.alarm.AlarmClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record AlarmOpenPayload(BlockPos pos, AlarmSettings settings, List<String> files) implements CustomPacketPayload {
    public static final Type<AlarmOpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "alarm_open"));

    public static final StreamCodec<FriendlyByteBuf, AlarmOpenPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                AlarmSettings.STREAM_CODEC.encode(buf, p.settings());
                buf.writeCollection(p.files(), (b, s) -> b.writeUtf(s, 128));
            },
            buf -> new AlarmOpenPayload(buf.readBlockPos(), AlarmSettings.STREAM_CODEC.decode(buf),
                    buf.readList(b -> b.readUtf(128))));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final AlarmOpenPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> AlarmClient.open(payload.pos(), payload.settings(), payload.files()));
    }
}
