package com.maxenonyme.highseas.block.entity;

import com.maxenonyme.highseas.CreateHighSeas;
import com.maxenonyme.highseas.block.BuoyBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;
import net.minecraft.util.Mth;

public class BuoySeatEntity extends Entity {

    private static final float HEAD_SWEEP = 65.0f;

    private static final EntityDataAccessor<BlockPos> BUOY = SynchedEntityData.defineId(BuoySeatEntity.class,
            EntityDataSerializers.BLOCK_POS);

    public BuoySeatEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setInvisible(true);
    }

    public BuoySeatEntity(Level level) {
        this(CreateHighSeas.BUOY_SEAT.get(), level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BUOY, BlockPos.ZERO);
    }

    public void setBuoy(BlockPos pos) {
        entityData.set(BUOY, pos);
    }

    public BlockPos getBuoyPos() {
        return entityData.get(BUOY);
    }

    @Override
    public void tick() {
        if (level().isClientSide)
            return;
        if (!isVehicle()) {
            discard();
            return;
        }
        if (!(level().getBlockState(getBuoyPos()).getBlock() instanceof BuoyBlock))
            discard();
    }

    @Override
    public void setDeltaMovement(Vec3 motion) {
    }

    @Override
    protected void positionRider(Entity rider, Entity.MoveFunction callback) {
        if (hasPassenger(rider))
            callback.accept(rider, getX(), getY(), getZ());
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        return null;
    }

    @Override
    protected boolean canAddPassenger(Entity entity) {
        return getPassengers().isEmpty();
    }

    @Override
    protected boolean canRide(Entity entity) {
        return !(entity instanceof FakePlayer);
    }

    @Override
    public void onPassengerTurned(Entity rider) {
        hold(rider);
    }

    @Override
    protected void addPassenger(Entity rider) {
        super.addPassenger(rider);
        rider.setYRot(getYRot());
        rider.setYHeadRot(getYRot());
        hold(rider);
    }

    private void hold(Entity rider) {
        rider.setYBodyRot(getYRot());
        float drift = Mth.wrapDegrees(rider.getYRot() - getYRot());
        float allowed = Mth.clamp(drift, -HEAD_SWEEP, HEAD_SWEEP);
        rider.yRotO += allowed - drift;
        rider.setYRot(rider.getYRot() + allowed - drift);
        rider.setYHeadRot(rider.getYRot());
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity rider) {
        return position().add(0.0, 0.6, 0.0);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Buoy"))
            entityData.set(BUOY, BlockPos.of(tag.getLong("Buoy")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("Buoy", getBuoyPos().asLong());
    }
}
