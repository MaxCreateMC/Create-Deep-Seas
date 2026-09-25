package com.maxenonyme.createsubmarine.submarine.block.entity;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.ElectrolyzerBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.item.ItemHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;

public class ElectrolyzerBlockEntity extends KineticBlockEntity {
    private static final int FE_PER_RPM = 4;
    private static final int MAX_GENERATION = 1000;
    private static final ResourceLocation ELECTRON_TUBE = ResourceLocation.fromNamespaceAndPath("create", "electron_tube");

    public final FluidTank waterTank = new FluidTank(4000, fluid -> fluid.getFluid().is(FluidTags.WATER));
    public final FluidTank oxygenTank = new FluidTank(4000,
            fluid -> fluid.getFluid().isSame(CreateSubmarine.OXYGEN.get()));
    public final EnergyStorage energyStorage = new EnergyStorage(10000, 1000, 1000);

    public final ItemStackHandler module = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return isElectronTube(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public final IFluidHandler combinedFluidHandler = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return tank == 0 ? waterTank.getFluid() : oxygenTank.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0 ? waterTank.getCapacity() : oxygenTank.getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return tank == 0 ? waterTank.isFluidValid(stack) : oxygenTank.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.getFluid().is(FluidTags.WATER))
                return waterTank.fill(resource, action);
            return 0;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.getFluid().isSame(CreateSubmarine.OXYGEN.get()))
                return oxygenTank.drain(resource, action);
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return oxygenTank.drain(maxDrain, action);
        }
    };

    private boolean isEnabled = false;
    private int generated;

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energyStorage.getEnergyStored();
                case 1 -> energyStorage.getMaxEnergyStored();
                case 2 -> waterTank.getFluidAmount();
                case 3 -> waterTank.getCapacity();
                case 4 -> oxygenTank.getFluidAmount();
                case 5 -> oxygenTank.getCapacity();
                case 6 -> isEnabled ? 1 : 0;
                case 7 -> generated;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 6 -> isEnabled = (value == 1);
                case 7 -> generated = value;
            }
        }

        @Override
        public int getCount() {
            return 8;
        }
    };

    public ElectrolyzerBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSubmarine.ELECTROLYZER_BE.get(), pos, state);
    }

    public static boolean isElectronTube(ItemStack stack) {
        return ELECTRON_TUBE.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public boolean hasAlternator() {
        return !module.getStackInSlot(0).isEmpty();
    }

    @Override
    public void tick() {
        super.tick();
        BlockState state = getBlockState();
        if (level.isClientSide) {
            if (state.getValue(ElectrolyzerBlock.POWERED) && level.random.nextInt(5) == 0) {
                double x = worldPosition.getX() + 0.4 + level.random.nextDouble() * 0.2;
                double z = worldPosition.getZ() + 0.4 + level.random.nextDouble() * 0.2;
                double y = worldPosition.getY() + 1.75;
                level.addParticle(ParticleTypes.BUBBLE_POP, x, y, z, 0, 0.1, 0);
            }
            return;
        }

        if (hasAlternator() != state.getValue(ElectrolyzerBlock.ALTERNATOR)) {
            switchToBlockState(level, worldPosition, state.setValue(ElectrolyzerBlock.ALTERNATOR, hasAlternator()));
            return;
        }

        generated = 0;
        if (hasAlternator() && getSpeed() != 0) {
            int offer = Math.min(MAX_GENERATION, (int) (Math.abs(getSpeed()) * FE_PER_RPM));
            generated = energyStorage.receiveEnergy(offer, false);
        }

        boolean canWork = isEnabled &&
                energyStorage.getEnergyStored() >= 250 &&
                waterTank.getFluidAmount() >= 10 &&
                oxygenTank.getFluidAmount() + 10 <= oxygenTank.getCapacity();

        if (canWork) {
            energyStorage.extractEnergy(250, false);
            waterTank.drain(10, IFluidHandler.FluidAction.EXECUTE);
            oxygenTank.fill(new FluidStack(CreateSubmarine.OXYGEN.get(), 10), IFluidHandler.FluidAction.EXECUTE);
            setChanged();
            sendData();

            if (!state.getValue(ElectrolyzerBlock.POWERED))
                level.setBlock(worldPosition, state.setValue(ElectrolyzerBlock.POWERED, true), 3);
        } else {
            if (generated > 0)
                setChanged();
            if (state.getValue(ElectrolyzerBlock.POWERED))
                level.setBlock(worldPosition, state.setValue(ElectrolyzerBlock.POWERED, false), 3);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        ItemHelper.dropContents(level, worldPosition, module);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("WaterTank", waterTank.writeToNBT(registries, new CompoundTag()));
        tag.put("OxygenTank", oxygenTank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putBoolean("Enabled", isEnabled);
        tag.put("Module", module.serializeNBT(registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        waterTank.readFromNBT(registries, tag.getCompound("WaterTank"));
        oxygenTank.readFromNBT(registries, tag.getCompound("OxygenTank"));
        int stored = tag.getInt("Energy");
        energyStorage.extractEnergy(energyStorage.getEnergyStored(), false);
        energyStorage.receiveEnergy(stored, false);
        isEnabled = tag.getBoolean("Enabled");
        if (tag.contains("Module"))
            module.deserializeNBT(registries, tag.getCompound("Module"));
    }

    public void toggleEnabled() {
        isEnabled = !isEnabled;
        setChanged();
    }
}
