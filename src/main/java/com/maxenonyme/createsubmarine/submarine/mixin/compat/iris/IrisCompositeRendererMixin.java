package com.maxenonyme.createsubmarine.submarine.mixin.compat.iris;

import com.maxenonyme.createsubmarine.submarine.compat.iris.ShaderpackSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.CompositeRenderer", remap = false)
public abstract class IrisCompositeRendererMixin {

    @Inject(method = "renderAll", at = @At("HEAD"), remap = false, require = 0)
    private void createsubmarine$bindHull(CallbackInfo ci) {
        ShaderpackSupport.bindHull();
    }
}
