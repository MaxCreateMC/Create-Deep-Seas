package com.maxenonyme.createsubmarine.submarine.mixin.compat.aeronautics;

import com.mojang.logging.LogUtils;
import foundry.veil.api.event.VeilRegisterBlockLayersEvent;
import foundry.veil.api.event.VeilRegisterFixedBuffersEvent;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import foundry.veil.platform.VeilEventPlatform;

@Mixin(targets = "dev.eriksonn.aeronautics.AeronauticsClient", remap = false)
public class AeronauticsVeilCrashFixMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean createsubmarine$warned = false;

    @Redirect(method = "registerEvents", at = @At(value = "INVOKE", target = "Lfoundry/veil/platform/VeilEventPlatform;onVeilRegisterBlockLayers(Lfoundry/veil/api/event/VeilRegisterBlockLayersEvent;)V", remap = false), remap = false, require = 0)
    private static void createsubmarine$redirectBlockLayers(VeilEventPlatform instance,
            VeilRegisterBlockLayersEvent event) {
        try {
            instance.onVeilRegisterBlockLayers(event);
        } catch (IllegalArgumentException e) {
            createsubmarine$suppress(e);
        }
    }

    @Redirect(method = "registerEvents", at = @At(value = "INVOKE", target = "Lfoundry/veil/platform/VeilEventPlatform;onVeilRenderLevelStage(Lfoundry/veil/api/event/VeilRenderLevelStageEvent;)V", remap = false), remap = false, require = 0)
    private static void createsubmarine$redirectRenderLevelStage(VeilEventPlatform instance,
            VeilRenderLevelStageEvent event) {
        try {
            instance.onVeilRenderLevelStage(event);
        } catch (IllegalArgumentException e) {
            createsubmarine$suppress(e);
        }
    }

    @Redirect(method = "registerEvents", at = @At(value = "INVOKE", target = "Lfoundry/veil/platform/VeilEventPlatform;onVeilRegisterFixedBuffers(Lfoundry/veil/api/event/VeilRegisterFixedBuffersEvent;)V", remap = false), remap = false, require = 0)
    private static void createsubmarine$redirectFixedBuffers(VeilEventPlatform instance,
            VeilRegisterFixedBuffersEvent event) {
        try {
            instance.onVeilRegisterFixedBuffers(event);
        } catch (IllegalArgumentException e) {
            createsubmarine$suppress(e);
        }
    }

    private static void createsubmarine$suppress(IllegalArgumentException e) {
        if (createsubmarine$warned)
            return;

        LOGGER.warn("Suppressed Aeronautics Veil event re-registration: {}", e.getMessage());
        createsubmarine$warned = true;
    }
}
