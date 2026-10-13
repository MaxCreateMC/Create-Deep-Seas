package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig;
import net.minecraft.ResourceLocationException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

public record HullConfigEditPayload(Map<String, HullStrengthConfig.HullProperty> changed) implements CustomPacketPayload {
    public static final Type<HullConfigEditPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "hull_config_edit"));

    public static final StreamCodec<FriendlyByteBuf, HullConfigEditPayload> CODEC = StreamCodec.of(
        HullConfigEditPayload::write,
        HullConfigEditPayload::read
    );

    private static void write(FriendlyByteBuf buf, HullConfigEditPayload payload) {
        buf.writeVarInt(payload.changed.size());
        for (Map.Entry<String, HullStrengthConfig.HullProperty> e : payload.changed.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue().maxWaterDepth());
            buf.writeFloat(e.getValue().implosionChance());
        }
    }

    private static HullConfigEditPayload read(FriendlyByteBuf buf) {
        int size = Math.max(0, Math.min(buf.readVarInt(), 10000));
        Map<String, HullStrengthConfig.HullProperty> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(256);
            int depth = buf.readVarInt();
            float chance = buf.readFloat();
            map.put(key, new HullStrengthConfig.HullProperty(depth, chance));
        }
        return new HullConfigEditPayload(map);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final HullConfigEditPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            if (!player.hasPermissions(2) && !player.server.isSingleplayerOwner(player.getGameProfile()))
                return;

            payload.changed().forEach((key, prop) -> {
                if (key == null || key.isEmpty())
                    return;
                try { ResourceLocation.parse(key); }
                catch (ResourceLocationException ex) { return; }
                float chance = prop.implosionChance();
                if (!Float.isFinite(chance))
                    return;
                int depth = Math.max(0, Math.min(prop.maxWaterDepth(), 100000));
                HullStrengthConfig.update(key, depth, chance);
            });
            HullStrengthConfig.save();

            HullConfigSyncPayload sync = new HullConfigSyncPayload(HullStrengthConfig.getValues());
            for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                PacketDistributor.sendToPlayer(p, sync);
            }
        });
    }
}
