package com.maxenonyme.highseas.sail;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.SteelCableItem;
import com.maxenonyme.highseas.block.BoatSailBlock;
import com.maxenonyme.highseas.block.entity.HalyardBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
import dev.simulated_team.simulated.index.SimDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class SailRigging {
    private SailRigging() {
    }

    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity();
        if (!(stack.getItem() instanceof RopeItem))
            return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        boolean clickedSail = level.getBlockState(pos).getBlock() instanceof BoatSailBlock;
        BlockPos first = stack.get(SimDataComponents.ROPE_FIRST_CONNECTION);
        boolean firstSail = first != null && level.getBlockState(first).getBlock() instanceof BoatSailBlock;
        if (!clickedSail && !firstSail)
            return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(level instanceof ServerLevel server))
            return;

        if (clickedSail) {
            rig(server, pos);
            if (RopeItem.isValidRopeAttachment(server, pos))
                Halyard.prepare(server, pos);
        }
        if (first == null) {
            if (RopeItem.isValidRopeAttachment(server, pos)) {
                stack.set(SimDataComponents.ROPE_FIRST_CONNECTION, pos);
                server.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.4f, 1.2f);
            }
            return;
        }
        stack.remove(SimDataComponents.ROPE_FIRST_CONNECTION);
        if (first.equals(pos))
            return;
        if (tie(server, first, pos, stack.is(CreateSubmarine.STEEL_CABLE.get())) && !player.isCreative())
            stack.shrink(1);
    }

    private static void rig(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BoatSailBlock && !state.getValue(BoatSailBlock.RIGGED))
            level.setBlock(pos, state.setValue(BoatSailBlock.RIGGED, true), 3);
    }

    private static boolean tie(ServerLevel level, BlockPos a, BlockPos b, boolean steel) {
        if (Halyard.isHalyard(level, a))
            a = Halyard.relocate(level, a, b);
        if (Halyard.isHalyard(level, b))
            b = Halyard.relocate(level, b, a);
        RopeStrandHolderBehavior holderA = RopeItem.getRopeHolder(level, a);
        RopeStrandHolderBehavior holderB = RopeItem.getRopeHolder(level, b);
        if (holderA == null || holderB == null || holderA.isAttached() || holderB.isAttached())
            return false;
        orient(level, holderA, holderB);
        orient(level, holderB, holderA);
        if (holderB.blockEntity instanceof RopeWinchBlockEntity && !(holderA.blockEntity instanceof RopeWinchBlockEntity)) {
            RopeStrandHolderBehavior swap = holderA;
            holderA = holderB;
            holderB = swap;
        }
        boolean made = steel ? SteelCableItem.createSteelRope(holderA, holderB) : holderA.createRope(holderB, false);
        if (made) {
            level.playSound(null, a, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f);
            level.playSound(null, b, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f);
        }
        return made;
    }

    private static void orient(ServerLevel level, RopeStrandHolderBehavior self, RopeStrandHolderBehavior other) {
        if (!(self.blockEntity instanceof HalyardBlockEntity bridle))
            return;
        BlockPos p = bridle.getBlockPos();
        BlockState state = level.getBlockState(p);
        if (!state.hasProperty(BoatSailBlock.AXIS))
            return;
        Direction plus = Direction.get(Direction.AxisDirection.POSITIVE, state.getValue(BoatSailBlock.AXIS));
        Vec3 center = Vec3.atCenterOf(p);
        Vec3 from = Sable.HELPER.projectOutOfSubLevel(level, center);
        Vec3 tip = Sable.HELPER.projectOutOfSubLevel(level, center.add(plus.getStepX(), plus.getStepY(), plus.getStepZ()));
        Vec3 to = Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(other.blockEntity.getBlockPos()));
        bridle.setSide(tip.subtract(from).dot(to.subtract(from)) >= 0.0 ? 1 : -1);
    }
}
