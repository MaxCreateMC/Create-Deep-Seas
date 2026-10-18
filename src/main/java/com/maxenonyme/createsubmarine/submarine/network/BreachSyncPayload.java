package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;

public record BreachSyncPayload(UUID subId, List<BlockPos> plugs) implements CustomPacketPayload {
    private static final int MAX_PLUGS = 4096;

    public static final Type<BreachSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "breach_sync"));

    public static final StreamCodec<FriendlyByteBuf, BreachSyncPayload> CODEC = CustomPacketPayload.codec(
            BreachSyncPayload::write, BreachSyncPayload::new);

    public BreachSyncPayload(FriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readList(b -> b.readBlockPos()));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(subId);
        buf.writeCollection(plugs.size() > MAX_PLUGS ? plugs.subList(0, MAX_PLUGS) : plugs,
                (b, pos) -> b.writeBlockPos(pos));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BreachSyncPayload payload, IPayloadContext context) {
        if (payload.plugs().size() > MAX_PLUGS)
            return;
        context.enqueueWork(() -> CompartmentTracker.restorePlugs(payload.subId(), payload.plugs()));
    }
}
