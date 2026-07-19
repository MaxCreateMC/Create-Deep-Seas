package com.maxenonyme.createsubmarine.submarine.block.entity;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class BallastTankBlockEntity extends BlockEntity
        implements IHaveGoggleInformation, dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor {
    private static final int CAPACITY = 8000;

    // Updated by the normal server tick and consumed by the physics tick.
    private boolean cachedUnderwater;
    private double cachedSubmergedRatio;
    private double cachedDistanceToSurface;
    public final FluidTank tank = new FluidTank(CAPACITY) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    };
    private List<BallastTankBlockEntity> cachedCluster;
    private long clusterCacheTick = -1;

    public BallastTankBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSubmarine.BALLAST_TANK_BE.get(), pos, state);
    }

    private List<BallastTankBlockEntity> getCluster() {
        if (level == null)
            return List.of(this);
        long tick = level.getGameTime();
        if (cachedCluster != null && tick - clusterCacheTick < 5)
            return cachedCluster;
        List<BallastTankBlockEntity> cluster = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(worldPosition);
        visited.add(worldPosition);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (level.getBlockEntity(current) instanceof BallastTankBlockEntity be) {
                cluster.add(be);
                for (Direction dir : Direction.values()) {
                    BlockPos next = current.relative(dir);
                    if (!visited.contains(next)
                            && level.getBlockState(next).getBlock() == CreateSubmarine.BALLAST_TANK.get()) {
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

    private boolean checkVentConnection(Direction side) {
        if (level == null)
            return false;
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> clusterPositions = new HashSet<>();
        for (BallastTankBlockEntity be : getCluster()) {
            clusterPositions.add(be.getBlockPos());
        }
        for (BallastTankBlockEntity be : getCluster()) {
            for (Direction d : Direction.values()) {
                BlockPos n = be.getBlockPos().relative(d);
                if (!clusterPositions.contains(n) && visited.add(n)) {
                    queue.add(n);
                }
            }
        }
        int maxDepth = 60;
        int count = 0;
        while (!queue.isEmpty() && count < maxDepth) {
            BlockPos pos = queue.poll();
            count++;
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BallastVentBlockEntity)
                return true;
            BlockState state = level.getBlockState(pos);
            net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(state.getBlock());
            if (id != null && id.getNamespace().equals("create") &&
                    (id.getPath().contains("pump") || id.getPath().contains("pipe")
                            || id.getPath().contains("valve"))) {
                for (Direction dir : Direction.values()) {
                    BlockPos next = pos.relative(dir);
                    if (!visited.contains(next)) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        return false;
    }

    public int getClusterTotalCapacity() {
        return getCluster().size() * CAPACITY;
    }

    public int getClusterTotalAmount() {
        int total = 0;
        for (BallastTankBlockEntity be : getCluster())
            total += be.tank.getFluidAmount();
        return total;
    }

    public int fillCluster(int amount, FluidAction action) {
        int filled = 0, toFill = amount;
        for (BallastTankBlockEntity be : getCluster()) {
            int added = be.tank.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, toFill), action);
            filled += added;
            toFill -= added;
            if (toFill <= 0)
                break;
        }
        return filled;
    }

    public int drainCluster(int amount, FluidAction action) {
        int drained = 0, toDrain = amount;
        for (BallastTankBlockEntity be : getCluster()) {
            FluidStack stack = be.tank.drain(toDrain, action);
            drained += stack.getAmount();
            toDrain -= stack.getAmount();
            if (toDrain <= 0)
                break;
        }
        return drained;
    }

    public IFluidHandler getClusterFluidHandler(Direction side) {
        return new IFluidHandler() {
            private long lastCheckTick = -1;
            private boolean isVent = false;

            private int getMaxRate() {
                if (level != null) {
                    long tick = level.getGameTime();
                    if (tick - lastCheckTick > 10) {
                        isVent = checkVentConnection(side);
                        lastCheckTick = tick;
                    }
                }
                return isVent ? Integer.MAX_VALUE : 81;
            }

            @Override
            public int getTanks() {
                return 1;
            }

            @Override
            public @NotNull FluidStack getFluidInTank(int tank) {
                int total = 0;
                for (BallastTankBlockEntity be : getCluster())
                    total += be.tank.getFluidAmount();
                return new FluidStack(net.minecraft.world.level.material.Fluids.WATER, total);
            }

            @Override
            public int getTankCapacity(int tank) {
                return getCluster().size() * CAPACITY;
            }

            @Override
            public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
                return stack.getFluid().isSame(net.minecraft.world.level.material.Fluids.WATER);
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                if (resource.isEmpty() || !isFluidValid(0, resource))
                    return 0;
                int filled = 0;
                int maxRate = getMaxRate();
                int toFill = Math.min(resource.getAmount(), maxRate);
                if (toFill <= 0)
                    return 0;
                for (BallastTankBlockEntity be : getCluster()) {
                    int added = be.tank.fill(resource.copyWithAmount(toFill), action);
                    filled += added;
                    toFill -= added;
                    if (toFill <= 0)
                        break;
                }
                return filled;
            }

            @Override
            public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
                if (resource.isEmpty() || !isFluidValid(0, resource))
                    return FluidStack.EMPTY;
                return drain(resource.getAmount(), action);
            }

            @Override
            public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
                int drained = 0;
                int maxRate = getMaxRate();
                int toDrain = Math.min(maxDrain, maxRate);
                if (toDrain <= 0)
                    return FluidStack.EMPTY;
                for (BallastTankBlockEntity be : getCluster()) {
                    FluidStack stack = be.tank.drain(toDrain, action);
                    drained += stack.getAmount();
                    toDrain -= stack.getAmount();
                    if (toDrain <= 0)
                        break;
                }
                return new FluidStack(net.minecraft.world.level.material.Fluids.WATER, drained);
            }
        };
    }

    public static void serverTick(Level level, BlockPos pos, BallastTankBlockEntity be) {
        if (level.isClientSide())
            return;

        be.shareFluidWithNeighbors();
        be.clearCachedWaterState();

        SubLevelAccess access = SableCompanion.INSTANCE.getContaining(level, pos);
        if (!(access instanceof ServerSubLevel sub))
            return;

        Vector3d worldPos = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        sub.logicalPose().transformPosition(worldPos);

        Level parentLevel = com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry
                .getLevel(sub.getUniqueId());
        if (parentLevel == null && sub instanceof dev.ryanhcode.sable.sublevel.SubLevel sl) {
            dev.ryanhcode.sable.sublevel.plot.LevelPlot plot = sl.getPlot();
            if (plot != null && sl.getLevel() != null) {
                parentLevel = sl.getLevel();
                dev.ryanhcode.sable.companion.math.BoundingBox3ic bounds = plot.getBoundingBox();
                com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry.register(
                        sub.getUniqueId(), sub, parentLevel,
                        new com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry.PlotBounds(
                                bounds.minX(), bounds.maxX(), bounds.minY(), bounds.maxY(),
                                bounds.minZ(), bounds.maxZ()));
            }
        }

        if (parentLevel == null)
            return;

        BlockPos parentPos = BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
        double waterSurfaceY = findWaterSurface(parentLevel, parentPos);
        if (!Double.isFinite(waterSurfaceY))
            return;

        double depth = waterSurfaceY - (worldPos.y - 0.5);
        if (depth <= 0.0)
            return;

        be.cachedUnderwater = true;
        be.cachedSubmergedRatio = Math.clamp(depth, 0.0, 1.0);
        be.cachedDistanceToSurface = waterSurfaceY - worldPos.y;
    }

    private void clearCachedWaterState() {
        cachedUnderwater = false;
        cachedSubmergedRatio = 0.0;
        cachedDistanceToSurface = 0.0;
    }

    private static double findWaterSurface(Level level, BlockPos pos) {
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

    private void shareFluidWithNeighbors() {
        if (level == null || level.isClientSide)
            return;
        int myAmount = tank.getFluidAmount();
        if (myAmount <= 2)
            return;
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(dir);
            BlockEntity otherBE = level.getBlockEntity(neighborPos);
            if (!(otherBE instanceof BallastTankBlockEntity other))
                continue;
            int otherAmount = other.tank.getFluidAmount();
            if (myAmount > otherAmount + 2) {
                int toTransfer = Math.max(1, (myAmount - otherAmount) / 2);
                FluidStack drained = tank.drain(toTransfer, FluidAction.EXECUTE);
                if (!drained.isEmpty()) {
                    other.tank.fill(drained, FluidAction.EXECUTE);
                    myAmount -= drained.getAmount();
                }
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_submarine.gui.goggles.ballast_status")
                        .withStyle(ChatFormatting.GRAY)));
        List<BallastTankBlockEntity> cluster = getCluster();
        int totalWater = 0;
        for (BallastTankBlockEntity be : cluster)
            totalWater += be.tank.getFluidAmount();
        int totalCapacity = cluster.size() * CAPACITY;
        tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_submarine.gui.goggles.water").withStyle(ChatFormatting.BLUE))
                .append(Component.literal(": " + totalWater + " / " + totalCapacity + " mB")
                        .withStyle(ChatFormatting.WHITE)));
        tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_submarine.gui.goggles.air").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(": " + (totalCapacity - totalWater) + " / " + totalCapacity + " mB")
                        .withStyle(ChatFormatting.WHITE)));
        if (cluster.size() > 1) {
            tooltip.add(Component.literal("    ")
                    .append(Component.translatable("create_submarine.gui.goggles.connected_tanks")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(": " + cluster.size()).withStyle(ChatFormatting.GRAY)));
        }
        return true;
    }

    private static int countWaterAbove(Level level, BlockPos pos) {
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

    private static Vector3d worldToLocal(SubLevelAccess sub, Vector3d vector) {
        return sub.logicalPose()
                .orientation()
                .conjugate(new org.joml.Quaterniond())
                .transform(vector);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null)
            loadAdditional(tag, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
    }

    private BallastTankBlockEntity getMaster() {
        List<BallastTankBlockEntity> cluster = getCluster();
        BallastTankBlockEntity master = this;
        for (BallastTankBlockEntity be : cluster) {
            if (be.worldPosition.compareTo(master.worldPosition) < 0) {
                master = be;
            }
        }
        return master;
    }

    @Override
    public void sable$physicsTick(ServerSubLevel sub, RigidBodyHandle handle, double timeStep) {
        if (this != getMaster() || handle == null || !handle.isValid())
            return;

        List<BallastTankBlockEntity> cluster = getCluster();
        Vector3d velocity = handle.getLinearVelocity(new Vector3d());
        dev.ryanhcode.sable.api.physics.force.QueuedForceGroup forceGroup =
                sub.getOrCreateQueuedForceGroup(CreateSubmarine.BALLAST_FORCE_GROUP.get());

        boolean anyTankUnderwater = false;
        Vector3d clusterCenter = new Vector3d();

        for (BallastTankBlockEntity tankEntity : cluster) {
            clusterCenter.add(
                    tankEntity.worldPosition.getX() + 0.5,
                    tankEntity.worldPosition.getY() + 0.5,
                    tankEntity.worldPosition.getZ() + 0.5);

            if (!tankEntity.cachedUnderwater)
                continue;

            anyTankUnderwater = true;
            double impulseY = calculateBallastImpulseY(tankEntity, velocity.y(), timeStep);
            if (!Double.isFinite(impulseY) || Math.abs(impulseY) <= 1.0e-9)
                continue;

            Vector3d localPoint = new Vector3d(
                    tankEntity.worldPosition.getX() + 0.5,
                    tankEntity.worldPosition.getY() + 0.5,
                    tankEntity.worldPosition.getZ() + 0.5);
            Vector3d localImpulse = worldToLocal(sub, new Vector3d(0.0, impulseY, 0.0));
            forceGroup.applyAndRecordPointForce(localPoint, localImpulse);
        }

        if (!anyTankUnderwater || cluster.isEmpty())
            return;

        clusterCenter.div(cluster.size());
        applyHorizontalDrag(sub, forceGroup, clusterCenter, velocity, timeStep);
    }

    private static double calculateBallastImpulseY(
            BallastTankBlockEntity tankEntity, double currentVelocityY, double timeStep) {
        double fillRatio = (double) tankEntity.tank.getFluidAmount() / CAPACITY;
        double maxSpeed = com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig
                .BALLAST_VERTICAL_SPEED.get();
        double targetVelocityY = (0.5 - fillRatio) * 2.0 * maxSpeed;

        if (targetVelocityY > 0.0) {
            targetVelocityY = Math.max(-0.1,
                    Math.min(targetVelocityY, tankEntity.cachedDistanceToSurface));
        }

        double perceivedVelocityY = Math.clamp(currentVelocityY, -0.2, 0.2);
        double velocityError = targetVelocityY - perceivedVelocityY;
        double forceMultiplier = com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig
                .BALLAST_FORCE_MULTIPLIER.get();
        double liftPerTank = com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig
                .BALLAST_LIFT_PER_TANK.get();

        // The old implementation applied this value once per game tick. 
        // Now its calculated based on timestep.
        double impulseY = velocityError * liftPerTank * 0.16 * forceMultiplier
                * tankEntity.cachedSubmergedRatio * 20.0 * timeStep;

        if (Math.abs(currentVelocityY) < 0.01 && impulseY < 0.0)
            impulseY *= 0.1;

        return impulseY;
    }

    private static void applyHorizontalDrag(
            ServerSubLevel sub,
            dev.ryanhcode.sable.api.physics.force.QueuedForceGroup forceGroup,
            Vector3d localPoint,
            Vector3d velocity,
            double timeStep) {
        double mass = sub.getMassTracker().getMass();
        double dragCoefficient = 0.035;
        double impulseScale = 20.0 * timeStep;
        double impulseX = -velocity.x() * mass * dragCoefficient * impulseScale;
        double impulseZ = -velocity.z() * mass * dragCoefficient * impulseScale;

        if (Math.abs(impulseX) <= 0.01 && Math.abs(impulseZ) <= 0.01)
            return;

        Vector3d localImpulse = worldToLocal(sub, new Vector3d(impulseX, 0.0, impulseZ));
        forceGroup.applyAndRecordPointForce(localPoint, localImpulse);
    }

}

