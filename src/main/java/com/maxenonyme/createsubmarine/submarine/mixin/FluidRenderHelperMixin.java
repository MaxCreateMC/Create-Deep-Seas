package com.maxenonyme.createsubmarine.submarine.mixin;

import com.maxenonyme.createsubmarine.submarine.client.renderer.SubmarineWaterCullBuffer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.render.FluidRenderHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FluidRenderHelper.class, remap = false)
public class FluidRenderHelperMixin {

    @ModifyVariable(method = {"renderFluidBox", "renderFluidStack"}, at = @At("HEAD"), argsOnly = true)
    private static MultiBufferSource createsubmarine$forceEntityTranslucent(MultiBufferSource buffer) {
        if (!SubmarineWaterCullBuffer.isRenderingSubmarineFluid())
            return buffer;
        return type -> {
            String typeStr = type.toString();
            if (typeStr.contains("translucent") && !typeStr.contains("entity")) {
                return buffer.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
            }
            return buffer.getBuffer(type);
        };
    }

    @Inject(method = "putVertex", at = @At("HEAD"), cancellable = true)
    private static void createsubmarine$injectOverlay(VertexConsumer vc, PoseStack ms, float x, float y, float z, int color, float u, float v, Direction face, int light, CallbackInfo ci) {
        if (!SubmarineWaterCullBuffer.isRenderingSubmarineFluid())
            return;
        vc.addVertex(ms.last().pose(), x, y, z)
          .setColor(color)
          .setUv(u, v)
          .setOverlay(OverlayTexture.NO_OVERLAY)
          .setLight(light)
          .setNormal(face.getStepX(), face.getStepY(), face.getStepZ());
        ci.cancel();
    }
}