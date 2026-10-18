package com.maxenonyme.createsubmarine.submarine.mixin.vanilla;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.shaders.Uniform;
import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.render.water_occlusion.SableWaterOcclusionPreProcessor;
import dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class TerrainOcclusionMixin {
    @Unique
    private ShaderInstance createsubmarine$terrainShader;

    @Inject(method = "renderSectionLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderInstance;apply()V", shift = At.Shift.BEFORE), require = 0)
    private void createsubmarine$occludeTerrainLayer(RenderType renderType, double x, double y, double z,
            Matrix4f frustum, Matrix4f projection, CallbackInfo ci, @Local ShaderInstance shader) {
        if (!WaterOcclusionRenderer.isEnabled() || shader == null)
            return;
        if (renderType != RenderType.solid() && renderType != RenderType.cutout() && renderType != RenderType.cutoutMipped())
            return;
        SableClient.WATER_OCCLUSION_RENDERER.setupTranslucentShader(shader);
        createsubmarine$terrainShader = shader;
    }

    @Inject(method = "renderSectionLayer", at = @At("RETURN"), require = 0)
    private void createsubmarine$releaseTerrainLayer(RenderType renderType, double x, double y, double z,
            Matrix4f frustum, Matrix4f projection, CallbackInfo ci) {
        ShaderInstance shader = createsubmarine$terrainShader;
        if (shader == null)
            return;
        createsubmarine$terrainShader = null;
        Uniform enabled = shader.getUniform(SableWaterOcclusionPreProcessor.ENABLE_UNIFORM);
        if (enabled != null)
            enabled.set(0.0F);
    }
}
