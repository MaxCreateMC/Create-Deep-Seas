package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.item.PressureGogglesItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PressureModePayload() implements CustomPacketPayload {
    public static final Type<PressureModePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "pressure_mode"));

    public static final StreamCodec<FriendlyByteBuf, PressureModePayload> CODEC = StreamCodec.unit(new PressureModePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final PressureModePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player != null && player.isShiftKeyDown() && player.getMainHandItem().isEmpty()
                    && PressureGogglesItem.isWearing(player))
                PressureGogglesItem.toggle(player, player.getItemBySlot(EquipmentSlot.HEAD));
        });
    }
}
