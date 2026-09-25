package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import java.util.HashSet;
import java.util.Set;

public final class DiffuserZoneProtection {
    private DiffuserZoneProtection() {
    }

    private static boolean isProtected(LevelAccessor level, BlockPos pos) {
        return isMachine(level.getBlockState(pos.below()));
    }

    private static boolean isMachine(BlockState state) {
        return state.is(CreateSubmarine.ELECTROLYZER.get()) || state.is(CreateSubmarine.OXYGENE_DIFFUSER.get());
    }

    public static boolean canPlaceMachine(BlockPlaceContext context) {
        if (context.getLevel().getBlockState(context.getClickedPos().above()).canBeReplaced())
            return true;
        if (context.getPlayer() instanceof ServerPlayer player)
            notifyBlocked(player);
        return false;
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof BlockItem))
            return;
        Level level = event.getLevel();
        BlockPos placePos = level.getBlockState(event.getPos()).canBeReplaced()
                ? event.getPos()
                : event.getPos().relative(event.getFace() == null ? Direction.UP : event.getFace());
        if (!isProtected(level, placePos))
            return;
        event.setCanceled(true);
        if (!level.isClientSide && event.getEntity() instanceof ServerPlayer player) {
            notifyBlocked(player);
            resync(player);
        }
    }

    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!isProtected(event.getLevel(), event.getPos()))
            return;
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) {
            notifyBlocked(player);
            resync(player);
        }
    }

    public static void onPistonMove(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level))
            return;
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve())
            return;
        Direction moveDir = event.getPistonMoveType() == PistonEvent.PistonMoveType.EXTEND
                ? event.getDirection()
                : event.getDirection().getOpposite();
        Set<BlockPos> moving = new HashSet<>(resolver.getToPush());
        Set<BlockPos> landing = new HashSet<>();
        for (BlockPos pushed : moving)
            landing.add(pushed.relative(moveDir));
        for (BlockPos pushed : moving) {
            BlockPos target = pushed.relative(moveDir);
            if (isProtected(level, target) && !moving.contains(target.below())) {
                event.setCanceled(true);
                return;
            }
            if (!isMachine(level.getBlockState(pushed)))
                continue;
            BlockPos above = target.above();
            boolean blockedAbove = landing.contains(above)
                    || (!moving.contains(above) && !level.getBlockState(above).canBeReplaced());
            if (blockedAbove) {
                event.setCanceled(true);
                return;
            }
        }
    }

    private static void resync(ServerPlayer player) {
        player.inventoryMenu.sendAllDataToRemote();
        player.containerMenu.sendAllDataToRemote();
    }

    private static void notifyBlocked(ServerPlayer player) {
        player.displayClientMessage(
                Component.literal("✖ ")
                        .append(Component.translatable("message.create_submarine.machine_zone.obstructed"))
                        .withStyle(ChatFormatting.RED),
                true);
    }
}
