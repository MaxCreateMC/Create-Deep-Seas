package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import com.maxenonyme.createsubmarine.submarine.network.CameraShakePayload;
import com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3d;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SubmarineSinkingSystem {
    private static final Random RAND = new Random();
    private static final Set<UUID> CRASHED = ConcurrentHashMap.newKeySet();
    private static final int CUT_SPACING_MIN = 4;
    private static final int CUT_SPACING_SPREAD = 3;

    private record ScheduledRemoval(Level parentLevel, BlockPos pos, long tick) {
    }

    private static final Queue<ScheduledRemoval> PENDING = new ConcurrentLinkedQueue<>();

    private record Burst(ServerLevel level, SubLevelAccess sub, Vector3d local, long tick, float size) {
    }

    private static final Queue<Burst> BURSTS = new ConcurrentLinkedQueue<>();
    private static final double RUMBLE_REACH = 48.0;

    public static void clearCrashed() {
        CRASHED.clear();
        PENDING.clear();
        BURSTS.clear();
    }

    public static boolean isCrashing(UUID id) {
        return CRASHED.contains(id);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        long currentTick = event.getServer().getTickCount();
        PENDING.removeIf(removal -> {
            if (currentTick < removal.tick())
                return false;
            removeBlock(removal.parentLevel(), removal.pos());
            return true;
        });
        BURSTS.removeIf(burst -> {
            if (currentTick < burst.tick())
                return false;
            detonate(burst);
            return true;
        });
    }

    private static void detonate(Burst burst) {
        Vector3d at = new Vector3d(burst.local());
        burst.sub().logicalPose().transformPosition(at);
        ServerLevel level = burst.level();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.2, 0.2, 0.2, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, (int) (6 * burst.size()), 1.2, 1.2, 1.2, 0.1);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, (int) (10 * burst.size()), 1.0, 1.0, 1.0,
                0.08);
        level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, at.x, at.y, at.z, (int) (24 * burst.size()), 1.5, 1.5,
                1.5, 0.3);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS,
                3.0f * burst.size(), 0.45f + RAND.nextFloat() * 0.35f);
        level.playSound(null, at.x, at.y, at.z, CreateSubmarine.UNDERWATER_EXPLOSION_SOUND.get(), SoundSource.BLOCKS,
                4.0f * burst.size(), 0.75f + RAND.nextFloat() * 0.4f);
        for (ServerPlayer player : level.players()) {
            double distance = Math.sqrt(player.distanceToSqr(at.x, at.y, at.z));
            if (distance > RUMBLE_REACH || player.isDeadOrDying())
                continue;
            float strength = (float) (1 - distance / RUMBLE_REACH) * burst.size();
            PacketDistributor.sendToPlayer(player, new CameraShakePayload(2f + 5f * strength, 10 + (int) (12 * strength)));
        }
    }

    private static void burst(ServerLevel level, SubLevelAccess sub, double x, double y, double z, long tick,
            float size) {
        BURSTS.offer(new Burst(level, sub, new Vector3d(x, y, z), tick, size));
    }

    public static void onCrashed(UUID id, SubLevelAccess sub, Level parentLevel, SubLevelRegistry.PlotBounds bounds) {
        if (SubmarineConfig.DISABLE_IMPLOSION.get())
            return;
        if (!CRASHED.add(id))
            return;
        if (!(parentLevel instanceof ServerLevel serverLevel))
            return;
        destroyLifeSupport(parentLevel, bounds);
        applySinkingForce(sub);
        Vector3d worldCenter = new Vector3d(
                (bounds.minX() + bounds.maxX()) / 2.0,
                (bounds.minY() + bounds.maxY()) / 2.0,
                (bounds.minZ() + bounds.maxZ()) / 2.0);
        sub.logicalPose().transformPosition(worldCenter);
        BlockPos worldCenterPos = BlockPos.containing(worldCenter.x, worldCenter.y, worldCenter.z);
        serverLevel.playSound(null, worldCenterPos, CreateSubmarine.IMPLOSION_SOUND.get(), SoundSource.BLOCKS, 2.0f,
                1.0f);
        serverLevel.playSound(null, worldCenterPos, CreateSubmarine.UNDERWATER_EXPLOSION_SOUND.get(), SoundSource.BLOCKS,
                8.0f, 0.7f);
        for (int i = 0; i < 80; i++) {
            Vector3d p = new Vector3d(
                    bounds.minX() + RAND.nextDouble() * (bounds.maxX() - bounds.minX()),
                    bounds.minY() + RAND.nextDouble() * (bounds.maxY() - bounds.minY()),
                    bounds.minZ() + RAND.nextDouble() * (bounds.maxZ() - bounds.minZ()));
            sub.logicalPose().transformPosition(p);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 1, 0.5, 0.5, 0.5, 0.05);
        }
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, worldCenter.x, worldCenter.y, worldCenter.z, 5, 3.0,
                3.0, 3.0, 0.3);
        ImplosionSequence.blast(serverLevel, new Vec3(worldCenter.x, worldCenter.y, worldCenter.z),
                ImplosionSequence.aboard(sub, bounds));
        scheduleBursts(serverLevel, sub, bounds);
        scheduleStructuralCuts(serverLevel, sub, bounds);
    }

    private static void scheduleBursts(ServerLevel level, SubLevelAccess sub, SubLevelRegistry.PlotBounds bounds) {
        long base = level.getServer().getTickCount();
        long volume = (long) (bounds.maxX() - bounds.minX() + 1) * (bounds.maxY() - bounds.minY() + 1)
                * (bounds.maxZ() - bounds.minZ() + 1);
        int count = (int) Math.max(8, Math.min(24, volume / 60));
        for (int i = 0; i < count; i++) {
            burst(level, sub,
                    bounds.minX() + RAND.nextDouble() * (bounds.maxX() - bounds.minX() + 1),
                    bounds.minY() + RAND.nextDouble() * (bounds.maxY() - bounds.minY() + 1),
                    bounds.minZ() + RAND.nextDouble() * (bounds.maxZ() - bounds.minZ() + 1),
                    base + 2 + RAND.nextInt(80), 0.6f + RAND.nextFloat() * 0.6f);
        }
    }

    /**
     * Implodes a single compartment (a decompression chamber opened to deep water before it filled)
     * using the same teardown plumbing as a full crash, but bounded to that chamber: its
     * ocean-facing walls cave in over the next ticks, the water it made is cleared, and it is marked
     * compromised so it reads as flooded. The rest of the submarine is left whole and is NOT marked
     * as crashing.
     */
    public static void implodeCompartment(UUID id, SubLevelAccess sub, Level parentLevel,
            CompartmentDetector.Component comp) {
        if (SubmarineConfig.DISABLE_IMPLOSION.get())
            return;
        if (!(parentLevel instanceof ServerLevel serverLevel))
            return;

        long baseTick = serverLevel.getServer().getTickCount();

        // Only the walls that face the ocean give way; interior walls stay so neighbouring
        // compartments keep their seal and the sub does not unzip from here.
        for (BlockPos p : comp.hull()) {
            boolean facesExterior = false;
            for (Direction dir : Direction.values()) {
                if (!CompartmentTracker.isWithinShip(id, p.relative(dir))) {
                    facesExterior = true;
                    break;
                }
            }
            if (facesExterior) {
                PENDING.offer(new ScheduledRemoval(parentLevel, p.immutable(), baseTick + RAND.nextInt(20)));
            }
        }
        // The water this chamber created vanishes as the pocket collapses.
        for (BlockPos p : comp.internal()) {
            if (parentLevel.getFluidState(p).is(FluidTags.WATER)) {
                PENDING.offer(new ScheduledRemoval(parentLevel, p.immutable(), baseTick + RAND.nextInt(10)));
            }
        }
        CompartmentTracker.markCompromised(id, comp.anchor());

        Vector3d center = new Vector3d();
        int n = 0;
        for (BlockPos p : comp.internal()) {
            center.add(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            n++;
        }
        if (n == 0)
            return;
        center.div(n);
        Vector3d worldCenter = new Vector3d(center);
        sub.logicalPose().transformPosition(worldCenter);
        serverLevel.playSound(null, BlockPos.containing(worldCenter.x, worldCenter.y, worldCenter.z),
                CreateSubmarine.IMPLOSION_SOUND.get(), SoundSource.BLOCKS, 2.0f, 1.0f);
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, worldCenter.x, worldCenter.y, worldCenter.z,
                2, 1.0, 1.0, 1.0, 0.1);
        ImplosionSequence.blast(serverLevel, new Vec3(worldCenter.x, worldCenter.y, worldCenter.z),
                ImplosionSequence.inside(sub, comp.internal()));
        burst(serverLevel, sub, center.x, center.y, center.z, baseTick + 3, 1.0f);
        burst(serverLevel, sub, center.x, center.y, center.z, baseTick + 9, 0.7f);
        for (BlockPos p : comp.internal()) {
            Vector3d wp = new Vector3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            sub.logicalPose().transformPosition(wp);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, wp.x, wp.y, wp.z, 1, 0.3, 0.3, 0.3, 0.02);
        }
    }

    private static void destroyLifeSupport(Level level, SubLevelRegistry.PlotBounds bounds) {
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(CreateSubmarine.CREATIVE_OXYGENATOR.get()) || state.is(CreateSubmarine.OXYGENE_DIFFUSER.get())
                            || state.is(CreateSubmarine.BALLAST_TANK.get())) {
                        level.destroyBlock(pos, false);
                    }
                }
            }
        }
    }

    private static void removeBlock(Level parentLevel, BlockPos pos) {
        if (parentLevel.getBlockState(pos).isAir())
            return;
        parentLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    private static void scheduleStructuralCuts(ServerLevel level, SubLevelAccess sub, SubLevelRegistry.PlotBounds bounds) {
        long base = level.getServer().getTickCount();
        int minX = bounds.minX(), maxX = bounds.maxX();
        int minY = bounds.minY(), maxY = bounds.maxY();
        int minZ = bounds.minZ(), maxZ = bounds.maxZ();
        int dx = maxX - minX, dy = maxY - minY, dz = maxZ - minZ;
        double cx = (minX + maxX + 1) / 2.0, cy = (minY + maxY + 1) / 2.0, cz = (minZ + maxZ + 1) / 2.0;

        if (dy >= 4)
            cutY(level, sub, bounds, minY + dy / 2, base + 4, cx, cz);
        if (dy >= 10)
            cutY(level, sub, bounds, minY + dy / 3, base + 18, cx, cz);

        boolean alongX = dx >= dz;
        int from = (alongX ? minX : minZ) + 3;
        int to = (alongX ? maxX : maxZ) - 2;
        long tick = base + 6;
        for (int at = from; at < to; at += CUT_SPACING_MIN + RAND.nextInt(CUT_SPACING_SPREAD)) {
            if (alongX)
                cutX(level, sub, bounds, at, tick, cy, cz);
            else
                cutZ(level, sub, bounds, at, tick, cx, cy);
            tick += 5;
        }
        int across = alongX ? dz : dx;
        if (across >= 8) {
            if (alongX)
                cutZ(level, sub, bounds, minZ + dz / 2, base + 30, cx, cy);
            else
                cutX(level, sub, bounds, minX + dx / 2, base + 30, cy, cz);
        }

        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    PENDING.offer(new ScheduledRemoval(level, new BlockPos(x, y, z),
                            base + 140 + RAND.nextInt(160)));
    }

    private static void cutX(ServerLevel level, SubLevelAccess sub, SubLevelRegistry.PlotBounds plot, int x, long tick,
            double cy, double cz) {
        for (int y = plot.minY(); y <= plot.maxY(); y++)
            for (int z = plot.minZ(); z <= plot.maxZ(); z++)
                PENDING.offer(new ScheduledRemoval(level, new BlockPos(x, y, z), tick));
        burst(level, sub, x + 0.5, cy, cz, tick, 1.0f);
    }

    private static void cutZ(ServerLevel level, SubLevelAccess sub, SubLevelRegistry.PlotBounds plot, int z, long tick,
            double cx, double cy) {
        for (int y = plot.minY(); y <= plot.maxY(); y++)
            for (int x = plot.minX(); x <= plot.maxX(); x++)
                PENDING.offer(new ScheduledRemoval(level, new BlockPos(x, y, z), tick));
        burst(level, sub, cx, cy, z + 0.5, tick, 1.0f);
    }

    private static void cutY(ServerLevel level, SubLevelAccess sub, SubLevelRegistry.PlotBounds plot, int y, long tick,
            double cx, double cz) {
        for (int x = plot.minX(); x <= plot.maxX(); x++)
            for (int z = plot.minZ(); z <= plot.maxZ(); z++)
                PENDING.offer(new ScheduledRemoval(level, new BlockPos(x, y, z), tick));
        burst(level, sub, cx, y + 0.5, cz, tick, 1.2f);
    }

    private static void applySinkingForce(SubLevelAccess sub) {
        Object handle = SablePhysicsHelper.getHandle(sub);
        if (handle == null)
            return;
        double mass = SablePhysicsHelper.readMass(sub);
        double force = Math.max(mass * 18.0, 3000.0);
        SablePhysicsHelper.applyLinearImpulse(handle, new Vector3d(
                (RAND.nextDouble() - 0.5) * force * 0.6,
                -force,
                (RAND.nextDouble() - 0.5) * force * 0.6));
        double spin = mass * 8.0;
        SablePhysicsHelper.applyAngularImpulse(handle, new Vector3d(
                (RAND.nextDouble() - 0.5) * spin,
                (RAND.nextDouble() - 0.5) * spin * 0.3,
                (RAND.nextDouble() - 0.5) * spin));
    }
}
