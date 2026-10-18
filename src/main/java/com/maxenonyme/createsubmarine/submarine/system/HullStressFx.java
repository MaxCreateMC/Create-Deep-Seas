package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import com.simibubi.create.AllParticleTypes;
import com.simibubi.create.content.fluids.particle.FluidParticleData;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;

public class HullStressFx {
    private static final float[] JET_SPEED = { 0f, 0.18f, 0.32f, 0.5f, 0.75f };
    private static final int[] JET_COUNT = { 0, 1, 2, 4, 7 };
    private static final float[] POP_CHANCE = { 0f, 0.004f, 0.01f, 0.025f, 0.08f };
    private static final double SPREAD = 0.18;

    private static FluidParticleData water;

    public static void onServerTick(ServerTickEvent.Post event) {
        Map<UUID, Map<BlockPos, Integer>> all = SubmarinePressureSystem.cracks();
        if (all.isEmpty())
            return;
        boolean spray = event.getServer().getTickCount() % 2 == 0;
        for (Map.Entry<UUID, Map<BlockPos, Integer>> entry : all.entrySet()) {
            UUID id = entry.getKey();
            SubLevelAccess sub = SubLevelRegistry.getAll().get(id);
            Level plot = SubLevelRegistry.getLevel(id);
            if (sub == null || !(plot instanceof ServerLevel)
                    || !((sub instanceof SubLevel sl ? sl.getLevel() : plot) instanceof ServerLevel ocean))
                continue;
            for (Map.Entry<BlockPos, Integer> crack : entry.getValue().entrySet()) {
                int level = Math.min(JET_SPEED.length - 1, crack.getValue());
                BlockPos pos = crack.getKey();
                Direction inward = inward(id, plot, pos);
                if (inward == null)
                    continue;
                if (spray)
                    jet(ocean, sub, pos, inward, level);
                if (ocean.random.nextFloat() < POP_CHANCE[level])
                    pop(ocean, sub, pos, inward, plot.getBlockState(pos), 1f);
            }
        }
    }

    public static void popAt(ServerLevel ocean, UUID id, SubLevelAccess sub, Level plot, BlockPos pos, float strength) {
        Direction inward = inward(id, plot, pos);
        if (inward != null)
            pop(ocean, sub, pos, inward, plot.getBlockState(pos), strength);
    }

    private static Direction inward(UUID id, Level plot, BlockPos pos) {
        if (plot.getBlockState(pos).isAir())
            return null;
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            BlockState state = plot.getBlockState(next);
            if ((state.isAir() || CompartmentDetector.isPermeable(state)) && CompartmentTracker.isWithinShip(id, next))
                return direction;
        }
        return null;
    }

    private static Vector3d face(SubLevelAccess sub, BlockPos pos, Direction inward, RandomSource random) {
        Vector3d local = new Vector3d(
                pos.getX() + 0.5 + inward.getStepX() * 0.52 + (inward.getAxis() == Direction.Axis.X ? 0 : (random.nextDouble() - 0.5) * 0.6),
                pos.getY() + 0.5 + inward.getStepY() * 0.52 + (inward.getAxis() == Direction.Axis.Y ? 0 : (random.nextDouble() - 0.5) * 0.6),
                pos.getZ() + 0.5 + inward.getStepZ() * 0.52 + (inward.getAxis() == Direction.Axis.Z ? 0 : (random.nextDouble() - 0.5) * 0.6));
        return sub.logicalPose().transformPosition(local);
    }

    private static Vector3d heading(SubLevelAccess sub, Direction inward, RandomSource random, double spread) {
        Vector3d dir = new Vector3d(
                inward.getStepX() + (random.nextDouble() - 0.5) * 2 * spread,
                inward.getStepY() + (random.nextDouble() - 0.5) * 2 * spread,
                inward.getStepZ() + (random.nextDouble() - 0.5) * 2 * spread).normalize();
        return sub.logicalPose().orientation().transform(dir);
    }

    private static void jet(ServerLevel ocean, SubLevelAccess sub, BlockPos pos, Direction inward, int level) {
        if (water == null)
            water = new FluidParticleData(AllParticleTypes.FLUID_PARTICLE.get(), new FluidStack(Fluids.WATER, 1000));
        RandomSource random = ocean.random;
        for (int i = 0; i < JET_COUNT[level]; i++) {
            Vector3d at = face(sub, pos, inward, random);
            Vector3d dir = heading(sub, inward, random, SPREAD);
            double speed = JET_SPEED[level] * (0.8 + random.nextDouble() * 0.4);
            ocean.sendParticles(water, at.x, at.y, at.z, 0, dir.x, dir.y, dir.z, speed);
        }
    }

    private static void pop(ServerLevel ocean, SubLevelAccess sub, BlockPos pos, Direction inward, BlockState state, float strength) {
        RandomSource random = ocean.random;
        Vector3d at = face(sub, pos, inward, random);
        int sparks = 3 + (int) (strength * 5);
        for (int i = 0; i < sparks; i++) {
            Vector3d dir = heading(sub, inward, random, 0.7);
            ocean.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 0, dir.x, dir.y, dir.z, 0.25 + random.nextDouble() * 0.35);
        }
    }
}
