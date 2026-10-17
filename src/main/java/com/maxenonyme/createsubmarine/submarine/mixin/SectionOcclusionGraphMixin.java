package com.maxenonyme.createsubmarine.submarine.mixin;

import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicReference;

@Mixin(SectionOcclusionGraph.class)
public class SectionOcclusionGraphMixin {
    @Shadow
    @Final
    private AtomicReference<?> currentGraph;

    @Inject(method = "onSectionCompiled", at = @At("HEAD"), cancellable = true, require = 0)
    private void createsubmarine$guardSectionCompiled(CallbackInfo ci) {
        if (this.currentGraph.get() == null)
            ci.cancel();
    }

    @Inject(method = "onChunkLoaded", at = @At("HEAD"), cancellable = true, require = 0)
    private void createsubmarine$guardChunkLoaded(CallbackInfo ci) {
        if (this.currentGraph.get() == null)
            ci.cancel();
    }
}
