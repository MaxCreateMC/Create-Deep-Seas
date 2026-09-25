package com.maxenonyme.highseas.block.entity;

import com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper;
import com.maxenonyme.highseas.BoatBuoyancySystem;
import com.maxenonyme.highseas.CreateHighSeas;
import com.maxenonyme.highseas.block.BoatEngineBlock;
import com.maxenonyme.highseas.helm.EngineStatePayload;
import com.maxenonyme.highseas.helm.HelmInput;
import com.maxenonyme.highseas.helm.HelmSeatEntity;
import com.maxenonyme.highseas.helm.HelmServer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.util.Mth;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;
import com.maxenonyme.highseas.client.BoatEngineSoundHandler;
import com.maxenonyme.highseas.config.HighSeasConfig;

public class BoatEngineBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {

    private static final double RUDDER_ACCEL = 0.45;
    private static final double PROP_REACH = 1.0;
    private static final double PROP_BAND = 1.5;
    private static final float THROTTLE_RAMP_UP = 0.018f;
    private static final float THROTTLE_RAMP_DOWN = 0.045f;
    private static final float MAX_REVERSE_THROTTLE = 0.60f;
    private static final float STEER_RAMP = 0.10f;
    private static final float STEER_RETURN = 0.16f;
    private static final double TRIM_LEVER = 0.45;
    private static final int SYNC_INTERVAL = 10;

    private static Holder<SoundEvent> activateSound;
    private static Holder<SoundEvent> puffSound;
    private static boolean soundsResolved;

    private final SimpleContainer fuelSlot = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            BoatEngineBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return level != null && level.getBlockEntity(worldPosition) == BoatEngineBlockEntity.this
                    && Sable.HELPER.distanceSquaredWithSubLevels(level, player.getEyePosition(),
                            Vec3.atCenterOf(worldPosition)) <= 64;
        }
    };
    private int burnTime;
    private int burnDuration;
    private boolean enabled = true;

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> burnTime;
                case 1 -> burnDuration;
                default -> enabled ? 1 : 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> burnTime = value;
                case 1 -> burnDuration = value;
                default -> enabled = value != 0;
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    private float throttle;
    private float steer;
    private UUID driverId;
    private float lastSyncThrottle;
    private float lastSyncSteer;
    private int syncCooldown;
    private int throttleCap = 15;

    public float renderPropAngle;
    public float renderSteer;
    public long renderLastNanos;
    private boolean syncDirty = true;

    private final Vector3d forward = new Vector3d();
    private final Vector3d propWorld = new Vector3d();
    private final Vector3d velocity = new Vector3d();
    private final Vector3d push = new Vector3d();
    private final Vector3d at = new Vector3d();
    private final Quaterniond inverse = new Quaterniond();

    public BoatEngineBlockEntity(BlockPos pos, BlockState state) {
        super(CreateHighSeas.BOAT_ENGINE_BE.get(), pos, state);
    }

    public boolean hasFuel() {
        return enabled && burnTime > 0;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggleEnabled() {
        enabled = !enabled;
        if (!enabled) {
            clearDriver();
        }
        setChanged();
    }

    public SimpleContainer getFuelSlot() {
        return fuelSlot;
    }

    public boolean insertFuel(Player player, InteractionHand hand) {
        if (level == null || level.isClientSide)
            return false;
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || held.getBurnTime(null) <= 0)
            return false;
        ItemStack current = fuelSlot.getItem(0);
        if (current.isEmpty()) {
            fuelSlot.setItem(0, held.split(held.getCount()));
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(current, held))
            return false;
        int room = Math.min(current.getMaxStackSize(), fuelSlot.getMaxStackSize()) - current.getCount();
        if (room <= 0)
            return false;
        int moved = Math.min(room, held.getCount());
        current.grow(moved);
        held.shrink(moved);
        fuelSlot.setChanged();
        return true;
    }

    public void dropFuel() {
        if (level == null || level.isClientSide)
            return;
        ItemStack out = fuelSlot.removeItemNoUpdate(0);
        if (!out.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, out);
        }
    }

    public boolean hasDriver() {
        return driverId != null;
    }

    public void setDriver(UUID id) {
        driverId = id;
    }

    public void clearDriver() {
        driverId = null;
    }

    public float getThrottle() {
        return throttle;
    }

    public float getSteer() {
        return steer;
    }

    public int getThrottleCap() {
        return throttleCap;
    }

    public void setThrottleCap(int cap) {
        int clamped = Math.max(0, Math.min(15, cap));
        if (clamped == throttleCap)
            return;
        throttleCap = clamped;
        syncDirty = true;
        setChanged();
    }

    public void forceSync() {
        syncDirty = true;
    }

    public Vec3 helmAnchor() {
        return offset(-0.3125, -0.5, -1.75, getBlockState().getValue(BoatEngineBlock.FACING));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoatEngineBlockEntity be) {
        be.tickHelm();
        be.syncState();
        be.tickBurn();
        if (be.burnTime > 0) {
            be.tickExhaust();
        }
    }

    private void tickBurn() {
        if (!enabled) {
            setPowered(false);
            return;
        }
        if (burnTime > 0) {
            burnTime--;
        }
        if (burnTime <= 0) {
            ItemStack fuel = fuelSlot.getItem(0);
            int duration = fuel.getBurnTime(null);
            if (!fuel.isEmpty() && duration > 0) {
                burnTime = duration;
                burnDuration = duration;
                ItemStack bucket = fuel.getCraftingRemainingItem();
                fuel.shrink(1);
                if (fuel.isEmpty())
                    fuelSlot.setItem(0, bucket);
                setChanged();
            } else {
                burnDuration = 0;
            }
        }
        setPowered(burnTime > 0);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, BoatEngineBlockEntity be) {
        BoatEngineSoundHandler.tick(be);
    }

    private void tickHelm() {
        if (!(level instanceof ServerLevel sl))
            return;
        if (driverId != null) {
            Player driver = sl.getPlayerByUUID(driverId);
            if (driver == null || !(driver.getVehicle() instanceof HelmSeatEntity seat)
                    || !seat.getEnginePos().equals(worldPosition))
                driverId = null;
        }

        byte mask = driverId == null ? 0 : HelmServer.maskFor(driverId, sl.getGameTime());
        float cap = throttleCap / 15.0f;
        float throttleTarget = 0.0f;
        if (hasFuel()) {
            if (HelmInput.has(mask, HelmInput.FORWARD))
                throttleTarget = cap;
            else if (HelmInput.has(mask, HelmInput.BACKWARD))
                throttleTarget = -MAX_REVERSE_THROTTLE * cap;
        }
        float steerTarget = (HelmInput.has(mask, HelmInput.LEFT) ? 1.0f : 0.0f)
                - (HelmInput.has(mask, HelmInput.RIGHT) ? 1.0f : 0.0f);

        boolean spoolUp = Math.abs(throttleTarget) > Math.abs(throttle) && throttleTarget * throttle >= 0.0f;
        throttle = approach(throttle, throttleTarget, spoolUp ? THROTTLE_RAMP_UP : THROTTLE_RAMP_DOWN);
        steer = approach(steer, steerTarget, steerTarget == 0.0f ? STEER_RETURN : STEER_RAMP);
    }

    private static float approach(float current, float target, float step) {
        if (current < target)
            return Math.min(target, current + step);
        if (current > target)
            return Math.max(target, current - step);
        return target;
    }

    private void syncState() {
        if (!(level instanceof ServerLevel sl))
            return;
        if (syncCooldown > 0)
            syncCooldown--;
        boolean changed = Math.abs(throttle - lastSyncThrottle) >= 0.02f
                || Math.abs(steer - lastSyncSteer) >= 0.02f;
        boolean idle = throttle == 0.0f && steer == 0.0f && lastSyncThrottle == 0.0f && lastSyncSteer == 0.0f;
        if (!changed && !syncDirty && (idle || syncCooldown > 0))
            return;

        syncDirty = false;
        syncCooldown = SYNC_INTERVAL;
        lastSyncThrottle = throttle;
        lastSyncSteer = steer;

        Vector3d where = new Vector3d(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5);
        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, worldPosition);
        if (sub != null)
            sub.logicalPose().transformPosition(where);
        PacketDistributor.sendToPlayersNear(sl, null, where.x, where.y, where.z, 96.0,
                EngineStatePayload.of(worldPosition, throttle, steer, throttleCap));
    }

    private void setPowered(boolean powered) {
        if (level == null || level.isClientSide)
            return;
        BlockState state = getBlockState();
        if (!state.hasProperty(BoatEngineBlock.POWERED) || state.getValue(BoatEngineBlock.POWERED) == powered)
            return;
        level.setBlock(worldPosition, state.setValue(BoatEngineBlock.POWERED, powered), 3);
        if (powered)
            spawnActivation();
    }

    private void spawnActivation() {
        if (!(level instanceof ServerLevel sl))
            return;
        resolveSounds();
        Direction facing = getBlockState().getValue(BoatEngineBlock.FACING);
        Vec3 grille = offset(0.0, 0.15, -0.55, facing);
        sl.sendParticles(ParticleTypes.CLOUD, grille.x, grille.y, grille.z, 15, 0.12, 0.1, 0.12, 0.02);
        if (activateSound != null)
            sl.playSound(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                    activateSound, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    private void tickExhaust() {
        if (!(level instanceof ServerLevel sl) || sl.random.nextFloat() >= 0.06f)
            return;
        resolveSounds();
        Direction facing = getBlockState().getValue(BoatEngineBlock.FACING);
        Vec3 e1 = offset(-0.25, 0.55, 0.56, facing);
        Vec3 e2 = offset(0.25, 0.55, 0.56, facing);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, e1.x, e1.y, e1.z, 2, 0.02, 0.01, 0.02, 0.01);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, e2.x, e2.y, e2.z, 2, 0.02, 0.01, 0.02, 0.01);
        if (puffSound != null)
            sl.playSound(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                    puffSound, SoundSource.BLOCKS, 0.5f, 0.9f + sl.random.nextFloat() * 0.2f);
    }

    @Override
    public void sable$physicsTick(ServerSubLevel sub, RigidBodyHandle handle, double timeStep) {
        if (level == null || level.isClientSide)
            return;
        Direction facing = getBlockState().getValue(BoatEngineBlock.FACING);
        forward.set(facing.getStepX(), 0.0, facing.getStepZ());

        Pose3dc pose = sub.logicalPose();
        pose.orientation().conjugate(inverse);
        double mass = Math.max(1.0, SablePhysicsHelper.readMass(sub));

        Vec3 prop = offset(0.0, -1.15625, 0.40625, facing);
        pose.transformPosition(propWorld.set(prop.x, prop.y, prop.z));

        double surface = BoatBuoyancySystem.surfaceFor(sub.getUniqueId());
        if (surface == Double.NEGATIVE_INFINITY && level instanceof ServerLevel server) {
            surface = BoatBuoyancySystem.surfaceNear(server, propWorld.x, propWorld.y, propWorld.z);
        }
        if (surface == Double.NEGATIVE_INFINITY) {
            return;
        }
        double immersion = Mth.clamp((surface - propWorld.y + PROP_REACH) / PROP_BAND, 0.0, 1.0);
        if (immersion <= 0.0) {
            return;
        }

        inverse.transform(handle.getLinearVelocity(velocity));
        double along = velocity.x * forward.x + velocity.z * forward.z;

        if (Math.abs(throttle) > 0.001f && hasFuel()) {
            pose.orientation().transform(push.set(forward));
            push.y = 0.0;
            double bite = push.length();
            if (bite > 1.0e-3) {
                push.normalize();
                inverse.transform(push);
                double accel = HighSeasConfig.engineThrust * throttle * (throttle >= 0.0f ? 1.0 : HighSeasConfig.engineReverse) * bite * immersion;
                Vec3 lever = offset(0.0, -1.15625 * TRIM_LEVER, 0.40625, facing);
                handle.applyImpulseAtPoint(at.set(lever.x, lever.y, lever.z), push.mul(accel * mass * timeStep));
            }
        }

        if (Math.abs(steer) > 0.01f) {
            double lateral = RUDDER_ACCEL * steer * along * immersion * mass * timeStep;
            push.set(-forward.z * lateral, 0.0, forward.x * lateral);
            handle.applyImpulseAtPoint(at.set(prop.x, prop.y, prop.z), push);
        }
    }


    private Vec3 offset(double ox, double oy, double oz, Direction facing) {
        double dx;
        double dz;
        switch (facing) {
            case EAST -> {
                dx = -oz;
                dz = ox;
            }
            case SOUTH -> {
                dx = -ox;
                dz = -oz;
            }
            case WEST -> {
                dx = oz;
                dz = -ox;
            }
            default -> {
                dx = ox;
                dz = oz;
            }
        }
        return new Vec3(worldPosition.getX() + 0.5 + dx, worldPosition.getY() + 0.5 + oy,
                worldPosition.getZ() + 0.5 + dz);
    }

    private static void resolveSounds() {
        if (soundsResolved)
            return;
        soundsResolved = true;
        activateSound = resolve("simulated:block.portable_engine.activate");
        puffSound = resolve("simulated:block.portable_engine.puff");
    }

    private static Holder<SoundEvent> resolve(String path) {
        return BuiltInRegistries.SOUND_EVENT.getHolder(ResourceLocation.parse(path)).map(h -> (Holder<SoundEvent>) h)
                .orElse(null);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ItemStack fuel = fuelSlot.getItem(0);
        if (!fuel.isEmpty())
            tag.put("Fuel", fuel.save(registries));
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnDuration", burnDuration);
        tag.putBoolean("Enabled", enabled);
        tag.putByte("Cap", (byte) throttleCap);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fuelSlot.setItem(0, tag.contains("Fuel") ? ItemStack.parseOptional(registries, tag.getCompound("Fuel"))
                : ItemStack.EMPTY);
        burnTime = tag.getInt("BurnTime");
        burnDuration = tag.getInt("BurnDuration");
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
        throttleCap = tag.contains("Cap") ? Math.max(0, Math.min(15, tag.getByte("Cap"))) : 15;
    }
}
