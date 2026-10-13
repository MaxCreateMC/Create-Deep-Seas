package com.maxenonyme.highseas.block.entity;

import com.maxenonyme.highseas.block.AnchorBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import java.util.List;
import com.maxenonyme.createsubmarine.submarine.system.CableElectrificationSystem;
import com.maxenonyme.createsubmarine.submarine.util.WinchAnchorSignal;
import com.maxenonyme.highseas.config.HighSeasConfig;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public class AnchorBlockEntity extends SmartBlockEntity implements RopeStrandHolderBlockEntity {

    private static final double FLOOR_PROBE_NEAR = 0.55;
    private static final double FLOOR_PROBE_FAR = 1.05;

    private static final double LIGHT_MASS = 20.0;
    private static final double MASS_EPSILON = 5.0;

    private static final int REEL_GRACE_TICKS = 4;
    private static final double REEL_EXTENSION_EPSILON = 0.02;

    private RopeStrandHolderBehavior ropeBehavior;

    private double lastReelExtension = Double.NaN;
    private int lastReelPointCount = -1;
    private long lastReelGameTime = Long.MIN_VALUE;

    public AnchorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Vec3 getAttachmentPoint(BlockPos pos, BlockState state) {
        Direction facing = Direction.NORTH;
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            facing = state.getValue(HorizontalDirectionalBlock.FACING);
        }

        Vec3 offset = switch (facing) {
            case NORTH -> new Vec3(0.4, 1.45, 0.9);
            case SOUTH -> new Vec3(0.5, 1.45, 0.1);
            case EAST -> new Vec3(0.0, 1.45, 0.5);
            case WEST -> new Vec3(0.9, 1.45, 0.5);
            default -> new Vec3(0.5, 1.45, 0.5);
        };

        return new Vec3(pos.getX() + offset.x, pos.getY() + offset.y, pos.getZ() + offset.z);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        ropeBehavior = new RopeStrandHolderBehavior(this);
        behaviours.add(ropeBehavior);
    }

    @Override
    public RopeStrandHolderBehavior getBehavior() {
        return ropeBehavior;
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (level == null || subLevel.getPlot() == null) {
            return;
        }
        BlockState state = getBlockState();
        boolean cabled = state.hasProperty(AnchorBlock.HAS_CABLE) && state.getValue(AnchorBlock.HAS_CABLE);
        boolean grounded = state.hasProperty(AnchorBlock.GROUNDED) && state.getValue(AnchorBlock.GROUNDED);

        if (!cabled) {
            if (grounded) {
                unground(subLevel, state);
            }
            return;
        }

        ServerLevel world = subLevel.getLevel();
        if (world == null) {
            return;
        }

        Vector3d center = subLevel.logicalPose().transformPosition(new Vector3d(
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5));

        boolean onFloor = false;
        for (double drop = FLOOR_PROBE_NEAR; drop <= FLOOR_PROBE_FAR; drop += 0.5) {
            BlockPos below = BlockPos.containing(center.x, center.y - drop, center.z);
            BlockState ground = world.getBlockState(below);
            if (!ground.isAir() && !ground.getCollisionShape(world, below).isEmpty()) {
                onFloor = true;
                break;
            }
        }

        ServerRopeStrand strand = cableStrand();
        pushWinchSignal(strand, world, onFloor);

        if (!onFloor) {
            if (grounded) {
                unground(subLevel, state);
            }
            return;
        }

        if (!grounded) {
            level.setBlock(worldPosition, state.setValue(AnchorBlock.GROUNDED, true), 3);
        }

        if (winchReelingIn(strand, world)) {
            applyMass(subLevel, LIGHT_MASS);
            return;
        }
        applyMass(subLevel, HighSeasConfig.anchorMass);
    }

    private void pushWinchSignal(ServerRopeStrand strand, ServerLevel world, boolean grounded) {
        if (strand == null) {
            return;
        }
        for (RopeAttachment attachment : strand.getAttachments()) {
            ServerLevel lvl = CableElectrificationSystem.getLevelForAttachment(world, attachment);
            BlockEntity be = lvl.getBlockEntity(attachment.blockAttachment());
            if (be instanceof WinchAnchorSignal winch) {
                winch.createsubmarine$setAnchorGrounded(grounded);
            }
        }
    }

    private ServerRopeStrand cableStrand() {
        RopeStrandHolderBehavior behavior = getBehavior();
        if (behavior == null) {
            return null;
        }
        ServerRopeStrand strand = behavior.getOwnedStrand();
        return strand != null ? strand : behavior.getAttachedStrand();
    }

    private boolean winchReelingIn(ServerRopeStrand strand, ServerLevel world) {
        long now = world.getGameTime();
        if (strand != null) {
            double extension = strand.getExtension();
            int points = strand.getPoints().size();
            boolean shortening = (!Double.isNaN(lastReelExtension) && extension < lastReelExtension - REEL_EXTENSION_EPSILON)
                    || (lastReelPointCount >= 0 && points < lastReelPointCount);
            lastReelExtension = extension;
            lastReelPointCount = points;
            if (shortening) {
                lastReelGameTime = now;
            }
        }
        return now - lastReelGameTime <= REEL_GRACE_TICKS;
    }

    private void applyMass(ServerSubLevel subLevel, double target) {
        MassTracker self = subLevel.getSelfMassTracker();
        if (self == null) {
            return;
        }
        double current = self.getMass();
        if (current <= 0.0 || Math.abs(current - target) <= MASS_EPSILON) {
            return;
        }
        self.addBlockMass(level, getBlockState(), worldPosition, target - current, null);
    }

    private void unground(ServerSubLevel subLevel, BlockState state) {
        level.setBlock(worldPosition, state.setValue(AnchorBlock.GROUNDED, false), 3);
        applyMass(subLevel, LIGHT_MASS);
    }
}
