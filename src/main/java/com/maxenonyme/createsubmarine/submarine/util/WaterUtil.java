package com.maxenonyme.createsubmarine.submarine.util;

import org.joml.Vector3d;

import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class WaterUtil {
    public static Vector3d worldToLocal(SubLevelAccess sub, Vector3d vector) {
        return sub.logicalPose()
                .orientation()
                .conjugate(new org.joml.Quaterniond())
                .transform(vector);
    }

    public static double findWaterSurface(Level level, BlockPos pos) {
        net.minecraft.world.level.material.FluidState fluidState =
                com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker
                        .realFluidState(level, pos);
        if (fluidState.is(net.minecraft.tags.FluidTags.WATER)) {
            return pos.getY() + fluidState.getHeight(level, pos) + countWaterAbove(level, pos);
        }

        BlockPos belowPos = pos.below();
        net.minecraft.world.level.material.FluidState belowFluid =
                com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker
                        .realFluidState(level, belowPos);
        if (belowFluid.is(net.minecraft.tags.FluidTags.WATER)) {
            return belowPos.getY() + belowFluid.getHeight(level, belowPos)
                    + countWaterAbove(level, belowPos);
        }

        return Double.NaN;
    }

    public static int countWaterAbove(Level level, BlockPos pos) {
        int depth = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = pos.getY() + 1; y < pos.getY() + 1 + 200; y++) {
            m.set(pos.getX(), y, pos.getZ());
            if (com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker.realFluidState(level, m)
                    .is(net.minecraft.tags.FluidTags.WATER)) {
                depth++;
            } else {
                break;
            }
        }
        return depth;
    }
}
