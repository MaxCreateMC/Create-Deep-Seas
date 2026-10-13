package com.maxenonyme.createsubmarine.submarine.block.entity;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.system.SubmarinePressureSystem;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pufferfish;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import java.util.List;
import java.util.UUID;

public class BarometerBlockEntity extends BlockEntity implements IHaveHoveringInformation {
    private Pufferfish pufferfish;
    private boolean pufferfishFailed = false;
    private int cachedWeakestPressure = -1;
    private long lastHullScan = -1;

    private Component customName;
    public int syncedDepth = 0;
    public int syncedWeakest = -1;
    private int tickCount = 0;

    public BarometerBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSubmarine.BAROMETER_BE.get(), pos, state);
    }

    public Component getCustomName() {
        return this.customName;
    }

    public void setCustomName(Component name) {
        this.customName = name;
        if (this.pufferfish != null) {
            this.pufferfish.setCustomName(name);
            this.pufferfish.setCustomNameVisible(name != null);
        }
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public Pufferfish getPufferfish() {
        if (this.pufferfish == null && this.level != null && !this.pufferfishFailed) {
            try {
                this.pufferfish = new Pufferfish(EntityType.PUFFERFISH, this.level);
                this.pufferfish.setNoAi(true);
                this.pufferfish.setSilent(true);
                this.pufferfish.setInvulnerable(true);
                if (this.customName != null) {
                    this.pufferfish.setCustomName(this.customName);
                    this.pufferfish.setCustomNameVisible(true);
                }
            } catch (RuntimeException e) {
                this.pufferfishFailed = true;
                this.pufferfish = null;
            }
        }

        if (this.pufferfish != null) {
            int depth = this.syncedDepth;
            int weakest = this.syncedWeakest;
            int state = 0;

            if (depth > 0 && weakest != -1) {
                if (depth > weakest) {
                    state = 3;
                } else if (depth >= weakest * 0.80) {
                    state = 2;
                } else {
                    state = 1;
                }
            }

            if (state == 0) {
                this.pufferfish.setPuffState(0);
                this.pufferfish.hurtTime = 0;
            } else if (state == 1) {
                this.pufferfish.setPuffState(1);
                this.pufferfish.hurtTime = 0;
            } else if (state == 2) {
                this.pufferfish.setPuffState(2);
                this.pufferfish.hurtTime = 0;
            } else if (state == 3) {
                this.pufferfish.setPuffState(2);
                this.pufferfish.hurtTime = 15;
            }
        }

        return this.pufferfish;
    }

    private int getWeakestHullPressure(UUID subId) {
        long time = level.getGameTime();
        if (cachedWeakestPressure != -1 && time - lastHullScan < 40) {
            return cachedWeakestPressure;
        }
        cachedWeakestPressure = SubmarinePressureSystem
            .getWeakestHullDepth(subId, level);
        lastHullScan = time;
        return cachedWeakestPressure;
    }

    public void tick() {
        if (level == null || level.isClientSide)
            return;

        if (tickCount++ % 10 == 0) {
            UUID subId = SubLevelRegistry.findUUID(level, getBlockPos());
            int newDepth = 0;
            int newWeakest = -1;

            if (subId != null) {
                newDepth = SubmarinePressureSystem.getCachedDepth(subId);
                newWeakest = getWeakestHullPressure(subId);
            }

            if (newDepth != syncedDepth || newWeakest != syncedWeakest) {
                this.syncedDepth = newDepth;
                this.syncedWeakest = newWeakest;
                setChanged();
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("SyncedDepth", this.syncedDepth);
        tag.putInt("SyncedWeakest", this.syncedWeakest);
        if (this.customName != null) {
            tag.putString("CustomName", Component.Serializer.toJson(this.customName, registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("SyncedDepth"))
            this.syncedDepth = tag.getInt("SyncedDepth");
        if (tag.contains("SyncedWeakest"))
            this.syncedWeakest = tag.getInt("SyncedWeakest");
        if (tag.contains("CustomName", 8)) {
            try {
                this.customName = Component.Serializer.fromJson(tag.getString("CustomName"), registries);
                if (this.pufferfish != null) {
                    this.pufferfish.setCustomName(this.customName);
                    this.pufferfish.setCustomNameVisible(true);
                }
            } catch (RuntimeException e) {
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ")
            .append(Component.translatable("create_submarine.gui.goggles.barometer.title").withStyle(ChatFormatting.GRAY)));

        int depth = this.syncedDepth;
        int weakest = this.syncedWeakest;

        if (depth <= 0 || weakest == -1) {
            tooltip.add(Component.literal("    ")
                .append(Component.literal("\u25CB ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable("create_submarine.gui.goggles.barometer.no_pressure").withStyle(ChatFormatting.GRAY)));
        } else if (depth > weakest) {
            tooltip.add(Component.literal("    ")
                .append(Component.literal("\u26A0 ").withStyle(ChatFormatting.DARK_RED))
                .append(Component.translatable("create_submarine.gui.goggles.barometer.critical").withStyle(ChatFormatting.RED).withStyle(ChatFormatting.BOLD)));
        } else if (depth >= weakest * 0.80) {
            tooltip.add(Component.literal("    ")
                .append(Component.literal("\u26A0 ").withStyle(ChatFormatting.GOLD))
                .append(Component.translatable("create_submarine.gui.goggles.barometer.warning").withStyle(ChatFormatting.YELLOW)));
        } else {
            tooltip.add(Component.literal("    ")
                .append(Component.literal("\u2714 ").withStyle(ChatFormatting.DARK_GREEN))
                .append(Component.translatable("create_submarine.gui.goggles.barometer.acceptable").withStyle(ChatFormatting.GREEN)));
        }

        if (weakest != -1) {
            tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_submarine.gui.goggles.barometer.depth", depth, weakest).withStyle(ChatFormatting.AQUA)));
        }

        return true;
    }
}
