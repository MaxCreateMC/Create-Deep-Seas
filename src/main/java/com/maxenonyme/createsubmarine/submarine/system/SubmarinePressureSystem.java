package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.compartment.FloodSystem;
import com.maxenonyme.createsubmarine.submarine.network.SubCrackPayload;
import com.maxenonyme.createsubmarine.submarine.stress.HullShapeAnalyzer;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.ArrayList;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.maxenonyme.createsubmarine.submarine.network.ImplosionFxPayload;

public class SubmarinePressureSystem {
    private static final int TICK_INTERVAL = 20;
    private static final int MAX_WATER_SCAN = 400;
    private static final ResourceLocation SOUND_METAL_STRESS = ResourceLocation
            .withDefaultNamespace("entity.iron_golem.repair");
    private static int tickCounter = 0;
    private static final Random RAND = new Random();

    private static final Map<UUID, Set<BlockPos>> BREACHED_PLOT = new ConcurrentHashMap<>();
    private static final Map<UUID, List<CompartmentDetector.Component>> KNOWN_SEALED = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> CACHED_WATER_DEPTH = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<BlockPos, Integer>> CRACK_LEVELS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> WEAKEST_HULL = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> CACHED_DENSITY = new ConcurrentHashMap<>();
    private static final int MAX_FIRES_PER_TICK = 8;
    private static final double STRAIN_START = 0.80;
    private static final double STRAIN_REACH = 24.0;

    public static void setSealedCompartments(UUID id, List<CompartmentDetector.Component> comps) {
        KNOWN_SEALED.put(id, comps);
    }

    public static void clearSubmarine(UUID id) {
        BREACHED_PLOT.remove(id);
        KNOWN_SEALED.remove(id);
        CACHED_WATER_DEPTH.remove(id);
        CRACK_LEVELS.remove(id);
        WEAKEST_HULL.remove(id);
        CACHED_DENSITY.remove(id);
        MATERIALS.remove(id);
        WEAK_CACHE.remove(id);
        HullShapeAnalyzer.forget(id);
        GirderSupport.forget(id);
        GirderFailure.forget(id);
    }

    public static void clearAll() {
        BREACHED_PLOT.clear();
        KNOWN_SEALED.clear();
        CACHED_WATER_DEPTH.clear();
        CRACK_LEVELS.clear();
        WEAKEST_HULL.clear();
        CACHED_DENSITY.clear();
        MATERIALS.clear();
        WEAK_CACHE.clear();
        ENVELOPES.clear();
        HullShapeAnalyzer.clear();
        GirderSupport.clear();
        GirderFailure.clear();
    }

    public static int effectiveDepth(UUID id, BlockPos plotPos, int maxWaterDepth) {
        return Math.max(1, (int) Math.round(maxWaterDepth * HullShapeAnalyzer.factor(id, plotPos)
                * GirderSupport.factor(supports(id), plotPos) / getCachedDensity(id)));
    }

    private static GirderSupport.Layout supports(UUID id) {
        Level level = SubLevelRegistry.getLevel(id);
        SubLevelRegistry.PlotBounds b = SubLevelRegistry.getBounds(id);
        if (level == null || b == null)
            return GirderSupport.NONE;
        return GirderSupport.layout(level, id, 0L, b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }

    public record Repair(int restored, int cracks, int drained) {
    }

    private static final Map<UUID, Map<BlockPos, BlockState>> BROKEN = new ConcurrentHashMap<>();

    public static void recordBroken(UUID id, BlockPos plotPos, BlockState state) {
        BROKEN.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).put(plotPos.immutable(), state);
    }

    public static Repair repairAll(UUID id, Level plotLevel, Level oceanLevel) {
        int drained = 0;
        Set<BlockPos> flooded = FloodSystem.waterCells(id);
        if (flooded != null) {
            for (BlockPos p : new ArrayList<>(flooded)) {
                if (plotLevel.getBlockState(p).getBlock() instanceof LiquidBlock) {
                    plotLevel.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    drained++;
                }
            }
        }
        int restored = 0;
        Map<BlockPos, BlockState> broken = BROKEN.remove(id);
        if (broken != null) {
            for (Map.Entry<BlockPos, BlockState> e : broken.entrySet()) {
                BlockState now = plotLevel.getBlockState(e.getKey());
                if (now.isAir() || now.getBlock() instanceof LiquidBlock) {
                    plotLevel.setBlock(e.getKey(), e.getValue(), 3);
                    restored++;
                }
            }
        }
        int cracks = 0;
        Map<BlockPos, Integer> cracked = CRACK_LEVELS.remove(id);
        if (cracked != null) {
            for (BlockPos p : cracked.keySet()) {
                sendCrackPacket(oceanLevel, id, p, -1, 0);
                cracks++;
            }
        }
        BREACHED_PLOT.remove(id);
        WEAK_CACHE.remove(id);
        MATERIALS.remove(id);
        GirderSupport.forget(id);
        GirderFailure.forget(id);
        return new Repair(restored, cracks, drained);
    }

    public static void invalidate(UUID id) {
        MATERIALS.remove(id);
    }

    public static void impact(UUID id, BlockPos plotPos, ServerLevel oceanLevel) {
        Level plotLevel = SubLevelRegistry.getLevel(id);
        if (plotLevel == null)
            return;
        BlockState state = plotLevel.getBlockState(plotPos);
        if (state.isAir() || state.getFluidState().isSource())
            return;
        Map<BlockPos, Integer> cracks = CRACK_LEVELS.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
        int crackLevel = Math.min(3, cracks.getOrDefault(plotPos, 0) + 2);
        cracks.put(plotPos, crackLevel);
        sendCrackPacket(oceanLevel, id, plotPos, crackLevel, BuiltInRegistries.BLOCK.getId(state.getBlock()));
    }

    public static double getCachedDensity(UUID id) {
        return CACHED_DENSITY.getOrDefault(id, 1.0);
    }

    public static boolean isPressurized(UUID id) {
        return CACHED_WATER_DEPTH.getOrDefault(id, 0) > 0;
    }

    public static Map<UUID, Map<BlockPos, Integer>> getAllCracks() {
        return CRACK_LEVELS;
    }

    public static int getCachedDepth(UUID id) {
        return CACHED_WATER_DEPTH.getOrDefault(id, 0);
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

    public static boolean repairCrack(UUID id, BlockPos plotPos, Level oceanLevel) {
        Map<BlockPos, Integer> cracks = CRACK_LEVELS.get(id);
        if (cracks == null || !cracks.containsKey(plotPos))
            return false;

        int currentCrack = cracks.get(plotPos);
        if (currentCrack <= 1) {
            cracks.remove(plotPos);
            sendCrackPacket(oceanLevel, id, plotPos, -1, 0);
        } else {
            cracks.put(plotPos, currentCrack - 1);
            Level plotLevel = SubLevelRegistry.getLevel(id);
            int bid = plotLevel != null ? BuiltInRegistries.BLOCK.getId(plotLevel.getBlockState(plotPos).getBlock())
                    : 0;
            sendCrackPacket(oceanLevel, id, plotPos, currentCrack - 1, bid);
        }

        Vector3d worldVec = new Vector3d(plotPos.getX() + 0.5, plotPos.getY() + 0.5, plotPos.getZ() + 0.5);
        SubLevelAccess sub = CompartmentTracker.getSubsSnapshot().get(id);
        if (sub != null) {
            sub.logicalPose().transformPosition(worldVec);
        }
        BlockPos worldPos = BlockPos.containing(worldVec.x, worldVec.y, worldVec.z);
        oceanLevel.playSound(null, worldPos, SoundEvents.IRON_GOLEM_REPAIR,
                SoundSource.BLOCKS, 0.6f, 1.0f + RAND.nextFloat() * 0.3f);
        return true;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter % TICK_INTERVAL != 0)
            return;
        if (SubmarineConfig.DISABLE_IMPLOSION.get())
            return;

        for (Map.Entry<UUID, SubLevelAccess> entry : SubLevelRegistry.getAll().entrySet()) {
            processSubmarine(entry.getKey(), entry.getValue());
        }
    }

    private static void processSubmarine(UUID id, SubLevelAccess sub) {
        Level plotLevel = SubLevelRegistry.getLevel(id);
        if (plotLevel == null)
            return;

        SubLevelRegistry.PlotBounds bounds = SubLevelRegistry.getBounds(id);
        if (bounds == null)
            return;

        Level oceanLevel = sub instanceof SubLevel sl ? sl.getLevel() : plotLevel;

        Vector3dc subCenter = sub.logicalPose().position();
        LiquidColumn column = measureColumn(oceanLevel, subCenter);
        int surfaceY = column.surfaceY();
        CACHED_WATER_DEPTH.put(id, surfaceY == Integer.MIN_VALUE ? 0 : surfaceY - (int) Math.round(subCenter.y()));
        CACHED_DENSITY.put(id, column.density());

        if (SubmarineConfig.LAVA_BURNS_HULL.get())
            burnInLava(id, plotLevel, oceanLevel, sub);

        if (surfaceY == Integer.MIN_VALUE) {
            BREACHED_PLOT.remove(id);
            return;
        }

        if (SubmarineSinkingSystem.isCrashing(id))
            return;

        strainCrew(id, plotLevel, oceanLevel, subCenter, bounds);

        Set<BlockPos> breached = BREACHED_PLOT.get(id);

        boolean[] creakPlayed = { false };
        long volume = (long) (bounds.maxX() - bounds.minX() + 1) * (bounds.maxY() - bounds.minY() + 1)
                * (bounds.maxZ() - bounds.minZ() + 1);
        int samples = (int) Math.min(250, Math.max(15, volume / 150));

        if (SubmarineConfig.hybridPressure()) {
            Long2FloatOpenHashMap exterior = HullShapeAnalyzer.exterior(id);
            if (exterior != null) {
                crackWeakest(id, plotLevel, oceanLevel, sub, exterior, breached, surfaceY, column.density(),
                        Math.max(3, samples / 5), creakPlayed);
                return;
            }
        }

        for (int i = 0; i < samples; i++) {
            BlockPos plotPos = bounds.randomInside(RAND);
            if (plotPos == null || (breached != null && breached.contains(plotPos)))
                continue;

            BlockState state = plotLevel.getBlockState(plotPos);
            if (state.isAir() || state.getFluidState().isSource())
                continue;

            BlockState actualState = getActualBlockState(plotLevel, plotPos, state);
            Optional<HullStrengthConfig.HullProperty> propOpt = HullStrengthConfig.getFor(actualState);
            if (propOpt.isEmpty())
                continue;
            HullStrengthConfig.HullProperty prop = propOpt.get();

            applyPressure(id, plotLevel, oceanLevel, sub, plotPos, state, prop, surfaceY, creakPlayed);
        }
    }

    private record Pick(BlockPos pos, BlockState state, HullStrengthConfig.HullProperty prop, double key) {
    }

    private record HullMaterials(Long2FloatOpenHashMap source, long tick, long[] cells, float[] limits,
            HullStrengthConfig.HullProperty[] props) {
    }

    private static final Map<UUID, HullMaterials> MATERIALS = new ConcurrentHashMap<>();
    private static final int MATERIAL_REFRESH = 100;

    private static HullMaterials materials(UUID id, Level plotLevel, Long2FloatOpenHashMap exterior) {
        long now = plotLevel.getGameTime();
        HullMaterials cached = MATERIALS.get(id);
        if (cached != null && cached.source() == exterior && now - cached.tick() < MATERIAL_REFRESH)
            return cached;
        int cap = exterior.size();
        long[] cells = new long[cap];
        float[] limits = new float[cap];
        HullStrengthConfig.HullProperty[] props = new HullStrengthConfig.HullProperty[cap];
        int n = 0;
        GirderSupport.Layout supports = supports(id);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (Long2FloatMap.Entry e : exterior.long2FloatEntrySet()) {
            m.set(e.getLongKey());
            BlockState state = plotLevel.getBlockState(m);
            if (state.isAir() || state.getFluidState().isSource())
                continue;
            Optional<HullStrengthConfig.HullProperty> prop = HullStrengthConfig
                    .getFor(getActualBlockState(plotLevel, m, state));
            if (prop.isEmpty())
                continue;
            cells[n] = e.getLongKey();
            limits[n] = (float) (prop.get().maxWaterDepth() * e.getFloatValue() * GirderSupport.factor(supports, m));
            props[n] = prop.get();
            n++;
        }
        HullMaterials built = new HullMaterials(exterior, now, Arrays.copyOf(cells, n), Arrays.copyOf(limits, n),
                Arrays.copyOf(props, n));
        MATERIALS.put(id, built);
        return built;
    }

    private static void crackWeakest(UUID id, Level plotLevel, Level oceanLevel, SubLevelAccess sub,
            Long2FloatOpenHashMap exterior, Set<BlockPos> breached, int surfaceY, double density, int picks,
            boolean[] creakPlayed) {
        HullMaterials hull = materials(id, plotLevel, exterior);
        PriorityQueue<Pick> best = new PriorityQueue<>(Comparator.comparingDouble(Pick::key));
        Vector3d worldVec = new Vector3d();
        for (int i = 0; i < hull.cells().length; i++) {
            long cell = hull.cells()[i];
            worldVec.set(BlockPos.getX(cell) + 0.5, BlockPos.getY(cell) + 0.5, BlockPos.getZ(cell) + 0.5);
            sub.logicalPose().transformPosition(worldVec);
            int depth = surfaceY - (int) Math.floor(worldVec.y);
            double overload = depth * density / hull.limits()[i];
            if (overload <= 1.0)
                continue;
            double weight = (overload - 1.0) * (overload - 1.0);
            double key = Math.log(1.0 - RAND.nextDouble()) / weight;
            if (best.size() >= picks && key <= best.peek().key())
                continue;
            BlockPos plotPos = BlockPos.of(cell);
            if (breached != null && breached.contains(plotPos))
                continue;
            BlockState state = plotLevel.getBlockState(plotPos);
            if (state.isAir() || state.getFluidState().isSource())
                continue;
            if (best.size() >= picks)
                best.poll();
            best.add(new Pick(plotPos, state, hull.props()[i], key));
        }
        for (Pick p : best)
            applyPressure(id, plotLevel, oceanLevel, sub, p.pos(), p.state(), p.prop(), surfaceY, creakPlayed);
    }

    private static void strainCrew(UUID id, Level plotLevel, Level oceanLevel, Vector3dc center,
            SubLevelRegistry.PlotBounds bounds) {
        if (!(oceanLevel instanceof ServerLevel server))
            return;
        int depth = getCachedDepth(id);
        if (depth <= 0)
            return;
        Integer weakest = WEAKEST_HULL.get(id);
        if (weakest == null || tickCounter % (TICK_INTERVAL * 5) == 0) {
            weakest = getWeakestHullDepth(id, plotLevel);
            WEAKEST_HULL.put(id, weakest);
        }
        if (weakest <= 0)
            return;
        double strain = (depth - weakest * STRAIN_START) / (weakest * (1 - STRAIN_START));
        if (strain <= 0)
            return;
        float felt = (float) Math.min(1.0, strain);
        int span = Math.max(bounds.maxX() - bounds.minX(), Math.max(bounds.maxY() - bounds.minY(), bounds.maxZ() - bounds.minZ()));
        double reach = Math.max(STRAIN_REACH, span);
        Vec3 origin = new Vec3(center.x(), center.y(), center.z());
        for (ServerPlayer player : server.players()) {
            if (player.distanceToSqr(origin) <= reach * reach)
                PacketDistributor.sendToPlayer(player,
                        new ImplosionFxPayload(ImplosionFxPayload.STRAIN, felt, TICK_INTERVAL + 10));
        }
    }

    public record LiquidColumn(int surfaceY, double density) {
    }

    private static final LiquidColumn DRY = new LiquidColumn(Integer.MIN_VALUE, 1.0);

    public static int measureSurfaceY(Level level, Vector3dc subCenter) {
        return measureColumn(level, subCenter).surfaceY();
    }

    public static LiquidColumn measureColumn(Level level, Vector3dc subCenter) {
        int x = (int) Math.round(subCenter.x());
        int z = (int) Math.round(subCenter.z());
        int startY = (int) Math.round(subCenter.y());

        int top = Math.min(startY + MAX_WATER_SCAN, level.getMaxBuildHeight());
        ChunkAccess chunk = level.getChunk(
                x >> 4, z >> 4,
                ChunkStatus.FULL, false);
        if (chunk == null)
            return DRY;

        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        m.set(x, startY, z);
        if (CompartmentTracker.realFluidState(chunk, m).isEmpty())
            return DRY;
        int surfaceY = Integer.MIN_VALUE;
        double sum = 0;
        int count = 0;
        for (int y = startY; y < top; y++) {
            m.set(x, y, z);
            FluidState fluid = CompartmentTracker.realFluidState(chunk, m);
            if (!fluid.isEmpty()) {
                surfaceY = y;
                sum += LiquidDensity.of(fluid.getType());
                count++;
            } else if (isRealAir(chunk, m)) {
                break;
            }
        }

        return new LiquidColumn(surfaceY, count == 0 ? 1.0 : sum / count);
    }

    private static void burnInLava(UUID id, Level plotLevel, Level oceanLevel, SubLevelAccess sub) {
        Long2FloatOpenHashMap exterior = HullShapeAnalyzer.exterior(id);
        if (exterior == null)
            return;
        int fires = 0;
        BlockPos.MutableBlockPos plotPos = new BlockPos.MutableBlockPos();
        Vector3d w = new Vector3d();
        for (long cell : exterior.keySet()) {
            plotPos.set(cell);
            BlockState state = plotLevel.getBlockState(plotPos);
            if (state.isAir())
                continue;
            for (Direction dir : Direction.values()) {
                BlockPos out = plotPos.relative(dir);
                if (CompartmentTracker.isWithinShip(id, out) || !plotLevel.getBlockState(out).isAir())
                    continue;
                int flammability = state.getFlammability(plotLevel, plotPos, dir);
                if (flammability <= 0 || RAND.nextInt(300) >= flammability)
                    continue;
                sub.logicalPose().transformPosition(w.set(out.getX() + 0.5, out.getY() + 0.5, out.getZ() + 0.5));
                if (!CompartmentTracker.realFluidState(oceanLevel, BlockPos.containing(w.x, w.y, w.z)).is(FluidTags.LAVA))
                    continue;
                plotLevel.setBlockAndUpdate(out, BaseFireBlock.getState(plotLevel, out));
                if (++fires >= MAX_FIRES_PER_TICK)
                    return;
                break;
            }
        }
    }

    private static boolean isRealAir(ChunkAccess chunk, BlockPos pos) {
        int idx = chunk.getSectionIndex(pos.getY());
        if (idx < 0 || idx >= chunk.getSections().length)
            return true;
        LevelChunkSection section = chunk.getSection(idx);
        if (section == null || section.hasOnlyAir())
            return true;
        return section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15).isAir();
    }

    private static void applyPressure(UUID id, Level plotLevel, Level oceanLevel, SubLevelAccess sub, BlockPos plotPos,
            BlockState state, HullStrengthConfig.HullProperty prop, int surfaceY, boolean[] creakPlayed) {
        CompartmentDetector.Component comp = envelope(id).get(plotPos);
        if (comp == null || CompartmentTracker.isCompromised(id, comp.anchor()))
            return;

        boolean facesExterior = false;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = plotPos.relative(dir);
            if (!CompartmentTracker.isWithinShip(id, neighbor)) {
                facesExterior = true;
                break;
            }
        }
        if (!facesExterior)
            return;

        Vector3d worldVec = new Vector3d(plotPos.getX() + 0.5, plotPos.getY() + 0.5, plotPos.getZ() + 0.5);
        sub.logicalPose().transformPosition(worldVec);

        int depth = surfaceY - (int) Math.floor(worldVec.y);
        int limit = effectiveDepth(id, plotPos, prop.maxWaterDepth());
        if (depth <= limit)
            return;

        if (GirderFailure.protects(id, plotPos))
            return;
        GirderSupport.Support carrier = GirderSupport.carrier(supports(id), plotPos);
        if (carrier != null && oceanLevel instanceof ServerLevel loaded) {
            GirderFailure.load(loaded, id, sub, plotLevel, carrier);
            return;
        }

        float depthMultiplier = (float) depth / limit;
        if (RAND.nextFloat() >= prop.implosionChance() * depthMultiplier)
            return;
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

        if (crackLevel >= 4) {
            if (!(oceanLevel instanceof ServerLevel serverOcean) || ImplosionSequence.isBuilding(id))
                return;
            Vec3 origin = new Vec3(worldVec.x, worldVec.y, worldVec.z);
            boolean crush = depth >= FloodSystem.crushDepth() && comp.hull().contains(plotPos);
            ImplosionSequence.buildUp(serverOcean, id, origin, () -> {
                cracks.remove(plotPos);
                sendCrackPacket(oceanLevel, id, plotPos, -1, 0);
                if (sub instanceof SubLevel gone && gone.isRemoved())
                    return;
                if (plotLevel.getBlockState(plotPos).isAir() || SubmarineSinkingSystem.isCrashing(id))
                    return;
                SoundType soundType = SoundType.STONE;
                try {
                    soundType = state.getBlock().getSoundType(state, plotLevel, plotPos, null);
                } catch (Exception ignored) {
                }
                oceanLevel.playSound(null, worldPos, soundType.getBreakSound(), SoundSource.BLOCKS, 1.6f,
                        0.65f + RAND.nextFloat() * 0.3f);
                recordBroken(id, plotPos, state);
                plotLevel.destroyBlock(plotPos, true);
                BREACHED_PLOT.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(plotPos);
                if (!crush) {
                    ImplosionSequence.rupture(serverOcean, origin);
                    return;
                }
                SubLevelRegistry.PlotBounds b = SubLevelRegistry.getBounds(id);
                if (b != null)
                    SubmarineSinkingSystem.onCrashed(id, sub, plotLevel, b);
            });
        } else {
            cracks.put(plotPos, crackLevel);
            sendCrackPacket(oceanLevel, id, plotPos, crackLevel, blockId);
            if (oceanLevel instanceof ServerLevel serverLevel) {
                int count = crackLevel == 1 ? 5 : (crackLevel * 2);
                serverLevel.sendParticles(ParticleTypes.DRIPPING_WATER, worldVec.x, worldVec.y, worldVec.z, count, 0.3,
                        0.3, 0.3, 0.05);
            }
        }
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
        Level oceanLevel = sub instanceof SubLevel sl ? sl.getLevel() : plotLevel;
        sendCrackPacket(oceanLevel, id, plotPos, -1, 0);
    }

    private static void sendCrackPacket(Level oceanLevel, UUID id, BlockPos plotPos, int crackLevel, int blockId) {
        if (!(oceanLevel instanceof ServerLevel sl))
            return;
        SubCrackPayload payload = new SubCrackPayload(id, plotPos, crackLevel, blockId);
        for (ServerPlayer player : sl.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static boolean hasAnySealedCompartment(UUID id) {
        List<CompartmentDetector.Component> comps = KNOWN_SEALED.get(id);
        if (comps == null || comps.isEmpty())
            return true;

        for (CompartmentDetector.Component c : comps) {
            if (c.sealed() && !CompartmentTracker.isCompromised(id, c.anchor()))
                return true;
        }
        return false;
    }

    private static final Map<String, Optional<Method>> LOOKUPS = new ConcurrentHashMap<>();

    private static Optional<Method> lookup(Class<?> type, String name) {
        return LOOKUPS.computeIfAbsent(type.getName() + "#" + name, key -> {
            try {
                return Optional.of(type.getMethod(name));
            } catch (NoSuchMethodException e) {
                return Optional.empty();
            }
        });
    }

    private static Object call(Object target, String name) {
        Optional<Method> method = lookup(target.getClass(), name);
        if (method.isEmpty())
            return null;
        try {
            return method.get().invoke(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static BlockState copiedMaterial(BlockEntity be) {
        Object storage = call(be, "getMaterialItemStorage");
        if (storage != null && call(storage, "getAllMaterials") instanceof Collection<?> materials) {
            BlockState weakest = null;
            int weakestDepth = Integer.MAX_VALUE;
            for (Object entry : materials) {
                if (!(entry instanceof BlockState state) || state.isAir() || isCopycatBase(state))
                    continue;
                int depth = HullStrengthConfig.getFor(state).map(HullStrengthConfig.HullProperty::maxWaterDepth)
                        .orElse(Integer.MAX_VALUE);
                if (weakest == null || depth < weakestDepth) {
                    weakest = state;
                    weakestDepth = depth;
                }
            }
            if (weakest != null)
                return weakest;
        }
        return call(be, "getMaterial") instanceof BlockState state && !state.isAir() ? state : null;
    }

    public static BlockState getActualBlockState(Level level, BlockPos pos, BlockState originalState) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            BlockState copied = copiedMaterial(be);
            if (copied != null && !isCopycatBase(copied))
                return copied;
            ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
            if (id != null && id.getPath().contains("copycat")) {
                CompoundTag nbt = be.saveWithFullMetadata(level.registryAccess());
                if (nbt.contains("Material")) {
                    BlockState mat = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), nbt.getCompound("Material"));
                    if (mat != null && !mat.isAir()) return mat;
                }
                if (nbt.contains("material_data")) {
                    CompoundTag data = nbt.getCompound("material_data");
                    for (String key : data.getAllKeys()) {
                        CompoundTag itemTag = data.getCompound(key);
                        if (itemTag.contains("material")) {
                            BlockState mat = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), itemTag.getCompound("material"));
                            if (mat != null && !mat.isAir()) return mat;
                        }
                    }
                }
            }
        }
        return originalState;
    }

    private static boolean isCopycatBase(BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key.getPath().equals("copycat_base");
    }

    public static boolean holdsAirPocket(Level level, CompartmentDetector.Component comp) {
        int room = 0;
        int air = 0;
        for (BlockPos p : comp.internal()) {
            BlockState state = level.getBlockState(p);
            boolean water = state.getFluidState().is(FluidTags.WATER);
            if (!state.isAir() && !water)
                continue;
            room++;
            if (!water)
                air++;
        }
        return air * 10 > room;
    }

    public record WeakPoint(int depth, BlockState state, BlockPos pos) {
    }

    public static int getWeakestHullDepth(UUID subId, Level plotLevel) {
        WeakPoint weak = weakestHull(subId, plotLevel);
        return weak == null ? -1 : weak.depth();
    }

    private record CachedWeak(long tick, WeakPoint point) {
    }

    private static final Map<UUID, CachedWeak> WEAK_CACHE = new ConcurrentHashMap<>();
    private static final int WEAK_CACHE_TICKS = 20;

    public static WeakPoint weakestHull(UUID subId, Level plotLevel) {
        long now = plotLevel.getGameTime();
        CachedWeak cached = WEAK_CACHE.get(subId);
        if (cached != null && now - cached.tick() >= 0 && now - cached.tick() < WEAK_CACHE_TICKS)
            return cached.point();
        WeakPoint point = scanWeakest(subId, plotLevel);
        WEAK_CACHE.put(subId, new CachedWeak(now, point));
        return point;
    }

    private static WeakPoint scanWeakest(UUID subId, Level plotLevel) {
        WeakPoint weakest = null;
        for (Map.Entry<BlockPos, CompartmentDetector.Component> entry : envelope(subId).entrySet()) {
            if (CompartmentTracker.isCompromised(subId, entry.getValue().anchor()))
                continue;
            BlockPos bp = entry.getKey();
            boolean facesExterior = false;
            for (Direction dir : Direction.values()) {
                if (!CompartmentTracker.isWithinShip(subId, bp.relative(dir))) {
                    facesExterior = true;
                    break;
                }
            }
            if (!facesExterior)
                continue;
            BlockState state = plotLevel.getBlockState(bp);
            if (state.isAir())
                continue;
            BlockState strengthState = getActualBlockState(plotLevel, bp, state);
            Optional<HullStrengthConfig.HullProperty> prop = HullStrengthConfig.getFor(strengthState);
            if (prop.isEmpty())
                continue;
            int limit = effectiveDepth(subId, bp, prop.get().maxWaterDepth());
            if (weakest == null || limit < weakest.depth()) {
                weakest = new WeakPoint(limit, strengthState, bp.immutable());
            }
        }
        return weakest;
    }

    private record Envelope(List<CompartmentDetector.Component> source, Map<BlockPos, CompartmentDetector.Component> shell) {
    }

    private static final Map<UUID, Envelope> ENVELOPES = new ConcurrentHashMap<>();
    private static final int SHELL_LAYERS = 4;

    private static Map<BlockPos, CompartmentDetector.Component> envelope(UUID id) {
        List<CompartmentDetector.Component> comps = CompartmentTracker.getCompartments(id);
        Envelope cached = ENVELOPES.get(id);
        if (cached != null && cached.source() == comps)
            return cached.shell();
        Set<BlockPos> solid = CompartmentTracker.solidBlocks(id);
        Map<BlockPos, CompartmentDetector.Component> shell = new HashMap<>();
        for (CompartmentDetector.Component c : comps) {
            if (!c.sealed())
                continue;
            List<BlockPos> frontier = new ArrayList<>(c.hull());
            for (BlockPos p : frontier)
                shell.putIfAbsent(p, c);
            for (int layer = 1; layer < SHELL_LAYERS && !frontier.isEmpty(); layer++) {
                List<BlockPos> next = new ArrayList<>();
                for (BlockPos p : frontier) {
                    for (Direction dir : Direction.values()) {
                        BlockPos n = p.relative(dir);
                        if (solid.contains(n) && !c.internal().contains(n) && !shell.containsKey(n)) {
                            shell.put(n, c);
                            next.add(n);
                        }
                    }
                }
                frontier = next;
            }
        }
        ENVELOPES.put(id, new Envelope(comps, shell));
        return shell;
    }

    public static boolean isUnderHighPressure(UUID id, Level plotLevel) {
        int depth = getCachedDepth(id);
        if (depth <= 0) return false;
        int weakest = getWeakestHullDepth(id, plotLevel);
        if (weakest == -1) return false;
        return depth >= weakest * 0.80;
    }

    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide())
            return;
        if (SubmarineConfig.DISABLE_IMPLOSION.get())
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
        if (!facesExterior || !holdsAirPocket(level, comp))
            return;

        SubLevelAccess sub = SubLevelRegistry.getAll().get(id);
        SubLevelRegistry.PlotBounds bounds = SubLevelRegistry.getBounds(id);
        if (sub == null || bounds == null)
            return;
        SubmarineSinkingSystem.onCrashed(id, sub, level, bounds);
    }
}
