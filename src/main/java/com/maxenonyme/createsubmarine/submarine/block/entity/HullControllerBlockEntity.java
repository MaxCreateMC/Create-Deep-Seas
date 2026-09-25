package com.maxenonyme.createsubmarine.submarine.block.entity;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import com.maxenonyme.createsubmarine.submarine.system.SubmarinePressureSystem;
import com.maxenonyme.createsubmarine.submarine.system.SubmarineHullManager;
import com.maxenonyme.createsubmarine.submarine.system.SubmarineDriverRegistry;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
public class HullControllerBlockEntity extends BlockEntity {

    private UUID currentSubLevelId = null;
    private boolean subLevelRegistered = false;
    public HullControllerBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSubmarine.CREATIVE_OXYGENATOR_BE.get(), pos, state);
    }
    public void tick() {
        if (level == null) return;
        SubLevelAccess subAccess = SableCompanion.INSTANCE.getContaining(level, worldPosition);
        if (subAccess instanceof SubLevel sub) {
            currentSubLevelId = sub.getUniqueId();
            long gameTick = level.getGameTime();
            if (!SubmarineDriverRegistry.claim(currentSubLevelId, worldPosition, SubmarineDriverRegistry.HULL_CONTROLLER, gameTick)) {
                return;
            }
            CompartmentTracker.claimSubmarine(currentSubLevelId, gameTick);
            long lastUpdate = CompartmentTracker.lastUpdateTick(currentSubLevelId);
            long sinceUpdate = gameTick - lastUpdate;
            boolean scanDue = lastUpdate == 0
                    || CompartmentTracker.isStructureDirty(currentSubLevelId)
                    || sinceUpdate >= 200;
            if (!CompartmentTracker.isScanActive(currentSubLevelId)
                    && scanDue && sinceUpdate >= getDynamicScanDelay()) {
                CompartmentTracker.beginScanIfIdle(currentSubLevelId, sub);
            }
            if (CompartmentTracker.isScanActive(currentSubLevelId)) {
                boolean done = CompartmentTracker.stepScan(currentSubLevelId, sub, getDynamicScanBudget(), gameTick);
                if (done && !level.isClientSide) {
                    SubmarinePressureSystem.setSealedCompartments(
                        currentSubLevelId, CompartmentTracker.getCompartments(currentSubLevelId));
                }
            }
            LevelPlot plot = sub.getPlot();
            if (plot != null) {
                BoundingBox3ic bounds = plot.getBoundingBox();
                Vector3d dimensions = CompartmentTracker.getOrComputeDimensions(currentSubLevelId, bounds);
                Pose3dc pose = sub.logicalPose();
                if (CompartmentTracker.poseMovedEnough(currentSubLevelId, pose, 0.01, 1e-6)) {
                    SubmarineHullManager.updateHull(currentSubLevelId, pose.position(), dimensions, pose.orientation());
                    CompartmentTracker.updateAABB(currentSubLevelId, pose.position(), dimensions);
                    CompartmentTracker.recordPose(currentSubLevelId, pose);
                }
                if (!level.isClientSide && !subLevelRegistered) {
                    SubLevelRegistry.register(
                        currentSubLevelId, sub, level,
                        new SubLevelRegistry.PlotBounds(bounds.minX(), bounds.maxX(), bounds.minY(), bounds.maxY(), bounds.minZ(), bounds.maxZ())
                    );
                    subLevelRegistered = true;
                }
            }
        } else if (currentSubLevelId != null) {
            releaseDriver();
            currentSubLevelId = null;
            subLevelRegistered = false;
        }
    }

    private int getDynamicScanDelay() {
        if (level == null) return 20;
        if (level.isClientSide) {
            return 2;
        }
        MinecraftServer server = level.getServer();
        if (server != null) {
            int players = server.getPlayerCount();
            if (players <= 1) return 1;
            return Math.min(40, players * 5);
        }
        return 20;
    }

    private int getDynamicScanBudget() {
        return 1500;
    }

    private void releaseDriver() {
        if (currentSubLevelId == null) return;
        long tick = level != null ? level.getGameTime() : 0L;
        if (SubmarineDriverRegistry.release(currentSubLevelId, worldPosition, tick)) {
            SubmarineHullManager.removeHull(currentSubLevelId);
            if (level != null && !level.isClientSide) {
                SubLevelRegistry.unregister(currentSubLevelId);
                SubmarinePressureSystem.clearSubmarine(currentSubLevelId);
            }
            CompartmentTracker.remove(currentSubLevelId);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (currentSubLevelId != null) {
            releaseDriver();
            currentSubLevelId = null;
            subLevelRegistered = false;
        }
    }
}