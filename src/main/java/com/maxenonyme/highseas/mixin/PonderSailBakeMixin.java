package com.maxenonyme.highseas.mixin;

import com.maxenonyme.highseas.client.SailContraptionContext;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.ponder.foundation.element.WorldSectionElementImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WorldSectionElementImpl.class, remap = false)
public class PonderSailBakeMixin {

    @Inject(method = "buildStructureBuffer", at = @At("HEAD"), require = 0)
    private void createhighseas$beginBake(CallbackInfoReturnable<SuperByteBuffer> cir) {
        SailContraptionContext.BAKING.set(Boolean.TRUE);
    }

    @Inject(method = "buildStructureBuffer", at = @At("RETURN"), require = 0)
    private void createhighseas$endBake(CallbackInfoReturnable<SuperByteBuffer> cir) {
        SailContraptionContext.BAKING.set(Boolean.FALSE);
    }
}
