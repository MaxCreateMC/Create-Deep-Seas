package com.maxenonyme.createsubmarine.submarine.block.entity;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.FluidState;
import org.joml.Quaterniond;

public class FloaterBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
    private Vector3d recordedForceVec = null;
    private static final int PRESSURE_THRESHOLD = 50;
    private static final int PRESSURE_CHECK_INTERVAL = 20;
    private static final int MAX_WATER_SCAN = 200;

    private int pressureTickCounter = 0;

    private double pendingForceY;
    private long sharedTick = -1;
    private SubLevelAccess sharedSub;
    private Vector3dc sharedVel;
    private double sharedVelY;
    private double sharedMass = 1.0;

    private double cachedSurfaceY = -999.0;
    private BlockPos cachedSurfacePos;
    private long cachedSurfaceUntil = -1;

    public FloaterBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSubmarine.FLOATER_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, FloaterBlockEntity be) {
        FloaterBlockEntity master = be.getMaster();
        master.refreshShared(level);
        SubLevelAccess sub = master.sharedSub;
        boolean sealed = sub != null
                && CompartmentTracker.hasAnySealed(sub.getUniqueId());

        if (++be.pressureTickCounter >= PRESSURE_CHECK_INTERVAL) {
            be.pressureTickCounter = 0;
            Level worldLevel = level;
            BlockPos worldPos = pos;
            if (sub != null) {
                Level parent = SubLevelRegistry.getLevel(sub.getUniqueId());
                if (parent != null)
                    worldLevel = parent;
                Vector3d wp = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                sub.logicalPose().transformPosition(wp);
                worldPos = BlockPos.containing(wp.x, wp.y, wp.z);
            }
            int threshold = PRESSURE_THRESHOLD;
            BlockState blockState = level.getBlockState(pos);
            Optional<HullStrengthConfig.HullProperty> propOpt = HullStrengthConfig.getFor(blockState);
            if (propOpt.isPresent()) {
                threshold = propOpt.get().maxWaterDepth();
            }
            if (sealed && countWaterAbove(worldLevel, worldPos) > threshold) {
                burst(level, pos);
                return;
            }
        }

        if (sub == null)
            return;

        Vector3d worldPos = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        sub.logicalPose().transformPosition(worldPos);

        Vector3dc currentVel = master.sharedVel;
        double currentVelY = master.sharedVelY;

        Level parentLevel = SubLevelRegistry.getLevel(sub.getUniqueId());
        if (parentLevel == null && sub instanceof SubLevel sl) {
            parentLevel = sl.getLevel();
        }

        if (parentLevel == null)
            return;

        BlockPos parentPos = BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
        long now = level.getGameTime();
        double localWaterSurfaceY;

        if (parentPos.equals(be.cachedSurfacePos) && now < be.cachedSurfaceUntil) {
            localWaterSurfaceY = be.cachedSurfaceY;
        } else {
            localWaterSurfaceY = -999.0;
            FluidState fluidState = CompartmentTracker.realFluidState(parentLevel, parentPos);
            if (fluidState.is(FluidTags.WATER)) {
                float h = fluidState.getHeight(parentLevel, parentPos);
                localWaterSurfaceY = parentPos.getY() + h + countWaterAbove(parentLevel, parentPos);
            } else {
                BlockPos belowPos = parentPos.below();
                FluidState belowFluid = CompartmentTracker.realFluidState(parentLevel, belowPos);
                if (belowFluid.is(FluidTags.WATER)) {
                    float h = belowFluid.getHeight(parentLevel, belowPos);
                    localWaterSurfaceY = belowPos.getY() + h + countWaterAbove(parentLevel, belowPos);
                }
            }
            be.cachedSurfacePos = parentPos;
            be.cachedSurfaceY = localWaterSurfaceY;
            be.cachedSurfaceUntil = now + 40;
        }

        double depth = localWaterSurfaceY - (worldPos.y - 0.5);
        boolean isUnderWater = (depth > 0.0);

        if (!isUnderWater) {
            if (sealed) checkCrash(level, pos, parentLevel, parentPos, currentVel);
            return;
        }

        if (sealed) checkCrash(level, pos, parentLevel, parentPos, currentVel);

        double submergedRatio = Math.max(0.0, Math.min(1.0, depth));
        double distanceToSurface = localWaterSurfaceY - worldPos.y;
        double targetVelY = Math.max(-0.1, Math.min(1.0, distanceToSurface));

        double perceivedVelY = Math.max(-1.0, Math.min(1.0, currentVelY));
        double errorY = targetVelY - perceivedVelY;

        double forceMult = SubmarineConfig.BALLAST_FORCE_MULTIPLIER.get();
        double liftPerFloater = SubmarineConfig.FLOATER_LIFT.get();
        double forceToApply = errorY * liftPerFloater * 0.12 * forceMult * submergedRatio;
        double share = master.sharedMass / Math.max(1, master.getCluster().size());
        double cap = share * (Math.abs(errorY) * 0.8 + (forceToApply > 0 ? 0.55 : 0.0));
        forceToApply = Math.max(-cap, Math.min(cap, forceToApply));

        if (Double.isFinite(forceToApply)) {
            double finalForce = (Math.abs(currentVelY) < 0.01 && forceToApply < 0) ? forceToApply * 0.1 : forceToApply;
            master.pendingForceY += finalForce;
            if (sub instanceof ServerSubLevel ssl && ssl.isTrackingIndividualQueuedForces()) {
                Vector3d forceVec = new Vector3d(0, finalForce, 0);
                sub.logicalPose().orientation().conjugate(new Quaterniond()).transform(forceVec);
                be.recordedForceVec = forceVec;
            }
        }
    }

    private void refreshShared(Level level) {
        long tick = level.getGameTime();
        if (sharedTick == tick)
            return;
        sharedTick = tick;
        sharedSub = SableCompanion.INSTANCE.getContaining(level, worldPosition);
        Object handle = sharedSub != null ? SablePhysicsHelper.getHandle(sharedSub) : null;
        sharedVel = SablePhysicsHelper.getVelocity(handle);
        sharedVelY = sharedVel != null ? sharedVel.y() : 0;
        sharedMass = sharedSub != null ? SablePhysicsHelper.readMass(sharedSub) : 1.0;
    }

    private static int countWaterAbove(Level level, BlockPos pos) {
        int depth = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = pos.getY() + 1; y < pos.getY() + 1 + MAX_WATER_SCAN; y++) {
            m.set(pos.getX(), y, pos.getZ());
            if (CompartmentTracker.realFluidState(level, m).is(FluidTags.WATER)) {
                depth++;
            } else {
                break;
            }
        }
        return depth;
    }

    private static void burst(Level level, BlockPos pos) {
        level.playSound(null, pos, CreateSubmarine.IMPLOSION_SOUND.get(), SoundSource.BLOCKS, 1.2f, 1.4f);
        level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1.0f, 0.7f);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    12, 0.3, 0.3, 0.3, 0.05);
            serverLevel.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20,
                    0.4, 0.2, 0.4, 0.1);
        }
        level.destroyBlock(pos, false);
    }

    private static void checkCrash(Level subLevel, BlockPos localPos, Level parentLevel, BlockPos parentPos,
            Vector3dc vel) {
        if (vel == null)
            return;
        double speed = vel.length();
        if (speed < 0.35)
            return;

        BlockState parentState = parentLevel.getBlockState(parentPos);
        if (parentState.isSolid() && !parentState.is(BlockTags.LEAVES)) {
            subLevel.destroyBlock(localPos, false);
            parentLevel.playSound(null, parentPos, SoundEvents.FIREWORK_ROCKET_BLAST,
                    SoundSource.BLOCKS, 1.0F, 1.5F);
            parentLevel.playSound(null, parentPos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS,
                    1.0F, 0.8F);
        }
    }

    private static final int CLUSTER_REFRESH = 100;
    private static volatile int clusterEpoch;

    private volatile List<FloaterBlockEntity> cachedCluster;
    private volatile FloaterBlockEntity cachedMaster;
    private long clusterCacheTick = -1;
    private int cachedEpoch = -1;

    @Override
    public void onLoad() {
        super.onLoad();
        clusterEpoch++;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        clusterEpoch++;
    }

    public List<FloaterBlockEntity> getCluster() {
        if (level == null) return List.of(this);
        long tick = level.getGameTime();
        List<FloaterBlockEntity> known = cachedCluster;
        if (known != null && cachedEpoch == clusterEpoch && tick - clusterCacheTick < CLUSTER_REFRESH) return known;
        int epoch = clusterEpoch;
        Block floaterBlock = CreateSubmarine.FLOATER.get();
        List<FloaterBlockEntity> cluster = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(worldPosition);
        visited.add(worldPosition);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (level.getBlockEntity(current) instanceof FloaterBlockEntity be) {
                cluster.add(be);
                for (Direction dir : Direction.values()) {
                    BlockPos next = current.relative(dir);
                    if (!visited.contains(next) && level.getBlockState(next).getBlock() == floaterBlock) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        FloaterBlockEntity master = this;
        for (FloaterBlockEntity be : cluster) {
            if (be.worldPosition.compareTo(master.worldPosition) < 0) {
                master = be;
            }
        }
        cluster = List.copyOf(cluster);
        for (FloaterBlockEntity be : cluster) {
            be.cachedCluster = cluster;
            be.clusterCacheTick = tick;
            be.cachedEpoch = epoch;
            be.cachedMaster = master;
        }
        return cluster;
    }

    private FloaterBlockEntity getMaster() {
        getCluster();
        return cachedMaster != null ? cachedMaster : this;
    }

    @Override
    public void sable$physicsTick(ServerSubLevel sub, RigidBodyHandle handle, double timeStep) {
        List<FloaterBlockEntity> cluster = cachedCluster;
        if (cachedMaster != this || cluster == null) return;

        if (pendingForceY != 0.0) {
            Vector3d forceVec = new Vector3d(0, pendingForceY, 0);
            sub.logicalPose().orientation().conjugate(new Quaterniond()).transform(forceVec);
            handle.applyLinearImpulse(forceVec);
            pendingForceY = 0.0;
        }

        if (sub.isTrackingIndividualQueuedForces()) {
            QueuedForceGroup forceGroup = sub.getOrCreateQueuedForceGroup(CreateSubmarine.FLOATER_FORCE_GROUP.get());
            Vector3d totalForce = new Vector3d();
            Vector3d centerPos = new Vector3d();
            int count = 0;
            for (FloaterBlockEntity be : cluster) {
                if (be.recordedForceVec != null) {
                    totalForce.add(be.recordedForceVec);
                    centerPos.add(be.worldPosition.getX() + 0.5, be.worldPosition.getY() + 0.5, be.worldPosition.getZ() + 0.5);
                    be.recordedForceVec = null;
                    count++;
                }
            }
            if (count > 0) {
                centerPos.div(count);
                Vector3d recordVec = totalForce.mul(20.0 * timeStep);
                forceGroup.recordPointForce(centerPos, recordVec);
            }
        } else {
            for (FloaterBlockEntity be : cluster) {
                be.recordedForceVec = null;
            }
        }
    }
}
