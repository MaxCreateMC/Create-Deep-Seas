package com.maxenonyme.highseas.gui;

import com.maxenonyme.highseas.CreateHighSeas;
import com.maxenonyme.highseas.block.entity.BoatEngineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record EngineTogglePayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<EngineTogglePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateHighSeas.MOD_ID, "engine_toggle"));

    public static final StreamCodec<FriendlyByteBuf, EngineTogglePayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBlockPos(payload.pos()),
            buf -> new EngineTogglePayload(buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EngineTogglePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null)
                return;
            if (!(player.containerMenu instanceof BoatEngineMenu menu) || !menu.pos.equals(payload.pos()))
                return;
            if (player.level().getBlockEntity(payload.pos()) instanceof BoatEngineBlockEntity be)
                be.toggleEnabled();
        });
    }
}
