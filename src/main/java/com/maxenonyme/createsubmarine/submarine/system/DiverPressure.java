package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3d;

public class DiverPressure {

    private static final int LETHAL_MARGIN = 60;

    private static final ResourceKey<DamageType> PRESSURE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "pressure"));
    private static final ResourceKey<DamageType> CRUSHED = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "crushed"));

    private static Item helmet;
    private static Item boots;

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 5 != 0)
            return;
        if (player.isCreative() || player.isSpectator() || !player.isInWater())
            return;

        SubmarinePressureSystem.LiquidColumn column = SubmarinePressureSystem.measureColumn(player.level(),
                new Vector3d(player.getX(), player.getEyeY(), player.getZ()));
        if (column.surfaceY() == Integer.MIN_VALUE)
            return;
        double depth = (column.surfaceY() + 1 - player.getEyeY()) * column.density();
        double over = depth - (wearsCopperSuit(player) ? 280 : 120);
        if (over < 0)
            return;

        if (over >= LETHAL_MARGIN) {
            player.hurt(source(player, CRUSHED), Float.MAX_VALUE);
            return;
        }
        int interval = (int) Mth.lerp(over / LETHAL_MARGIN, 40, 10) / 5 * 5;
        if (player.tickCount % interval != 0)
            return;
        player.hurt(source(player, PRESSURE), 1 + (float) over / 6);
    }

    private static boolean wearsCopperSuit(ServerPlayer player) {
        if (helmet == null) {
            helmet = BuiltInRegistries.ITEM
                    .get(ResourceLocation.fromNamespaceAndPath("create", "copper_diving_helmet"));
            boots = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "copper_diving_boots"));
        }
        return player.getItemBySlot(EquipmentSlot.HEAD).is(helmet)
                && player.getItemBySlot(EquipmentSlot.CHEST).is(CreateSubmarine.DIVING_CHESTPLATE.get())
                && player.getItemBySlot(EquipmentSlot.LEGS).is(CreateSubmarine.DIVING_LEGGINGS.get())
                && player.getItemBySlot(EquipmentSlot.FEET).is(boots);
    }

    private static DamageSource source(ServerPlayer player, ResourceKey<DamageType> type) {
        return new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(type));
    }
}
