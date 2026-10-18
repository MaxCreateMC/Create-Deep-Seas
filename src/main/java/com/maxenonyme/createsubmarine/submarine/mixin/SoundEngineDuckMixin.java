package com.maxenonyme.createsubmarine.submarine.mixin;

import com.maxenonyme.createsubmarine.submarine.client.PressureVision;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class SoundEngineDuckMixin {

    @Inject(method = "getVolume", at = @At("RETURN"), cancellable = true)
    private void createsubmarine$duckUnderPressure(SoundSource source, CallbackInfoReturnable<Float> cir) {
        if (source == null || source == SoundSource.MASTER)
            return;
        float duck = PressureVision.duckVolume();
        if (duck < 1f)
            cir.setReturnValue(cir.getReturnValueF() * duck);
    }
}
