package com.maxenonyme.createsubmarine.submarine.mixin;

import dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.ryanhcode.sable.SableClient;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void createsubmarine$preRenderTranslucent(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
            GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f modelView, Matrix4f projection,
            CallbackInfo ci) {

        if (!WaterOcclusionRenderer.isEnabled())
            return;

        WaterOcclusionRenderer renderer = SableClient.WATER_OCCLUSION_RENDERER;

        renderer.preRenderTranslucent(new Matrix4f(modelView), new Matrix4f(projection));
    }
}