package com.maxenonyme.createsubmarine.submarine.mixin.compat.simulated;

import com.maxenonyme.createsubmarine.submarine.client.DeviceGlitch;
import dev.simulated_team.simulated.content.blocks.velocity_sensor.VelocitySensorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VelocitySensorBlockEntity.class, remap = false)
public abstract class VelocitySensorGlitchMixin {

    @Inject(method = "getFanAngle", at = @At("RETURN"), cancellable = true, require = 0)
    private void createsubmarine$panicFan(float partialTick, CallbackInfoReturnable<Float> cir) {
        BlockEntity self = (BlockEntity) (Object) this;
        float glitch = DeviceGlitch.at(self.getLevel(), self.getBlockPos());
        if (glitch > 0f)
            cir.setReturnValue(cir.getReturnValueF() + DeviceGlitch.spinDegrees(partialTick) * 1.7f);
    }
}
