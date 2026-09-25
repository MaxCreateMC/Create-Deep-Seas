package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientLevelRopeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record CableStrandRemovePayload(UUID strandId) implements CustomPacketPayload {

    public static final Type<CableStrandRemovePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "cable_strand_remove"));

    public static final StreamCodec<FriendlyByteBuf, CableStrandRemovePayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CableStrandRemovePayload::strandId,
            CableStrandRemovePayload::new);

    @Override
    public Type<CableStrandRemovePayload> type() {
        return TYPE;
    }

    public static void handle(CableStrandRemovePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null)
                return;
            ClientLevelRopeManager manager = ClientLevelRopeManager.getOrCreate(mc.level);
            if (manager != null)
                manager.removeStrand(payload.strandId());
        });
    }
}
