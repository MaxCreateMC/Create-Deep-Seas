package com.maxenonyme.vortexengine.mixin;

import com.maxenonyme.vortexengine.light.LightIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LevelChunkGlowMixin {

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void vortex$onSetBlockState(BlockPos pos, BlockState state, boolean isMoving,
            CallbackInfoReturnable<BlockState> cir) {
        BlockState old = cir.getReturnValue();
        LevelChunk chunk = (LevelChunk) (Object) this;

        if (old != null && chunk.getLevel().isClientSide)
            LightIndex.onBlockChanged(chunk, pos, old, state);
    }
}
