package com.maxenonyme.createsubmarine.submarine.block.entity;

import com.maxenonyme.createsubmarine.submarine.block.WaterThrusterBlock;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WaterThrusterBlockEntity extends BlockEntity implements BlockEntitySubLevelPropellerActor, BlockEntityPropeller {
    public static final int WATER_CAPACITY = 1000;
    /** multiblock footsizes that this block can form, in preference order */
    private static final int[] MULTIBLOCK_WIDTHS = {3, 2};

    public final FluidTank waterTank = new FluidTank(WATER_CAPACITY, fluid -> fluid.getFluid().isSame(Fluids.WATER)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    };

    private int thrustMagnitude = 0;
    /** width of the formed multiblock (0 = not formed, otherwise 2 or 3) */
    private int multiblockWidth = 0;
    /** corner block of the formed multiblock that renders its model (null if not formed) */
    @Nullable
    private BlockPos multiblockCorner;
    private boolean formationDirty = true;

    public WaterThrusterBlockEntity(BlockPos pos, BlockState state) {
        super(com.maxenonyme.createsubmarine.CreateSubmarine.WATER_THRUSTER_BE.get(), pos, state);
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, WaterThrusterBlockEntity be) {
        if (level.isClientSide) return;

        int drained = be.waterTank.drain(50, IFluidHandler.FluidAction.EXECUTE).getAmount();
        boolean changed = be.thrustMagnitude != drained;
        be.thrustMagnitude = drained;

        if (be.formationDirty || (level.getGameTime() % 20 == 0)) {
            be.formationDirty = false;
            be.refreshFormation();
        }

        if (be.thrustMagnitude > 0 && level.getGameTime() % 4 == 0) {
            be.spawnWaterParticles(be.multiblockWidth > 0);
        }
        if (changed) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    public void markFormationDirty() {
        this.formationDirty = true;
    }

    /** 0 = not part of a formed multiblock, otherwise the formed edge length (2 or 3) */
    public int getMultiblockWidth() {
        if (multiblockWidth > 0) return multiblockWidth;
        if (level == null || level.isClientSide()) return clientMultiblockWidth();
        return multiblockWidth;
    }

    /**
     * True on every member of a formed multiblock (renders the empty blockstate model;
     * the corner draws the actual model via {@link MultiblockWaterThrusterRenderer}).
     */
    public boolean isPartOfMultiblock() {
        final BlockState state = getBlockState();
        return state.hasProperty(WaterThrusterBlock.CONTROLLER) && state.getValue(WaterThrusterBlock.CONTROLLER);
    }

    /**
     * True only on the corner that owns the multiblock model. On the client this is
     * derived from neighbouring blocks, because BE data (width/corner) is not synced
     * with the blockstate update that flips CONTROLLER.
     */
    public boolean isMultiblockCorner() {
        if (!isPartOfMultiblock()) return false;
        if (level == null) return false;
        if (!level.isClientSide()) {
            return multiblockWidth > 0 && multiblockCorner != null && worldPosition.equals(multiblockCorner);
        }
        return clientIsCorner();
    }

    public boolean isController() {
        return isMultiblockCorner();
    }

    // ---- client-side multiblock view (no network data required) ----

    private int[] clientAxes(BlockState state) {
        final Direction facing = state.getValue(BlockStateProperties.FACING);
        // {a1, a2, s1, s2}: the corner is at min (s=1) or max (s=-1) along each axis;
        // a1/a2 are the in-plane axes of the block footprint.
        return switch (facing) {
            case UP -> new int[]{0, 2, 1, 1};
            case DOWN -> new int[]{0, 2, 1, -1};
            case NORTH -> new int[]{0, 1, 1, 1};
            case SOUTH -> new int[]{0, 1, 1, -1};
            case WEST -> new int[]{1, 2, 1, 1};
            default -> new int[]{1, 2, -1, 1}; // EAST
        };
    }

    private Direction.Axis axisOf(int axisCode) {
        return axisCode == 0 ? Direction.Axis.X : axisCode == 1 ? Direction.Axis.Y : Direction.Axis.Z;
    }

    private boolean clientIsMember(BlockPos pos, Direction facing) {
        if (level == null) return false;
        final BlockState other = level.getBlockState(pos);
        return other.getBlock() instanceof WaterThrusterBlock
                && other.getValue(BlockStateProperties.FACING) == facing
                && other.hasProperty(WaterThrusterBlock.CONTROLLER)
                && other.getValue(WaterThrusterBlock.CONTROLLER);
    }

    private boolean clientIsCorner() {
        final BlockState state = getBlockState();
        final int[] ax = clientAxes(state);
        final Direction facing = state.getValue(BlockStateProperties.FACING);
        // both outward neighbours along the corner's preferred axes must be empty.
        // min-corner (s=1) faces -axis, max-corner (s=-1) faces +axis: both are -s.
        return !clientIsMember(worldPosition.relative(axisOf(ax[0]), -ax[2]), facing)
            && !clientIsMember(worldPosition.relative(axisOf(ax[1]), -ax[3]), facing);    }

    private int clientMultiblockWidth() {
        if (level == null || level.isClientSide()) {
            if (!isPartOfMultiblock()) return 0;
            final BlockState state = getBlockState();
            final int[] ax = clientAxes(state);
            // count how many members fit inward from this corner along each axis
            final int c1 = clientCount(worldPosition, axisOf(ax[0]), ax[2]);
            final int c2 = clientCount(worldPosition, axisOf(ax[1]), ax[3]);
            final int w = Math.min(c1, c2);
            return w >= 2 ? Math.min(w, 3) : 0;
        }
        return multiblockWidth;
    }

    private int clientCount(BlockPos start, Direction.Axis axis, int step) {
        int count = 0;
        BlockPos p = start;
        final Direction facing = getBlockState().getValue(BlockStateProperties.FACING);
        while (count < 4 && clientIsMember(p.relative(axis, step), facing)) {
            p = p.relative(axis, step);
            count++;
        }
        return count + 1;
    }

    /**
     * Detects whether this thruster is part of a complete 2x2 or 3x3 square in the
     * plane perpendicular to its facing. Exactly one block of a complete multiblock
     * (the lexicographically first corner) becomes the controller: it renders the
     * multiblock model and propeller, the others render nothing extra.
     */
    public void refreshFormation() {
        if (level == null || level.isClientSide()) return;
        final BlockState state = getBlockState();
        if (!(state.getBlock() instanceof WaterThrusterBlock self)) return;
        final Direction facing = state.getValue(BlockStateProperties.FACING);

        final Direction a1, a2;
        final int s1, s2; // +1: corner is the min along that axis, -1: the max
        switch (facing) {
            case UP ->    { a1 = Direction.EAST; a2 = Direction.SOUTH; s1 = 1;  s2 = 1;  }
            case DOWN ->  { a1 = Direction.EAST; a2 = Direction.SOUTH; s1 = 1;  s2 = -1; }
            case NORTH -> { a1 = Direction.EAST; a2 = Direction.UP;    s1 = 1;  s2 = 1;  }
            case SOUTH -> { a1 = Direction.EAST; a2 = Direction.UP;    s1 = 1;  s2 = -1; }
            case WEST ->  { a1 = Direction.UP;   a2 = Direction.SOUTH; s1 = 1;  s2 = 1;  }
            default ->    { a1 = Direction.UP;   a2 = Direction.SOUTH; s1 = -1; s2 = 1;  } // EAST
        }

        BlockPos corner = null;
        int width = 0;
        for (final int w : MULTIBLOCK_WIDTHS) {
            final Set<BlockPos> members = collectMembers(w, a1, a2, facing, self);
            if (members == null) continue;
            for (final BlockPos candidate : sortForCorner(members, a1, a2, s1, s2)) {
                boolean complete = true;
                for (int i = 0; i < w && complete; i++) {
                    for (int j = 0; j < w && complete; j++) {
                        if (!members.contains(candidate.relative(a1, i).relative(a2, j))) complete = false;
                    }
                }
                if (complete) {
                    corner = candidate;
                    width = w;
                    break;
                }
            }
            if (corner != null) break;
        }

        // any member of a formed multiblock renders the empty model; the corner draws the rest
        final boolean formed = width > 0 && corner != null;
        this.multiblockWidth = width;
        this.multiblockCorner = corner;
        final boolean wasFormed = state.hasProperty(WaterThrusterBlock.CONTROLLER)
                && state.getValue(WaterThrusterBlock.CONTROLLER);

        if (formed != wasFormed) {
            if (level != null) {
                level.setBlock(worldPosition, state.setValue(WaterThrusterBlock.CONTROLLER, formed), 3);
            }
            com.mojang.logging.LogUtils.getLogger().warn(
                "[MBThruster] {} at {} formed={} width={} corner={}",
                level == null || level.isClientSide() ? "client" : "server",
                worldPosition, formed, width, corner);
            setChanged();
        }
    }

    /** all same-facing thrusters connected to this block, bounded to w*w cells; null if the blob is too big */
    @Nullable
    private Set<BlockPos> collectMembers(int w, Direction a1, Direction a2, Direction facing, WaterThrusterBlock self) {
        final Set<BlockPos> members = new HashSet<>();
        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(worldPosition);
        while (!queue.isEmpty()) {
            final BlockPos p = queue.poll();
            if (!members.add(p)) continue;
            if (w * w < members.size()) return null;
            for (final Direction d : new Direction[]{a1, a1.getOpposite(), a2, a2.getOpposite()}) {
                final BlockPos q = p.relative(d);
                if (isMember(q, facing, self)) queue.add(q);
            }
        }
        return members;
    }

    private List<BlockPos> sortForCorner(Set<BlockPos> in, Direction a1, Direction a2, int s1, int s2) {
        final List<BlockPos> list = new ArrayList<>(in);
        final java.util.function.ToDoubleFunction<BlockPos> key = p -> {
            double k1 = a1.getAxis().choose(p.getX(), p.getY(), p.getZ()) * (long) s1;
            double k2 = a2.getAxis().choose(p.getX(), p.getY(), p.getZ()) * (long) s2;
            return k1 * 1e6 + k2;
        };
        list.sort(java.util.Comparator.comparingDouble(key));
        return list;
    }

    private boolean isMember(BlockPos pos, Direction facing, WaterThrusterBlock self) {
        if (level == null) return false;
        final BlockState other = level.getBlockState(pos);
        return other.getBlock() == self && other.getValue(BlockStateProperties.FACING) == facing;
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

    private void spawnWaterParticles(boolean multiblock) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel))
            return;

        Direction facing = getBlockState().getValue(BlockStateProperties.FACING);

        double ox = worldPosition.getX() + 0.5 + facing.getStepX() * 0.7;
        double oy = worldPosition.getY() + 0.5 + facing.getStepY() * 0.7;
        double oz = worldPosition.getZ() + 0.5 + facing.getStepZ() * 0.7;

        double vx = facing.getStepX() * 1.5;
        double vy = facing.getStepY() * 1.5;
        double vz = facing.getStepZ() * 1.5;

        for (int i = 0; i < (multiblock ? 6 : 2); i++) {
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
        tag.putInt("MultiblockWidth", multiblockWidth);
        if (multiblockCorner != null) tag.putLong("MultiblockCorner", multiblockCorner.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        waterTank.readFromNBT(registries, tag.getCompound("WaterTank"));
        thrustMagnitude = tag.getInt("Thrust");
        multiblockWidth = tag.getInt("MultiblockWidth");
        multiblockCorner = tag.contains("MultiblockCorner") ? BlockPos.of(tag.getLong("MultiblockCorner")) : null;
        formationDirty = true;
    }
}