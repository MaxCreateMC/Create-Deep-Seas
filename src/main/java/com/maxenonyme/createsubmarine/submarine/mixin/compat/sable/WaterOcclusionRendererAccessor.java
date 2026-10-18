package com.maxenonyme.createsubmarine.submarine.mixin.compat.sable;

import dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = WaterOcclusionRenderer.class, remap = false)
public interface WaterOcclusionRendererAccessor {
    @Accessor("closeBuffer")
    AdvancedFbo createsubmarine$getCloseBuffer();

    @Accessor("farBuffer")
    AdvancedFbo createsubmarine$getFarBuffer();
}
