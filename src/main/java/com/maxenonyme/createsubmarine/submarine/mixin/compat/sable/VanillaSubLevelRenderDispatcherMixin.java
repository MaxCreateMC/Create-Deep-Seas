package com.maxenonyme.createsubmarine.submarine.mixin.compat.sable;

import com.mojang.blaze3d.shaders.Uniform;
import dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = VanillaSubLevelRenderDispatcher.class, remap = false)
public class VanillaSubLevelRenderDispatcherMixin {

    @Inject(method = "renderSectionLayer", at = @At("HEAD"), remap = false, require = 0)
    private void createsubmarine$disableWaterOcclusion(Iterable<ClientSubLevel> sublevels, RenderType renderType,
            ShaderInstance shader, double cameraX, double cameraY, double cameraZ,
            Matrix4f modelView, Matrix4f projection, float partialTicks, CallbackInfo ci) {
        if (!WaterOcclusionRenderer.isEnabled() || shader == null)
            return;
        Uniform u = shader.getUniform("SableWaterOcclusionEnabled");
        if (u == null)
            return;
        u.set(0.0F);
        u.upload();
    }
}
