package com.maxenonyme.highseas.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.maxenonyme.highseas.compat.iris.sail.IrisSailPatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;
import java.util.Map;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.transform.TransformPatcher", remap = false)
public abstract class IrisSailVanillaMixin {

    @ModifyReturnValue(method = "patchVanilla", at = @At("RETURN"), remap = false, require = 0)
    private static Map<Object, String> createhighseas$injectSailBillow(Map<Object, String> transformed,
            @Local(argsOnly = true, ordinal = 0) String name) {
        if (transformed == null)
            return null;

        for (Map.Entry<Object, String> entry : transformed.entrySet()) {
            if (!"VERTEX".equals(String.valueOf(entry.getKey())))
                continue;

            String source = IrisSailPatch.apply(name, entry.getValue());
            if (source == null)
                return transformed;

            Map<Object, String> patched = new HashMap<>(transformed);
            patched.put(entry.getKey(), source);
            return patched;
        }

        return transformed;
    }
}
