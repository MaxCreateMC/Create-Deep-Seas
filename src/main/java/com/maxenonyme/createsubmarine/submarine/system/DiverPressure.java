package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.network.CameraShakePayload;
import com.maxenonyme.createsubmarine.submarine.network.ImplosionFxPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
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
        if (player.isSpectator())
            return;

        double depth = player.isInWater() ? depthOf(player) : 0;
        boolean suited = wearsCopperSuit(player);
        int limit = suited ? 280 : 120;
        think(player, depth >= limit * 0.9);
        if (depth <= 0)
            return;

        if (suited)
            PacketDistributor.sendToPlayer(player,
                    new ImplosionFxPayload(ImplosionFxPayload.DEPTH, (float) depth, limit));
        if (player.isCreative())
            return;
        double over = depth - limit;
        if (over < 0)
            return;

        PacketDistributor.sendToPlayer(player,
                new ImplosionFxPayload(ImplosionFxPayload.SQUEEZE, 1f, 15));
        if (over >= 10)
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0, true, false, true));
        if (over >= LETHAL_MARGIN) {
            player.hurt(source(player, CRUSHED), Float.MAX_VALUE);
            return;
        }
        int interval = (int) Mth.lerp(over / LETHAL_MARGIN, 20, 10) / 5 * 5;
        if (player.tickCount % interval != 0)
            return;
        player.hurt(source(player, PRESSURE), 0.5f + (float) over / 12);
        PacketDistributor.sendToPlayer(player, new CameraShakePayload(1.5f + (float) over / 20, 8));
    }

    private static double depthOf(ServerPlayer player) {
        SubmarinePressureSystem.LiquidColumn column = SubmarinePressureSystem.measureColumn(player.level(),
                new Vector3d(player.getX(), player.getEyeY(), player.getZ()));
        if (column.surfaceY() == Integer.MIN_VALUE)
            return 0;
        return Math.max(0, (column.surfaceY() + 1 - player.getEyeY()) * column.density());
    }

    private static void think(ServerPlayer player, boolean deep) {
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean("PressureDeep") == deep)
            return;
        data.putBoolean("PressureDeep", deep);
        long now = player.level().getGameTime();
        if (!deep || data.contains("PressureThought") && now - data.getLong("PressureThought") < 6600)
            return;

        data.putLong("PressureThought", now);
        PacketDistributor.sendToPlayer(player,
                new ImplosionFxPayload(ImplosionFxPayload.THOUGHT, 0f, player.getRandom().nextInt(6)));
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
