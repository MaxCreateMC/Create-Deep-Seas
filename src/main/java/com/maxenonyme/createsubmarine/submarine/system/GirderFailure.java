package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.FluidTags;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GirderFailure {
    private GirderFailure() {
    }

    private static final int DURATION = 80;
    private static final int FLIGHT = 60;
    private static final int REACH = 16;
    private static final int RADIUS = 4;
    private static final double HOLD = 12.0;
    private static final double JITTER = 0.12;
    private static final double WOBBLE = 3.0;
    private static final double LAUNCH = 16.0;
    private static final double SPIN = 9.0;
    private static final double SHUDDER = 0.04;
    private static final double STEP = 0.05;
    private static final double SINK = 1.2;
    private static final int MAX_IMPACTS = 3;
    private static final Random RAND = new Random();

    private static final class Failure {
        final Level plotLevel;
        final SubLevelAccess sub;
        final GirderSupport.Support support;
        final BlockPos cell;
        final BlockState state;
        final long start;
        ServerSubLevel piece;
        long launched = -1;
        Vector3d last;
        int impacts;

        Failure(Level plotLevel, SubLevelAccess sub, GirderSupport.Support support, long start) {
            this.plotLevel = plotLevel;
            this.sub = sub;
            this.support = support;
            this.cell = BlockPos.of(support.girders()[support.girders().length / 2]);
            this.state = plotLevel.getBlockState(cell);
            this.start = start;
        }
    }

    private static final Map<UUID, Map<Long, Failure>> ACTIVE = new ConcurrentHashMap<>();

    public static void load(ServerLevel ocean, UUID id, SubLevelAccess sub, Level plotLevel, GirderSupport.Support support) {
        ACTIVE.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(support.a(), k -> new Failure(plotLevel, sub, support, ocean.getGameTime()));
    }

    public static boolean protects(UUID id, BlockPos pos) {
        Map<Long, Failure> failures = ACTIVE.get(id);
        if (failures == null)
            return false;
        for (Failure f : failures.values()) {
            if (f.launched >= 0)
                continue;
            if (near(f.support.a(), pos) || near(f.support.b(), pos))
                return true;
        }
        return false;
    }

    private static boolean near(long c, BlockPos pos) {
        double dx = BlockPos.getX(c) - pos.getX(), dy = BlockPos.getY(c) - pos.getY(), dz = BlockPos.getZ(c) - pos.getZ();
        return dx * dx + dy * dy + dz * dz <= RADIUS * RADIUS;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        sink(event);
        if (ACTIVE.isEmpty())
            return;
        Iterator<Map.Entry<UUID, Map<Long, Failure>>> subs = ACTIVE.entrySet().iterator();
        while (subs.hasNext()) {
            Map.Entry<UUID, Map<Long, Failure>> entry = subs.next();
            UUID id = entry.getKey();
            entry.getValue().values().removeIf(f -> step(id, f));
            if (entry.getValue().isEmpty())
                subs.remove();
        }
    }

    private static boolean step(UUID id, Failure f) {
        if (f.sub instanceof SubLevel gone && gone.isRemoved())
            return true;
        Level oceanLevel = f.sub instanceof SubLevel sl ? sl.getLevel() : f.plotLevel;
        if (!(oceanLevel instanceof ServerLevel ocean) || !(f.plotLevel instanceof ServerLevel plot))
            return true;
        long now = ocean.getGameTime();
        if (f.piece == null) {
            if (!GirderSupport.girder(plot.getBlockState(f.cell)))
                return true;
            f.piece = detach(plot, f.cell);
            if (f.piece == null)
                return true;
            SubmarinePressureSystem.recordBroken(id, f.cell, f.state);
        }
        if (f.piece.isRemoved())
            return true;
        Object handle = SablePhysicsHelper.getHandle(f.piece);
        if (handle == null)
            return true;
        if (f.launched >= 0)
            return fly(id, f, ocean, handle, now);

        long age = now - f.start;
        double t = Math.min(1.0, age / (double) DURATION);
        Vector3d home = f.sub.logicalPose().transformPosition(new Vector3d(f.cell.getX() + 0.5, f.cell.getY() + 0.5, f.cell.getZ() + 0.5));
        BlockPos worldPos = BlockPos.containing(home.x, home.y, home.z);
        if (age >= DURATION) {
            launch(id, f, ocean, plot, handle, home, worldPos, now);
            return false;
        }
        hold(f, ocean, handle, home, t);
        int every = Math.max(2, 12 - (int) (t * 10.0));
        if (age % every == 0) {
            ocean.sendParticles(ParticleTypes.SMOKE, home.x, home.y, home.z, 1 + (int) (t * 6.0), 0.2, 0.4, 0.2, 0.01);
            ocean.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, f.state), home.x, home.y, home.z,
                    1 + (int) (t * 4.0), 0.25, 0.4, 0.25, 0.05);
            ocean.playSound(null, worldPos, t < 0.6 ? SoundEvents.IRON_GOLEM_REPAIR : SoundEvents.IRON_GOLEM_DAMAGE,
                    SoundSource.BLOCKS, 0.3f + (float) t * 0.9f, 0.45f + (float) t * 0.5f + RAND.nextFloat() * 0.1f);
            shudder(f.sub, SHUDDER * t);
        }
        return false;
    }

    private static ServerSubLevel detach(ServerLevel plot, BlockPos cell) {
        try {
            ServerSubLevel piece = SubLevelAssemblyHelper.assembleBlocks(plot, cell, Set.of(cell),
                    new BoundingBox3i(cell.getX(), cell.getY(), cell.getZ(), cell.getX(), cell.getY(), cell.getZ()));
            if (piece != null) {
                Blocks.AIR.defaultBlockState().updateNeighbourShapes(plot, cell, Block.UPDATE_ALL);
                plot.blockUpdated(cell, Blocks.AIR);
                Object handle = SablePhysicsHelper.getHandle(piece);
                if (handle != null)
                    SablePhysicsHelper.wakeUp(handle);
            }
            return piece;
        } catch (RuntimeException e) {
            CreateSubmarine.LOGGER.error("Could not tear the girder at {} loose", cell, e);
            return null;
        }
    }

    private static void hold(Failure f, ServerLevel ocean, Object handle, Vector3d home, double t) {
        Vector3d target = new Vector3d(home).add(
                (RAND.nextDouble() - 0.5) * 2.0 * JITTER * t,
                (RAND.nextDouble() - 0.5) * 2.0 * JITTER * t,
                (RAND.nextDouble() - 0.5) * 2.0 * JITTER * t);
        Vector3dc shipVelocity = SablePhysicsHelper.getVelocity(f.sub);
        Vector3d desired = shipVelocity != null ? new Vector3d(shipVelocity) : new Vector3d();
        desired.fma(HOLD, new Vector3d(target).sub(f.piece.logicalPose().position()));
        Vector3dc velocity = SablePhysicsHelper.getVelocity(handle);
        Vector3d change = new Vector3d(desired);
        if (velocity != null)
            change.sub(velocity);
        change.fma(-STEP, DimensionPhysicsData.getGravity(ocean));
        SablePhysicsHelper.addLinearVelocity(handle, change);

        Quaterniond error = new Quaterniond(f.sub.logicalPose().orientation())
                .mul(new Quaterniond(f.piece.logicalPose().orientation()).conjugate());
        if (error.w < 0.0)
            error.set(-error.x, -error.y, -error.z, -error.w);
        AxisAngle4d aa = new AxisAngle4d().set(error);
        Vector3d spin = new Vector3d();
        if (!Double.isNaN(aa.angle) && aa.angle > 1.0e-4)
            spin.set(aa.x, aa.y, aa.z).mul(aa.angle * HOLD);
        spin.add((RAND.nextDouble() - 0.5) * WOBBLE * t, (RAND.nextDouble() - 0.5) * WOBBLE * t, (RAND.nextDouble() - 0.5) * WOBBLE * t);
        Vector3dc omega = SablePhysicsHelper.getAngularVelocity(handle);
        if (omega != null)
            spin.sub(omega);
        SablePhysicsHelper.addAngularVelocity(handle, spin);
    }

    private static void launch(UUID id, Failure f, ServerLevel ocean, ServerLevel plot, Object handle, Vector3d home,
                               BlockPos worldPos, long now) {
        Direction way = aim(plot, f);
        Vector3d dir = new Vector3d(way.getStepX(), way.getStepY(), way.getStepZ());
        Direction.Axis beam = beamAxis(f.support);
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis == way.getAxis() || axis == beam)
                continue;
            double wiggle = (RAND.nextDouble() - 0.5) * 0.5;
            dir.add(axis == Direction.Axis.X ? wiggle : 0.0, axis == Direction.Axis.Y ? wiggle : 0.0, axis == Direction.Axis.Z ? wiggle : 0.0);
        }
        dir.normalize();
        f.sub.logicalPose().orientation().transform(dir);
        Vector3dc shipVelocity = SablePhysicsHelper.getVelocity(f.sub);
        Vector3d velocity = new Vector3d(dir).mul(LAUNCH);
        if (shipVelocity != null)
            velocity.add(shipVelocity);
        Vector3dc current = SablePhysicsHelper.getVelocity(handle);
        Vector3d change = new Vector3d(velocity);
        if (current != null)
            change.sub(current);
        SablePhysicsHelper.addLinearVelocity(handle, change);
        SablePhysicsHelper.addAngularVelocity(handle, new Vector3d(
                (RAND.nextDouble() - 0.5) * SPIN, (RAND.nextDouble() - 0.5) * SPIN, (RAND.nextDouble() - 0.5) * SPIN));

        ocean.playSound(null, worldPos, SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS, 1.4f, 0.6f);
        ocean.playSound(null, worldPos, SoundEvents.IRON_GOLEM_DEATH, SoundSource.BLOCKS, 0.9f, 0.5f);
        ocean.sendParticles(ParticleTypes.LARGE_SMOKE, home.x, home.y, home.z, 24, 0.35, 0.5, 0.35, 0.03);
        ocean.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, f.state), home.x, home.y, home.z, 40, 0.3, 0.4, 0.3, 0.15);
        GirderSupport.collapse(id, f.support);
        SubmarinePressureSystem.invalidate(id);
        shudder(f.sub, SHUDDER * 3.0);
        f.launched = now;
        f.last = velocity;
    }

    private static Direction.Axis beamAxis(GirderSupport.Support support) {
        int dx = Math.abs(BlockPos.getX(support.a()) - BlockPos.getX(support.b()));
        int dy = Math.abs(BlockPos.getY(support.a()) - BlockPos.getY(support.b()));
        return dx > 0 ? Direction.Axis.X : dy > 0 ? Direction.Axis.Y : Direction.Axis.Z;
    }

    private static Direction aim(ServerLevel plot, Failure f) {
        Direction.Axis beam = beamAxis(f.support);
        Direction best = null;
        int bestFree = -1;
        boolean bestHits = false;
        for (Direction d : Direction.values()) {
            if (d.getAxis() == beam)
                continue;
            int free = 0;
            boolean hits = false;
            for (int k = 1; k <= REACH; k++) {
                BlockState s = plot.getBlockState(f.cell.relative(d, k));
                if (!s.isAir() && !CompartmentDetector.isPermeable(s)) {
                    hits = true;
                    break;
                }
                free++;
            }
            if (free == 0)
                continue;
            boolean better = hits && !bestHits || hits == bestHits && (free > bestFree || free == bestFree && RAND.nextBoolean());
            if (best == null || better) {
                best = d;
                bestFree = free;
                bestHits = hits;
            }
        }
        return best != null ? best : Direction.UP;
    }

    private static boolean fly(UUID id, Failure f, ServerLevel ocean, Object handle, long now) {
        if (now - f.launched > FLIGHT || f.impacts >= MAX_IMPACTS)
            return true;
        Vector3dc v = SablePhysicsHelper.getVelocity(handle);
        if (v == null)
            return true;
        Vector3d velocity = new Vector3d(v);
        Vector3d before = f.last;
        f.last = velocity;
        if (before == null || before.length() < 2.0)
            return false;
        boolean bounced = velocity.dot(before) < 0.0 || velocity.length() < before.length() * 0.55;
        if (!bounced)
            return false;
        f.impacts++;
        Vector3d at = new Vector3d(f.piece.logicalPose().position());
        BlockPos worldPos = BlockPos.containing(at.x, at.y, at.z);
        ocean.playSound(null, worldPos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, (float) Math.min(1.2, before.length() / 12.0), 0.8f + RAND.nextFloat() * 0.3f);
        ocean.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, f.state), at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.1);
        Vector3d local = f.sub.logicalPose().transformPositionInverse(new Vector3d(at));
        Vector3d heading = f.sub.logicalPose().orientation().transformInverse(new Vector3d(before).normalize());
        for (double reach = 0.6; reach <= 1.6; reach += 0.5) {
            BlockPos hit = BlockPos.containing(local.x + heading.x * reach, local.y + heading.y * reach, local.z + heading.z * reach);
            BlockState s = f.plotLevel.getBlockState(hit);
            if (s.isAir() || CompartmentDetector.isPermeable(s) || GirderSupport.girder(s))
                continue;
            if (outer(id, hit))
                SubmarinePressureSystem.impact(id, hit, ocean);
            break;
        }
        return false;
    }

    private static boolean outer(UUID id, BlockPos pos) {
        for (Direction d : Direction.values())
            if (!CompartmentTracker.isWithinShip(id, pos.relative(d)))
                return true;
        return false;
    }

    private static void sink(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null)
                continue;
            for (SubLevel piece : container.getAllSubLevels()) {
                if (piece.isRemoved() || piece.getPlot() == null || held(piece) || !loose(level, piece))
                    continue;
                Vector3dc at = piece.logicalPose().position();
                if (!level.getFluidState(BlockPos.containing(at.x(), at.y(), at.z())).is(FluidTags.WATER) || !indoors(at))
                    continue;
                Object handle = SablePhysicsHelper.getHandle(piece);
                if (handle == null)
                    continue;
                Vector3d change = new Vector3d(DimensionPhysicsData.getGravity(level)).mul(STEP * SINK);
                Vector3dc v = SablePhysicsHelper.getVelocity(handle);
                if (v != null && v.y() > 0.0)
                    change.y -= v.y() * 0.2;
                SablePhysicsHelper.addLinearVelocity(handle, change);
            }
        }
    }

    private static boolean held(SubLevel piece) {
        for (Map<Long, Failure> failures : ACTIVE.values())
            for (Failure f : failures.values())
                if (f.piece == piece && f.launched < 0)
                    return true;
        return false;
    }

    public static boolean loose(Level level, SubLevel piece) {
        BoundingBox3ic b = piece.getPlot().getBoundingBox();
        if (b.minX() != b.maxX() || b.minY() != b.maxY() || b.minZ() != b.maxZ())
            return false;
        return GirderSupport.girder(level.getBlockState(new BlockPos(b.minX(), b.minY(), b.minZ())));
    }

    private static boolean indoors(Vector3dc at) {
        for (Map.Entry<UUID, SubLevelAccess> e : SubLevelRegistry.getAll().entrySet()) {
            UUID id = e.getKey();
            if (SubmarinePressureSystem.isBreached(id))
                continue;
            Vector3d local = e.getValue().logicalPose().transformPositionInverse(new Vector3d(at));
            if (CompartmentTracker.isWithinShip(id, BlockPos.containing(local.x, local.y, local.z)))
                return true;
        }
        return false;
    }

    private static void shudder(SubLevelAccess sub, double strength) {
        Object handle = SablePhysicsHelper.getHandle(sub);
        if (handle == null || strength <= 0.0)
            return;
        SablePhysicsHelper.addAngularVelocity(handle, new Vector3d(
                (RAND.nextDouble() - 0.5) * strength, (RAND.nextDouble() - 0.5) * strength, (RAND.nextDouble() - 0.5) * strength));
    }

    public static void forget(UUID id) {
        ACTIVE.remove(id);
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
