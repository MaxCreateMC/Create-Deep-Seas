package com.maxenonyme.createsubmarine.submarine.block.entity;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.BallastVentBlock;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import dev.ryanhcode.sable.sublevel.SubLevel;

public class BallastVentBlockEntity extends BlockEntity {
    private static final int OCEAN = 1_000_000;

    private final IFluidHandler sea = new SeaHandler();
    private boolean submerged;
    private int scanCooldown = 0;

    public BallastVentBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSubmarine.BALLAST_VENT_BE.get(), pos, state);
    }

    public void tick() {
        if (level == null || level.isClientSide)
            return;
        if (--scanCooldown <= 0) {
            submerged = isAnyOpenFaceSubmerged();
            scanCooldown = 20;
        }
    }

    public IFluidHandler getFluidHandlerForSide(Direction side) {
        return sea;
    }

    private class SeaHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return submerged ? new FluidStack(Fluids.WATER, OCEAN) : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return OCEAN;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return stack.getFluid().is(FluidTags.WATER);
        }

        @Override
        public int fill(@NotNull FluidStack resource, FluidAction action) {
            if (!submerged || resource.isEmpty() || !isFluidValid(0, resource))
                return 0;
            if (action.execute())
                bubble(false);
            return resource.getAmount();
        }

        @Override
        public @NotNull FluidStack drain(@NotNull FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !isFluidValid(0, resource))
                return FluidStack.EMPTY;
            return drain(resource.getAmount(), action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            if (!submerged || maxDrain <= 0)
                return FluidStack.EMPTY;
            if (action.execute())
                bubble(true);
            return new FluidStack(Fluids.WATER, maxDrain);
        }
    }

    private List<Direction> getOpenFaces() {
        BlockState state = getBlockState();
        List<Direction> faces = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            if (!state.getValue(BallastVentBlock.propertyForDirection(dir)))
                faces.add(dir);
        }
        return faces;
    }

    private boolean isAnyOpenFaceSubmerged() {
        for (Direction dir : getOpenFaces()) {
            if (isSubmerged(level, worldPosition.relative(dir)))
                return true;
        }
        return false;
    }

    private void bubble(boolean intake) {
        if (!(level instanceof ServerLevel serverLevel) || level.getGameTime() % 4 != 0)
            return;
        for (Direction dir : getOpenFaces()) {
            if (!isSubmerged(level, worldPosition.relative(dir)))
                continue;
            double fx = worldPosition.getX() + 0.5 + dir.getStepX() * 0.6;
            double fy = worldPosition.getY() + 0.5 + dir.getStepY() * 0.6;
            double fz = worldPosition.getZ() + 0.5 + dir.getStepZ() * 0.6;
            if (intake) {
                serverLevel.sendParticles(ParticleTypes.BUBBLE, fx, fy, fz, 5, 0.9, 0.9, 0.9, 0.5);
            } else {
                serverLevel.sendParticles(ParticleTypes.SPLASH, fx, fy, fz, 5, 0.9, 0.9, 0.9, 0.5);
                serverLevel.sendParticles(ParticleTypes.BUBBLE, fx, fy, fz, 2, 0.9, 0.9, 0.9, 0.3);
            }
        }
    }

    private boolean isSubmerged(Level level, BlockPos pos) {
        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, pos);
        if (sub == null)
            return level.getFluidState(pos).is(FluidTags.WATER);
        Vector3d worldPos = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        sub.logicalPose().transformPosition(worldPos);
        BlockPos wPos = BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
        Level parentLevel = SubLevelRegistry.getLevel(sub.getUniqueId());
        if (parentLevel == null && sub instanceof SubLevel sl) {
            parentLevel = sl.getLevel();
        }
        if (parentLevel == null)
            return false;
        return CompartmentTracker.realFluidState(parentLevel, wPos)
                .is(FluidTags.WATER);
    }
}
