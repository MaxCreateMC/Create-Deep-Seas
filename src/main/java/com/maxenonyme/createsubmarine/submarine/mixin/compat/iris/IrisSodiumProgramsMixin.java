package com.maxenonyme.createsubmarine.submarine.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compat.sodium.WaterOcclusionPatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import java.util.Map;


//j'ai tester avec la majorité des shaders connue, photon, Bliss, complementay et je sais plus quoi, c'est compatible


@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.SodiumPrograms", remap = false)
public abstract class IrisSodiumProgramsMixin {

    @WrapMethod(method = "createGlShaders", remap = false, require = 0)
    private Map<Object, Object> createsubmarine$fallBackWithoutOcclusion(String passName, Map<Object, String> transformed,
            Operation<Map<Object, Object>> original) {
        try {
            return original.call(passName, transformed);
        } catch (RuntimeException e) {
            Map<?, String> untouched = WaterOcclusionPatch.untouched(transformed);
            if (untouched == null)
                throw e;

            CreateSubmarine.LOGGER.warn("The shaderpack's {} program did not compile with the water occlusion patch, "
                    + "loading it untouched instead", passName, e);
            return original.call(passName, untouched);
        }
    }
}
