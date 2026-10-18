package com.maxenonyme.createsubmarine.submarine.mixin;

import com.maxenonyme.createsubmarine.submarine.client.renderer.SubmarineWaterCullBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.render.FluidRenderHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FluidRenderHelper.class, remap = false)
public class FluidRenderHelperBuilderMixin {
    @Inject(method = "getFluidBuilder", at = @At("HEAD"), cancellable = true)
    private static void createsubmarine$forceEntityTranslucentBuilder(MultiBufferSource buffer,
            CallbackInfoReturnable<VertexConsumer> cir) {
        if (!SubmarineWaterCullBuffer.isRenderingSubmarineFluid())
            return;
        cir.setReturnValue(buffer.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS)));
    }
}