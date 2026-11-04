package com.maxenonyme.vortexengine.atmosphere;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public record Sunlight(float x, float y, float strength, double surface) {

    public static Sunlight of(ClientLevel level, float partialTick, Vec3 eye) {
        double surface = surface(level, eye);
        if (Double.isNaN(surface))
            return new Sunlight(0, 1, 0, level.getSeaLevel());

        float angle = level.getSunAngle(partialTick);
        float towardX = -Mth.sin(angle);
        float height = Mth.cos(angle);

        float day = Mth.clamp((height + 0.05f) / 0.3f, 0, 1) * (1 - level.getRainLevel(partialTick) * 0.6f);
        if (!level.dimensionType().hasSkyLight())
            day = 0;

        float side = Math.abs(towardX) / 1.33f;
        float rise = Mth.sqrt(1 - side * side);
        return new Sunlight(Math.copySign(side, towardX), rise, day, surface);
    }

    public float red() {
        return 0.45f * strength;
    }

    public float green() {
        return 0.8f * strength;
    }

    public float blue() {
        return strength;
    }

    private static double surface(ClientLevel level, Vec3 eye) {
        BlockPos.MutableBlockPos cursor = BlockPos.containing(eye).mutable();
        FluidState start = level.getFluidState(cursor);
        if (!start.is(CreateSubmarine.WATER_TAG))
            return level.getSeaLevel();
        if (!start.isSource() && start.hasProperty(FlowingFluid.FALLING) && start.getValue(FlowingFluid.FALLING))
            return Double.NaN;

        double top = cursor.getY() + 0.9;

        for (int i = 0; i < 96; i++) {
            cursor.move(0, 1, 0);
            if (level.getFluidState(cursor).is(CreateSubmarine.WATER_TAG))
                top = cursor.getY() + 0.9;
            else if (level.getBlockState(cursor).isAir())
                return level.getBrightness(LightLayer.SKY, cursor) > 0 ? top : Double.NaN;
        }

        return Double.NaN;
    }
}
