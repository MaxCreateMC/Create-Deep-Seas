package com.maxenonyme.createsubmarine.submarine.mixin.compat.sable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinhelpers.sublevel_render.vanilla.VanillaSubLevelBlockEntityRenderer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionRegion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VanillaSubLevelBlockEntityRenderer.class, remap = false)
public abstract class SableSubLevelBEFogMixin {

    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    @Unique
    private float[] createsubmarine$savedFogColor;
    @Inject(method = "renderSingleBE", at = @At("HEAD"), remap = false)
    private void createsubmarine$defogBegin(BlockEntity blockEntity, PoseStack poseStack, float partialTick,
            double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        createsubmarine$savedFogColor = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;
        WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(mc.level);
        if (container == null)
            return;
        WaterOcclusionRegion region = container.getOccludingRegion(mc.gameRenderer.getMainCamera().getPosition());
        if (region == null)
            return;
        SubLevel cameraSub = Sable.HELPER.getContaining(mc.level, region.getVolume().getMinBlockPos());
        ClientSubLevel beSub = Sable.HELPER.getContainingClient(blockEntity);
        if (cameraSub == null || cameraSub != beSub)
            return;

        this.renderBuffers.bufferSource().endBatch();
        float[] fog = RenderSystem.getShaderFogColor();
        createsubmarine$savedFogColor = new float[] { fog[0], fog[1], fog[2], fog[3] };
        RenderSystem.setShaderFogColor(fog[0], fog[1], fog[2], 0.0f);
    }

    @Inject(method = "renderSingleBE", at = @At("TAIL"), remap = false)
    private void createsubmarine$defogEnd(BlockEntity blockEntity, PoseStack poseStack, float partialTick,
            double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        if (createsubmarine$savedFogColor == null)
            return;
        this.renderBuffers.bufferSource().endBatch();
        RenderSystem.setShaderFogColor(createsubmarine$savedFogColor[0], createsubmarine$savedFogColor[1],
                createsubmarine$savedFogColor[2], createsubmarine$savedFogColor[3]);
        createsubmarine$savedFogColor = null;
    }
}
