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
import net.minecraft.tags.FluidTags;
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

public class SubmarinePropellerBlockEntity extends BasePropellerBlockEntity {
    private static final int GROUP_SCAN_INTERVAL = 10;

    private volatile BlockPos groupMaster;
    private volatile double groupThrust;
    private int groupScan;

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
    public float getRadius() {
        return isGroupMaster() ? 2.0f : 1.0f;
    }

    @Override
    public double getThrust() {
        if (groupMaster == null)
            return super.getThrust();
        return isGroupMaster() ? groupThrust : 0;
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

    public Vec3 getGroupCenter() {
        if (!isGroupMaster())
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
        return new BlockPos[] {
                groupMaster,
                groupMaster.relative(plane[0]),
                groupMaster.relative(plane[1]),
                groupMaster.relative(plane[0]).relative(plane[1])
        };
    }

    private BlockPos findGroupMaster() {
        BlockState state = getBlockState();
        Direction[] plane = planeAxes(getBlockDirection());
        for (int a = 0; a <= 1; a++) {
            for (int b = 0; b <= 1; b++) {
                BlockPos corner = worldPosition.relative(plane[0], -a).relative(plane[1], -b);
                if (isIsolatedSquare(corner, plane, state))
                    return corner;
            }
        }
        return null;
    }

    private boolean isIsolatedSquare(BlockPos corner, Direction[] plane, BlockState reference) {
        for (int i = -1; i <= 2; i++) {
            for (int j = -1; j <= 2; j++) {
                boolean inside = i >= 0 && i <= 1 && j >= 0 && j <= 1;
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
        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, worldPosition);
        if (sub == null) {
            return level.getFluidState(worldPosition).is(FluidTags.WATER);
        }
        Vector3d worldPos = new Vector3d(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
        sub.logicalPose().transformPosition(worldPos);
        BlockPos wPos = BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
        Level parentLevel = SubLevelRegistry.getLevel(sub.getUniqueId());
        if (parentLevel == null && sub instanceof SubLevel sl) {
            parentLevel = sl.getLevel();
        }
        if (parentLevel == null) {
            return false;
        }
        return CompartmentTracker.realFluidState(parentLevel, wPos).is(FluidTags.WATER);
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && --groupScan <= 0) {
            groupScan = GROUP_SCAN_INTERVAL;
            groupMaster = findGroupMaster();
        }
        if (isGroupMaster()) {
            double total = 0;
            for (BlockPos member : groupMembers()) {
                if (level.getBlockEntity(member) instanceof SubmarinePropellerBlockEntity prop)
                    total += prop.getConfigThrust() * prop.getDirectionIndependentSpeed();
            }
            groupThrust = total;
        }
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
        if (level == null) return;
        int particleCount = (int) (Math.abs(this.rotationSpeed) * 0.05f) + 1;
        float speed = (float)(this.getConfigAirflow() * getDirectionIndependentSpeed() / 20f);
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

