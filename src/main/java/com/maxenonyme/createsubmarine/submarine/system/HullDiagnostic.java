package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.submarine.block.entity.CommandSubBlockEntity;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.network.DiagnosticPayload;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class HullDiagnostic {

    private static final Direction[] START_ORDER = { Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST,
            Direction.WEST, Direction.DOWN };

    public static DiagnosticPayload run(CommandSubBlockEntity console) {
        Level level = console.getLevel();
        BlockPos pos = console.getBlockPos();
        SubLevelAccess access = level == null ? null : SableCompanion.INSTANCE.getContaining(level, pos);
        if (!(access instanceof SubLevel subLevel) || subLevel.getPlot() == null)
            return DiagnosticPayload.noVessel(pos);

        UUID id = subLevel.getUniqueId();
        Pose3dc pose = subLevel.logicalPose();

        SubmarinePressureSystem.WeakPoint weak = SubmarinePressureSystem.weakestHull(id, level);
        String weakest = weak == null ? "" : weak.state().getBlock().getDescriptionId();
        BlockPos weakestAt = weak == null ? null : toWorld(pose, weak.pos());

        List<CompartmentDetector.Component> comps = CompartmentTracker.getCompartments(id);

        Set<BlockPos> breaches = new LinkedHashSet<>(CompartmentTracker.plugs(id));
        BlockPos start = startCell(level, pos);
        boolean hermetic = false;
        if (start != null) {
            CompartmentDetector.Component room = null;
            for (CompartmentDetector.Component compartment : comps) {
                if (compartment.internal().contains(start)) {
                    room = compartment;
                    break;
                }
            }
            hermetic = room != null && room.sealed() && !CompartmentTracker.isCompromised(id, room.anchor())
                    && !CompartmentTracker.isBreached(id, room);
            if (!hermetic && (room == null || !room.sealed())) {
                SubmarineInfoCommand.Leaks leaks = SubmarineInfoCommand.findLeaks(level, subLevel.getPlot().getBoundingBox(),
                        start);
                if (leaks.status() == SubmarineInfoCommand.LeakStatus.LEAKING)
                    breaches.addAll(leaks.blocks());
            }
        }

        List<BlockPos> listed = new ArrayList<>();
        for (BlockPos blockPos : breaches) {
            if (listed.size() >= DiagnosticPayload.MAX_LISTED)
                break;
            listed.add(toWorld(pose, blockPos));
        }

        int depth = console.syncedDepth;
        int maxDepth = weak == null ? -1 : weak.depth();
        return new DiagnosticPayload(pos, true, depth, maxDepth, weakest, weakestAt,
                SubmarinePressureSystem.getCrackCount(id), hermetic, breaches.size(), listed);
    }

    private static BlockPos startCell(Level level, BlockPos console) {
        for (Direction dir : START_ORDER) {
            BlockPos next = console.relative(dir);
            if (CompartmentDetector.isPermeable(level.getBlockState(next)))
                return next;
        }
        return null;
    }

    private static BlockPos toWorld(Pose3dc pose, BlockPos plotPos) {
        Vector3d point = new Vector3d(plotPos.getX() + 0.5, plotPos.getY() + 0.5, plotPos.getZ() + 0.5);
        pose.transformPosition(point);
        return BlockPos.containing(point.x, point.y, point.z);
    }
}
