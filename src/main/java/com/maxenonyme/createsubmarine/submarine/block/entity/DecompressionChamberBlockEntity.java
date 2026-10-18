package com.maxenonyme.createsubmarine.submarine.block.entity;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.DecompressionChamberBlock;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import com.maxenonyme.createsubmarine.submarine.system.SubmarinePressureSystem;
import com.maxenonyme.createsubmarine.submarine.system.SubmarineSinkingSystem;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.EmptyFluidHandler;
import org.jetbrains.annotations.NotNull;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DecompressionChamberBlockEntity extends BlockEntity {

    private enum Mode {
        NONE, CHAMBER, OCEAN
    }

    private Mode mode = Mode.NONE;

    private CompartmentDetector.Component cachedCompartment;
    private UUID cachedSubId;
    private int pendingFill;
    private int pendingDrain;
    private long lastFlowTick = Long.MIN_VALUE;
    private int lastFlowDir;
    private int scanCooldown = 0;

    public DecompressionChamberBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSubmarine.DECOMPRESSION_CHAMBER_BE.get(), pos, state);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide && cachedCompartment != null) {
            for (BlockPos p : cachedCompartment.internal()) {
                GlobalPos globalPos = GlobalPos.of(level.dimension(), p);
                CHAMBER_WATER_BLOCKS.remove(globalPos);
            }
        }
    }

    public void tick() {
        if (level == null || level.isClientSide)
            return;

        if (level.getGameTime() % 5 == 0 && checkChamberBreach())
            return;

        scanCooldown--;
        if (scanCooldown <= 0) {
            detectMode();
            scanCooldown = 40;
        }

        processChamber();
    }

    private void detectMode() {
        Mode oldMode = mode;

        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, worldPosition);
        UUID id = subLevel == null ? null : subLevel.getUniqueId();
        CompartmentDetector.Component comp = id == null ? null : findChamberCompartment(id);
        if (comp != null) {
            mode = Mode.CHAMBER;
            cachedCompartment = comp;
            cachedSubId = id;
        } else {
            if (mode == Mode.CHAMBER)
                handleChamberLost();
            cachedCompartment = null;
            cachedSubId = null;
            mode = isAnyHolesFaceSubmerged() ? Mode.OCEAN : Mode.NONE;
        }

        if (oldMode != mode && level != null && !level.isClientSide) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    private boolean isAnyHolesFaceSubmerged() {
        for (Direction dir : getHolesFaces()) {
            if (isSubmerged(level, worldPosition.relative(dir)))
                return true;
        }
        return false;
    }

    private boolean isSubmerged(Level level, BlockPos pos) {
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, pos);
        if (subLevel == null)
            return level.getFluidState(pos).is(FluidTags.WATER);
        Vector3d worldPos = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        subLevel.logicalPose().transformPosition(worldPos);
        BlockPos wPos = BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
        Level parentLevel = SubLevelRegistry.getLevel(subLevel.getUniqueId());
        if (parentLevel == null && subLevel instanceof SubLevel sl) {
            parentLevel = sl.getLevel();
        }
        if (parentLevel == null)
            return false;
        return CompartmentTracker.realFluidState(parentLevel, wPos)
                .is(FluidTags.WATER);
    }

    private CompartmentDetector.Component findChamberCompartment(UUID id) {
        List<CompartmentDetector.Component> comps = CompartmentTracker.getCompartments(id);
        if (comps.isEmpty())
            return null;
        for (Direction dir : getHolesFaces()) {
            BlockPos neighbor = worldPosition.relative(dir);
            for (CompartmentDetector.Component compartment : comps) {
                if (!compartment.sealed() || CompartmentTracker.isCompromised(id, compartment.anchor()))
                    continue;
                if (compartment.internal().contains(neighbor))
                    return compartment;
            }
        }
        return null;
    }

    private boolean checkChamberBreach() {
        if (cachedCompartment == null || cachedSubId == null)
            return false;
        if (!isChamberBreached(cachedSubId, cachedCompartment))
            return false;
        handleChamberLost();
        cachedCompartment = null;
        cachedSubId = null;
        return true;
    }

    private void handleChamberLost() {
        if (cachedCompartment == null || cachedSubId == null)
            return;
        if (SubmarineConfig.DISABLE_IMPLOSION.get())
            return;
        if (!SubmarinePressureSystem.holdsAirPocket(level, cachedCompartment))
            return;
        if (!SubmarinePressureSystem.isUnderHighPressure(cachedSubId, level))
            return;
        if (!isChamberBreached(cachedSubId, cachedCompartment))
            return;
        implodeChamber(cachedSubId, cachedCompartment);
    }

    private boolean isChamberBreached(UUID id, CompartmentDetector.Component old) {
        BlockPos air = sampleChamberCell();
        if (air == null)
            return false;
        for (CompartmentDetector.Component compartment : CompartmentTracker.getCompartments(id)) {
            if (compartment.internal().contains(air))
                return !compartment.sealed() || CompartmentTracker.isCompromised(id, compartment.anchor())
                        || CompartmentTracker.isBreached(id, compartment);
        }
        return false;
    }

    private BlockPos sampleChamberCell() {
        if (cachedCompartment == null)
            return null;
        for (Direction dir : getHolesFaces()) {
            BlockPos n = worldPosition.relative(dir);
            if (cachedCompartment.internal().contains(n))
                return n;
        }
        return null;
    }

    private boolean chamberHasAir(CompartmentDetector.Component comp) {
        for (BlockPos p : comp.internal()) {
            if (isEmptyCell(p))
                return true;
        }
        return false;
    }

    private void implodeChamber(UUID id, CompartmentDetector.Component comp) {
        Level parentLevel = SubLevelRegistry.getLevel(id);
        if (parentLevel == null)
            parentLevel = level;
        SubLevelAccess subLevel = SubLevelRegistry.getAll().get(id);
        if (subLevel == null)
            return;
        pendingFill = 0;
        pendingDrain = 0;
        SubmarineSinkingSystem
                .implodeCompartment(id, subLevel, parentLevel, comp);
    }

    private static final int STILL = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final Set<GlobalPos> CHAMBER_WATER_BLOCKS = ConcurrentHashMap
            .newKeySet();

    public static boolean isChamberPartialWater(Level level, BlockPos pos) {
        return CHAMBER_WATER_BLOCKS.contains(GlobalPos.of(level.dimension(), pos));
    }

    private int getWaterLevel(BlockPos p) {
        BlockState state = level.getBlockState(p);
        if (state.isAir())
            return 0;
        FluidState fs = level.getFluidState(p);
        if (fs.is(FluidTags.WATER)) {
            return fs.getAmount();
        }
        return -1;
    }

    private void setWaterLevel(BlockPos p, int newLevel) {
        GlobalPos globalPos = GlobalPos.of(level.dimension(), p);
        if (newLevel <= 0) {
            CHAMBER_WATER_BLOCKS.remove(globalPos);
            level.setBlock(p, Blocks.AIR.defaultBlockState(), STILL);
        } else if (newLevel >= 8) {
            CHAMBER_WATER_BLOCKS.remove(globalPos);
            level.setBlock(p,
                    Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 0), STILL);
        } else {
            CHAMBER_WATER_BLOCKS.add(globalPos);
            int stateLevel = 8 - newLevel;
            level.setBlock(p, Blocks.WATER.defaultBlockState()
                    .setValue(LiquidBlock.LEVEL, stateLevel), STILL);
        }
        if (level instanceof ServerLevel serverLevel)
            serverLevel.getFluidTicks().clearArea(new BoundingBox(p));
    }

    private void processChamber() {
        if (cachedCompartment == null)
            return;

        int water = chamberWaterVolume();
        pendingFill = Math.min(pendingFill, Math.max(0, chamberCapacity() - water));
        pendingDrain = Math.min(pendingDrain, water);

        Set<BlockPos> reachable = getReachableBlocks();

        int budget = 64;
        while (pendingFill >= 125 && budget-- > 0) {
            BlockPos p = findNextFillBlock(reachable);
            if (p == null) {
                pendingFill = 0;
                break;
            }
            int lvl = getWaterLevel(p);
            setWaterLevel(p, lvl + 1);
            pendingFill -= 125;
        }

        budget = 64;
        while (pendingDrain >= 125 && budget-- > 0) {
            BlockPos p = findNextDrainBlock(reachable);
            if (p == null) {
                pendingDrain = 0;
                break;
            }
            int lvl = getWaterLevel(p);
            setWaterLevel(p, lvl - 1);
            pendingDrain -= 125;
        }

        spawnChamberParticles();
    }

    private void spawnChamberParticles() {
        if (!(level instanceof ServerLevel serverLevel))
            return;
        if (level.getGameTime() % 4 != 0)
            return;
        if (lastFlowDir == 0 || level.getGameTime() - lastFlowTick > 5)
            return;

        boolean filling = lastFlowDir > 0;
        boolean dripping = filling && chamberHasAir(cachedCompartment);

        BlockState myState = getBlockState();
        Direction frontFace = myState.getValue(DecompressionChamberBlock.FACING);
        List<Direction> priorityFaces = new ArrayList<>();
        priorityFaces.add(frontFace);
        for (Direction dir : getHolesFaces()) {
            if (dir != frontFace)
                priorityFaces.add(dir);
        }

        boolean foundPrimary = false;

        for (Direction dir : priorityFaces) {
            BlockPos hole = worldPosition.relative(dir);
            BlockState state = level.getBlockState(hole);

            boolean isBlocked = !state.isAir() && !state.getFluidState().is(FluidTags.WATER) && state.getFluidState().isEmpty();
            if (isBlocked)
                continue;

            double fx = worldPosition.getX() + 0.5 + dir.getStepX() * 0.55;
            double fy = worldPosition.getY() + 0.5 + dir.getStepY() * 0.55;
            double fz = worldPosition.getZ() + 0.5 + dir.getStepZ() * 0.55;
            double sign = filling ? 1.0 : -1.0;
            double vx = dir.getStepX() * 0.18 * sign;
            double vy = dir.getStepY() * 0.18 * sign;
            double vz = dir.getStepZ() * 0.18 * sign;

            for (int i = 0; i < 2; i++)
                serverLevel.sendParticles(ParticleTypes.BUBBLE, fx, fy, fz, 0, vx, vy, vz, 1.0);

            if (!foundPrimary) {
                foundPrimary = true;
                if (dripping) {
                    serverLevel.sendParticles(ParticleTypes.FALLING_WATER, fx, fy, fz, 2, 0.12, 0.0, 0.12, 0.0);
                }
            }
        }
    }

    private Set<BlockPos> getReachableBlocks() {
        Set<BlockPos> internal = cachedCompartment.internal();
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();

        for (Direction dir : getHolesFaces()) {
            BlockPos hole = worldPosition.relative(dir);
            if (internal.contains(hole)) {
                queue.add(hole);
                visited.add(hole);
            }
        }

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            int currentLvl = getWaterLevel(current);
            boolean isVent = false;
            for (Direction dir : getHolesFaces()) {
                if (worldPosition.relative(dir).equals(current)) {
                    isVent = true;
                    break;
                }
            }

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (!internal.contains(neighbor) || visited.contains(neighbor))
                    continue;

                boolean canMove = false;
                if (dir == Direction.DOWN) {
                    canMove = true;
                } else if (dir.getAxis().isHorizontal()) {
                    if (isVent || currentLvl > 0)
                        canMove = true;
                } else if (dir == Direction.UP) {
                    if (isVent || currentLvl == 8)
                        canMove = true;
                }

                if (canMove) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return visited;
    }

    private BlockPos findNextFillBlock(Set<BlockPos> reachable) {
        BlockPos bestPos = null;
        int bestY = Integer.MAX_VALUE;
        int bestLvl = Integer.MAX_VALUE;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos p : reachable) {
            int lvl = getWaterLevel(p);
            if (lvl >= 0 && lvl < 8) {
                if (p.getY() < bestY) {
                    bestY = p.getY();
                    bestLvl = lvl;
                    bestDist = p.distSqr(worldPosition);
                    bestPos = p;
                } else if (p.getY() == bestY) {
                    if (lvl < bestLvl) {
                        bestLvl = lvl;
                        bestDist = p.distSqr(worldPosition);
                        bestPos = p;
                    } else if (lvl == bestLvl) {
                        double dist = p.distSqr(worldPosition);
                        if (dist < bestDist) {
                            bestDist = dist;
                            bestPos = p;
                        }
                    }
                }
            }
        }
        return bestPos;
    }

    private BlockPos findNextDrainBlock(Set<BlockPos> reachable) {
        BlockState myState = getBlockState();
        Direction frontFace = myState.getValue(DecompressionChamberBlock.FACING);
        List<Direction> priorityFaces = new ArrayList<>();
        priorityFaces.add(frontFace);
        for (Direction dir : getHolesFaces()) {
            if (dir != frontFace)
                priorityFaces.add(dir);
        }

        BlockPos activeHole = null;
        for (Direction dir : priorityFaces) {
            BlockPos blockPos = worldPosition.relative(dir);
            BlockState blockState = level.getBlockState(blockPos);
            if (blockState.isAir() || blockState.getFluidState().is(FluidTags.WATER) || !blockState.getFluidState().isEmpty()) {
                activeHole = blockPos;
                break;
            }
        }

        BlockPos bestPos = null;
        int bestY = Integer.MIN_VALUE;
        int bestLvl = Integer.MIN_VALUE;
        double bestDist = -1;

        for (BlockPos p : reachable) {
            if (p.equals(activeHole))
                continue;

            int lvl = getWaterLevel(p);
            if (lvl > 0) {
                if (p.getY() > bestY) {
                    bestY = p.getY();
                    bestLvl = lvl;
                    bestDist = p.distSqr(worldPosition);
                    bestPos = p;
                } else if (p.getY() == bestY) {
                    if (lvl > bestLvl) {
                        bestLvl = lvl;
                        bestDist = p.distSqr(worldPosition);
                        bestPos = p;
                    } else if (lvl == bestLvl) {
                        double dist = p.distSqr(worldPosition);
                        if (dist > bestDist) {
                            bestDist = dist;
                            bestPos = p;
                        }
                    }
                }
            }
        }

        if (bestPos == null && activeHole != null && reachable.contains(activeHole)) {
            int lvl = getWaterLevel(activeHole);
            if (lvl > 0) {
                bestPos = activeHole;
            }
        }

        return bestPos;
    }

    private boolean isEmptyCell(BlockPos p) {
        BlockState state = level.getBlockState(p);
        return state.isAir() || (!state.getFluidState().isEmpty() && !state.getFluidState().is(FluidTags.WATER));
    }

    private boolean isWaterCell(BlockPos p) {
        return level.getFluidState(p).is(FluidTags.WATER);
    }

    private boolean isVentBlocked() {
        for (Direction dir : getHolesFaces()) {
            BlockPos hole = worldPosition.relative(dir);
            BlockState state = level.getBlockState(hole);
            if (state.isAir() || state.getFluidState().is(FluidTags.WATER) || !state.getFluidState().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private int chamberWaterVolume() {
        if (cachedCompartment == null)
            return 0;
        int total = 0;
        for (BlockPos p : cachedCompartment.internal()) {
            int lvl = getWaterLevel(p);
            if (lvl > 0)
                total += lvl * 125;
        }
        return total;
    }

    private int chamberCapacity() {
        if (cachedCompartment == null)
            return 0;
        int count = 0;
        for (BlockPos p : cachedCompartment.internal()) {
            if (getWaterLevel(p) >= 0)
                count++;
        }
        return count * 1000;
    }

    private List<Direction> getHolesFaces() {
        BlockState state = getBlockState();
        Direction pipeFace = state.getValue(DecompressionChamberBlock.FACING).getOpposite();
        List<Direction> faces = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            if (dir != pipeFace) {
                faces.add(dir);
            }
        }
        return faces;
    }

    private final IFluidHandler chamber = new ChamberHandler();
    private final IFluidHandler ocean = new OceanHandler();
    private final IFluidHandler valve = new Valve();

    public IFluidHandler getFluidHandlerForSide(Direction side) {
        Direction pipeFace = getBlockState().getValue(DecompressionChamberBlock.FACING).getOpposite();
        return side == pipeFace ? valve : null;
    }

    private IFluidHandler current() {
        if (level == null || level.isClientSide)
            return EmptyFluidHandler.INSTANCE;
        if (mode == Mode.NONE)
            detectMode();
        return switch (mode) {
            case CHAMBER -> chamber;
            case OCEAN -> ocean;
            default -> EmptyFluidHandler.INSTANCE;
        };
    }

    private class Valve implements IFluidHandler {
        @Override
        public int getTanks() {
            return current().getTanks();
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return current().getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return current().getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return current().isFluidValid(tank, stack);
        }

        @Override
        public int fill(@NotNull FluidStack resource, IFluidHandler.FluidAction action) {
            return current().fill(resource, action);
        }

        @Override
        public @NotNull FluidStack drain(@NotNull FluidStack resource, IFluidHandler.FluidAction action) {
            return current().drain(resource, action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
            return current().drain(maxDrain, action);
        }
    }

    private class ChamberHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            int volume = chamberWaterVolume() + pendingFill;
            return volume <= 0 ? FluidStack.EMPTY
                    : new FluidStack(Fluids.WATER, volume);
        }

        @Override
        public int getTankCapacity(int tank) {
            return chamberCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return stack.getFluid().is(FluidTags.WATER);
        }

        @Override
        public int fill(@NotNull FluidStack resource, IFluidHandler.FluidAction action) {
            if (isVentBlocked())
                return 0;
            if (resource.isEmpty() || !isFluidValid(0, resource))
                return 0;
            int room = chamberCapacity() - (chamberWaterVolume() + pendingFill);
            if (room <= 0)
                return 0;

            int toAccept = Math.min(resource.getAmount(), room);

            if (action.execute()) {
                pendingFill += toAccept;
                lastFlowTick = level.getGameTime();
                lastFlowDir = 1;
            }
            return toAccept;
        }

        @Override
        public @NotNull FluidStack drain(@NotNull FluidStack resource, IFluidHandler.FluidAction action) {
            if (isVentBlocked())
                return FluidStack.EMPTY;
            if (resource.isEmpty() || !isFluidValid(0, resource))
                return FluidStack.EMPTY;
            return drain(resource.getAmount(), action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
            if (isVentBlocked())
                return FluidStack.EMPTY;
            int available = chamberWaterVolume() - pendingDrain;

            int amount = Math.min(maxDrain, available);
            if (amount <= 0)
                return FluidStack.EMPTY;

            if (action.execute()) {
                pendingDrain += amount;
                lastFlowTick = level.getGameTime();
                lastFlowDir = -1;
            }
            return new FluidStack(Fluids.WATER, amount);
        }
    }

    private static class OceanHandler implements IFluidHandler {
        private static final int OCEAN = 1_000_000;

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return new FluidStack(Fluids.WATER, OCEAN);
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
        public int fill(@NotNull FluidStack resource, IFluidHandler.FluidAction action) {
            return isFluidValid(0, resource) ? resource.getAmount() : 0;
        }

        @Override
        public @NotNull FluidStack drain(@NotNull FluidStack resource, IFluidHandler.FluidAction action) {
            if (resource.isEmpty() || !isFluidValid(0, resource))
                return FluidStack.EMPTY;
            return drain(resource.getAmount(), action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
            if (maxDrain <= 0)
                return FluidStack.EMPTY;
            return new FluidStack(Fluids.WATER, maxDrain);
        }
    }
}
