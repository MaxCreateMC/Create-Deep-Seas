package com.maxenonyme.highseas.item;

import com.maxenonyme.highseas.CreateHighSeas;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SeaglideImpactPayload() implements CustomPacketPayload {

    public static final float DAMAGE = 3.0f;

    public static final SeaglideImpactPayload INSTANCE = new SeaglideImpactPayload();

    public static final Type<SeaglideImpactPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateHighSeas.MOD_ID, "seaglide_impact"));

    public static final StreamCodec<ByteBuf, SeaglideImpactPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SeaglideImpactPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            if (player.isInWater() || player.isSpectator() || player.getAbilities().invulnerable)
                return;
            if (!player.getMainHandItem().is(CreateHighSeas.SEAGLIDE.get())
                    && !player.getOffhandItem().is(CreateHighSeas.SEAGLIDE.get()))
                return;
            player.hurt(player.damageSources().fall(), DAMAGE);
        });
    }
}
