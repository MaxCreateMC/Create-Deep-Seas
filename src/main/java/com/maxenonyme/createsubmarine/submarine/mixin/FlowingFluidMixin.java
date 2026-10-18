package com.maxenonyme.createsubmarine.submarine.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.maxenonyme.createsubmarine.submarine.block.entity.BallastVentBlockEntity;
import com.maxenonyme.createsubmarine.submarine.block.entity.DecompressionChamberBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;

@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$preventVentFluidTick(Level level, BlockPos pos, FluidState state, CallbackInfo ci) {
        if (DecompressionChamberBlockEntity
                .isChamberPartialWater(level, pos)) {
            ci.cancel();
        }
    }
}
