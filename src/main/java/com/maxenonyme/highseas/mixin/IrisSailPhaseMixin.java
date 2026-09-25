package com.maxenonyme.highseas.mixin;

import com.maxenonyme.highseas.client.IrisCompat;
import com.maxenonyme.highseas.client.SailRenderTypes;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.WorldRenderingPhase", remap = false)
public class IrisSailPhaseMixin {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "fromTerrainRenderType", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void createhighseas$acceptSailLayer(RenderType layer, CallbackInfoReturnable cir) {
        if (layer == SailRenderTypes.sail()) {
            Object none = IrisCompat.nonePhase();
            if (none != null) {
                cir.setReturnValue(none);
            }
        }
    }
}
