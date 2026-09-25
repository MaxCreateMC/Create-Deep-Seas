package com.maxenonyme.highseas.sail;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public record SailGroup(Direction.Axis axis, Vec3 localCenter, int area, BlockPos min, BlockPos max, int supportSign, long startTick) {

    public Vec3 localNormal() {
        return switch (axis) {
            case X -> new Vec3(1, 0, 0);
            case Y -> new Vec3(0, 1, 0);
            case Z -> new Vec3(0, 0, 1);
        };
    }
}
