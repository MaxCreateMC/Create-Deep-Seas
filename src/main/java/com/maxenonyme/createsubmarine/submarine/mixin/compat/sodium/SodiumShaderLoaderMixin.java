package com.maxenonyme.createsubmarine.submarine.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compat.sodium.WaterOcclusionPatch;
import net.caffeinemc.mods.sodium.client.gl.shader.GlShader;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderConstants;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;

@Pseudo
@Mixin(value = ShaderLoader.class, remap = false, priority = 2000)
public abstract class SodiumShaderLoaderMixin {

    private static final ThreadLocal<Boolean> createsubmarine$bypass = ThreadLocal.withInitial(() -> false);

    @WrapMethod(method = "loadShader", remap = false)
    private static GlShader createsubmarine$fallBackWithoutOcclusion(ShaderType type, ResourceLocation name,
            ShaderConstants constants, Operation<GlShader> original) {
        try {
            return original.call(type, name, constants);
        } catch (RuntimeException e) {
            if (createsubmarine$bypass.get() || !isTarget(name.getPath()))
                throw e;

            CreateSubmarine.LOGGER.warn("Sodium could not compile {} with the water occlusion patch, loading it untouched instead", name, e);
            createsubmarine$bypass.set(true);

            try {
                return original.call(type, name, constants);
            } finally {
                createsubmarine$bypass.set(false);
            }
        }
    }

    @ModifyReturnValue(method = "getShaderSource", at = @At("RETURN"), remap = false)
    private static String createsubmarine$injectOcclusion(String source, ResourceLocation location) {
        if (createsubmarine$bypass.get() || !isTarget(location.getPath()))
            return source;

        return WaterOcclusionPatch.apply(source, false);
    }

    private static boolean isTarget(String path) {
        return path.endsWith(".fsh") && path.contains("block_layer");
    }
}
