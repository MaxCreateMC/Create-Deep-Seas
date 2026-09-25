package com.maxenonyme.createsubmarine.submarine.mixin.compat.sable;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionRegion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.maxenonyme.createsubmarine.submarine.client.renderer.SubLevelRenderPoseCapture;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.UUID;
import net.minecraft.util.Mth;

@Pseudo
@Mixin(value = VanillaChunkedSubLevelRenderData.class, remap = false)
public abstract class SableSubLevelPocketFogMixin {

    @Shadow(remap = false)
    @Final
    private ClientSubLevel subLevel;

    @Unique
    private float[] createsubmarine$savedFogColor;

    @Inject(method = "renderChunkedSubLevel", at = @At("HEAD"), remap = false, require = 0)
    private void createsubmarine$defogBegin(RenderType layer, ShaderInstance shader, Matrix4f modelView,
            double camX, double camY, double camZ, CallbackInfo ci) {
        if (this.subLevel.getLevel() != null) {
            SubLevelRenderPoseCapture.capture(
                    this.subLevel.getUniqueId(), this.subLevel.renderPose());
        }
        createsubmarine$savedFogColor = null;
        if (shader.FOG_COLOR == null) return;
        if (this.subLevel.getLevel() == null) return;
        WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(this.subLevel.getLevel());
        if (container == null) return;
        WaterOcclusionRegion region = container.getOccludingRegion(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        boolean shouldClearFog = false;
        if (region != null) {
            SubLevel regionSub = Sable.HELPER.getContaining(this.subLevel.getLevel(), region.getVolume().getMinBlockPos());
            if (regionSub != this.subLevel) {
                Vector3dc p = this.subLevel.renderPose().position();
                Vec3 local = regionSub != null
                        ? regionSub.logicalPose().transformPositionInverse(new Vec3(p.x(), p.y(), p.z()))
                        : new Vec3(p.x(), p.y(), p.z());
                if (region.getVolume().getOccupied(Mth.floor(local.x), Mth.floor(local.y), Mth.floor(local.z))) {
                    shouldClearFog = true;
                }
            }
        }
        
        if (!shouldClearFog) {
            UUID sealedSub = CompartmentTracker.findSealedSublevelExact(this.subLevel.getLevel(), Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
            if (sealedSub != null && sealedSub.equals(this.subLevel.getUniqueId())) {
                shouldClearFog = true;
            }
        }

        if (!shouldClearFog) return;

        float[] fog = RenderSystem.getShaderFogColor();
        createsubmarine$savedFogColor = new float[] { fog[0], fog[1], fog[2], fog[3] };
        shader.FOG_COLOR.set(0.0f, 0.0f, 0.0f, 0.0f);
        shader.FOG_COLOR.upload();
    }

    @Inject(method = "renderChunkedSubLevel", at = @At("TAIL"), remap = false, require = 0)
    private void createsubmarine$defogEnd(RenderType layer, ShaderInstance shader, Matrix4f modelView,
            double camX, double camY, double camZ, CallbackInfo ci) {
        if (createsubmarine$savedFogColor == null) return;
        shader.FOG_COLOR.set(createsubmarine$savedFogColor[0], createsubmarine$savedFogColor[1],
                createsubmarine$savedFogColor[2], createsubmarine$savedFogColor[3]);
        shader.FOG_COLOR.upload();
        createsubmarine$savedFogColor = null;
    }
}
