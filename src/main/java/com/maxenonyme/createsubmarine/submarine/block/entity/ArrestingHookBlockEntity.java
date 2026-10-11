package com.maxenonyme.createsubmarine.submarine.block.entity;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.ArrestingHookBlock;
import com.maxenonyme.createsubmarine.submarine.system.SteelCablePhysicsSystem;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.object.box.BoxPhysicsObject;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

public class ArrestingHookBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {

    public static final float REST_ANGLE = -180.5f;
    public static final float ACTIVE_ANGLE = -22.5f;

    public float clientArmAngle;
    public long clientLastNanos = 0L;

    private static final double HOOK_REACH = 1.3 - 5.0 / 16.0;
    private static final double HOOK_HEIGHT = 0.5 - 5.0 / 16.0;
    private static final double CATCH_RADIUS = 2.0;

    private static final double SPRING_STIFFNESS = 100.0;
    private static final double SPRING_DAMPING = 16.0;
    private static final double MAX_STRETCH = 3.0;
    private static final double HOOK_COLLIDER_HALF_EXTENT = 0.4;
    private static final double HOOK_COLLIDER_MASS = 50.0;

    private ServerRopeStrand caughtStrand;

    private BoxPhysicsObject hookCollider;
    private SubLevelPhysicsSystem hookColliderSystem;

    public ArrestingHookBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSubmarine.ARRESTING_HOOK_BE.get(), pos, state);
        this.clientArmAngle = targetAngle(state);
    }

    public static float targetAngle(BlockState state) {
        return state.hasProperty(ArrestingHookBlock.POWERED) && state.getValue(ArrestingHookBlock.POWERED)
                ? ACTIVE_ANGLE
                : REST_ANGLE;
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (level == null || subLevel.getPlot() == null) {
            return;
        }
        BlockState state = getBlockState();
        boolean deployed = state.hasProperty(ArrestingHookBlock.POWERED) && state.getValue(ArrestingHookBlock.POWERED);
        if (!deployed) {
            caughtStrand = null;
            releaseCollider();
            return;
        }
        pullTowardCable(subLevel, handle, timeStep);
    }

    private void pullTowardCable(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        ServerLevel parentLevel = subLevel.getLevel();
        if (parentLevel == null) {
            return;
        }
        ServerLevelRopeManager ropeManager = ServerLevelRopeManager.getOrCreate(parentLevel);
        if (ropeManager == null) {
            return;
        }

        Direction armDir = getBlockState().getValue(ArrestingHookBlock.FACING).getOpposite();
        Vector3d hookLocal = new Vector3d(
                worldPosition.getX() + 0.5 + armDir.getStepX() * HOOK_REACH,
                worldPosition.getY() + HOOK_HEIGHT,
                worldPosition.getZ() + 0.5 + armDir.getStepZ() * HOOK_REACH);
        Vector3d hookWorld = subLevel.logicalPose().transformPosition(new Vector3d(hookLocal));

        if (caughtStrand != null && !caughtStrand.isActive()) {
            caughtStrand = null;
        }

        Vector3d cableDir = new Vector3d();
        Vector3d cablePoint = caughtStrand != null
                ? closestPointOnStrand(caughtStrand, hookWorld, cableDir)
                : findCableToGrab(ropeManager, parentLevel, subLevel.getUniqueId(), hookWorld, cableDir);
        if (cablePoint == null) {
            releaseCollider();
            return;
        }

        driveCollider(parentLevel, hookWorld);

        double mass = subLevel.getMassTracker().getMass();
        if (mass <= 0.0) {
            return;
        }

        Vector3d stretch = new Vector3d(cablePoint).sub(hookWorld);
        double stretchLen = stretch.length();
        if (stretchLen > MAX_STRETCH) {
            stretch.mul(MAX_STRETCH / stretchLen);
        }
        Vector3d velocity = handle.getLinearVelocity(new Vector3d());
        Vector3d accel = new Vector3d(stretch).mul(SPRING_STIFFNESS).sub(velocity.mul(SPRING_DAMPING));
        Vector3d impulseWorld = accel.mul(mass * timeStep);
        Vector3d impulseLocal = new Vector3d(impulseWorld);
        subLevel.logicalPose().orientation().transformInverse(impulseLocal);
        handle.applyImpulseAtPoint(hookLocal, impulseLocal);
    }

    private Vector3d findCableToGrab(ServerLevelRopeManager ropeManager, ServerLevel parentLevel, UUID ownId,
            Vector3d hookWorld, Vector3d cableDir) {
        Vector3d bestPoint = null;
        double bestDistSq = CATCH_RADIUS * CATCH_RADIUS;
        Vector3d scratchDir = new Vector3d();

        for (ServerRopeStrand strand : ropeManager.getAllStrands()) {
            if (!strand.isActive() || !SteelCablePhysicsSystem.isSteelCable(strand, parentLevel)) {
                continue;
            }
            if (clingsToSubLevel(strand, ownId)) {
                continue;
            }
            Vector3d closest = closestPointOnStrand(strand, hookWorld, scratchDir);
            if (closest == null) {
                continue;
            }
            double distSq = hookWorld.distanceSquared(closest);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestPoint = closest;
                cableDir.set(scratchDir);
                caughtStrand = strand;
            }
        }
        return bestPoint;
    }

    private static Vector3d closestPointOnStrand(ServerRopeStrand strand, Vector3d hookWorld, Vector3d dir) {
        List<Vector3d> points = strand.getPoints();
        Vector3d best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (int i = 0; i < points.size() - 1; i++) {
            Vector3d a = points.get(i);
            Vector3d b = points.get(i + 1);
            Vector3d closest = SteelCablePhysicsSystem.getClosestPointOnSegment(a, b, hookWorld);
            double distSq = hookWorld.distanceSquared(closest);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = closest;
                dir.set(b).sub(a);
            }
        }
        return best;
    }

    private static boolean clingsToSubLevel(ServerRopeStrand strand, UUID subLevelId) {
        RopeAttachment start = strand.getAttachment(RopeAttachmentPoint.START);
        RopeAttachment end = strand.getAttachment(RopeAttachmentPoint.END);
        return (start != null && subLevelId.equals(start.subLevelID()))
                || (end != null && subLevelId.equals(end.subLevelID()));
    }

    private void driveCollider(ServerLevel parentLevel, Vector3d hookWorld) {
        SubLevelPhysicsSystem system = getPhysicsSystem(parentLevel);
        if (system == null) {
            return;
        }
        if (hookCollider == null) {
            Pose3d pose = new Pose3d();
            pose.position().set(hookWorld);
            hookCollider = new BoxPhysicsObject(pose,
                    new Vector3d(HOOK_COLLIDER_HALF_EXTENT, HOOK_COLLIDER_HALF_EXTENT, HOOK_COLLIDER_HALF_EXTENT),
                    HOOK_COLLIDER_MASS);
            hookColliderSystem = system;
            system.addObject(hookCollider);
        }
        try {
            system.getPipeline().teleport(hookCollider, hookWorld, new Quaterniond());
        } catch (RuntimeException e) {
        }
    }

    private void releaseCollider() {
        if (hookCollider != null && hookColliderSystem != null) {
            try {
                hookColliderSystem.removeObject(hookCollider);
            } catch (RuntimeException e) {
            }
        }
        hookCollider = null;
        hookColliderSystem = null;
    }

    private static SubLevelPhysicsSystem getPhysicsSystem(ServerLevel parentLevel) {
        SubLevelContainer container = SubLevelContainer.getContainer(parentLevel);
        return container instanceof ServerSubLevelContainer serverContainer ? serverContainer.physicsSystem() : null;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        releaseCollider();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        releaseCollider();
    }
}
