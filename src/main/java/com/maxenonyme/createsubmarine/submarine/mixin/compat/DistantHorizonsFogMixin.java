package com.maxenonyme.createsubmarine.submarine.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.maxenonyme.createsubmarine.submarine.client.SubmarineFogHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = {
        "com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper_neoforge",
        "com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper"
}, remap = false)
public class DistantHorizonsFogMixin {

    @ModifyReturnValue(method = "isFogStateSpecial", at = @At("RETURN"), remap = false, require = 0)
    private boolean createsubmarine$keepWaterFogInSealedHull(boolean special) {
        return special || SubmarineFogHandler.shouldFog();
    }
}
