package com.maxenonyme.createsubmarine.submarine.mixin.compat.iris;

import com.maxenonyme.createsubmarine.submarine.compat.iris.ShaderpackSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.transform.TransformPatcher", remap = false)
public abstract class IrisCompositeShaderMixin {

    @ModifyVariable(method = "patchComposite", at = @At("HEAD"), argsOnly = true, ordinal = 3, remap = false, require = 0)
    private static String createsubmarine$patchPhoton(String fragment) {
        return ShaderpackSupport.patchFragment(fragment);
    }
}
