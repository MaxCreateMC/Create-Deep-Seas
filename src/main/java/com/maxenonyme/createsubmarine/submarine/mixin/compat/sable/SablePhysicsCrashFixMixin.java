package com.maxenonyme.createsubmarine.submarine.mixin.compat.sable;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = AbstractContraptionEntity.class, remap = false)
public abstract class SablePhysicsCrashFixMixin {
    @Inject(method = "sable$buildProperties", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$preventSableCrash(CallbackInfo ci) {
        AbstractContraptionEntity entity = (AbstractContraptionEntity) (Object) this;
        if (entity.getContraption() == null || entity.getContraption().getBlocks().isEmpty()) {
            ci.cancel();
        }
    }
}
