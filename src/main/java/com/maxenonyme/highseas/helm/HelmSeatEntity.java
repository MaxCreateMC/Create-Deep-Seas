package com.maxenonyme.highseas.helm;

import com.maxenonyme.highseas.CreateHighSeas;
import com.maxenonyme.highseas.block.BoatEngineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

public class HelmSeatEntity extends Entity {

    private static final EntityDataAccessor<BlockPos> ENGINE = SynchedEntityData.defineId(HelmSeatEntity.class,
            EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Direction> FACING = SynchedEntityData.defineId(HelmSeatEntity.class,
            EntityDataSerializers.DIRECTION);

    public HelmSeatEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setInvisible(true);
    }

    public HelmSeatEntity(Level level) {
        this(CreateHighSeas.HELM_SEAT.get(), level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ENGINE, BlockPos.ZERO);
        builder.define(FACING, Direction.NORTH);
    }

    public void setEngine(BlockPos pos, Direction facing) {
        entityData.set(ENGINE, pos);
        entityData.set(FACING, facing);
    }

    public BlockPos getEnginePos() {
        return entityData.get(ENGINE);
    }

    public Direction getFacing() {
        return entityData.get(FACING);
    }

    @Override
    public void tick() {
        if (level().isClientSide)
            return;
        if (!isVehicle()) {
            discard();
            return;
        }
        if (!(level().getBlockState(getEnginePos()).getBlock() instanceof BoatEngineBlock))
            discard();
    }

    @Override
    public void setDeltaMovement(Vec3 motion) {
    }

    @Override
    protected void positionRider(Entity rider, Entity.MoveFunction callback) {
        if (hasPassenger(rider)) {
            callback.accept(rider, getX(), getY(), getZ());
            pinBody(rider);
        }
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        pinBody(passenger);
    }

    private void pinBody(Entity rider) {
        float yaw = getFacing().toYRot();
        rider.setYBodyRot(yaw);
        if (rider instanceof LivingEntity living)
            living.yBodyRotO = yaw;
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
    public Vec3 getDismountLocationForPassenger(LivingEntity rider) {
        Direction f = getFacing();
        return position().add(f.getStepX() * 0.6, 0.15, f.getStepZ() * 0.6);
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
        if (tag.contains("Engine"))
            entityData.set(ENGINE, BlockPos.of(tag.getLong("Engine")));
        if (tag.contains("Facing"))
            entityData.set(FACING, Direction.from2DDataValue(tag.getByte("Facing")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("Engine", getEnginePos().asLong());
        tag.putByte("Facing", (byte) getFacing().get2DDataValue());
    }
}
