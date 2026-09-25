package com.maxenonyme.createsubmarine.submarine.block.entity;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class PumpControllerBlockEntity extends PumpBlockEntity {
    public static final int HOLD = 0;
    public static final int FILL = 1;
    public static final int DRAIN = -1;

    private static final int FOUND_BALLAST = 1;
    private static final int FOUND_VENT = 2;

    private BlockPos computer;
    private int command = FILL;
    private float rate = 1f;
    private Direction ballastSide;
    private int scanCooldown;

    public PumpControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide)
            return;

        int wanted = FILL;
        float wantedRate = 1f;
        BlockPos found = null;
        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, worldPosition);
        CommandSubBlockEntity brain = sub == null ? null : CommandSubBlockEntity.onSub(sub.getUniqueId());
        if (brain != null) {
            found = brain.getBlockPos();
            brain.reportPump(worldPosition, level.getGameTime());
            wanted = brain.pumpCommand();
            wantedRate = brain.pumpRate();
        }

        Direction side = ballastSide;
        if (--scanCooldown <= 0) {
            scanCooldown = 20;
            side = findBallastSide();
        }

        if (wanted != command || wantedRate != rate || !Objects.equals(found, computer) || side != ballastSide) {
            command = wanted;
            rate = wantedRate;
            computer = found;
            ballastSide = side;
            updatePressureChange();
            sendData();
        }
    }

    private Direction findBallastSide() {
        Direction front = getFront();
        if (front == null)
            return null;
        int ahead = scan(front);
        int behind = scan(front.getOpposite());
        boolean ballastAhead = (ahead & FOUND_BALLAST) != 0;
        boolean ballastBehind = (behind & FOUND_BALLAST) != 0;
        if (ballastAhead != ballastBehind)
            return ballastAhead ? front : front.getOpposite();
        boolean ventAhead = (ahead & FOUND_VENT) != 0;
        boolean ventBehind = (behind & FOUND_VENT) != 0;
        if (ventAhead != ventBehind)
            return ventAhead ? front.getOpposite() : front;
        return null;
    }

    private int scan(Direction side) {
        Set<BlockPos> seen = new HashSet<>();
        seen.add(worldPosition);
        List<BlockPos> layer = new ArrayList<>();
        layer.add(worldPosition.relative(side));
        seen.add(layer.get(0));
        int result = 0;
        for (int step = 0; step <= FluidPropagator.getPumpRange() && !layer.isEmpty(); step++) {
            List<BlockPos> next = new ArrayList<>();
            for (BlockPos pos : layer) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof BallastTankBlockEntity) {
                    result |= FOUND_BALLAST;
                    continue;
                }
                if (be instanceof BallastVentBlockEntity) {
                    result |= FOUND_VENT;
                    continue;
                }
                if (be instanceof PumpBlockEntity)
                    continue;
                FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, pos);
                if (pipe == null)
                    continue;
                for (Direction d : FluidPropagator.getPipeConnections(level.getBlockState(pos), pipe)) {
                    BlockPos neighbour = pos.relative(d);
                    if (seen.add(neighbour))
                        next.add(neighbour);
                }
            }
            if (result == (FOUND_BALLAST | FOUND_VENT))
                break;
            layer = next;
        }
        return result;
    }

    @Override
    public float getSpeed() {
        float speed = super.getSpeed();
        if (computer == null)
            return speed;
        return command == HOLD ? 0 : speed * rate;
    }

    @Override
    public boolean isPullingOnSide(boolean front) {
        Direction facing = getFront();
        if (computer == null || facing == null)
            return !front;
        Direction target = ballastSide == null ? facing : ballastSide;
        boolean towardBallast = (front ? facing : facing.getOpposite()) == target;
        return command == DRAIN ? towardBallast : !towardBallast;
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putInt("Command", command);
        compound.putFloat("Rate", rate);
        if (computer != null)
            compound.put("Computer", NbtUtils.writeBlockPos(computer));
        if (ballastSide != null)
            compound.putInt("BallastSide", ballastSide.get3DDataValue());
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        int previousCommand = command;
        float previousRate = rate;
        boolean wasLinked = computer != null;
        Direction previousSide = ballastSide;
        super.read(compound, registries, clientPacket);
        command = compound.contains("Command") ? compound.getInt("Command") : FILL;
        rate = compound.contains("Rate") ? compound.getFloat("Rate") : 1f;
        computer = NbtUtils.readBlockPos(compound, "Computer").orElse(null);
        ballastSide = compound.contains("BallastSide") ? Direction.from3DDataValue(compound.getInt("BallastSide")) : null;
        if (clientPacket && (previousCommand != command || previousRate != rate || wasLinked != (computer != null)
                || previousSide != ballastSide))
            VisualizationHelper.queueUpdate(this);
    }
}
