package com.maxenonyme.highseas.item;

import com.maxenonyme.highseas.CreateHighSeas;
import com.maxenonyme.highseas.config.HighSeasConfig;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record SeaglideDrainPayload(byte ticks) implements CustomPacketPayload {

    public static final float EXHAUSTION_PER_TICK = 0.02f;
    public static final int MAX_TICKS = 40;

    public static final Type<SeaglideDrainPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateHighSeas.MOD_ID, "seaglide_drain"));

    public static final StreamCodec<FriendlyByteBuf, SeaglideDrainPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeByte(payload.ticks()),
            buf -> new SeaglideDrainPayload(buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SeaglideDrainPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            if (!player.getMainHandItem().is(CreateHighSeas.SEAGLIDE.get())
                    && !player.getOffhandItem().is(CreateHighSeas.SEAGLIDE.get()))
                return;
            int ticks = Math.min(Math.max(payload.ticks(), 0), MAX_TICKS);
            if (!HighSeasConfig.seaglideBacktank) {
                player.causeFoodExhaustion(EXHAUSTION_PER_TICK * ticks);
                return;
            }
            List<ItemStack> tanks = BacktankUtil.getAllWithAir(player);
            if (!tanks.isEmpty())
                BacktankUtil.consumeAir(player, tanks.get(0), Math.max(1, Math.round(ticks * HighSeasConfig.seaglideAirPerSecond / 20f)));
        });
    }

    public static boolean hasFuel(Player player) {
        if (HighSeasConfig.seaglideBacktank)
            return !BacktankUtil.getAllWithAir(player).isEmpty();
        return player.getFoodData().getFoodLevel() > 0;
    }
}
