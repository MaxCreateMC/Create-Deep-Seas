package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.function.BiConsumer;

public record DiagnosticPayload(BlockPos console, boolean onVessel, int depth, int maxDepth, String weakest,
        BlockPos weakestAt, int cracks, boolean hermetic, int breachCount, List<BlockPos> breaches)
        implements CustomPacketPayload {

    public static final int MAX_LISTED = 8;

    public static final Type<DiagnosticPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "diagnostic"));

    public static final StreamCodec<FriendlyByteBuf, DiagnosticPayload> CODEC = CustomPacketPayload.codec(
            DiagnosticPayload::write, DiagnosticPayload::new);

    public static DiagnosticPayload noVessel(BlockPos console) {
        return new DiagnosticPayload(console, false, 0, -1, "", null, 0, false, 0, List.of());
    }

    public DiagnosticPayload(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(256),
                buf.readBoolean() ? buf.readBlockPos() : null, buf.readVarInt(), buf.readBoolean(),
                buf.readVarInt(), readBreaches(buf));
    }

    private static List<BlockPos> readBreaches(FriendlyByteBuf buf) {
        List<BlockPos> list = buf.readList(b -> b.readBlockPos());
        return list.size() > MAX_LISTED ? List.copyOf(list.subList(0, MAX_LISTED)) : list;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(console);
        buf.writeBoolean(onVessel);
        buf.writeVarInt(depth);
        buf.writeVarInt(maxDepth);
        buf.writeUtf(weakest, 256);
        buf.writeBoolean(weakestAt != null);
        if (weakestAt != null)
            buf.writeBlockPos(weakestAt);
        buf.writeVarInt(cracks);
        buf.writeBoolean(hermetic);
        buf.writeVarInt(breachCount);
        buf.writeCollection(breaches.size() > MAX_LISTED ? breaches.subList(0, MAX_LISTED) : breaches,
                (b, pos) -> b.writeBlockPos(pos));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static BiConsumer<DiagnosticPayload, IPayloadContext> handler = (payload, context) -> {
    };

    public static void handle(DiagnosticPayload payload, IPayloadContext context) {
        handler.accept(payload, context);
    }
}
