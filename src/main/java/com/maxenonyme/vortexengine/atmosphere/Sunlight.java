package com.maxenonyme.vortexengine.atmosphere;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public record Sunlight(float x, float y, float strength, double surface) {

    public static Sunlight of(ClientLevel level, float partialTick, Vec3 eye) {
        float angle = level.getSunAngle(partialTick);
        float towardX = -Mth.sin(angle);
        float height = Mth.cos(angle);

        float day = Mth.clamp((height + 0.05f) / 0.3f, 0, 1) * (1 - level.getRainLevel(partialTick) * 0.6f);
        if (!level.dimensionType().hasSkyLight())
            day = 0;

        float side = Math.abs(towardX) / 1.33f;
        float rise = Mth.sqrt(1 - side * side);
        return new Sunlight(Math.copySign(side, towardX), rise, day, surface(level, eye));
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
        if (!level.getFluidState(cursor).is(CreateSubmarine.WATER_TAG))
            return level.getSeaLevel();

        for (int i = 0; i < 96; i++) {
            cursor.move(0, 1, 0);
            if (!level.getFluidState(cursor).is(CreateSubmarine.WATER_TAG))
                return cursor.getY() - 0.1;
        }

        return cursor.getY();
    }
}
