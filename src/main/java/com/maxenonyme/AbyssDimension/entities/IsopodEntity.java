package com.maxenonyme.AbyssDimension.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.TryFindWaterGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

public class IsopodEntity extends WaterAnimal {
    private static final EntityDataAccessor<Integer> DATA_SIZE = SynchedEntityData.defineId(IsopodEntity.class, EntityDataSerializers.INT);

    public final AnimationState burstAnimationState = new AnimationState();

    private final WaterBoundPathNavigation waterNavigation;
    private final GroundPathNavigation groundNavigation;
    private int burstCooldown;
    public boolean dashing;
    private boolean wasMovingFast;
    private boolean sizeInitialized;
    private int scaredTimer;
    private boolean dashingUp;

    public IsopodEntity(EntityType<? extends WaterAnimal> type, Level level) {
        super(type, level);
        this.waterNavigation = new WaterBoundPathNavigation(this, level);
        this.groundNavigation = new GroundPathNavigation(this, level);
        this.navigation = this.groundNavigation;
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 1.0)
            .add(Attributes.MOVEMENT_SPEED, 0.12);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new TryFindWaterGoal(this));
        this.goalSelector.addGoal(1, new IsopodAttackGoal());
        this.goalSelector.addGoal(2, new RandomSwimmingGoal(this, 1.0, 40));
        this.goalSelector.addGoal(3, new RandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, Drowned.class, true));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Guardian.class, true));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return this.groundNavigation;
    }

    @Override
    public void tick() {
        super.tick();
        if (!sizeInitialized && !this.level().isClientSide()) {
            sizeInitialized = true;
            this.setSize(this.entityData.get(DATA_SIZE), true);
        }
        if (this.level().isClientSide()) {
            this.updateAnimations();
        }
    }

    @Override
    public void aiStep() {
        this.navigation = this.onGround()
            ? this.groundNavigation
            : (this.isInWaterOrBubble() || this.isInLava() ? this.waterNavigation : this.groundNavigation);
        super.aiStep();
        if (!this.level().isClientSide()) {
            if (scaredTimer > 0) {
                scaredTimer--;
                if (scaredTimer == 0) {
                    Player nearest = this.level().getNearestPlayer(this, 10.0);
                    if (nearest != null && this.distanceTo(nearest) <= 10.0) {
                        scaredTimer = 20;
                    }
                }
            }

            if (this.isInWater() || this.isInLava()) {
                Vec3 vel = this.getDeltaMovement();
                if (scaredTimer > 0 && dashingUp) {
                    if (this.getY() < this.level().getSeaLevel() - 2) {
                        this.setDeltaMovement(vel.x, Math.min(vel.y + 0.03, 0.4), vel.z);
                    }
                } else if (scaredTimer == 0) {
                    if (!this.onGround() && vel.y > -0.02) {
                        this.setDeltaMovement(vel.x, Math.max(vel.y - 0.005, -0.02), vel.z);
                    }
                }
            }

            if (this.burstCooldown > 0) {
                this.burstCooldown--;
                if (this.burstCooldown == 35) {
                    float size = (float)this.getSize();
                    float dashSpeed = (float)(0.8 * size + 0.5);
                    Vec3 dir = this.getLookAngle();
                    this.setDeltaMovement(dir.x * dashSpeed, this.dashingUp ? 0.4 : 0.0, dir.z * dashSpeed);
                    this.hurtMarked = true;
                    this.dashing = true;
                }
                if (this.burstCooldown < 35) {
                    this.dashing = false;
                }
            }
        }
    }

    @Override
    public void setTarget(LivingEntity target) {
        LivingEntity old = this.getTarget();
        super.setTarget(target);
        if (old == null && target != null && !this.level().isClientSide()) {
            this.level().getEntitiesOfClass(IsopodEntity.class,
                this.getBoundingBox().inflate(50.0),
                other -> other != this && other.getTarget() == null)
            .forEach(other -> other.setTarget(target));
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result && !this.level().isClientSide() && (this.isInWater() || this.isInLava())) {
            if (source.getEntity() != null) {
                Vec3 away = this.position().subtract(source.getEntity().position()).normalize();
                this.setYRot((float)(Mth.atan2(away.z, away.x) * (180D / Math.PI)) - 90.0F);
                this.yBodyRot = this.getYRot();
                this.yHeadRot = this.getYRot();
            }
            this.setDeltaMovement(Vec3.ZERO);
            this.dashing = false;
            this.dashingUp = true;
            this.burstCooldown = 40;
            this.scaredTimer = 600;
        }
        return result;
    }

    @Override
    public void push(Entity entity) {
        super.push(entity);
        if (!this.level().isClientSide() && this.dashing && entity instanceof LivingEntity hit && !(hit instanceof IsopodEntity)) {
            float speed = (float)this.getDeltaMovement().length();
            if (speed > 0.4F) {
                int damage = (int)(this.getSize() * Math.max(speed, 1.0F) * 4.0F);
                hit.hurt(this.damageSources().mobAttack(this), damage);
            }
        }
    }

    @Override
    protected void handleAirSupply(int air) {
        this.setAirSupply(300);
    }

    private void updateAnimations() {
        Vec3 vel = this.getDeltaMovement();
        boolean movingFast = vel.length() > 0.6;
        if (this.isInWater() || this.isInLava()) {
            if (movingFast && !wasMovingFast) {
                this.burstAnimationState.start(this.tickCount);
            }
            if (!movingFast) {
                this.burstAnimationState.stop();
            }
            wasMovingFast = movingFast;
        } else {
            this.burstAnimationState.stop();
            wasMovingFast = false;
        }
    }

    public int getSize() {
        return this.entityData.get(DATA_SIZE);
    }

    public void setSize(int size, boolean resetHealth) {
        int clamped = Mth.clamp(size, 1, 4);
        this.entityData.set(DATA_SIZE, clamped);
        this.refreshDimensions();
        var attr = this.getAttribute(Attributes.MAX_HEALTH);
        float hp = (float)(clamped * clamped);
        if (attr != null) {
            attr.setBaseValue((double)hp);
        }
        if (resetHealth) {
            this.setHealth(hp);
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (DATA_SIZE.equals(accessor)) {
            this.refreshDimensions();
        }
        super.onSyncedDataUpdated(accessor);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return super.getDefaultDimensions(pose).scale((float)this.getSize());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SIZE, 1);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Size", this.getSize());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setSize(tag.contains("Size") ? tag.getInt("Size") : 1, true);
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.TROPICAL_FISH_AMBIENT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.TROPICAL_FISH_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.TROPICAL_FISH_HURT;
    }

    @Override
    public int getMaxHeadYRot() {
        return 0;
    }

    @Override
    public int getMaxHeadXRot() {
        return 0;
    }

    public static boolean checkSpawnRules(EntityType<? extends WaterAnimal> type, LevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return level.getFluidState(pos).is(FluidTags.WATER) && pos.getY() < 48;
    }

    private class IsopodAttackGoal extends Goal {
        private int retreatTimer;

        IsopodAttackGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = IsopodEntity.this.getTarget();
            return target != null && target.isAlive()
                && (IsopodEntity.this.isInWaterOrBubble() || IsopodEntity.this.isInLava());
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = IsopodEntity.this.getTarget();
            return target != null && target.isAlive() && !IsopodEntity.this.isDeadOrDying();
        }

        @Override
        public void start() {
            retreatTimer = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = IsopodEntity.this.getTarget();
            if (target == null) return;

            if (retreatTimer > 0) {
                retreatTimer--;
                Vec3 away = IsopodEntity.this.position().subtract(target.position()).normalize().scale(10.0);
                Vec3 retreatPos = IsopodEntity.this.position().add(away);
                IsopodEntity.this.getNavigation().moveTo(retreatPos.x, retreatPos.y, retreatPos.z, 0.8);
                return;
            }

            double dist = IsopodEntity.this.distanceToSqr(target);
            if (dist < 49.0) {
                Vec3 dir = target.position().subtract(IsopodEntity.this.position()).normalize();
                float size = (float)IsopodEntity.this.getSize();
                float dashSpeed = (float)(0.8 * size + 0.5);
                IsopodEntity.this.setDeltaMovement(
                    dir.x * dashSpeed,
                    dir.y * dashSpeed * 0.5F + 0.1F,
                    dir.z * dashSpeed
                );
                IsopodEntity.this.hurtMarked = true;
                IsopodEntity.this.dashing = true;
                retreatTimer = 30;
            } else {
                Vec3 dirToTarget = target.position().subtract(IsopodEntity.this.position()).normalize();
                Vec3 aim = target.position().add(dirToTarget.scale(4.0));
                IsopodEntity.this.getNavigation().moveTo(aim.x, aim.y, aim.z, 1.0);
            }
        }
    }
}
