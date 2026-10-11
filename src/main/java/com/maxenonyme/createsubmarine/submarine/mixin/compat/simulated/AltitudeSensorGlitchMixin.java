package com.maxenonyme.createsubmarine.submarine.mixin.compat.simulated;

import com.maxenonyme.createsubmarine.submarine.client.DeviceGlitch;
import dev.simulated_team.simulated.content.blocks.altitude_sensor.AltitudeSensorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AltitudeSensorBlockEntity.class, remap = false)
public abstract class AltitudeSensorGlitchMixin {

    @Inject(method = "getVisualHeight", at = @At("RETURN"), cancellable = true, require = 0)
    private void createsubmarine$panicHeight(float partialTick, CallbackInfoReturnable<Float> cir) {
        BlockEntity self = (BlockEntity) (Object) this;
        float glitch = DeviceGlitch.at(self.getLevel(), self.getBlockPos());
        if (glitch <= 0f)
            return;
        float time = DeviceGlitch.time(self.getLevel(), partialTick);
        cir.setReturnValue(cir.getReturnValueF() + glitch * 320f * DeviceGlitch.noise(self.getBlockPos(), time * 0.8f));
    }
}
