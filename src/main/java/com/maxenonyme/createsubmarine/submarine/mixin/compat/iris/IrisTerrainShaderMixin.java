package com.maxenonyme.createsubmarine.submarine.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.maxenonyme.createsubmarine.submarine.compat.sodium.WaterOcclusionPatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//holy moly, At last, the support is on its way


@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.transform.TransformPatcher", remap = false)
public abstract class IrisTerrainShaderMixin {

    private static final Pattern RENDER_SCALE = Pattern.compile(
            "gl_Position\\.xy\\s*=\\s*gl_Position\\.xy\\s*\\*\\s*vec2\\s*\\(\\s*([0-9]*\\.?[0-9]+)");

    @ModifyReturnValue(method = "patchSodium", at = @At("RETURN"), remap = false, require = 0)
    private static Map<Object, String> createsubmarine$injectOcclusion(Map<Object, String> transformed) {
        if (transformed == null)
            return null;

        Map<Object, String> patched = new HashMap<>(transformed);
        String renderScale = null;

        for (Map.Entry<Object, String> entry : patched.entrySet()) {
            Matcher scale = RENDER_SCALE.matcher("VERTEX".equals(String.valueOf(entry.getKey())) ? entry.getValue() : "");
            if (scale.find())
                renderScale = scale.group(1);
        }

        boolean changed = false;

        for (Map.Entry<Object, String> entry : patched.entrySet()) {
            if (!"FRAGMENT".equals(String.valueOf(entry.getKey())))
                continue;

            String source = WaterOcclusionPatch.apply(entry.getValue(), true, renderScale);
            if (source != null && !source.equals(entry.getValue())) {
                entry.setValue(source);
                changed = true;
            }
        }

        if (!changed)
            return transformed;

        WaterOcclusionPatch.remember(patched, transformed);
        return patched;
    }
}
