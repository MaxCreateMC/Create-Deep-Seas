package com.maxenonyme.theabyss.mixin;

import com.maxenonyme.theabyss.client.AbyssSpecialEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbyssSpecialEffects.class)
public class AbyssSpecialEffectsMixin {

    @Inject(method = "isFoggyAt", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$isFoggyAt(int x, int y, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
