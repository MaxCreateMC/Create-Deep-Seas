package com.maxenonyme.createsubmarine.submarine.mixin.compat;

import com.maxenonyme.createsubmarine.submarine.client.renderer.SodiumWaterOcclusionBridge;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.neoforge.render.FluidRendererImpl", remap = false)
public abstract class SodiumFluidRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$onRenderFluid(@Coerce Object levelSlice, BlockState blockState, FluidState fluidState,
            BlockPos blockPos, BlockPos offset, @Coerce Object collector, @Coerce Object buffers, CallbackInfo ci) {
        if (SodiumWaterOcclusionBridge.PIXEL_PERFECT_ACTIVE)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && CompartmentTracker.isOccluded(mc.level, blockPos)) {
            SodiumWaterOcclusionBridge.FALLBACK_HOLES.add(SectionPos.asLong(blockPos));
            ci.cancel();
        }
    }
}
