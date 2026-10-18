package com.maxenonyme.createsubmarine.submarine.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class EntityPocketFogMixin {

    @Unique
    private float[] createsubmarine$savedFogColor;

    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void createsubmarine$defogBegin(Entity entity, double camX, double camY, double camZ, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci) {
        createsubmarine$savedFogColor = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;
        WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(mc.level);
        if (container == null)
            return;
        if (container.getOccludingRegion(mc.gameRenderer.getMainCamera().getPosition()) == null)
            return;
        if (!container.isOccluded(entity.position()))
            return;

        if (bufferSource instanceof MultiBufferSource.BufferSource buffers)
            buffers.endBatch();
        float[] fog = RenderSystem.getShaderFogColor();
        createsubmarine$savedFogColor = new float[] { fog[0], fog[1], fog[2], fog[3] };
        RenderSystem.setShaderFogColor(fog[0], fog[1], fog[2], 0.0f);
    }

    @Inject(method = "renderEntity", at = @At("TAIL"))
    private void createsubmarine$defogEnd(Entity entity, double camX, double camY, double camZ, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci) {
        if (createsubmarine$savedFogColor == null)
            return;
        if (bufferSource instanceof MultiBufferSource.BufferSource buffers)
            buffers.endBatch();
        RenderSystem.setShaderFogColor(createsubmarine$savedFogColor[0], createsubmarine$savedFogColor[1],
                createsubmarine$savedFogColor[2], createsubmarine$savedFogColor[3]);
        createsubmarine$savedFogColor = null;
    }
}
