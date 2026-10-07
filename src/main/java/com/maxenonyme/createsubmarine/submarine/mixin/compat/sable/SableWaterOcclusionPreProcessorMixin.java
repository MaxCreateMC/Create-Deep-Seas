package com.maxenonyme.createsubmarine.submarine.mixin.compat.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.maxenonyme.createsubmarine.submarine.util.CompatUtil;
import dev.ryanhcode.sable.render.water_occlusion.SableWaterOcclusionPreProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Pseudo
@Mixin(value = SableWaterOcclusionPreProcessor.class, remap = false)
public abstract class SableWaterOcclusionPreProcessorMixin {
    private static final Set<String> createsubmarine$TERRAIN = Set.of("rendertype_solid", "rendertype_cutout",
            "rendertype_cutout_mipped");

    @Inject(method = "modify", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void createsubmarine$skipVanillaWaterDiscard(@Coerce Object ctx, @Coerce Object tree, CallbackInfo ci) {
        if (CompatUtil.isSodiumLoaded()) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "modify", at = @At(value = "INVOKE", target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z"),
            remap = false, require = 0)
    private boolean createsubmarine$patchTerrainLayers(String name, Object expected, Operation<Boolean> original) {
        return original.call(name, expected) || createsubmarine$TERRAIN.contains(name);
    }
}
