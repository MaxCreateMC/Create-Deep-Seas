package com.maxenonyme.createsubmarine.submarine.mixin.compat;

import com.maxenonyme.createsubmarine.submarine.client.DeviceGlitch;
import com.simibubi.create.content.kinetics.gauge.GaugeBlockEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GaugeBlockEntity.class, remap = false)
public abstract class GaugeGlitchMixin {

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void createsubmarine$panicDial(CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        float glitch = DeviceGlitch.at(self.getLevel(), self.getBlockPos());
        if (glitch <= 0f)
            return;
        GaugeBlockEntity gauge = (GaugeBlockEntity) (Object) this;
        float wild = 0.55f + 0.6f * DeviceGlitch.noise(self.getBlockPos(), DeviceGlitch.time(self.getLevel(), 0f) * 0.6f);
        gauge.dialState = Mth.lerp(glitch, gauge.dialState, Mth.clamp(wild, 0f, 1.12f));
    }
}
