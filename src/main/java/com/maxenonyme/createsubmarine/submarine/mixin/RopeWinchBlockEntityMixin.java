package com.maxenonyme.createsubmarine.submarine.mixin;

import com.maxenonyme.createsubmarine.submarine.util.WinchAnchorSignal;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(value = RopeWinchBlockEntity.class, remap = false)
public abstract class RopeWinchBlockEntityMixin implements WinchAnchorSignal {

    @Unique
    private boolean createsubmarine$anchorGrounded = false;

    @Override
    public boolean createsubmarine$isAnchorGrounded() {
        return this.createsubmarine$anchorGrounded;
    }

    @Override
    public void createsubmarine$setAnchorGrounded(boolean val) {
        if (this.createsubmarine$anchorGrounded == val) return;
        this.createsubmarine$anchorGrounded = val;
        SmartBlockEntity be = (SmartBlockEntity) (Object) this;
        if (be.getLevel() != null && !be.getLevel().isClientSide) {
            be.getLevel().updateNeighborsAt(be.getBlockPos(), be.getBlockState().getBlock());
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void createsubmarine$clearStaleAnchorSignal(CallbackInfo ci) {
        if (!this.createsubmarine$anchorGrounded) return;
        SmartBlockEntity be = (SmartBlockEntity) (Object) this;
        if (be.getLevel() == null || be.getLevel().isClientSide) return;
        RopeStrandHolderBehavior behavior = be.getBehaviour(RopeStrandHolderBehavior.TYPE);
        if (behavior == null || (behavior.getOwnedStrand() == null && behavior.getAttachedStrand() == null)) {
            createsubmarine$setAnchorGrounded(false);
        }
    }
}
