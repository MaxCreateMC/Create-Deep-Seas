package com.maxenonyme.createsubmarine.submarine.mixin.compat.veil;

import com.maxenonyme.createsubmarine.submarine.client.VeilUploadGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "foundry.veil.impl.client.render.light.VoxelShadowGrid", remap = false)
public class VoxelShadowGridMixin {

    @Inject(method = "setup", at = @At("HEAD"), remap = false)
    private static void createsubmarine$resetUnpack(CallbackInfo ci) {
        VeilUploadGuard.reset();
    }
}
