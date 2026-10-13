package com.maxenonyme.createsubmarine.submarine.mixin.compat;

import com.maxenonyme.createsubmarine.submarine.client.renderer.SodiumWaterOcclusionBridge;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class SodiumBlockRendererMixin {
    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void createsubmarine$skipOccludedBlocks(BakedModel model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
        if (SodiumWaterOcclusionBridge.PIXEL_PERFECT_ACTIVE)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && CompartmentTracker.isOccluded(mc.level, pos)) {
            ci.cancel();
        }
    }
}
