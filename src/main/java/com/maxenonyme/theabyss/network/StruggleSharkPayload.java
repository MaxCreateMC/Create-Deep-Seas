package com.maxenonyme.theabyss.network;

import com.maxenonyme.theabyss.CreateAbyss;
import com.maxenonyme.theabyss.entities.CookiecutterSharkEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record StruggleSharkPayload(int sharkId) implements CustomPacketPayload {
    public static final Type<StruggleSharkPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateAbyss.MOD_ID, "struggle_shark"));

    public static final StreamCodec<FriendlyByteBuf, StruggleSharkPayload> CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeVarInt(payload.sharkId()),
        buf -> new StruggleSharkPayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final StruggleSharkPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null)
                return;
            Entity entity = player.level().getEntity(payload.sharkId());
            if (entity instanceof CookiecutterSharkEntity shark && shark.isLatchedTo(player)) {
                shark.addStruggle();
            }
        });
    }
}
