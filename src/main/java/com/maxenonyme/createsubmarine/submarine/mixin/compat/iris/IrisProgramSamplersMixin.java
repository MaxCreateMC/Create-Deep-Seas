package com.maxenonyme.createsubmarine.submarine.mixin.compat.iris;

import com.llamalad7.mixinextras.sugar.Local;
import com.maxenonyme.createsubmarine.submarine.compat.sodium.SodiumWaterOcclusionBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Set;

//It’s quite sensitive; there may be conflicts with other mods if it allocates units 8/9 
//bon bah enfaite sa prener trop de unit pour Iteration mskin

@Pseudo
@Mixin(targets = "net.irisshaders.iris.gl.program.ProgramSamplers", remap = false)
public abstract class IrisProgramSamplersMixin {

    @ModifyVariable(method = "builder", at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
    private static Set<Integer> createsubmarine$reserveOcclusionUnits(Set<Integer> reserved,
            @Local(argsOnly = true) int program) {
        return SodiumWaterOcclusionBridge.reserveUnits(program, reserved);
    }
}
