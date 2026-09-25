package com.maxenonyme.createsubmarine.submarine.block.entity;

import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import dev.ryanhcode.sable.api.block.propeller.BlockEntitySubLevelPropellerActor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class WaterThrusterBlockEntity extends BlockEntity implements BlockEntitySubLevelPropellerActor, BlockEntityPropeller {
    public static final int WATER_CAPACITY = 1000;

    public final FluidTank waterTank = new FluidTank(WATER_CAPACITY, fluid -> fluid.getFluid().is(net.minecraft.tags.FluidTags.WATER)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    };

    private int thrustMagnitude = 0;

    public WaterThrusterBlockEntity(BlockPos pos, BlockState state) {
        super(com.maxenonyme.createsubmarine.CreateSubmarine.WATER_THRUSTER_BE.get(), pos, state);
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, WaterThrusterBlockEntity be) {
        if (level.isClientSide) return;

        int drained = be.waterTank.drain(50, IFluidHandler.FluidAction.EXECUTE).getAmount();
        boolean changed = be.thrustMagnitude != drained;
        be.thrustMagnitude = drained;

        if (be.thrustMagnitude > 0 && level.getGameTime() % 4 == 0) {
            be.spawnWaterParticles();
        }
        if (changed) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) loadAdditional(tag, registries);
    }

    @Override
    public double getThrust() {
        return thrustMagnitude * 8.0 * com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.WATER_THRUSTER_POWER_MULTIPLIER.get();
    }

    @Override
    public double getAirflow() {
        return thrustMagnitude * 0.4 * com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.WATER_THRUSTER_POWER_MULTIPLIER.get();
    }

    @Override
    public boolean isActive() {
        return thrustMagnitude > 0;
    }

    @Override
    public Direction getBlockDirection() {
        return getBlockState().getValue(BlockStateProperties.FACING);
    }

    @Override
    public BlockEntityPropeller getPropeller() {
        return this;
    }

    private void spawnWaterParticles() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel))
            return;

        Direction facing = getBlockState().getValue(BlockStateProperties.FACING);

        double ox = worldPosition.getX() + 0.5 + facing.getStepX() * 0.7;
        double oy = worldPosition.getY() + 0.5 + facing.getStepY() * 0.7;
        double oz = worldPosition.getZ() + 0.5 + facing.getStepZ() * 0.7;

        double vx = facing.getStepX() * 1.5;
        double vy = facing.getStepY() * 1.5;
        double vz = facing.getStepZ() * 1.5;

        for (int i = 0; i < 2; i++) {
            double rx = (level.random.nextDouble() - 0.5) * 0.05;
            double ry = (level.random.nextDouble() - 0.5) * 0.05;
            double rz = (level.random.nextDouble() - 0.5) * 0.05;

            serverLevel.sendParticles(ParticleTypes.BUBBLE,
                    ox + rx, oy + ry, oz + rz,
                    0,
                    vx + rx, vy + ry, vz + rz,
                    1.5
            );
        }

        serverLevel.sendParticles(ParticleTypes.SPLASH,
                ox, oy, oz,
                1,
                0.1, 0.1, 0.1,
                0.8
        );

        if (level.random.nextFloat() < 0.1f) {
             serverLevel.sendParticles(ParticleTypes.GUST,
                ox, oy, oz,
                1, 0, 0, 0, 0.2
            );
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("WaterTank", waterTank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("Thrust", thrustMagnitude);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        waterTank.readFromNBT(registries, tag.getCompound("WaterTank"));
        thrustMagnitude = tag.getInt("Thrust");
    }
}