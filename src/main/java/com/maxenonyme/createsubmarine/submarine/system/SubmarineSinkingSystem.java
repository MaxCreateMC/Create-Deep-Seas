package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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

    private record ScheduledRemoval(Level parentLevel, BlockPos pos, long tick) {
    }

    private static final Queue<ScheduledRemoval> PENDING = new ConcurrentLinkedQueue<>();

    public static void clearCrashed() {
        CRASHED.clear();
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
    }

    public static void onCrashed(UUID id, SubLevelAccess sub, Level parentLevel, SubLevelRegistry.PlotBounds bounds) {
        if (com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.DISABLE_IMPLOSION.get()) return;
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
        scheduleStructuralCuts(parentLevel, bounds);
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
        if (com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.DISABLE_IMPLOSION.get()) return;
        if (!(parentLevel instanceof ServerLevel serverLevel)) return;

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
            if (parentLevel.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)) {
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
        if (n == 0) return;
        center.div(n);
        Vector3d worldCenter = new Vector3d(center);
        sub.logicalPose().transformPosition(worldCenter);
        serverLevel.playSound(null, BlockPos.containing(worldCenter.x, worldCenter.y, worldCenter.z),
                CreateSubmarine.IMPLOSION_SOUND.get(), SoundSource.BLOCKS, 2.0f, 1.0f);
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, worldCenter.x, worldCenter.y, worldCenter.z,
                2, 1.0, 1.0, 1.0, 0.1);
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
                    net.minecraft.world.level.block.state.BlockState s = level.getBlockState(pos);
                    if (s.is(CreateSubmarine.CREATIVE_OXYGENATOR.get()) || s.is(CreateSubmarine.OXYGENE_DIFFUSER.get())) {
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

    private static void scheduleStructuralCuts(Level parentLevel, SubLevelRegistry.PlotBounds bounds) {
        long baseTick = ((ServerLevel) parentLevel).getServer().getTickCount();
        int dy = bounds.maxY() - bounds.minY();
        int dx = bounds.maxX() - bounds.minX();
        int dz = bounds.maxZ() - bounds.minZ();
        if (dy >= 2) {
            int y = bounds.minY() + dy / 2;
            long cutTick = baseTick + 5;
            for (int x = bounds.minX(); x <= bounds.maxX(); x++)
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++)
                    PENDING.offer(new ScheduledRemoval(parentLevel, new BlockPos(x, y, z), cutTick));
        }
        long cutTick2 = baseTick + 35;
        if (dx >= dz && dx >= 2) {
            int x = bounds.minX() + dx / 2;
            for (int y2 = bounds.minY(); y2 <= bounds.maxY(); y2++)
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++)
                    PENDING.offer(new ScheduledRemoval(parentLevel, new BlockPos(x, y2, z), cutTick2));
        } else if (dz >= 2) {
            int z = bounds.minZ() + dz / 2;
            for (int y2 = bounds.minY(); y2 <= bounds.maxY(); y2++)
                for (int x2 = bounds.minX(); x2 <= bounds.maxX(); x2++)
                    PENDING.offer(new ScheduledRemoval(parentLevel, new BlockPos(x2, y2, z), cutTick2));
        }
        for (int x = bounds.minX(); x <= bounds.maxX(); x++)
            for (int y = bounds.minY(); y <= bounds.maxY(); y++)
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++)
                    PENDING.offer(new ScheduledRemoval(parentLevel, new BlockPos(x, y, z),
                            baseTick + 60 + RAND.nextInt(120)));
    }

    private static void applySinkingForce(SubLevelAccess sub) {
        Object handle = com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper.getHandle(sub);
        if (handle == null) return;
        double mass = com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper.readMass(sub);
        double force = Math.max(mass * 18.0, 3000.0);
        com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper.applyLinearImpulse(handle, new Vector3d(
                (RAND.nextDouble() - 0.5) * force * 0.6,
                -force,
                (RAND.nextDouble() - 0.5) * force * 0.6));
        double spin = mass * 8.0;
        com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper.applyAngularImpulse(handle, new Vector3d(
                (RAND.nextDouble() - 0.5) * spin,
                (RAND.nextDouble() - 0.5) * spin * 0.3,
                (RAND.nextDouble() - 0.5) * spin));
    }
}
