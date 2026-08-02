package com.maxenonyme.createsubmarine.submarine.item.harpoon_gun;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.util.HarpoonRopeHandler;
import com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;

import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.UUID;

public class HarpoonEntity extends Projectile {

    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(
        HarpoonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_STUCK = SynchedEntityData.defineId(
        HarpoonEntity.class, EntityDataSerializers.BOOLEAN);

    private int life;
    private UUID ownerUUID;
    private int ownerId;

    public HarpoonEntity(EntityType<? extends HarpoonEntity> type, Level level) {
        super(type, level);
    }

    public HarpoonEntity(Level level, Player player) {
        super(CreateSubmarine.HARPOON_ENTITY.get(), level);
        this.ownerUUID = player.getUUID();
        this.ownerId = player.getId();
        this.setOwner(player);
        Vec3 pos = player.getEyePosition().add(player.getLookAngle().scale(0.5));
        this.setPos(pos);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, -1);
        builder.define(DATA_STUCK, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) this.ownerUUID = tag.getUUID("Owner");
        this.life = tag.getInt("Life");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        tag.putInt("Life", this.life);
    }

    public boolean isStuck() {
        return this.entityData.get(DATA_STUCK);
    }

    public void setStuck(boolean stuck) {
        this.entityData.set(DATA_STUCK, stuck);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            if (this.isStuck()) {
                this.life++;
                if (this.life > 600) {
                    removeRope();
                    this.discard();
                }
                return;
            }

            HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hitResult.getType() != HitResult.Type.MISS) {
                this.onHit(hitResult);
            }

            createRopeIfNeeded();

            this.setDeltaMovement(this.getDeltaMovement().add(0, -0.03, 0));
        }

        Vec3 vec3 = this.getDeltaMovement();
        double d = this.getX() + vec3.x;
        double e = this.getY() + vec3.y;
        double f = this.getZ() + vec3.z;
        this.setPos(d, e, f);
    }

    private void createRopeIfNeeded() {
        if (this.level() instanceof ServerLevel serverLevel && ownerUUID != null) {
            HarpoonRopeHandler.getOrCreate(serverLevel).createRopeIfNeeded(this, serverLevel);
        }
    }

    private void removeRope() {
        if (this.level() instanceof ServerLevel serverLevel) {
            HarpoonRopeHandler.getOrCreate(serverLevel).removeRope(this);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        if (target instanceof Player player && player.getUUID().equals(ownerUUID)) {
            return;
        }
        if (target instanceof LivingEntity living) {
            living.hurt(this.damageSources().thrown(this, this.getOwner()), 8.0F);
        }
        this.setStuck(true);
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        this.setStuck(true);
        this.setDeltaMovement(Vec3.ZERO);

        if (this.level() instanceof ServerLevel serverLevel) {
            UUID subId = SubLevelRegistry.findUUID(serverLevel, result.getBlockPos());
            if (subId != null) {
                applyPullToSubLevel(serverLevel, subId);
            }
        }
    }

    private void applyPullToSubLevel(ServerLevel level, UUID subLevelId) {
        SubLevelAccess sub = SubLevelRegistry.getAll().get(subLevelId);
        if (sub == null) return;

        Object handle = SablePhysicsHelper.getHandle(sub);
        if (handle == null) return;

        double mass = SablePhysicsHelper.readMass(sub);
        if (mass <= 0) return;

        Player owner = this.getOwner() instanceof Player p ? p : null;
        if (owner == null) return;

        Vec3 dir = new Vec3(owner.getX() - this.getX(), owner.getY() - this.getY(), owner.getZ() - this.getZ()).normalize();
        double forceMagnitude = Math.min(mass * 10.0, 50000.0);
        Vector3d impulse = new Vector3d(dir.x * forceMagnitude, dir.y * forceMagnitude, dir.z * forceMagnitude);

        SablePhysicsHelper.wakeUp(handle);
        SablePhysicsHelper.applyLinearImpulse(handle, impulse);
    }

    protected boolean canHitEntity(Entity entity) {
        if (entity instanceof Player player && player.getUUID().equals(ownerUUID)) {
            return false;
        }
        return entity.isPickable() && !entity.isSpectator();
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }
}
