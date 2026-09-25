package com.maxenonyme.createsubmarine.submarine.mixin;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.SteelCableItem;
import com.maxenonyme.createsubmarine.submarine.util.WinchAnchorSignal;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlock;
import dev.simulated_team.simulated.index.SimDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RopeWinchBlock.class, remap = false)
public class RopeWinchBlockMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, remap = true)
    private void createsubmarine$steelCableToWinch(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (!stack.is(CreateSubmarine.STEEL_CABLE.get())) return;
        if (player.isShiftKeyDown()) return;
        if (!stack.has(SimDataComponents.ROPE_FIRST_CONNECTION)) return;

        BlockPos firstPos = stack.get(SimDataComponents.ROPE_FIRST_CONNECTION);
        if (!level.isClientSide) {
            boolean success = SteelCableItem.attachSteelCable(level, firstPos, pos);
            if (success && !player.isCreative()) {
                stack.shrink(1);
            }
        }
        stack.remove(SimDataComponents.ROPE_FIRST_CONNECTION);
        cir.setReturnValue(ItemInteractionResult.sidedSuccess(level.isClientSide));
    }

    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof WinchAnchorSignal winch
                && winch.createsubmarine$isAnchorGrounded() ? 15 : 0;
    }
}
