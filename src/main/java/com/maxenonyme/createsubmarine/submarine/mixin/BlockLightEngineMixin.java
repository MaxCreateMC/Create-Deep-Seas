package com.maxenonyme.createsubmarine.submarine.mixin;

import com.maxenonyme.createsubmarine.submarine.system.LightFailure;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockLightEngine.class)
public abstract class BlockLightEngineMixin {

    @Inject(method = "getEmission", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$failedLight(long pos, BlockState state, CallbackInfoReturnable<Integer> cir) {
        if (!LightFailure.any())
            return;
        BlockGetter getter = ((LightEngineAccessor) this).createsubmarine$chunkSource().getLevel();
        if (getter instanceof Level level && LightFailure.isDark(level, pos))
            cir.setReturnValue(0);
    }
}
