package com.maxenonyme.highseas.mixin;

import com.maxenonyme.highseas.client.SailRenderTypes;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = DefaultMaterials.class, remap = false)
public class SailSodiumMaterialMixin {

    @Shadow
    @Final
    public static Material CUTOUT;

    @Inject(method = "forRenderLayer", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void createhighseas$sailMaterial(RenderType layer, CallbackInfoReturnable<Material> cir) {
        if (layer == SailRenderTypes.sail()) {
            cir.setReturnValue(CUTOUT);
        }
    }
}
