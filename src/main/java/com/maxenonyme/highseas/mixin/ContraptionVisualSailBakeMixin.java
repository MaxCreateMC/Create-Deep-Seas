package com.maxenonyme.highseas.mixin;

import com.maxenonyme.highseas.client.SailContraptionContext;
import com.simibubi.create.content.contraptions.render.ContraptionVisual;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ContraptionVisual.class, remap = false)
public class ContraptionVisualSailBakeMixin {

    @Inject(method = "setupStructure", at = @At("HEAD"), require = 0)
    private void createhighseas$beginBake(CallbackInfo ci) {
        SailContraptionContext.BAKING.set(Boolean.TRUE);
    }

    @Inject(method = "setupStructure", at = @At("RETURN"), require = 0)
    private void createhighseas$endBake(CallbackInfo ci) {
        SailContraptionContext.BAKING.set(Boolean.FALSE);
    }
}
