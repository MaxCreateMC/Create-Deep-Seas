package com.maxenonyme.highseas.mixin;

import com.maxenonyme.highseas.client.SailContraptionContext;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import net.createmod.catnip.render.SuperByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ContraptionEntityRenderer.class, remap = false)
public class ContraptionSailBakeMixin {

    @Inject(method = "buildStructureBuffer", at = @At("HEAD"), require = 0)
    private static void createhighseas$beginBake(CallbackInfoReturnable<SuperByteBuffer> cir) {
        SailContraptionContext.BAKING.set(Boolean.TRUE);
    }

    @Inject(method = "buildStructureBuffer", at = @At("RETURN"), require = 0)
    private static void createhighseas$endBake(CallbackInfoReturnable<SuperByteBuffer> cir) {
        SailContraptionContext.BAKING.set(Boolean.FALSE);
    }
}
