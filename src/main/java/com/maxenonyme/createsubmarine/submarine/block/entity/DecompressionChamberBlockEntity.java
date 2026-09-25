package com.maxenonyme.createsubmarine.submarine.block.entity;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.DecompressionChamberBlock;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import com.maxenonyme.createsubmarine.submarine.system.SubmarinePressureSystem;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

import net.minecraft.tags.FluidTags;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
                net.minecraft.core.GlobalPos globalPos = net.minecraft.core.GlobalPos.of(level.dimension(), p);
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

        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, worldPosition);
        UUID id = sub == null ? null : sub.getUniqueId();
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
        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, pos);
        if (sub == null)
            return level.getFluidState(pos).is(FluidTags.WATER);
        Vector3d worldPos = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        sub.logicalPose().transformPosition(worldPos);
        BlockPos wPos = BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
        Level parentLevel = SubLevelRegistry.getLevel(sub.getUniqueId());
        if (parentLevel == null && sub instanceof dev.ryanhcode.sable.sublevel.SubLevel sl) {
            parentLevel = sl.getLevel();
        }
        if (parentLevel == null)
            return false;
        return com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker.realFluidState(parentLevel, wPos)
                .is(FluidTags.WATER);
    }

    private CompartmentDetector.Component findChamberCompartment(UUID id) {
        List<CompartmentDetector.Component> comps = CompartmentTracker.getCompartments(id);
        if (comps.isEmpty())
            return null;
        for (Direction dir : getHolesFaces()) {
            BlockPos neighbor = worldPosition.relative(dir);
            for (CompartmentDetector.Component c : comps) {
                if (!c.sealed() || CompartmentTracker.isCompromised(id, c.anchor()))
                    continue;
                if (c.internal().contains(neighbor))
                    return c;
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
        if (!chamberHasAir(cachedCompartment))
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
        for (CompartmentDetector.Component c : CompartmentTracker.getCompartments(id)) {
            if (c.internal().contains(air))
                return !c.sealed() || CompartmentTracker.isCompromised(id, c.anchor());
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
        SubLevelAccess sub = SubLevelRegistry.getAll().get(id);
        if (sub == null)
            return;
        pendingFill = 0;
        pendingDrain = 0;
        com.maxenonyme.createsubmarine.submarine.system.SubmarineSinkingSystem
                .implodeCompartment(id, sub, parentLevel, comp);
    }

    private static final java.util.Set<net.minecraft.core.GlobalPos> CHAMBER_WATER_BLOCKS = java.util.concurrent.ConcurrentHashMap
            .newKeySet();

    public static boolean isChamberPartialWater(Level level, BlockPos pos) {
        return CHAMBER_WATER_BLOCKS.contains(net.minecraft.core.GlobalPos.of(level.dimension(), pos));
    }

    private int getWaterLevel(BlockPos p) {
        BlockState state = level.getBlockState(p);
        if (state.isAir())
            return 0;
        net.minecraft.world.level.material.FluidState fs = level.getFluidState(p);
        if (fs.is(net.minecraft.tags.FluidTags.WATER)) {
            return fs.getAmount();
        }
        return -1;
    }

    private void setWaterLevel(BlockPos p, int newLevel) {
        net.minecraft.core.GlobalPos globalPos = net.minecraft.core.GlobalPos.of(level.dimension(), p);
        if (newLevel <= 0) {
            CHAMBER_WATER_BLOCKS.remove(globalPos);
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        } else if (newLevel >= 8) {
            CHAMBER_WATER_BLOCKS.remove(globalPos);
            level.setBlock(p,
                    Blocks.WATER.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 0), 3);
        } else {
            CHAMBER_WATER_BLOCKS.add(globalPos);
            int stateLevel = 8 - newLevel;
            level.setBlock(p, Blocks.WATER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, stateLevel), 3);
        }
    }

    private void updateVisualSources(boolean dripping, boolean draining) {
        if (cachedCompartment == null)
            return;
        boolean blocked = isVentBlocked();

        BlockState myState = getBlockState();
        Direction frontFace = myState.getValue(DecompressionChamberBlock.FACING);

        List<Direction> priorityFaces = new ArrayList<>();
        priorityFaces.add(frontFace);
        for (Direction dir : getHolesFaces()) {
            if (dir != frontFace) {
                priorityFaces.add(dir);
            }
        }

        boolean placed = false;
        for (Direction dir : priorityFaces) {
            BlockPos hole = worldPosition.relative(dir);
            if (!cachedCompartment.internal().contains(hole))
                continue;

            BlockState state = level.getBlockState(hole);
            if (blocked)
                continue;

            if (dripping && !placed) {
                boolean canPlace = state.isAir() || state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)
                        || !state.getFluidState().isEmpty();
                if (canPlace) {
                    if (!state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)
                            || !state.getFluidState().isSource()) {
                        level.setBlock(hole, Blocks.WATER.defaultBlockState(), 3);
                    }
                    placed = true;
                }
            } else {
                if (!draining && state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)
                        && state.getFluidState().isSource()) {
                    if (chamberHasAir(cachedCompartment)) {
                        level.setBlock(hole, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private void processChamber() {
        if (cachedCompartment == null)
            return;

        boolean pipeFlowing = (level.getGameTime() - lastFlowTick <= 5);
        boolean filling = pipeFlowing && lastFlowDir > 0;
        boolean draining = pipeFlowing && lastFlowDir < 0;

        int water = chamberWaterVolume();
        pendingFill = Math.min(pendingFill, Math.max(0, chamberCapacity() - water));
        pendingDrain = Math.min(pendingDrain, water);

        java.util.Set<BlockPos> reachable = getReachableBlocks();

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

        boolean dripping = filling && chamberHasAir(cachedCompartment);
        updateVisualSources(dripping, draining);

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

            boolean isBlocked = !state.isAir() && !state.getFluidState().is(net.minecraft.tags.FluidTags.WATER) && state.getFluidState().isEmpty();
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

    private java.util.Set<BlockPos> getReachableBlocks() {
        java.util.Set<BlockPos> internal = cachedCompartment.internal();
        java.util.Queue<BlockPos> queue = new java.util.LinkedList<>();
        java.util.Set<BlockPos> visited = new java.util.HashSet<>();

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

    private BlockPos findNextFillBlock(java.util.Set<BlockPos> reachable) {
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

    private BlockPos findNextDrainBlock(java.util.Set<BlockPos> reachable) {
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
            BlockPos h = worldPosition.relative(dir);
            BlockState st = level.getBlockState(h);
            if (st.isAir() || st.getFluidState().is(net.minecraft.tags.FluidTags.WATER) || !st.getFluidState().isEmpty()) {
                activeHole = h;
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
        return state.isAir() || (!state.getFluidState().isEmpty() && !state.getFluidState().is(net.minecraft.tags.FluidTags.WATER));
    }

    private boolean isWaterCell(BlockPos p) {
        return level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER);
    }

    private boolean isVentBlocked() {
        for (Direction dir : getHolesFaces()) {
            BlockPos hole = worldPosition.relative(dir);
            BlockState state = level.getBlockState(hole);
            if (state.isAir() || state.getFluidState().is(net.minecraft.tags.FluidTags.WATER) || !state.getFluidState().isEmpty()) {
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

    public IFluidHandler getFluidHandlerForSide(Direction side) {
        if (level == null || level.isClientSide)
            return null;
        if (mode == Mode.NONE)
            detectMode();

        BlockState state = getBlockState();
        Direction pipeFace = state.getValue(DecompressionChamberBlock.FACING).getOpposite();
        if (side == pipeFace) {
            return switch (mode) {
                case CHAMBER -> new ChamberHandler();
                case OCEAN -> new OceanHandler();
                default -> null;
            };
        }
        return null;
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
                    : new FluidStack(net.minecraft.world.level.material.Fluids.WATER, volume);
        }

        @Override
        public int getTankCapacity(int tank) {
            return chamberCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return stack.getFluid().is(net.minecraft.tags.FluidTags.WATER);
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
            return new FluidStack(net.minecraft.world.level.material.Fluids.WATER, amount);
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
            return new FluidStack(net.minecraft.world.level.material.Fluids.WATER, OCEAN);
        }

        @Override
        public int getTankCapacity(int tank) {
            return OCEAN;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return stack.getFluid().is(net.minecraft.tags.FluidTags.WATER);
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
            return new FluidStack(net.minecraft.world.level.material.Fluids.WATER, maxDrain);
        }
    }
}
