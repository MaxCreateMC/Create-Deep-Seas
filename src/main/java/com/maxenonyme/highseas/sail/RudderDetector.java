package com.maxenonyme.highseas.sail;

import com.maxenonyme.highseas.block.RudderBlock;
import com.maxenonyme.highseas.wind.WindConfig;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

public final class RudderDetector {
    private RudderDetector() {
    }

    public static Vec3 centroid(BlockGetter level, BoundingBox3ic bounds) {
        long volume = (long) (bounds.maxX() - bounds.minX() + 1)
                * (bounds.maxY() - bounds.minY() + 1)
                * (bounds.maxZ() - bounds.minZ() + 1);
        if (volume <= 0 || volume > WindConfig.SAIL_SCAN_MAX_VOLUME) {
            return null;
        }

        double sx = 0, sy = 0, sz = 0;
        int count = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    m.set(x, y, z);
                    if (level.getBlockState(m).getBlock() instanceof RudderBlock) {
                        sx += x + 0.5;
                        sy += y + 0.5;
                        sz += z + 0.5;
                        count++;
                    }
                }
            }
        }
        if (count == 0) {
            return null;
        }
        return new Vec3(sx / count, sy / count, sz / count);
    }
}
