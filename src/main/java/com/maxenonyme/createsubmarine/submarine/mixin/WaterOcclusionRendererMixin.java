package com.maxenonyme.createsubmarine.submarine.mixin;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "dev.ryanhcode.sable.render.water_occlusion.SableWaterOcclusionPreProcessor", remap = false)
public class WaterOcclusionRendererMixin {

        @ModifyArg(method = "modify", at = @At(value = "INVOKE", target = "Lio/github/ocelot/glslprocessor/api/GlslParser;parseExpression(Ljava/lang/String;)Lio/github/ocelot/glslprocessor/api/node/GlslNode;", remap = false), index = 0, remap = false)
        private String createsubmarine$patchSurfaceWaterOcclusion(String src) {
                if (CreateSubmarine.DISABLE_WATER_OCCLUSION)
                        return src;

                return src.replace(
                                "waterDepth > closeDepth && waterDepth < farDepth",
                                "closeDepth < 1.0 && waterDepth > closeDepth && waterDepth < farDepth");
        }
}