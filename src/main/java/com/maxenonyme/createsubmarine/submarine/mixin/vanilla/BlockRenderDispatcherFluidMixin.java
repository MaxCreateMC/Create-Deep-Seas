package com.maxenonyme.createsubmarine.submarine.mixin.vanilla;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderDispatcher.class)
public class BlockRenderDispatcherFluidMixin {
    @Inject(method = "renderLiquid", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$cullFluidInHull(BlockPos pos, BlockAndTintGetter level, VertexConsumer consumer,
            BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        if (fluidState.is(FluidTags.WATER) || WaterOcclusionRenderer.isEnabled())
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && CompartmentTracker.isOccluded(mc.level, pos))
            ci.cancel();
    }
}
