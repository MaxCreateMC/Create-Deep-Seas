package com.maxenonyme.createsubmarine.submarine.block.entity;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import com.maxenonyme.createsubmarine.submarine.util.WaterUtil;

import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
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

public class FloaterBlockEntity extends BlockEntity implements dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor {
    private static final int PRESSURE_THRESHOLD = 50;
    private static final int PRESSURE_CHECK_INTERVAL = 20;
    private static final int MAX_WATER_SCAN = 200;

    private int pressureTickCounter = 0;
    private volatile boolean cachedUnderwater;
    private volatile double cachedSubmergedRatio;
    private volatile double cachedDistanceToSurface;
    private volatile Vector3d cachedVelocity = null;

    public FloaterBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSubmarine.FLOATER_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, FloaterBlockEntity be) {
        if (level.isClientSide())
            return;

        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, pos);
        boolean sealed = sub != null
                && com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker
                        .hasAnySealed(sub.getUniqueId());

        if (++be.pressureTickCounter >= PRESSURE_CHECK_INTERVAL) {
            be.pressureTickCounter = 0;

            Level worldLevel = level;
            BlockPos worldPos = pos;
            if (sub != null) {
                Level parent = SubLevelRegistry.getLevel(sub.getUniqueId());
                if (parent != null)
                    worldLevel = parent;

                Vector3d transformedPos = new Vector3d(
                        pos.getX() + 0.5,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5);
                sub.logicalPose().transformPosition(transformedPos);
                worldPos = BlockPos.containing(transformedPos.x, transformedPos.y, transformedPos.z);
            }

            int threshold = PRESSURE_THRESHOLD;
            BlockState blockState = level.getBlockState(pos);
            java.util.Optional<com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig.HullProperty> propOpt =
                    com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig.getFor(blockState);
            if (propOpt.isPresent())
                threshold = propOpt.get().maxWaterDepth();

            if (sealed && WaterUtil.countWaterAbove(worldLevel, worldPos) > threshold) {
                burst(level, pos);
                return;
            }
        }

        if (sub == null) {
            be.clearCachedWaterState();
            return;
        }

        Vector3d worldPos = new Vector3d(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5);
        sub.logicalPose().transformPosition(worldPos);

        Level parentLevel = SubLevelRegistry.getLevel(sub.getUniqueId());
        if (parentLevel == null && sub instanceof dev.ryanhcode.sable.sublevel.SubLevel sl)
            parentLevel = sl.getLevel();

        if (parentLevel == null) {
            be.clearCachedWaterState();
            return;
        }

        BlockPos parentPos = BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
        double localWaterSurfaceY = WaterUtil.findWaterSurface(parentLevel, parentPos);
        if (!Double.isFinite(localWaterSurfaceY)) {
            be.clearCachedWaterState();
            return;
        }

        double depth = localWaterSurfaceY - (worldPos.y - 0.5);

        be.cachedUnderwater = depth > 0.0;
        be.cachedSubmergedRatio = Math.max(0.0, Math.min(1.0, depth));
        be.cachedDistanceToSurface = localWaterSurfaceY - worldPos.y;

        Vector3d vel = be.cachedVelocity;
        if (sealed && vel != null)
            checkCrash(level, pos, parentLevel, parentPos, vel);
    }

    private void clearCachedWaterState() {
        cachedUnderwater = false;
        cachedSubmergedRatio = 0.0;
        cachedDistanceToSurface = 0.0;
    }

    private static void burst(Level level, BlockPos pos) {
        level.playSound(null, pos, CreateSubmarine.IMPLOSION_SOUND.get(), SoundSource.BLOCKS, 1.2f, 1.4f);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1.0f, 0.7f);
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

        net.minecraft.world.level.block.state.BlockState parentState = parentLevel.getBlockState(parentPos);
        if (parentState.isSolid() && !parentState.is(net.minecraft.tags.BlockTags.LEAVES)) {
            subLevel.destroyBlock(localPos, false);
            parentLevel.playSound(null, parentPos, net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST,
                    SoundSource.BLOCKS, 1.0F, 1.5F);
            parentLevel.playSound(null, parentPos, net.minecraft.sounds.SoundEvents.WOOL_BREAK, SoundSource.BLOCKS,
                    1.0F, 0.8F);
        }
    }

    private java.util.List<FloaterBlockEntity> cachedCluster;
    private long clusterCacheTick = -1;

    public java.util.List<FloaterBlockEntity> getCluster() {
        if (level == null) return java.util.List.of(this);
        long tick = level.getGameTime();
        if (cachedCluster != null && tick - clusterCacheTick < 5) return cachedCluster;
        java.util.List<FloaterBlockEntity> cluster = new java.util.ArrayList<>();
        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        java.util.Queue<BlockPos> queue = new java.util.LinkedList<>();
        queue.add(worldPosition);
        visited.add(worldPosition);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (level.getBlockEntity(current) instanceof FloaterBlockEntity be) {
                cluster.add(be);
                for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                    BlockPos next = current.relative(dir);
                    if (!visited.contains(next) && level.getBlockState(next).getBlock() == CreateSubmarine.FLOATER.get()) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        cachedCluster = cluster;
        clusterCacheTick = tick;
        return cluster;
    }

    private FloaterBlockEntity getMaster() {
        java.util.List<FloaterBlockEntity> cluster = getCluster();
        FloaterBlockEntity master = this;
        for (FloaterBlockEntity be : cluster) {
            if (be.worldPosition.compareTo(master.worldPosition) < 0) {
                master = be;
            }
        }
        return master;
    }

    @Override
    public void sable$physicsTick(
            dev.ryanhcode.sable.sublevel.ServerSubLevel sub,
            RigidBodyHandle handle,
            double timeStep) {
        if (handle == null || !handle.isValid()) {
            cachedVelocity = null;
            return;
        }

        
        Vector3d currentVelocity = new Vector3d();
        handle.getLinearVelocity(currentVelocity);
        cachedVelocity = currentVelocity;

        if (this != getMaster())
            return;

        QueuedForceGroup forceGroup = sub.getOrCreateQueuedForceGroup(
                CreateSubmarine.FLOATER_FORCE_GROUP.get());

        double perceivedVelY = Math.max(-0.2, Math.min(0.2, currentVelocity.y()));
        double forceMultiplier =
                com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig
                        .BALLAST_FORCE_MULTIPLIER.get();
        double liftPerFloater =
                com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig
                        .FLOATER_LIFT.get();
        double impulseScale = 20.0 * timeStep;

        for (FloaterBlockEntity floater : getCluster()) {
            if (!floater.cachedUnderwater)
                continue;

            double targetVelY = Math.max(
                    -0.1,
                    Math.min(1.0, floater.cachedDistanceToSurface));
            double velocityError = targetVelY - perceivedVelY;
            double forceY = velocityError
                    * liftPerFloater
                    * 0.12
                    * forceMultiplier
                    * floater.cachedSubmergedRatio;

            if (!Double.isFinite(forceY))
                continue;

            double finalForceY =
                    Math.abs(currentVelocity.y()) < 0.01 && forceY < 0.0
                            ? forceY * 0.1
                            : forceY;

            Vector3d localImpulse = WaterUtil.worldToLocal(
                    sub,
                    new Vector3d(0.0, finalForceY * impulseScale, 0.0));
            Vector3d localPoint = new Vector3d(
                    floater.worldPosition.getX() + 0.5,
                    floater.worldPosition.getY() + 0.5,
                    floater.worldPosition.getZ() + 0.5);

            forceGroup.applyAndRecordPointForce(localPoint, localImpulse);
        }
    }

}