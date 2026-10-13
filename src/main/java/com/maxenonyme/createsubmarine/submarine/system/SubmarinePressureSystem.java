package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.network.SubCrackPayload;
import com.maxenonyme.createsubmarine.submarine.stress.SubLevelStressAnalyzer;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SubmarinePressureSystem {
    private static final int TICK_INTERVAL = 20;
    private static final int MAX_WATER_SCAN = 400;
    private static final ResourceLocation SOUND_METAL_STRESS = ResourceLocation.withDefaultNamespace("entity.iron_golem.repair");
    private static int tickCounter = 0;
    private static final Random RAND = new Random();

    private static final Map<UUID, Set<BlockPos>> BREACHED_PLOT = new ConcurrentHashMap<>();
    private static final Map<UUID, List<CompartmentDetector.Component>> KNOWN_SEALED = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> CACHED_WATER_DEPTH = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<BlockPos, Integer>> CRACK_LEVELS = new ConcurrentHashMap<>();

    public static void setSealedCompartments(UUID id, List<CompartmentDetector.Component> comps) {
        KNOWN_SEALED.put(id, comps);
    }

    public static void clearSubmarine(UUID id) {
        BREACHED_PLOT.remove(id);
        KNOWN_SEALED.remove(id);
        CACHED_WATER_DEPTH.remove(id);
        CRACK_LEVELS.remove(id);
    }

    public static void clearAll() {
        BREACHED_PLOT.clear();
        KNOWN_SEALED.clear();
        CACHED_WATER_DEPTH.clear();
        CRACK_LEVELS.clear();
    }

    public static Map<UUID, Map<BlockPos, Integer>> getAllCracks() {
        return CRACK_LEVELS;
    }

    public static int getCachedDepth(UUID id) {
        return CACHED_WATER_DEPTH.getOrDefault(id, 0);
    }

    public static Set<BlockPos> getBreachedPositions(UUID id) {
        return BREACHED_PLOT.getOrDefault(id, Set.of());
    }

    public static boolean isBreached(UUID id) {
        Set<BlockPos> breached = BREACHED_PLOT.get(id);
        return breached != null && !breached.isEmpty();
    }

    public static int getCrackCount(UUID id) {
        Map<BlockPos, Integer> cracks = CRACK_LEVELS.get(id);
        return cracks == null ? 0 : cracks.size();
    }

    public static boolean hasCrack(UUID id, BlockPos plotPos) {
        Map<BlockPos, Integer> cracks = CRACK_LEVELS.get(id);
        return cracks != null && cracks.containsKey(plotPos);
    }

    public static boolean repairCrack(UUID id, BlockPos plotPos, SubLevelAccess sub, Level oceanLevel) {
        Map<BlockPos, Integer> cracks = CRACK_LEVELS.get(id);
        if (cracks == null || !cracks.containsKey(plotPos)) return false;
        cracks.remove(plotPos);
        sendCrackPacket(oceanLevel, id, plotPos, -1, 0);
        Vector3d worldVec = new Vector3d(plotPos.getX() + 0.5, plotPos.getY() + 0.5, plotPos.getZ() + 0.5);
        sub.logicalPose().transformPosition(worldVec);
        BlockPos worldPos = BlockPos.containing(worldVec.x, worldVec.y, worldVec.z);
        oceanLevel.playSound(null, worldPos, net.minecraft.sounds.SoundEvents.IRON_GOLEM_REPAIR,
                SoundSource.BLOCKS, 0.6f, 1.0f + RAND.nextFloat() * 0.3f);
        return true;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter % TICK_INTERVAL != 0) return;
        if (com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.DISABLE_IMPLOSION.get()) return;

        for (Map.Entry<UUID, SubLevelAccess> entry : SubLevelRegistry.getAll().entrySet()) {
            processSubmarine(entry.getKey(), entry.getValue());
        }


    }

    private static void processSubmarine(UUID id, SubLevelAccess sub) {
        Level plotLevel = SubLevelRegistry.getLevel(id);
        if (plotLevel == null) return;

        SubLevelRegistry.PlotBounds bounds = SubLevelRegistry.getBounds(id);
        if (bounds == null) return;

        Level oceanLevel = plotLevel;

        Vector3dc subCenter = sub.logicalPose().position();
        int surfaceY = measureSurfaceY(oceanLevel, subCenter);
        int waterDepth = surfaceY == Integer.MIN_VALUE ? 0 : surfaceY - (int) Math.round(subCenter.y());
        CACHED_WATER_DEPTH.put(id, waterDepth);
        if (surfaceY == Integer.MIN_VALUE) {
            BREACHED_PLOT.remove(id);
            return;
        }

        if (waterDepth <= 0) {
            BREACHED_PLOT.remove(id);
            return;
        }

        if (SubmarineSinkingSystem.isCrashing(id)) return;
    }

    public static int measureSurfaceY(Level level, Vector3dc subCenter) {
        int x = (int) Math.round(subCenter.x());
        int z = (int) Math.round(subCenter.z());
        int startY = (int) Math.round(subCenter.y());
        int seaLevel = level.getSeaLevel();

        int surfaceY = Integer.MIN_VALUE;
        int top = Math.min(startY + MAX_WATER_SCAN, level.getMaxBuildHeight());
        net.minecraft.world.level.chunk.ChunkAccess chunk = level.getChunk(
                x >> 4, z >> 4,
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
        if (chunk == null)
            return Integer.MIN_VALUE;

        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        try {
            for (int y = startY; y < top; y++) {
                m.set(x, y, z);
                if (CompartmentTracker.realFluidState(chunk, m).is(net.minecraft.tags.FluidTags.WATER)
                        || CompartmentTracker.realFluidState(chunk, m).is(net.minecraft.tags.FluidTags.LAVA)) {
                    surfaceY = y;
                } else if (y >= seaLevel) {
                    break;
                }
            }
        } catch (Exception ignored) {}

        return surfaceY;
    }

    private static void applyPressure(UUID id, Level plotLevel, Level oceanLevel, SubLevelAccess sub, BlockPos plotPos, BlockState state, HullStrengthConfig.HullProperty prop, int surfaceY, boolean[] creakPlayed, int[] breakBudget) {
        CompartmentDetector.Component comp = CompartmentTracker.findCompartmentAdjacent(id, plotPos);
        if (comp == null) return;
        if (CompartmentTracker.isCompromised(id, comp.anchor())) return;

        if (!comp.hull().contains(plotPos)) return;

        boolean facesExterior = false;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = plotPos.relative(dir);
            if (!comp.hull().contains(neighbor) && !comp.internal().contains(neighbor)) {
                facesExterior = true;
                break;
            }
        }
        if (!facesExterior) return;

        Vector3d worldVec = new Vector3d(plotPos.getX() + 0.5, plotPos.getY() + 0.5, plotPos.getZ() + 0.5);
        sub.logicalPose().transformPosition(worldVec);

        int depth = surfaceY - (int) Math.floor(worldVec.y);
        if (depth <= prop.maxWaterDepth()) return;

        if (RAND.nextFloat() >= prop.implosionChance()) return;
        BlockPos worldPos = BlockPos.containing(worldVec.x, worldVec.y, worldVec.z);

        if (!creakPlayed[0]) {
            SoundEvent creak = BuiltInRegistries.SOUND_EVENT.get(SOUND_METAL_STRESS);
            if (creak != null) {
                float pitch = 0.15f + RAND.nextFloat() * 0.15f;
                oceanLevel.playSound(null, worldPos, creak, SoundSource.BLOCKS, 0.45f, pitch);
                creakPlayed[0] = true;
            }
        }

        Map<BlockPos, Integer> cracks = CRACK_LEVELS.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
        int crackLevel = cracks.getOrDefault(plotPos, 0) + 1;
        int blockId = BuiltInRegistries.BLOCK.getId(state.getBlock());

        if (crackLevel >= 3) {
            cracks.remove(plotPos);
            sendCrackPacket(oceanLevel, id, plotPos, -1, 0);

            if (breakBudget[0] > 0) {
                breakBudget[0]--;
                SoundType soundType = SoundType.STONE;
                try { soundType = state.getBlock().getSoundType(state, plotLevel, plotPos, null); } catch (Exception ignored) {}
                oceanLevel.playSound(null, worldPos, soundType.getBreakSound(), SoundSource.BLOCKS, 1.6f, 0.65f + RAND.nextFloat() * 0.3f);
            }
            if (oceanLevel instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SPLASH, worldVec.x, worldVec.y + 0.5, worldVec.z, 25, 0.4, 0.1, 0.4, 0.2);
            }

            plotLevel.destroyBlock(plotPos, false);
            BREACHED_PLOT.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(plotPos);
            SubLevelRegistry.PlotBounds b = SubLevelRegistry.getBounds(id);
            if (b != null) {
                SubmarineSinkingSystem.onCrashed(id, sub, plotLevel, b);
            }
        } else {
            cracks.put(plotPos, crackLevel);
            sendCrackPacket(oceanLevel, id, plotPos, crackLevel, blockId);
            if (oceanLevel instanceof ServerLevel serverLevel) {
                int count = crackLevel == 1 ? 5 : 12;
                serverLevel.sendParticles(ParticleTypes.DRIPPING_WATER, worldVec.x, worldVec.y, worldVec.z, count, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    private static void sendCrackPacket(Level oceanLevel, UUID id, BlockPos plotPos, int crackLevel, int blockId) {
        if (!(oceanLevel instanceof ServerLevel sl)) return;
        SubCrackPayload payload = new SubCrackPayload(id, plotPos, crackLevel, blockId);
        for (net.minecraft.server.level.ServerPlayer player : sl.players()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static boolean isPressurized(UUID id) {
        return getCachedDepth(id) > 0 && !isBreached(id);
    }

    public static BlockState getActualBlockState(Level level, BlockPos pos, BlockState originalState) {
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
            if (id != null && id.getPath().contains("copycat")) {
                net.minecraft.nbt.CompoundTag nbt = be.saveWithFullMetadata(level.registryAccess());
                if (nbt.contains("Material")) {
                    BlockState mat = net.minecraft.nbt.NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), nbt.getCompound("Material"));
                    if (mat != null && !mat.isAir()) return mat;
                }
                if (nbt.contains("material_data")) {
                    net.minecraft.nbt.CompoundTag data = nbt.getCompound("material_data");
                    for (String key : data.getAllKeys()) {
                        net.minecraft.nbt.CompoundTag itemTag = data.getCompound(key);
                        if (itemTag.contains("material")) {
                            BlockState mat = net.minecraft.nbt.NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), itemTag.getCompound("material"));
                            if (mat != null && !mat.isAir()) return mat;
                        }
                    }
                }
            }
        }
        return originalState;
    }

    public static int getWeakestHullDepth(UUID subId, Level plotLevel) {
        int weakest = Integer.MAX_VALUE;
        for (CompartmentDetector.Component comp : CompartmentTracker.getCompartments(subId)) {
            if (!comp.sealed() || CompartmentTracker.isCompromised(subId, comp.anchor()))
                continue;
            for (BlockPos bp : comp.hull()) {
                boolean facesExterior = false;
                for (Direction dir : Direction.values()) {
                    if (!CompartmentTracker.isWithinShip(subId, bp.relative(dir))) {
                        facesExterior = true;
                        break;
                    }
                }
                if (!facesExterior) continue;
                BlockState state = plotLevel.getBlockState(bp);
                if (!state.isCollisionShapeFullBlock(plotLevel, bp)) continue;
                BlockState strengthState = getActualBlockState(plotLevel, bp, state);
                Optional<HullStrengthConfig.HullProperty> prop = HullStrengthConfig.getFor(strengthState);
                if (prop.isPresent() && prop.get().maxWaterDepth() < weakest) {
                    weakest = prop.get().maxWaterDepth();
                }
            }
        }
        return weakest == Integer.MAX_VALUE ? -1 : weakest;
    }

    public static boolean isUnderHighPressure(UUID id, Level plotLevel) {
        int depth = getCachedDepth(id);
        if (depth <= 0) return false;
        int weakest = getWeakestHullDepth(id, plotLevel);
        if (weakest == -1) return false;
        return depth >= weakest * 0.80;
    }

    public static void onBlockBroken(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide())
            return;
        if (com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.DISABLE_IMPLOSION.get())
            return;
        if (event.getPlayer() != null && event.getPlayer().isCreative())
            return;

        UUID id = SubLevelRegistry.findUUID(level, event.getPos());
        if (id == null || SubmarineSinkingSystem.isCrashing(id))
            return;
        if (!isUnderHighPressure(id, level))
            return;

        CompartmentDetector.Component comp = CompartmentTracker.findCompartmentAdjacent(id, event.getPos());
        if (comp == null || !comp.hull().contains(event.getPos()))
            return;

        boolean facesExterior = false;
        for (Direction dir : Direction.values()) {
            if (!CompartmentTracker.isWithinShip(id, event.getPos().relative(dir))) {
                facesExterior = true;
                break;
            }
        }
        if (!facesExterior) return;

        SubLevelAccess sub = SubLevelRegistry.getAll().get(id);
        SubLevelRegistry.PlotBounds bounds = SubLevelRegistry.getBounds(id);
        if (sub == null || bounds == null) return;
        SubmarineSinkingSystem.onCrashed(id, sub, level, bounds);
    }

    public static void onPlotBlockReplaced(Level plotLevel, BlockPos plotPos) {
        if (CRACK_LEVELS.isEmpty())
            return;
        UUID id = SubLevelRegistry.findUUID(plotLevel, plotPos);
        if (id == null)
            return;
        Map<BlockPos, Integer> cracks = CRACK_LEVELS.get(id);
        if (cracks == null || cracks.remove(plotPos) == null)
            return;
        SubLevelAccess sub = SubLevelRegistry.getAll().get(id);
        Level oceanLevel = sub instanceof dev.ryanhcode.sable.sublevel.SubLevel sl ? sl.getLevel() : plotLevel;
        sendCrackPacket(oceanLevel, id, plotPos, -1, 0);
    }

    // ---- Experimental-merge compatibility shims ----
    // CLASSIC is the cogfly lattice path. HYBRID and REALISTIC currently
    // delegate to it (identical depth) until shape/girder tuning lands.
    // (See SubmarineConfig.PressureModel.)

    public static int effectiveDepth(UUID id, BlockPos plotPos, int classicDepth) {
        return Math.max(1, classicDepth);
    }

    public static double getCachedDensity(UUID id) {
        return 1.0;
    }

    public record LiquidColumn(int surfaceY, double density) {}

    public static LiquidColumn measureColumn(Level level, Vector3dc pos) {
        return new LiquidColumn(measureSurfaceY(level, pos), 1.0);
    }

    public static Map<UUID, Map<BlockPos, Integer>> cracks() {
        return CRACK_LEVELS;
    }

    public static boolean holdsAirPocket(Level level, CompartmentDetector.Component comp) {
        int room = 0;
        int air = 0;
        for (BlockPos p : comp.internal()) {
            BlockState state = level.getBlockState(p);
            boolean water = state.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
            if (!state.isAir() && !water)
                continue;
            room++;
            if (!water)
                air++;
        }
        return air * 10 > room;
    }

    private static final Set<UUID> DOOMED = ConcurrentHashMap.newKeySet();

    public static boolean isDoomed(UUID id) {
        return DOOMED.contains(id);
    }

    public record WeakPoint(BlockPos pos, BlockState state, int depth) {}

    public static WeakPoint weakestHull(UUID subId, Level plotLevel) {
        WeakPoint worst = null;
        for (CompartmentDetector.Component comp : CompartmentTracker.getCompartments(subId)) {
            if (!comp.sealed() || CompartmentTracker.isCompromised(subId, comp.anchor()))
                continue;
            for (BlockPos bp : comp.hull()) {
                boolean facesExterior = false;
                for (Direction dir : Direction.values()) {
                    if (!CompartmentTracker.isWithinShip(subId, bp.relative(dir))) {
                        facesExterior = true;
                        break;
                    }
                }
                if (!facesExterior) continue;
                BlockState state = plotLevel.getBlockState(bp);
                if (!state.isCollisionShapeFullBlock(plotLevel, bp)) continue;
                BlockState strengthState = getActualBlockState(plotLevel, bp, state);
                Optional<HullStrengthConfig.HullProperty> prop = HullStrengthConfig.getFor(strengthState);
                if (prop.isPresent() && (worst == null || prop.get().maxWaterDepth() < worst.depth())) {
                    worst = new WeakPoint(bp.immutable(), strengthState, prop.get().maxWaterDepth());
                }
            }
        }
        return worst;
    }

    public static WeakPoint weakestHull(UUID id, SubLevelAccess sub) {
        Level plotLevel = SubLevelRegistry.getLevel(id);
        if (plotLevel == null) return null;
        return weakestHull(id, plotLevel);
    }

    private static final Map<UUID, Map<BlockPos, BlockState>> BROKEN = new ConcurrentHashMap<>();

    public static void recordBroken(UUID id, BlockPos plotPos, BlockState state) {
        BROKEN.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).put(plotPos.immutable(), state);
    }

    public record Repair(int restored, int cracks, int drained) {}

    public static Repair repairAll(UUID id, Level plotLevel, Level oceanLevel) {
        int drained = 0;
        try {
            Set<BlockPos> flooded = com.maxenonyme.createsubmarine.submarine.compartment.FloodSystem.waterCells(id);
            if (flooded != null) {
                for (BlockPos p : new java.util.ArrayList<>(flooded)) {
                    if (plotLevel.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
                        plotLevel.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                        drained++;
                    }
                }
            }
        } catch (Exception ignored) {}
        int restored = 0;
        Map<BlockPos, BlockState> broken = BROKEN.remove(id);
        if (broken != null) {
            for (Map.Entry<BlockPos, BlockState> e : broken.entrySet()) {
                BlockState now = plotLevel.getBlockState(e.getKey());
                if (now.isAir() || now.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
                    plotLevel.setBlock(e.getKey(), e.getValue(), 3);
                    restored++;
                }
            }
        }
        int crackCount = 0;
        Map<BlockPos, Integer> cracked = CRACK_LEVELS.remove(id);
        if (cracked != null) {
            for (BlockPos p : cracked.keySet()) {
                sendCrackPacket(oceanLevel, id, p, -1, 0);
                crackCount++;
            }
        }
        return new Repair(restored, crackCount, drained);
    }

    public static void impact(UUID id, BlockPos plotPos, Level oceanLevel) {
        Map<BlockPos, Integer> cracks = CRACK_LEVELS.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
        int crackLevel = Math.min(3, cracks.getOrDefault(plotPos, 0) + 2);
        cracks.put(plotPos, crackLevel);
        Level plotLevel = SubLevelRegistry.getLevel(id);
        int bid = 0;
        try {
            if (plotLevel != null) bid = BuiltInRegistries.BLOCK.getId(plotLevel.getBlockState(plotPos).getBlock());
        } catch (Exception ignored) {}
        sendCrackPacket(oceanLevel, id, plotPos, crackLevel, bid);
    }

    public static void invalidate(UUID id) {
        CACHED_WATER_DEPTH.remove(id);
    }

    public static boolean repairCrack(UUID id, BlockPos plotPos, Level oceanLevel) {
        Map<BlockPos, Integer> cracks = CRACK_LEVELS.get(id);
        if (cracks == null || !cracks.containsKey(plotPos)) return false;
        int current = cracks.get(plotPos);
        if (current <= 1) {
            cracks.remove(plotPos);
            sendCrackPacket(oceanLevel, id, plotPos, -1, 0);
        } else {
            cracks.put(plotPos, current - 1);
            Level plotLevel = SubLevelRegistry.getLevel(id);
            int bid = 0;
            try {
                if (plotLevel != null) bid = BuiltInRegistries.BLOCK.getId(plotLevel.getBlockState(plotPos).getBlock());
            } catch (Exception ignored) {}
            sendCrackPacket(oceanLevel, id, plotPos, current - 1, bid);
        }
        SubLevelAccess subLevel = CompartmentTracker.getSubsSnapshot().get(id);
        Vector3d worldVec = new Vector3d(plotPos.getX() + 0.5, plotPos.getY() + 0.5, plotPos.getZ() + 0.5);
        if (subLevel != null) subLevel.logicalPose().transformPosition(worldVec);
        BlockPos worldPos = BlockPos.containing(worldVec.x, worldVec.y, worldVec.z);
        oceanLevel.playSound(null, worldPos, net.minecraft.sounds.SoundEvents.IRON_GOLEM_REPAIR,
                SoundSource.BLOCKS, 0.6f, 1.0f + RAND.nextFloat() * 0.3f);
        return true;
    }
}
