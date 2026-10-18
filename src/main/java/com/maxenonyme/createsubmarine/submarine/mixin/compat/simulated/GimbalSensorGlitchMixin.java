package com.maxenonyme.createsubmarine.submarine.mixin.compat.simulated;

import com.maxenonyme.createsubmarine.submarine.client.DeviceGlitch;
import dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GimbalSensorBlockEntity.class, remap = false)
public abstract class GimbalSensorGlitchMixin {

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void createsubmarine$panicNeedle(CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        float glitch = DeviceGlitch.at(self.getLevel(), self.getBlockPos());
        if (glitch <= 0f)
            return;
        GimbalSensorBlockEntity gimbal = (GimbalSensorBlockEntity) (Object) this;
        int kicks = self.getLevel().random.nextFloat() < glitch ? 1 + (int) (glitch * 3f) : 0;
        for (int i = 0; i < kicks; i++)
            gimbal.randomNudge();
    }
}
