package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.alarm.AlarmSettings;
import com.maxenonyme.createsubmarine.submarine.alarm.AlarmSounds;
import com.maxenonyme.createsubmarine.submarine.block.entity.IndustrialAlarmBlockEntity;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AlarmConfigurePayload(BlockPos pos, AlarmSettings settings) implements CustomPacketPayload {
    public static final Type<AlarmConfigurePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "alarm_configure"));

    private static final double REACH = 8.0;

    public static final StreamCodec<FriendlyByteBuf, AlarmConfigurePayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                AlarmSettings.STREAM_CODEC.encode(buf, p.settings());
            },
            buf -> new AlarmConfigurePayload(buf.readBlockPos(), AlarmSettings.STREAM_CODEC.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final AlarmConfigurePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            if (!(player.level().getBlockEntity(payload.pos()) instanceof IndustrialAlarmBlockEntity alarm))
                return;
            Vec3 at = Sable.HELPER.projectOutOfSubLevel(player.level(), Vec3.atCenterOf(payload.pos()));
            if (player.distanceToSqr(at) > REACH * REACH)
                return;
            AlarmSettings settings = payload.settings().clamped();
            if (settings.sound().isEmpty()) {
                settings = settings.withSound("", 0L);
            } else {
                byte[] data = AlarmSounds.read(settings.sound());
                settings = data == null ? settings.withSound("", 0L) : settings.withSound(settings.sound(), AlarmSounds.hash(data));
            }
            alarm.configure(settings);
        });
    }
}
