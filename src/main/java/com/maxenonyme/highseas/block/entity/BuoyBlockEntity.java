package com.maxenonyme.highseas.block.entity;

import com.maxenonyme.highseas.BoatBuoyancySystem;
import com.maxenonyme.highseas.BuoyFloatSystem;
import com.maxenonyme.highseas.CreateHighSeas;
import com.maxenonyme.highseas.block.BuoyBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

public class BuoyBlockEntity extends SmartBlockEntity implements RopeStrandHolderBlockEntity {

    private static final double HITCH_X = 5.5 / 16.0;
    private static final double HITCH_Y = 5.25 / 16.0;
    private static final double HITCH_Z = -11.75 / 16.0;

    private static final double RIDE_HEIGHT = -0.15;
    private static final double SNAP = 4.0;
    private static final double MAX_RISE = 6.0;
    private static final double TOW_SPEED = 2.5;
    private static final int HOP_MIN_GAP = 30;
    private static final int HOP_MAX_GAP = 110;
    private static final double HOP_LOW = 1.0;
    private static final double HOP_HIGH = 3.0;
    private static final double GRAVITY = 9.8;
    private static final int HOP_FLIGHT = 200;
    private static final int SKIPS_MAX = 3;
    private static final double SKIP_FALLOFF = 0.55;
    private static final double SPLASH_FLOOR = 1.5;
    private static final double SPLASH_FULL = 8.0;
    private static final double MAX_SINK = 3.0;
    private static final double MAX_ERROR = 1.5;
    private static final double SWAY_DAMP = 0.25;
    private static final double TIP_DAMP = 3.0;
    private static final double RIGHTING = 2.5;
    private static final double BAND = 1.5;

    private RopeStrandHolderBehavior ropeBehavior;
    private int nextHop = HOP_MIN_GAP;
    private int airborne;
    private int skipsLeft;
    private double skipHeight;
    private double splashPower;
    private double splashX;
    private double splashY;
    private double splashZ;

    public BuoyBlockEntity(BlockPos pos, BlockState state) {
        super(CreateHighSeas.BUOY_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        ropeBehavior = new RopeStrandHolderBehavior(this);
        behaviours.add(ropeBehavior);
    }

    public RopeStrandHolderBehavior getBehavior() {
        return ropeBehavior;
    }

    @Override
    public Vec3 getAttachmentPoint(BlockPos pos, BlockState state) {
        return new Vec3(pos.getX() + HITCH_X, pos.getY() + HITCH_Y, pos.getZ() + HITCH_Z);
    }

    private double hop(ServerLevel server, Vector3dc velocity, double immersion) {
        if (velocity == null || immersion < 0.9) {
            return 0.0;
        }
        double towed = Math.sqrt(velocity.x() * velocity.x() + velocity.z() * velocity.z());
        if (towed < TOW_SPEED) {
            nextHop = HOP_MIN_GAP;
            return 0.0;
        }
        if (--nextHop > 0) {
            return 0.0;
        }
        nextHop = HOP_MIN_GAP + server.getRandom().nextInt(HOP_MAX_GAP - HOP_MIN_GAP);
        skipsLeft = server.getRandom().nextInt(SKIPS_MAX);
        skipHeight = HOP_LOW + server.getRandom().nextDouble() * (HOP_HIGH - HOP_LOW);
        return leap(velocity.y(), skipHeight);
    }

    @Override
    public void tick() {
        super.tick();
        showRope();
        if (level == null || level.isClientSide || splashPower <= SPLASH_FLOOR) {
            splashPower = 0.0;
            return;
        }
        double force = Mth.clamp((splashPower - SPLASH_FLOOR) / (SPLASH_FULL - SPLASH_FLOOR), 0.0, 1.0);
        splashPower = 0.0;
        level.playSound(null, splashX, splashY, splashZ, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS,
                0.5f + (float) force * 0.8f, 1.25f - (float) force * 0.45f);
        if (force > 0.35 && level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SPLASH, splashX, splashY, splashZ,
                    6 + (int) (force * 18.0), 0.55, 0.1, 0.55, 0.12);
        }
    }

    private void showRope() {
        if (level == null || level.isClientSide || ropeBehavior == null) {
            return;
        }
        boolean tied = ropeBehavior.ownsRope() || ropeBehavior.isAttached();
        BlockState state = getBlockState();
        if (state.hasProperty(BuoyBlock.ROPED) && state.getValue(BuoyBlock.ROPED) != tied) {
            level.setBlock(worldPosition, state.setValue(BuoyBlock.ROPED, tied), 3);
        }
    }

    private static double leap(double heave, double height) {
        return Math.max(0.0, Math.sqrt(2.0 * GRAVITY * height) - heave);
    }

    @Override
    public void sable$physicsTick(ServerSubLevel sub, RigidBodyHandle handle, double timeStep) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        Pose3dc pose = sub.logicalPose();
        Vector3d centre = new Vector3d(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5);
        pose.transformPosition(centre);

        double surface = BoatBuoyancySystem.surfaceNear(server, centre.x, centre.y, centre.z);
        if (surface == Double.NEGATIVE_INFINITY) {
            BuoyFloatSystem.release(sub.getUniqueId());
            return;
        }
        double immersion = Mth.clamp((surface - centre.y + BAND) / BAND, 0.0, 1.0);
        if (immersion <= 0.0) {
            BuoyFloatSystem.release(sub.getUniqueId());
            return;
        }

        Vector3dc velocity = handle.getLinearVelocity();
        double heave = velocity != null ? velocity.y() : 0.0;
        double error = Mth.clamp(surface - RIDE_HEIGHT - centre.y, -MAX_ERROR, MAX_ERROR);
        double wanted = Mth.clamp(error * SNAP, -MAX_SINK, MAX_RISE);

        double lift = (surface - RIDE_HEIGHT) - centre.y;
        BuoyFloatSystem.hold(sub.getUniqueId(), pose.position().y() + lift);

        double hop = hop(server, velocity, immersion);
        if (hop > 0.0) {
            airborne = HOP_FLIGHT;
        }

        double vertical;
        if (airborne > 0) {
            airborne--;
            vertical = hop;
            if (hop == 0.0 && heave < 0.0 && error > 0.0) {
                splashPower = -heave;
                splashX = centre.x;
                splashY = surface;
                splashZ = centre.z;
                if (skipsLeft > 0) {
                    skipsLeft--;
                    skipHeight *= SKIP_FALLOFF;
                    vertical = leap(heave, skipHeight);
                    airborne = HOP_FLIGHT;
                } else {
                    airborne = 0;
                }
            }
        } else {
            vertical = (wanted - heave) * immersion;
        }

        Vector3d nudge = new Vector3d(0.0, vertical, 0.0);
        if (velocity != null) {
            nudge.x = -velocity.x() * SWAY_DAMP * immersion * timeStep;
            nudge.z = -velocity.z() * SWAY_DAMP * immersion * timeStep;
        }

        Vector3d up = new Vector3d(0.0, 1.0, 0.0);
        pose.orientation().transform(up);
        Vector3d level = new Vector3d(-up.z * RIGHTING, 0.0, up.x * RIGHTING);
        Vector3dc spin = handle.getAngularVelocity();
        if (spin != null) {
            level.add(-spin.x() * TIP_DAMP, 0.0, -spin.z() * TIP_DAMP);
        }
        level.mul(immersion * timeStep);

        handle.addLinearAndAngularVelocity(nudge, level);
    }
}
