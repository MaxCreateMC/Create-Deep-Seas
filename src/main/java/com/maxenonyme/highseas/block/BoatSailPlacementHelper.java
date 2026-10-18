package com.maxenonyme.highseas.block;

import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.function.Predicate;

public class BoatSailPlacementHelper implements IPlacementHelper {
    private final Predicate<ItemStack> itemPredicate;
    private final Predicate<BlockState> statePredicate;

    public BoatSailPlacementHelper(Predicate<ItemStack> itemPredicate, Predicate<BlockState> statePredicate) {
        this.itemPredicate = itemPredicate;
        this.statePredicate = statePredicate;
    }

    @Override
    public Predicate<ItemStack> getItemPredicate() {
        return itemPredicate;
    }

    @Override
    public Predicate<BlockState> getStatePredicate() {
        return statePredicate;
    }

    @Override
    public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos, BlockHitResult ray) {
        if (!state.hasProperty(BlockStateProperties.AXIS))
            return PlacementOffset.fail();
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);

        List<Direction> validDir = IPlacementHelper.orderedByDistanceExceptAxis(pos, ray.getLocation(), axis);
        for (Direction dir : validDir) {
            if (!world.getBlockState(pos.relative(dir)).canBeReplaced())
                continue;
            return PlacementOffset.success(pos.relative(dir), s -> s.setValue(BlockStateProperties.AXIS, axis));
        }

        return PlacementOffset.fail();
    }
}
