package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.entity.CommandSubBlockEntity;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CommandSubPayload(BlockPos pos, int action, int value) implements CustomPacketPayload {
    public static final int SPEED = 0;
    public static final int DEPTH = 1;

    public static final Type<CommandSubPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "command_sub"));

    public static final StreamCodec<FriendlyByteBuf, CommandSubPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBlockPos(payload.pos());
            buf.writeVarInt(payload.action());
            buf.writeVarInt(payload.value());
        },
        buf -> new CommandSubPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final CommandSubPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) return;
            var level = player.level();
            BlockPos pos = payload.pos();
            if (!level.isLoaded(pos)) return;
            if (Sable.HELPER.distanceSquaredWithSubLevels(level, player.getEyePosition(), Vec3.atCenterOf(pos)) > 64) return;
            if (level.getBlockEntity(pos) instanceof CommandSubBlockEntity be) {
                be.apply(payload.action(), payload.value());
            }
        });
    }
}
