package com.maxenonyme.createsubmarine.submarine.block.entity;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.system.SubmarineDriverRegistry;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.joml.Vector3d;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.UUID;
import com.maxenonyme.createsubmarine.submarine.system.SubmarineHullManager;
import com.maxenonyme.createsubmarine.submarine.system.SubmarinePressureSystem;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;

@ParametersAreNonnullByDefault
public class OxygeneDiffuserBlockEntity extends BlockEntity implements IHaveGoggleInformation {

    private static final int TANK_CAPACITY  = 1000;
    private static final int CONSUME_EVERY  = 20;
    private static final int CONSUME_AMOUNT = 50;
    private static final int SCAN_BUDGET    = 1500;
    private static final int STARTUP_TICKS  = 100;

    public final FluidTank oxygenTank = new FluidTank(TANK_CAPACITY,
            fluid -> fluid.getFluid().isSame(CreateSubmarine.OXYGEN.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide)
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    };

    private UUID currentSubLevelId = null;
    private boolean subLevelRegistered = false;
    private int activeTicks = 0;

    public OxygeneDiffuserBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSubmarine.OXYGENE_DIFFUSER_BE.get(), pos, state);
    }

    @Override
    @SuppressWarnings("null")
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        LangBuilder mb = CreateLang.translate("generic.unit.millibuckets");
        CreateLang.translate("gui.goggles.fluid_container").forGoggles(tooltip);

        FluidStack dummy = new FluidStack(CreateSubmarine.OXYGEN.get(), 1);
        CreateLang.fluidName(dummy).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);

        CreateLang.builder()
                .add(CreateLang.number(oxygenTank.getFluidAmount()).add(mb).style(ChatFormatting.GOLD))
                .text(ChatFormatting.GRAY, " / ")
                .add(CreateLang.number(oxygenTank.getCapacity()).add(mb).style(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);

        return true;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, OxygeneDiffuserBlockEntity be) {
        if (level == null) return;

        boolean canRun = level.hasNeighborSignal(pos) && be.oxygenTank.getFluidAmount() > 0;

        SubLevelAccess subAccess = SableCompanion.INSTANCE.getContaining(level, pos);

        if (subAccess instanceof SubLevel sub && canRun) {
            be.currentSubLevelId = sub.getUniqueId();
            long gameTick = level.getGameTime();

            boolean owner = SubmarineDriverRegistry.claim(be.currentSubLevelId, pos, SubmarineDriverRegistry.OXYGEN_DIFFUSER, gameTick);
            be.activeTicks++;
            if (!owner) {
                return;
            }
            CompartmentTracker.claimSubmarine(be.currentSubLevelId, gameTick);

            if (!level.isClientSide) {
                if (gameTick % CONSUME_EVERY == 0) {
                    be.oxygenTank.drain(new FluidStack(CreateSubmarine.OXYGEN.get(), CONSUME_AMOUNT),
                            IFluidHandler.FluidAction.EXECUTE);
                    be.setChanged();
                }
            }

            if (be.activeTicks < STARTUP_TICKS) {
                if (!level.isClientSide && level instanceof ServerLevel serverLevel && gameTick % 4 == 0) {
                    serverLevel.sendParticles(ParticleTypes.BUBBLE,
                            pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                            15, 0.2, 0.2, 0.2, 0.05);
                }
                return;
            }

            long lastUpdate = CompartmentTracker.lastUpdateTick(be.currentSubLevelId);
            long sinceUpdate = gameTick - lastUpdate;
            boolean scanDue = lastUpdate == 0
                    || CompartmentTracker.isStructureDirty(be.currentSubLevelId)
                    || sinceUpdate >= 200;
            if (!CompartmentTracker.isScanActive(be.currentSubLevelId) && scanDue && sinceUpdate >= 20) {
                CompartmentTracker.beginScanIfIdle(be.currentSubLevelId, sub);
            }
            if (CompartmentTracker.isScanActive(be.currentSubLevelId)) {
                boolean done = CompartmentTracker.stepScan(be.currentSubLevelId, sub, SCAN_BUDGET, gameTick);
                if (done && !level.isClientSide) {
                    SubmarinePressureSystem.setSealedCompartments(
                        be.currentSubLevelId, CompartmentTracker.getCompartments(be.currentSubLevelId));
                }
            }

            LevelPlot plot = sub.getPlot();
            if (plot != null) {
                BoundingBox3ic bounds = plot.getBoundingBox();
                Vector3d dimensions = CompartmentTracker.getOrComputeDimensions(be.currentSubLevelId, bounds);
                Pose3dc pose = sub.logicalPose();
                if (CompartmentTracker.poseMovedEnough(be.currentSubLevelId, pose, 0.01, 1e-6)) {
                    SubmarineHullManager.updateHull(be.currentSubLevelId, pose.position(), dimensions, pose.orientation());
                    CompartmentTracker.updateAABB(be.currentSubLevelId, pose.position(), dimensions);
                    CompartmentTracker.recordPose(be.currentSubLevelId, pose);
                }
                if (!be.subLevelRegistered) {
                    if (!level.isClientSide) {
                        SubLevelRegistry.register(
                                be.currentSubLevelId, sub, level,
                                new SubLevelRegistry.PlotBounds(
                                        bounds.minX(), bounds.maxX(),
                                        bounds.minY(), bounds.maxY(),
                                        bounds.minZ(), bounds.maxZ()));
                    }
                    be.subLevelRegistered = true;
                }
            }

        } else if (be.currentSubLevelId != null) {

            be.cleanup();
        }
    }

    private void cleanup() {
        if (currentSubLevelId != null) {
            long tick = level != null ? level.getGameTime() : 0L;
            if (SubmarineDriverRegistry.release(currentSubLevelId, worldPosition, tick)) {
                SubmarineHullManager.removeHull(currentSubLevelId);
                if (level != null && !level.isClientSide) {
                    SubLevelRegistry.unregister(currentSubLevelId);
                    SubmarinePressureSystem.clearSubmarine(currentSubLevelId);
                }
                CompartmentTracker.remove(currentSubLevelId);
            }
        }
        currentSubLevelId = null;
        subLevelRegistered = false;
        activeTicks = 0;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        cleanup();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("OxygenTank", oxygenTank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("ActiveTicks", activeTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        oxygenTank.readFromNBT(registries, tag.getCompound("OxygenTank"));
        if (tag.contains("ActiveTicks")) {
            activeTicks = tag.getInt("ActiveTicks");
        }
    }
}