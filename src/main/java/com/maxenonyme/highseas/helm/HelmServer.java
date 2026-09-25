package com.maxenonyme.highseas.helm;

import com.maxenonyme.highseas.block.BoatEngineBlock;
import com.maxenonyme.highseas.block.entity.BoatEngineBlockEntity;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.phys.AABB;

public final class HelmServer {
    private HelmServer() {
    }

    private static final int INPUT_DECAY = 30;

    private record Latch(byte keys, long tick) {
    }

    private static final Map<UUID, Latch> INPUT = new ConcurrentHashMap<>();

    public static boolean tryMount(ServerPlayer player, BoatEngineBlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide)
            return false;
        if (player.isPassenger())
            return false;
        if (be.hasDriver()) {
            deny(player, "occupied");
            return false;
        }
        if (SableCompanion.INSTANCE.getContaining(level, be.getBlockPos()) == null) {
            deny(player, "not_a_ship");
            return false;
        }
        if (!be.hasFuel()) {
            deny(player, "no_fuel");
            return false;
        }

        Direction facing = be.getBlockState().getValue(BoatEngineBlock.FACING);
        Vec3 anchor = be.helmAnchor();
        if (!level.noBlockCollision(null, new AABB(anchor.x - 0.25, anchor.y + 0.3,
                anchor.z - 0.25, anchor.x + 0.25, anchor.y + 1.5, anchor.z + 0.25))) {
            deny(player, "blocked");
            return false;
        }
        HelmSeatEntity seat = new HelmSeatEntity(level);
        seat.setEngine(be.getBlockPos(), facing);
        seat.setPos(anchor.x, anchor.y, anchor.z);
        seat.setYRot(facing.toYRot());
        if (!level.addFreshEntity(seat))
            return false;
        if (!player.startRiding(seat, true)) {
            seat.discard();
            return false;
        }
        be.setDriver(player.getUUID());
        be.forceSync();
        INPUT.remove(player.getUUID());
        player.displayClientMessage(Component.translatable("message.create_high_seas.helm.engaged")
                .withStyle(ChatFormatting.GRAY), true);
        return true;
    }

    public static void acceptInput(ServerPlayer player, BlockPos engine, byte keys) {
        if (!(player.getVehicle() instanceof HelmSeatEntity seat))
            return;
        if (!seat.getEnginePos().equals(engine))
            return;
        INPUT.put(player.getUUID(), new Latch(keys, player.level().getGameTime()));
    }

    public static byte maskFor(UUID id, long now) {
        Latch latch = INPUT.get(id);
        if (latch == null || now - latch.tick() > INPUT_DECAY)
            return 0;
        return latch.keys();
    }

    public static void release(Player player) {
        INPUT.remove(player.getUUID());
        if (player.getVehicle() instanceof HelmSeatEntity seat) {
            player.stopRiding();
            seat.discard();
        }
    }

    public static void releaseAt(Level level, BlockPos pos) {
        if (level == null || level.isClientSide)
            return;
        if (level.getBlockEntity(pos) instanceof BoatEngineBlockEntity be)
            be.clearDriver();
        for (HelmSeatEntity seat : level.getEntitiesOfClass(HelmSeatEntity.class,
                new AABB(pos).inflate(4.0))) {
            if (!seat.getEnginePos().equals(pos))
                continue;
            seat.ejectPassengers();
            seat.discard();
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (INPUT.isEmpty())
            return;
        Iterator<Map.Entry<UUID, Latch>> it = INPUT.entrySet().iterator();
        while (it.hasNext()) {
            UUID id = it.next().getKey();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            if (player == null || !(player.getVehicle() instanceof HelmSeatEntity))
                it.remove();
        }
    }

    public static void clearAll() {
        INPUT.clear();
    }

    private static void deny(ServerPlayer player, String key) {
        player.displayClientMessage(Component.literal("✖ ")
                .append(Component.translatable("message.create_high_seas.helm." + key))
                .withStyle(ChatFormatting.RED), true);
    }
}
