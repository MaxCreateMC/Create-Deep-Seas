package com.maxenonyme.AbyssDimension.mixin;

import com.maxenonyme.AbyssDimension.client.PDAManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightTexture.class)
public class LightTextureMixin {

    @Unique
    private final int[] createsubmarine$darkRowCache = new int[16];

    @WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/NativeImage;setPixelRGBA(III)V"), require = 0)
    private void createsubmarine$wrapSetPixelRGBA(NativeImage instance, int x, int y, int color, Operation<Void> original) {
        if ((PDAManager.isFlickering() && !PDAManager.isLightsOn())
                || com.maxenonyme.createsubmarine.submarine.client.ImplosionCinematics.lightsOut()) {
            if (y >= 0 && y < createsubmarine$darkRowCache.length) {
                if (x == 0) {
                    createsubmarine$darkRowCache[y] = color;
                }
                color = createsubmarine$darkRowCache[y];
            }
        }
        original.call(instance, x, y, color);
    }
}
