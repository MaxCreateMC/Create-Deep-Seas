package com.maxenonyme.createsubmarine.submarine.mixin.compat.iris;

import com.maxenonyme.createsubmarine.submarine.compat.iris.ShaderpackSupport;
import com.maxenonyme.createsubmarine.submarine.compat.sodium.SodiumWaterOcclusionBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL41;

import java.util.HashSet;
import java.util.Set;

//It’s quite sensitive; there may be conflicts with other mods if it allocates units 8/9 
//de ce que je sais, aucun autre mods touche specifiquement a ses units mdr

@Pseudo
@Mixin(targets = "net.irisshaders.iris.gl.program.ProgramSamplers", remap = false)
public abstract class IrisProgramSamplersMixin {

    @ModifyVariable(method = "builder", at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
    private static Set<Integer> createsubmarine$reserveOcclusionUnits(Set<Integer> reserved) {
        Set<Integer> units = new HashSet<>(reserved);
        units.add(SodiumWaterOcclusionBridge.CLOSE_TEXTURE_UNIT);
        units.add(SodiumWaterOcclusionBridge.FAR_TEXTURE_UNIT);
        return units;
    }

    @Inject(method = "builder", at = @At("HEAD"), remap = false, require = 0)
    private static void createsubmarine$pointHullSampler(int program, Set<Integer> reserved,
            CallbackInfoReturnable<Object> cir) {
        int location = GL20.glGetUniformLocation(program, ShaderpackSupport.HULL_SAMPLER);
        if (location >= 0)
            GL41.glProgramUniform1i(program, location, SodiumWaterOcclusionBridge.FAR_TEXTURE_UNIT);
    }
}
