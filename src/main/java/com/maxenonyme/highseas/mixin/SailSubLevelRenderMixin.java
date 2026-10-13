package com.maxenonyme.highseas.mixin;

import com.maxenonyme.highseas.client.SailRenderTypes;
import com.maxenonyme.highseas.client.SailShaderState;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = VanillaChunkedSubLevelRenderData.class, remap = false)
public class SailSubLevelRenderMixin {

    @Final
    @Shadow
    private ClientSubLevel subLevel;

    @Inject(method = "renderChunkedSubLevel", at = @At("HEAD"), remap = false, require = 0)
    private void createhighseas$sailUniforms(final RenderType layer,
                                             final ShaderInstance shader,
                                             final Matrix4f modelView,
                                             final double camX,
                                             final double camY,
                                             final double camZ,
                                             final CallbackInfo ci) {
        if (shader == null || layer != SailRenderTypes.sail()) {
            return;
        }
        Pose3dc renderPose = this.subLevel.renderPose();
        SailShaderState.prepareForSublevel(this.subLevel, shader, renderPose, camX, camY, camZ);
    }
}
