package com.maxenonyme.highseas.sail;

import com.maxenonyme.highseas.block.RudderBlock;
import com.maxenonyme.highseas.wind.WindConfig;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.world.phys.Vec3;

public final class RudderDetector {
    private RudderDetector() {
    }

    public static Vec3 centroid(LevelPlot plot) {
        BoundingBox3ic bounds = plot.getBoundingBox();
        long volume = (long) (bounds.maxX() - bounds.minX() + 1)
                * (bounds.maxY() - bounds.minY() + 1)
                * (bounds.maxZ() - bounds.minZ() + 1);
        if (volume <= 0 || volume > WindConfig.SAIL_SCAN_MAX_VOLUME) {
            return null;
        }

        double[] sum = new double[4];
        SailDetector.scan(plot, state -> state.getBlock() instanceof RudderBlock, (pos, state) -> {
            sum[0] += pos.getX() + 0.5;
            sum[1] += pos.getY() + 0.5;
            sum[2] += pos.getZ() + 0.5;
            sum[3]++;
        });
        if (sum[3] == 0) {
            return null;
        }
        return new Vec3(sum[0] / sum[3], sum[1] / sum[3], sum[2] / sum[3]);
    }
}
