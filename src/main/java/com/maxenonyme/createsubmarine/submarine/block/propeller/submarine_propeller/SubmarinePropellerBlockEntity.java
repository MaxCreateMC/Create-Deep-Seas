package com.maxenonyme.createsubmarine.submarine.block.propeller.submarine_propeller;

import dev.eriksonn.aeronautics.config.AeroConfig;
import dev.eriksonn.aeronautics.content.blocks.propeller.small.BasePropellerBlock;
import dev.eriksonn.aeronautics.content.blocks.propeller.small.BasePropellerBlockEntity;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public class SubmarinePropellerBlockEntity extends BasePropellerBlockEntity {
    private static final int GROUP_SCAN_INTERVAL = 10;
    public static final ModelProperty<Boolean> HIDDEN_BY_CLUSTER = new ModelProperty<>();

    private volatile BlockPos groupMaster;
    private volatile int groupSize;
    private volatile double groupThrust;
    private volatile BlockPos driver;
    private int groupScan;
    private BlockPos[] members = new BlockPos[0];
    private double memberThrust;

    public SubmarinePropellerBlockEntity(final BlockPos pos, final BlockState state) {
        super(CreateSubmarine.SUBMARINE_PROPELLER_BE.get(), pos, state);
    }

    @Override
    public double getConfigThrust() {
        if (isSubmerged()) {
            return SubmarineConfig.SUBMARINE_PROPELLER_POWER_MULTIPLIER.get() * AeroConfig.server().physics.andesitePropellerThrust.get();
        }
        return 0.0;
    }

    @Override
    public double getConfigAirflow() {
        if (isSubmerged()) {
            return SubmarineConfig.SUBMARINE_PROPELLER_POWER_MULTIPLIER.get() * AeroConfig.server().physics.andesitePropellerAirflow.get();
        }
        return 0.0;
    }

    @Override
    public float calculateStressApplied() {
        float impact = super.calculateStressApplied();
        if (groupMaster == null)
            return impact;
        lastStressApplied = impact * groupSize * groupSize;
        return lastStressApplied;
    }

    @Override
    public float getRadius() {
        return isGroupMaster() ? groupSize : 1.0f;
    }

    @Override
    public double getThrust() {
        if (groupMaster == null)
            return super.getThrust();
        return isGroupMaster() ? groupThrust : 0;
    }

    @Override
    public double getAirflow() {
        SubmarinePropellerBlockEntity source = clusterDriver();
        if (source == this)
            return super.getAirflow();
        return getConfigAirflow() * source.getDirectionIndependentSpeed();
    }

    @Override
    public boolean isActive() {
        SubmarinePropellerBlockEntity source = clusterDriver();
        return source == this ? super.isActive() : source.isActive();
    }

    public SubmarinePropellerBlockEntity clusterDriver() {
        BlockPos pos = driver;
        if (pos == null || !isGroupMaster() || level == null)
            return this;
        return level.getBlockEntity(pos) instanceof SubmarinePropellerBlockEntity prop ? prop : this;
    }

    @Override
    public void applyForces(ServerSubLevel subLevel, Vec3 thrustDirection, double timeStep) {
        if (groupMaster != null && !isGroupMaster())
            return;
        Vec3 thrust = thrustDirection.scale(getScaledThrust() * timeStep);
        Vector3d center = JOMLConversion.toJOML(getGroupCenter());
        subLevel.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get())
                .applyAndRecordPointForce(center, new Vector3d(thrust.x, thrust.y, thrust.z));
    }

    public BlockPos getGroupMaster() {
        return groupMaster;
    }

    public boolean isGroupMaster() {
        return worldPosition.equals(groupMaster);
    }

    public boolean isGroupMember() {
        return groupMaster != null && !isGroupMaster();
    }

    public int getClusterSize() {
        return groupMaster != null ? groupSize : 0;
    }

    public Vec3 getGroupCenter() {
        if (!isGroupMaster() || groupSize == 3)
            return worldPosition.getCenter();
        Direction[] plane = planeAxes(getBlockDirection());
        return worldPosition.getCenter().add(
                (plane[0].getStepX() + plane[1].getStepX()) * 0.5,
                (plane[0].getStepY() + plane[1].getStepY()) * 0.5,
                (plane[0].getStepZ() + plane[1].getStepZ()) * 0.5);
    }

    public static Direction[] planeAxes(Direction facing) {
        return switch (facing.getAxis()) {
            case X -> new Direction[] { Direction.UP, Direction.SOUTH };
            case Y -> new Direction[] { Direction.EAST, Direction.SOUTH };
            case Z -> new Direction[] { Direction.EAST, Direction.UP };
        };
    }

    private BlockPos[] groupMembers() {
        Direction[] plane = planeAxes(getBlockDirection());
        BlockPos corner = groupSize == 3 ? groupMaster.relative(plane[0], -1).relative(plane[1], -1) : groupMaster;
        BlockPos[] members = new BlockPos[groupSize * groupSize];
        for (int i = 0; i < groupSize; i++) {
            for (int j = 0; j < groupSize; j++)
                members[i * groupSize + j] = corner.relative(plane[0], i).relative(plane[1], j);
        }
        return members;
    }

    private BlockPos findGroupMaster(int size) {
        BlockState state = getBlockState();
        Direction[] plane = planeAxes(getBlockDirection());
        for (int a = 0; a < size; a++) {
            for (int b = 0; b < size; b++) {
                BlockPos corner = worldPosition.relative(plane[0], -a).relative(plane[1], -b);
                if (isIsolatedSquare(corner, plane, state, size))
                    return size == 3 ? corner.relative(plane[0]).relative(plane[1]) : corner;
            }
        }
        return null;
    }

    private boolean isIsolatedSquare(BlockPos corner, Direction[] plane, BlockState reference, int size) {
        for (int i = -1; i <= size; i++) {
            for (int j = -1; j <= size; j++) {
                boolean inside = i >= 0 && i < size && j >= 0 && j < size;
                BlockPos pos = corner.relative(plane[0], i).relative(plane[1], j);
                if (matches(level.getBlockState(pos), reference) != inside)
                    return false;
            }
        }
        return true;
    }

    private static boolean matches(BlockState state, BlockState reference) {
        return state.getBlock() instanceof SubmarinePropellerBlock
                && state.getValue(BlockStateProperties.FACING) == reference.getValue(BlockStateProperties.FACING)
                && state.getValue(BasePropellerBlock.REVERSED) == reference.getValue(BasePropellerBlock.REVERSED);
    }

    @Override
    public float getOffset() {
        return 3 / 16f;
    }

    private boolean isSubmerged() {
        if (level == null) {
            return false;
        }
        SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(level, worldPosition);
        if (ship == null) {
            return level.getFluidState(worldPosition).is(CreateSubmarine.WATER_TAG);
        }
        Vector3d worldPos = new Vector3d(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
        ship.logicalPose().transformPosition(worldPos);
        BlockPos wPos = BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
        Level parentLevel = SubLevelRegistry.getLevel(ship.getUniqueId());
        if (parentLevel == null && ship instanceof SubLevel sl) {
            parentLevel = sl.getLevel();
        }
        if (parentLevel == null) {
            return false;
        }
        return CompartmentTracker.realFluidState(parentLevel, wPos).is(CreateSubmarine.WATER_TAG);
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && --groupScan <= 0) {
            groupScan = GROUP_SCAN_INTERVAL;
            int size = 3;
            BlockPos master = findGroupMaster(size);
            if (master == null) {
                size = 2;
                master = findGroupMaster(size);
            }
            if (master == null)
                size = 0;
            int wasCluster = getClusterSize();
            boolean changed = size != groupSize || (master == null) != (groupMaster == null);
            groupMaster = master;
            groupSize = size;
            if (changed && !level.isClientSide && hasNetwork())
                getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
            if (level.isClientSide && (wasCluster == 0) != (getClusterSize() == 0)) {
                requestModelDataUpdate();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
            }
            members = isGroupMaster() ? groupMembers() : new BlockPos[0];
            memberThrust = 0;
            for (BlockPos member : members) {
                if (level.getBlockEntity(member) instanceof SubmarinePropellerBlockEntity prop)
                    memberThrust += prop.getConfigThrust();
            }
        }
        if (isGroupMaster()) {
            SubmarinePropellerBlockEntity fastest = this;
            for (BlockPos member : members) {
                if (level.getBlockEntity(member) instanceof SubmarinePropellerBlockEntity prop
                        && Math.abs(prop.rotationSpeed) > Math.abs(fastest.rotationSpeed))
                    fastest = prop;
            }
            driver = fastest.worldPosition;
            groupThrust = memberThrust * fastest.getDirectionIndependentSpeed() * groupSize * groupSize;
        } else {
            driver = null;
        }
    }

    @Override
    public ModelData getModelData() {
        return getClusterSize() != 0 ? ModelData.builder().with(HIDDEN_BY_CLUSTER, true).build() : ModelData.EMPTY;
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(worldPosition).inflate(3);
    }

    @Override
    public void onActiveTick() {
        if (isGroupMember())
            return;
        if (this.prop != null) {
            this.prop.pushEntities();
        }
        if (level != null && level.isClientSide()) {
            if (isSubmerged()) {
                spawnSubmarineBubbles();
            } else if (this.prop != null) {
                this.prop.spawnParticles();
            }
        }
    }

    private void spawnSubmarineBubbles() {
        if (level == null)
            return;
        SubmarinePropellerBlockEntity source = clusterDriver();
        int particleCount = (int) (Math.abs(source.rotationSpeed) * 0.05f) + 1;
        float speed = (float)(this.getConfigAirflow() * source.getDirectionIndependentSpeed() / 20f);
        speed = Mth.clamp(speed, -5f, 5f);

        Direction dir = getBlockDirection();
        Vector3d thrustDir = JOMLConversion.toJOML(Vec3.atLowerCornerOf(dir.getNormal()));

        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, worldPosition);
        Vec3 center = getGroupCenter();
        Vector3d origin = new Vector3d(center.x, center.y, center.z);
        Vector3d pos = new Vector3d();
        Vector3d mutSpeed = new Vector3d();

        for (int i = 0; i < particleCount; i++) {
            double R = level.random.nextFloat() * getRadius();
            double angle = Math.PI * 2.0 * level.random.nextFloat();
            pos.set(Math.cos(angle) * R, getOffset(), Math.sin(angle) * R);
            dir.getRotation().transform(pos);
            pos.add(origin);


            double spawnOffset = 0.75 + level.random.nextFloat() * 0.5;
            pos.fma(Math.signum(speed) * spawnOffset, thrustDir);


            mutSpeed.set(thrustDir).mul(speed * 0.6 * (0.8 + level.random.nextFloat() * 0.4));

            if (subLevel != null) {
                subLevel.logicalPose().transformPosition(pos);
                subLevel.logicalPose().transformNormal(mutSpeed);
            }

            level.addParticle(ParticleTypes.BUBBLE, pos.x, pos.y, pos.z, mutSpeed.x, mutSpeed.y, mutSpeed.z);
        }
    }
}

