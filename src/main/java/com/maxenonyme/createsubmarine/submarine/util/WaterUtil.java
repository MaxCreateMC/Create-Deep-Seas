package com.maxenonyme.createsubmarine.submarine.util;

import org.joml.Vector3d;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;

import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;

public class WaterUtil {
    public static Vector3d worldToLocal(SubLevelAccess sub, Vector3d vector) {
        return sub.logicalPose()
                .orientation()
                .conjugate(new org.joml.Quaterniond())
                .transform(vector);
    }

    public static double findWaterSurface(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        FluidState fluid = CompartmentTracker.realFluidState(level, cursor);

        if (!fluid.is(FluidTags.WATER)) {
            cursor.move(Direction.DOWN);
            fluid = CompartmentTracker.realFluidState(level, cursor);

            if (!fluid.is(FluidTags.WATER)) {
                return Double.NaN;
            }
        }

        while (cursor.getY() + 1 < level.getMaxBuildHeight()) {
            BlockPos.MutableBlockPos above = cursor.mutable().move(Direction.UP);
            FluidState fluidAbove =
                    CompartmentTracker.realFluidState(level, above);

            if (!fluidAbove.is(FluidTags.WATER)) {
                break;
            }

            cursor.set(above);
            fluid = fluidAbove;
        }

        return cursor.getY() + fluid.getHeight(level, cursor);
    }

    public static int countWaterAbove(Level level, BlockPos pos) {
        int depth = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = pos.getY() + 1; y < level.getMaxBuildHeight(); y++) {
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
