package com.maxenonyme.theabyss.mixin;

import com.maxenonyme.theabyss.dimension.AbyssLayout;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseBasedChunkGenerator.class)
public class AbyssFluidPickerMixin {

    @Inject(method = "createFluidPicker", at = @At("HEAD"), cancellable = true)
    private static void createsubmarine$waterAllTheWay(NoiseGeneratorSettings settings,
            CallbackInfoReturnable<Aquifer.FluidPicker> cir) {
        if (settings.noiseSettings().minY() != AbyssLayout.BOTTOM || settings.seaLevel() != AbyssLayout.CEILING)
            return;

        Aquifer.FluidStatus water = new Aquifer.FluidStatus(settings.seaLevel(), settings.defaultFluid());
        cir.setReturnValue((x, y, z) -> water);
    }
}
