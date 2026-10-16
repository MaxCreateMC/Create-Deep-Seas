package com.maxenonyme.createsubmarine.submarine.mixin.compat.aeronautics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.eriksonn.aeronautics.content.blocks.levitite.LevititeShaderManager", remap = false)
public class AeronauticsShaderCrashFixMixin {

    @Inject(method = "isEnabled", at = @At("HEAD"), cancellable = true, remap = false)
    private static void createsubmarine$fixThreadCrash(CallbackInfoReturnable<Boolean> cir) {
        Thread currentThread = Thread.currentThread();
        if (!currentThread.getName().equals("Render thread")) {
            cir.setReturnValue(false);
        }
    }
}