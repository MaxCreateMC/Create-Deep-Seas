package com.maxenonyme.createsubmarine.submarine.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.maxenonyme.createsubmarine.submarine.compat.iris.ShaderpackSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
public abstract class IrisEyeInWaterMixin {

    @ModifyReturnValue(method = "isEyeInWater", at = @At("RETURN"), remap = false, require = 0)
    private static int createsubmarine$underwaterBehindHull(int original) {
        return original == 0 && ShaderpackSupport.lyingAboutWater() ? 1 : original;
    }
}
