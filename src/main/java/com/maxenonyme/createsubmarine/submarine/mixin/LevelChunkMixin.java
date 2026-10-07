package com.maxenonyme.createsubmarine.submarine.mixin;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @ModifyReturnValue(method = "getBlockState", at = @At("RETURN"))
    private BlockState createsubmarine$onGetBlockState(BlockState real, @Local(argsOnly = true) BlockPos pos) {
        if (real.isAir())
            return real;
        return CompartmentTracker.getLiedBlockState(((LevelChunk) (Object) this).getLevel(), pos, real);
    }
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void createsubmarine$onSetBlockState(BlockPos pos, BlockState state, boolean isMoving, CallbackInfoReturnable<BlockState> cir) {
        BlockState old = cir.getReturnValue();
        if (old == null) return;
        net.minecraft.world.level.Level level = ((LevelChunk) (Object) this).getLevel();
        CompartmentTracker.onPlotBlockChanged(level, pos, old, state);
        if (!level.isClientSide && old.getBlock() != state.getBlock()) {
            com.maxenonyme.createsubmarine.submarine.system.SubmarinePressureSystem.onPlotBlockReplaced(level, pos);
        }
    }
}
