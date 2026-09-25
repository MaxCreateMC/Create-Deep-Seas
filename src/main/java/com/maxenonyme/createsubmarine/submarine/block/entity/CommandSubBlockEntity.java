package com.maxenonyme.createsubmarine.submarine.block.entity;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import com.maxenonyme.createsubmarine.submarine.system.SubmarinePressureSystem;
import com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import com.maxenonyme.createsubmarine.submarine.network.CommandSubPayload;

public class CommandSubBlockEntity extends BlockEntity {
    public static final Set<CommandSubBlockEntity> LOADED_ON_CLIENT = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<UUID, CommandSubBlockEntity> BY_SUB = new ConcurrentHashMap<>();

    public int targetDepth = 20;
    public int speedMode = 1;

    public int syncedDepth;
    public int syncedWeakest = -1;
    public int surfaceY = Integer.MIN_VALUE;

    public double lastY = Double.NaN;
    public long lastNanos;
    public float shownVs;

    public int syncedFill = -1;
    public int syncedPumps;
    public int syncedStatus = 1;

    private static final float[] RATES = { 0.33f, 0.66f, 1f };
    private static final double BRAKING_MARGIN = 0.6;
    private static final double TRIM_RANGE = 2;
    private static final double TRIM_ACCEL = 1.5;

    private int tickCount;
    private UUID registeredSub;
    private int cachedWeakest = -1;
    private int command = PumpControllerBlockEntity.HOLD;
    private final Map<BlockPos, Long> pumps = new HashMap<>();
    private double lastFill = Double.NaN;
    private long lastFillTick;
    private double fillRate = 0.02;

    public CommandSubBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSubmarine.COMMAND_SUB_BE.get(), pos, state);
    }

    public void tick() {
        if (level == null || level.isClientSide)
            return;
        tickCount++;
        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, worldPosition);
        claim(sub == null ? null : sub.getUniqueId());
        if (tickCount % 10 == 0)
            measure(sub);
        if (tickCount % 5 == 0)
            steer(sub);
        trim(sub);
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

    private void measure(SubLevelAccess sub) {
        int depth = 0;
        int weakest = -1;
        int surface = Integer.MIN_VALUE;
        if (sub != null) {
            Level ocean = sub instanceof SubLevel sl && sl.getLevel() != null ? sl.getLevel() : level;
            Vector3dc center = sub.logicalPose().position();
            surface = SubmarinePressureSystem.measureSurfaceY(ocean, center);
            if (surface != Integer.MIN_VALUE)
                depth = Math.max(0, surface - (int) Math.floor(center.y()));
            if (tickCount % 40 == 0)
                cachedWeakest = SubmarinePressureSystem.getWeakestHullDepth(sub.getUniqueId(), level);
            weakest = cachedWeakest;
        }

        if (depth != syncedDepth || weakest != syncedWeakest || surface != surfaceY) {
            syncedDepth = depth;
            syncedWeakest = weakest;
            surfaceY = surface;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void steer(SubLevelAccess sub) {
        long now = level.getGameTime();
        pumps.values().removeIf(seen -> now - seen > 40);

        int wanted = PumpControllerBlockEntity.HOLD;
        double fill = sub == null ? Double.NaN : BallastTankBlockEntity.fillRatio(sub.getUniqueId());
        if (!Double.isNaN(fill)) {
            if (!Double.isNaN(lastFill) && command != PumpControllerBlockEntity.HOLD && now > lastFillTick) {
                double sample = Math.abs(fill - lastFill) / ((now - lastFillTick) / 20.0);
                if (sample > 0)
                    fillRate += (sample - fillRate) * 0.3;
            }
            lastFill = fill;
            lastFillTick = now;
        }
        if (sub != null && !Double.isNaN(fill)) {
            double error = depth(sub) - targetDepth;
            double maxSpeed = SubmarineConfig.BALLAST_VERTICAL_SPEED.get();
            double modeSpeed = maxSpeed * RATES[speedMode];
            double braking = Math.max(0.02, 2 * maxSpeed * fillRate) * BRAKING_MARGIN;
            double wantSpeed = Math.signum(error) * Math.min(modeSpeed, Math.sqrt(2 * braking * Math.abs(error)));
            double wantFill = Mth.clamp(0.5 - wantSpeed / (2 * maxSpeed), 0, 1);
            if (fill < wantFill - 0.01)
                wanted = PumpControllerBlockEntity.FILL;
            else if (fill > wantFill + 0.01)
                wanted = PumpControllerBlockEntity.DRAIN;
        }
        command = wanted;

        int percent = Double.isNaN(fill) ? -1 : (int) Math.round(fill * 100);
        int status = command + 1;
        if (percent != syncedFill || pumps.size() != syncedPumps || status != syncedStatus) {
            syncedFill = percent;
            syncedPumps = pumps.size();
            syncedStatus = status;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void trim(SubLevelAccess sub) {
        if (sub == null || pumps.isEmpty() || Double.isNaN(lastFill) || targetDepth == 0)
            return;
        double error = depth(sub) - targetDepth;
        if (Math.abs(error) > TRIM_RANGE)
            return;
        Object handle = SablePhysicsHelper.getHandle(sub);
        Vector3dc velocity = SablePhysicsHelper.getVelocity(handle);
        if (handle == null || velocity == null)
            return;
        double want = Mth.clamp(error * 0.6, -0.6, 0.6);
        double change = Mth.clamp(want - velocity.y(), -TRIM_ACCEL / 20, TRIM_ACCEL / 20);
        Vector3d impulse = new Vector3d(0, change * SablePhysicsHelper.readMass(sub), 0);
        sub.logicalPose().orientation().conjugate(new Quaterniond()).transform(impulse);
        SablePhysicsHelper.wakeUp(handle);
        SablePhysicsHelper.applyLinearImpulse(handle, impulse);
    }

    private double depth(SubLevelAccess sub) {
        return surfaceY == Integer.MIN_VALUE ? 0 : Math.max(0, surfaceY + 1 - sub.logicalPose().position().y());
    }

    public void reportPump(BlockPos pump, long gameTime) {
        pumps.put(pump, gameTime);
    }

    public int pumpCommand() {
        return command;
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
