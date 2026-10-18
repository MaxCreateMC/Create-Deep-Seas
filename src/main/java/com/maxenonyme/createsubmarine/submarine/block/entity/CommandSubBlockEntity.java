package com.maxenonyme.createsubmarine.submarine.block.entity;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import com.maxenonyme.createsubmarine.submarine.system.SubmarinePressureSystem;
import com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import com.maxenonyme.createsubmarine.submarine.network.CommandSubPayload;
import com.maxenonyme.createsubmarine.submarine.network.DiagnosticPayload;

public class CommandSubBlockEntity extends BlockEntity {
    public static final Set<CommandSubBlockEntity> LOADED_ON_CLIENT = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<UUID, CommandSubBlockEntity> BY_SUB = new ConcurrentHashMap<>();
    private static Map<UUID, Set<UUID>> dockLinks = Map.of();
    private static Level dockLinksLevel;
    private static long dockLinksTick = Long.MIN_VALUE;

    public int targetDepth = 20;
    public int speedMode = 1;

    public int syncedDepth;
    public int syncedWeakest = -1;
    public int surfaceY = Integer.MIN_VALUE;

    public double lastY = Double.NaN;
    public long lastNanos;
    public float shownVs;
    public int page;
    public boolean autopilot;
    public int syncedAutoTarget = -1;
    public double sonarPanX;
    public double sonarPanZ;
    public float sonarZoom = 1.8f;
    public float sonarYaw;
    public float sonarOrbit;

    public int syncedFill = -1;
    public int syncedPumps;
    public int syncedStatus = 1;
    public int syncedDock;

    public DiagnosticPayload report;
    public long scanStarted = -1;
    private long lastDiagnostic = -DIAGNOSTIC_COOLDOWN;

    private static final float[] RATES = { 0.33f, 0.66f, 1f };
    private static final double BRAKING_MARGIN = 0.6;
    private static final double TRIM_RANGE = 2;
    private static final double TRIM_ACCEL = 1.5;
    private static final double TANKS_PER_PUMP = 10;
    private static final int CLEARANCE = 4;
    private static final double LOOKAHEAD_SECONDS = 5;
    private static final int LOOKAHEAD_MIN = 6;
    private static final int LOOKAHEAD_MAX = 48;
    private static final int FLOOR_ABOVE = 24;
    private static final int FLOOR_BELOW = 96;
    private static final int DIAGNOSTIC_COOLDOWN = 40;

    private int tickCount;
    private UUID registeredSub;
    private int cachedWeakest = -1;
    private int command = PumpControllerBlockEntity.HOLD;
    private final Map<BlockPos, Long> pumps = new HashMap<>();
    private double lastFill = Double.NaN;
    private long lastFillTick;
    private double fillRate = 0.02;
    private double wantFill = Double.NaN;

    public CommandSubBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSubmarine.COMMAND_SUB_BE.get(), pos, state);
    }

    public void tick() {
        if (level == null || level.isClientSide)
            return;

        tickCount++;
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, worldPosition);
        claim(subLevel == null ? null : subLevel.getUniqueId());
        if (tickCount % 10 == 0)
            measure(subLevel);
        if (tickCount % 5 == 0)
            steer(subLevel);
        trim(subLevel);
    }

    private void claim(UUID subId) {
        if (!Objects.equals(subId, registeredSub)) {
            if (registeredSub != null)
                BY_SUB.remove(registeredSub, this);
            registeredSub = subId;
        }

        if (subId != null)
            BY_SUB.compute(subId, (id, current) -> current == null || current.isRemoved() ? this : current);
    }

    public static CommandSubBlockEntity onSub(UUID subId) {
        CommandSubBlockEntity computer = BY_SUB.get(subId);
        return computer == null || computer.isRemoved() ? null : computer;
    }

    private void measure(SubLevelAccess subLevel) {
        int depth = 0;
        int weakest = -1;
        int surface = Integer.MIN_VALUE;

        if (subLevel != null) {
            Level ocean = subLevel instanceof SubLevel sl && sl.getLevel() != null ? sl.getLevel() : level;
            Vector3dc center = subLevel.logicalPose().position();
            surface = SubmarinePressureSystem.measureSurfaceY(ocean, center);
            if (surface != Integer.MIN_VALUE)
                depth = Math.max(0, surface - (int) Math.floor(center.y()));
            if (tickCount % 40 == 0)
                cachedWeakest = SubmarinePressureSystem.getWeakestHullDepth(subLevel.getUniqueId(), level);
            weakest = cachedWeakest;
        }

        int auto = -1;
        if (autopilot && subLevel instanceof SubLevel sl && surface != Integer.MIN_VALUE
                && SonarBlockEntity.onSameSub(this) != null)
            auto = followFloor(sl, surface, weakest);

        if (depth != syncedDepth || weakest != syncedWeakest || surface != surfaceY || auto != syncedAutoTarget) {
            syncedDepth = depth;
            syncedWeakest = weakest;
            surfaceY = surface;
            syncedAutoTarget = auto;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private int followFloor(SubLevel subLevel, int surface, int weakest) {
        Level ocean = subLevel.getLevel();
        Pose3dc pose = subLevel.logicalPose();
        BoundingBox3ic box = subLevel.getPlot().getBoundingBox();
        Vector3d corner = new Vector3d();
        double keel = Double.MAX_VALUE;
        double[] xs = new double[5];
        double[] zs = new double[5];
        int n = 0;

        for (int i = 0; i < 8; i++) {
            corner.set((i & 1) == 0 ? box.minX() : box.maxX() + 1, (i & 2) == 0 ? box.minY() : box.maxY() + 1,
                    (i & 4) == 0 ? box.minZ() : box.maxZ() + 1);
            pose.transformPosition(corner);
            keel = Math.min(keel, corner.y);

            if ((i & 2) == 0) {
                xs[n] = corner.x;
                zs[n++] = corner.z;
            }
        }

        corner.set((box.minX() + box.maxX() + 1) / 2.0, box.minY(), (box.minZ() + box.maxZ() + 1) / 2.0);
        pose.transformPosition(corner);
        xs[4] = corner.x;
        zs[4] = corner.z;

        Vector3dc velocity = SablePhysicsHelper.getVelocity(subLevel);
        double vx = velocity == null ? 0 : velocity.x();
        double vz = velocity == null ? 0 : velocity.z();
        double speed = Math.sqrt(vx * vx + vz * vz);
        double reach = speed < 0.2 ? 0 : Mth.clamp(speed * LOOKAHEAD_SECONDS, LOOKAHEAD_MIN, LOOKAHEAD_MAX);
        double dx = speed < 0.2 ? 0 : vx / speed;
        double dz = speed < 0.2 ? 0 : vz / speed;

        int top = (int) Math.floor(keel) + FLOOR_ABOVE;
        int bottom = Math.max(ocean.getMinBuildHeight(), (int) Math.floor(keel) - FLOOR_BELOW);
        int highest = Integer.MIN_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (double ahead = 0; ahead <= reach; ahead += 2) {
            for (int s = 0; s < 5; s++) {
                int x = Mth.floor(xs[s] + dx * ahead);
                int z = Mth.floor(zs[s] + dz * ahead);
                ChunkAccess chunk = ocean.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null)
                    continue;

                for (int y = top; y >= bottom && y >= highest; y--) {
                    cursor.set(x, y, z);

                    if (CompartmentTracker.realBlockState(chunk, cursor).blocksMotion()) {
                        highest = Math.max(highest, y + 1);
                        break;
                    }
                }
            }

            if (reach == 0)
                break;
        }

        if (highest == Integer.MIN_VALUE)
            return -1;

        double keelOffset = pose.position().y() - keel;
        double wantY = highest + CLEARANCE + keelOffset;
        int target = Math.max(0, (int) Math.floor(surface + 1 - wantY));
        if (weakest > 0)
            target = Math.min(target, Math.max(0, weakest - 2));
        return target;
    }

    public int activeTarget() {
        return autopilot && syncedAutoTarget >= 0 ? syncedAutoTarget : targetDepth;
    }

    private void steer(SubLevelAccess subLevel) {
        long now = level.getGameTime();
        pumps.values().removeIf(seen -> now - seen > 40);

        int wanted = PumpControllerBlockEntity.HOLD;
        CommandSubBlockEntity leader = subLevel == null ? null : dockLeader(subLevel.getUniqueId());
        int dock = leader == null ? 0 : leader == this ? 1 : 2;
        double fill = subLevel == null ? Double.NaN : BallastTankBlockEntity.fillRatio(subLevel.getUniqueId());
        if (!Double.isNaN(fill)) {
            if (!Double.isNaN(lastFill) && command != PumpControllerBlockEntity.HOLD && now > lastFillTick) {
                double sample = Math.abs(fill - lastFill) / ((now - lastFillTick) / 20.0);
                if (sample > 0)
                    fillRate += (sample - fillRate) * 0.3;
            }

            lastFill = fill;
            lastFillTick = now;
        }

        wantFill = Double.NaN;

        if (dock == 2 && !Double.isNaN(fill)) {
            wantFill = leader.wantFill;
        } else if (subLevel != null && !Double.isNaN(fill)) {
            double error = depth(subLevel) - activeTarget();
            double maxSpeed = SubmarineConfig.BALLAST_VERTICAL_SPEED.get();
            double modeSpeed = maxSpeed * RATES[speedMode];
            double braking = Math.max(0.02, 2 * maxSpeed * fillRate) * BRAKING_MARGIN;
            double wantSpeed = Math.signum(error) * Math.min(modeSpeed, Math.sqrt(2 * braking * Math.abs(error)));
            Vector3dc velocity = SablePhysicsHelper.getVelocity(subLevel);
            double rising = velocity == null ? wantSpeed : velocity.y();
            double push = wantSpeed + 2 * (wantSpeed - rising);
            wantFill = Mth.clamp(0.5 - push / (2 * maxSpeed), 0, 1);
        }

        if (!Double.isNaN(wantFill)) {
            if (fill < wantFill - 0.01)
                wanted = PumpControllerBlockEntity.FILL;
            else if (fill > wantFill + 0.01)
                wanted = PumpControllerBlockEntity.DRAIN;
        }

        command = wanted;

        int percent = Double.isNaN(fill) ? -1 : (int) Math.round(fill * 100);
        int status = command + 1;
        if (percent != syncedFill || pumps.size() != syncedPumps || status != syncedStatus || dock != syncedDock) {
            syncedFill = percent;
            syncedPumps = pumps.size();
            syncedStatus = status;
            syncedDock = dock;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private CommandSubBlockEntity dockLeader(UUID self) {
        Map<UUID, Set<UUID>> links = dockLinks(level);
        if (!links.containsKey(self))
            return null;

        Deque<UUID> queue = new ArrayDeque<>();
        Set<UUID> seen = new HashSet<>();
        queue.add(self);
        seen.add(self);
        CommandSubBlockEntity leader = null;
        int computers = 0;

        while (!queue.isEmpty()) {
            UUID id = queue.poll();
            CommandSubBlockEntity computer = onSub(id);
            if (computer != null) {
                computers++;
                if (leader == null || id.compareTo(leader.registeredSub) < 0)
                    leader = computer;
            }

            for (UUID next : links.getOrDefault(id, Set.of()))
                if (seen.add(next))
                    queue.add(next);
        }

        return computers >= 2 ? leader : null;
    }

    private static Map<UUID, Set<UUID>> dockLinks(Level level) {
        long now = level.getGameTime();
        if (level == dockLinksLevel && now == dockLinksTick)
            return dockLinks;

        Map<UUID, Set<UUID>> links = new HashMap<>();
        Map<SectionPos, HashSet<BlockPos>> connectors = DockingConnectorBlockEntity.MAGNET_CONTROLLER.magnetMap
                .get(level);
        if (connectors != null) {
            for (HashSet<BlockPos> section : connectors.values()) {
                for (BlockPos pos : section) {
                    if (!(level.getBlockEntity(pos) instanceof DockingConnectorBlockEntity connector)
                            || !connector.isLocked() || connector.otherConnectorSubLevelId == null)
                        continue;
                    SubLevelAccess own = SableCompanion.INSTANCE.getContaining(level, pos);
                    UUID other = connector.otherConnectorSubLevelId;
                    if (own == null || own.getUniqueId().equals(other))
                        continue;

                    links.computeIfAbsent(own.getUniqueId(), id -> new HashSet<>()).add(other);
                    links.computeIfAbsent(other, id -> new HashSet<>()).add(own.getUniqueId());
                }
            }
        }

        dockLinks = links;
        dockLinksLevel = level;
        dockLinksTick = now;
        return links;
    }

    private void trim(SubLevelAccess subLevel) {
        if (subLevel == null || pumps.isEmpty() || Double.isNaN(lastFill) || activeTarget() == 0 || syncedDock == 2)
            return;

        double error = depth(subLevel) - activeTarget();
        if (Math.abs(error) > TRIM_RANGE)
            return;

        Object handle = SablePhysicsHelper.getHandle(subLevel);
        Vector3dc velocity = SablePhysicsHelper.getVelocity(handle);
        if (handle == null || velocity == null)
            return;

        double want = Mth.clamp(error * 0.6, -0.6, 0.6);
        double change = Mth.clamp(want - velocity.y(), -TRIM_ACCEL / 20, TRIM_ACCEL / 20);
        Vector3d impulse = new Vector3d(0, change * SablePhysicsHelper.readMass(subLevel), 0);
        subLevel.logicalPose().orientation().conjugate(new Quaterniond()).transform(impulse);
        SablePhysicsHelper.wakeUp(handle);
        SablePhysicsHelper.applyLinearImpulse(handle, impulse);
    }

    private double depth(SubLevelAccess subLevel) {
        return surfaceY == Integer.MIN_VALUE ? 0 : Math.max(0, surfaceY + 1 - subLevel.logicalPose().position().y());
    }

    public boolean mayDiagnose() {
        if (level == null)
            return false;

        long now = level.getGameTime();
        if (now - lastDiagnostic < DIAGNOSTIC_COOLDOWN)
            return false;

        lastDiagnostic = now;
        return true;
    }

    public void reportPump(BlockPos pump, long gameTime) {
        pumps.put(pump, gameTime);
    }

    public int pumpCommand() {
        return command;
    }

    public float pumpBoost() {
        if (registeredSub == null || pumps.isEmpty())
            return 1f;

        double needed = BallastTankBlockEntity.tankCount(registeredSub) / (pumps.size() * TANKS_PER_PUMP);
        return Math.max(1f, Math.round(needed * 4) / 4f);
    }

    public float pumpRate() {
        return RATES[speedMode];
    }

    public void apply(int action, int value) {
        if (level == null || level.isClientSide)
            return;

        if (action == CommandSubPayload.SPEED) {
            if (value < 0 || value > 2)
                return;

            speedMode = value;
        } else if (action == CommandSubPayload.DEPTH) {
            targetDepth = Math.max(0, Math.min(9999, value));
        } else if (action == CommandSubPayload.AUTOPILOT) {
            autopilot = value != 0 && SonarBlockEntity.onSameSub(this) != null;
            if (!autopilot)
                syncedAutoTarget = -1;
        } else {
            return;
        }

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide)
            LOADED_ON_CLIENT.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED_ON_CLIENT.remove(this);
        if (registeredSub != null)
            BY_SUB.remove(registeredSub, this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED_ON_CLIENT.remove(this);
        if (registeredSub != null)
            BY_SUB.remove(registeredSub, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("TargetDepth", targetDepth);
        tag.putInt("SpeedMode", speedMode);
        tag.putInt("Depth", syncedDepth);
        tag.putInt("Weakest", syncedWeakest);
        tag.putInt("Surface", surfaceY);
        tag.putInt("Fill", syncedFill);
        tag.putInt("Pumps", syncedPumps);
        tag.putInt("Status", syncedStatus);
        tag.putInt("Dock", syncedDock);
        tag.putBoolean("Autopilot", autopilot);
        tag.putInt("AutoTarget", syncedAutoTarget);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("TargetDepth"))
            targetDepth = tag.getInt("TargetDepth");
        if (tag.contains("SpeedMode"))
            speedMode = Mth.clamp(tag.getInt("SpeedMode"), 0, RATES.length - 1);
        syncedDepth = tag.getInt("Depth");
        syncedWeakest = tag.contains("Weakest") ? tag.getInt("Weakest") : -1;
        surfaceY = tag.contains("Surface") ? tag.getInt("Surface") : Integer.MIN_VALUE;
        syncedFill = tag.contains("Fill") ? tag.getInt("Fill") : -1;
        syncedPumps = tag.getInt("Pumps");
        syncedStatus = tag.contains("Status") ? tag.getInt("Status") : 1;
        syncedDock = tag.getInt("Dock");
        autopilot = tag.getBoolean("Autopilot");
        syncedAutoTarget = tag.contains("AutoTarget") ? tag.getInt("AutoTarget") : -1;
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
}
