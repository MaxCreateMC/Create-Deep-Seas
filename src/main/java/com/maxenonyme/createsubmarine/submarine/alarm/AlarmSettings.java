package com.maxenonyme.createsubmarine.submarine.alarm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

public record AlarmSettings(String sound, long hash, int onTicks, int offTicks, int volume, boolean followDanger) {

    public static final AlarmSettings DEFAULT = new AlarmSettings("", 0L, 20, 20, 100, true);

    public static final StreamCodec<FriendlyByteBuf, AlarmSettings> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                buf.writeUtf(s.sound(), 128);
                buf.writeLong(s.hash());
                buf.writeVarInt(s.onTicks());
                buf.writeVarInt(s.offTicks());
                buf.writeVarInt(s.volume());
                buf.writeBoolean(s.followDanger());
            },
            buf -> new AlarmSettings(buf.readUtf(128), buf.readLong(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readBoolean()));

    public AlarmSettings clamped() {
        return new AlarmSettings(sound, hash, Mth.clamp(onTicks, 1, 600), Mth.clamp(offTicks, 0, 600),
                Mth.clamp(volume, 10, 300), followDanger);
    }

    public AlarmSettings withSound(String name, long hash) {
        return new AlarmSettings(name, hash, onTicks, offTicks, volume, followDanger);
    }

    public void write(CompoundTag tag) {
        tag.putString("AlarmSound", sound);
        tag.putLong("AlarmHash", hash);
        tag.putInt("AlarmOn", onTicks);
        tag.putInt("AlarmOff", offTicks);
        tag.putInt("AlarmVolume", volume);
        tag.putBoolean("AlarmFollow", followDanger);
    }

    public static AlarmSettings read(CompoundTag tag) {
        if (!tag.contains("AlarmOn"))
            return DEFAULT;
        return new AlarmSettings(tag.getString("AlarmSound"), tag.getLong("AlarmHash"), tag.getInt("AlarmOn"),
                tag.getInt("AlarmOff"), tag.getInt("AlarmVolume"), tag.getBoolean("AlarmFollow")).clamped();
    }
}
