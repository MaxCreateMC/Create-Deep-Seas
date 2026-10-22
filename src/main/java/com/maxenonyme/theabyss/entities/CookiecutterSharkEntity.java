package com.maxenonyme.theabyss.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class CookiecutterSharkEntity extends WaterAnimal {
    private static final EntityDataAccessor<Boolean> DATA_LATCHED =
            SynchedEntityData.defineId(CookiecutterSharkEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_TARGET_ID =
            SynchedEntityData.defineId(CookiecutterSharkEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_LATCH_YAW =
            SynchedEntityData.defineId(CookiecutterSharkEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LATCH_HEIGHT =
            SynchedEntityData.defineId(CookiecutterSharkEntity.class, EntityDataSerializers.FLOAT);

    private static final int STRUGGLE_TO_DETACH = 3;
    private static final int LATCH_DAMAGE_INTERVAL = 20;
    private static final int LATCH_ANIM_TICKS = 35;
    private static final float LATCH_DAMAGE = 1.0F;

    public final AnimationState swimAnimationState = new AnimationState();
    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState latchAnimationState = new AnimationState();
    public final AnimationState latchIdleAnimationState = new AnimationState();

    private int chargeCooldown = 100;
    private int struggleHits = 0;
    private int latchDamageTimer = 0;
    private int clientLatchStartTick = -1;

    public CookiecutterSharkEntity(EntityType<? extends WaterAnimal> type, Level level) {
        super(type, level);
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LATCHED, false);
        builder.define(DATA_TARGET_ID, -1);
        builder.define(DATA_LATCH_YAW, 0.0F);
        builder.define(DATA_LATCH_HEIGHT, 0.0F);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new ChargePlayerGoal(this));
        this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0, 12));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    public boolean isLatched() {
        return this.entityData.get(DATA_LATCHED);
    }

    public int getLatchTargetId() {
        return this.entityData.get(DATA_TARGET_ID);
    }

    public boolean isLatchedTo(Player player) {
        return isLatched() && getLatchTargetId() == player.getId();
    }

    public int getChargeCooldown() {
        return chargeCooldown;
    }

    public void setChargeCooldown(int ticks) {
        this.chargeCooldown = ticks;
    }

    private float getLatchYaw() {
        return this.entityData.get(DATA_LATCH_YAW);
    }

    private float getLatchHeight() {
        return this.entityData.get(DATA_LATCH_HEIGHT);
    }

    public void latchOnto(Player player) {
        if (isLatched())
            return;
        this.entityData.set(DATA_TARGET_ID, player.getId());
        float yaw = this.random.nextFloat() * Mth.TWO_PI;
        float height = 0.2F + this.random.nextFloat() * Math.max(0.1F, player.getBbHeight() - 0.4F);
        this.entityData.set(DATA_LATCH_YAW, yaw);
        this.entityData.set(DATA_LATCH_HEIGHT, height);
        this.entityData.set(DATA_LATCHED, true);
        this.struggleHits = 0;
        this.latchDamageTimer = LATCH_DAMAGE_INTERVAL;
        this.setNoAi(true);
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.getNavigation().stop();
        this.playSound(SoundEvents.GUARDIAN_ATTACK, 1.0F, 1.4F);
    }

    public void detach() {
        if (!isLatched())
            return;
        this.entityData.set(DATA_LATCHED, false);
        this.entityData.set(DATA_TARGET_ID, -1);
        this.struggleHits = 0;
        this.noPhysics = false;
        if (!level().isClientSide) {
            this.setNoAi(false);
            this.setNoGravity(false);
            this.setChargeCooldown(300 + this.random.nextInt(400));
        }
    }

    public void addStruggle() {
        if (!isLatched())
            return;
        this.struggleHits++;
        this.playSound(SoundEvents.COD_FLOP, 0.8F, 1.2F);
        if (this.struggleHits >= STRUGGLE_TO_DETACH) {
            detach();
        }
    }

    @Override
    public void tick() {
        this.noPhysics = isLatched();
        super.tick();

        if (isLatched()) {
            Entity target = level().getEntity(getLatchTargetId());
            if (target instanceof Player player && player.isAlive() && !player.isSpectator()) {
                stickTo(player);
                if (!level().isClientSide) {
                    setAirSupply(getMaxAirSupply());
                    if (--latchDamageTimer <= 0) {
                        latchDamageTimer = LATCH_DAMAGE_INTERVAL;
                        player.hurt(level().damageSources().mobAttack(this), LATCH_DAMAGE);
                    }
                }
            } else if (!level().isClientSide) {
                detach();
            }
        } else if (!level().isClientSide && chargeCooldown > 0) {
            chargeCooldown--;
        }

        if (level().isClientSide) {
            updateAnimations();
        }
    }

    private void stickTo(Player player) {
        double r = player.getBbWidth() * 0.5 + 0.18;
        double yaw = getLatchYaw();
        double ox = Math.cos(yaw) * r;
        double oz = Math.sin(yaw) * r;
        double px = player.getX() + ox;
        double py = player.getY() + getLatchHeight();
        double pz = player.getZ() + oz;
        this.setPos(px, py, pz);
        this.setDeltaMovement(Vec3.ZERO);

        float faceYaw = (float) (Mth.atan2(player.getZ() - pz, player.getX() - px) * (180.0 / Math.PI)) - 90.0F;
        this.setYRot(faceYaw);
        this.yRotO = faceYaw;
        this.setYBodyRot(faceYaw);
        this.yHeadRot = faceYaw;
        this.yHeadRotO = faceYaw;
    }

    private void updateAnimations() {
        if (isLatched()) {
            this.swimAnimationState.stop();
            this.idleAnimationState.stop();
            if (clientLatchStartTick < 0) {
                clientLatchStartTick = this.tickCount;
            }
            if (this.tickCount - clientLatchStartTick < LATCH_ANIM_TICKS) {
                this.latchAnimationState.startIfStopped(this.tickCount);
                this.latchIdleAnimationState.stop();
            } else {
                this.latchIdleAnimationState.startIfStopped(this.tickCount);
                this.latchAnimationState.stop();
            }
        } else {
            clientLatchStartTick = -1;
            this.latchAnimationState.stop();
            this.latchIdleAnimationState.stop();
            if (this.isInWaterOrBubble()) {
                this.swimAnimationState.startIfStopped(this.tickCount);
                this.idleAnimationState.stop();
            } else {
                this.idleAnimationState.startIfStopped(this.tickCount);
                this.swimAnimationState.stop();
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ChargeCooldown", chargeCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("ChargeCooldown")) {
            chargeCooldown = tag.getInt("ChargeCooldown");
        }

        this.setNoAi(false);
        this.setNoGravity(false);
        this.noPhysics = false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.COD_AMBIENT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COD_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.COD_HURT;
    }

    public static boolean checkSpawnRules(EntityType<? extends WaterAnimal> type, LevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    private static class ChargePlayerGoal extends Goal {
        private final CookiecutterSharkEntity shark;
        private Player target;

        ChargePlayerGoal(CookiecutterSharkEntity shark) {
            this.shark = shark;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (shark.isLatched() || shark.getChargeCooldown() > 0)
                return false;
            Player player = shark.level().getNearestPlayer(shark, 16.0);
            if (player == null || !player.isAlive() || player.isSpectator() || player.isCreative())
                return false;
            if (!player.isInWaterOrBubble())
                return false;
            if (shark.getRandom().nextInt(120) != 0)
                return false;
            this.target = player;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && target.isAlive() && !shark.isLatched()
                    && target.isInWaterOrBubble()
                    && shark.distanceToSqr(target) < 36.0 * 36.0;
        }

        @Override
        public void stop() {
            shark.setChargeCooldown(200 + shark.getRandom().nextInt(400));
            this.target = null;
        }

        @Override
        public void tick() {
            if (target == null)
                return;
            shark.getLookControl().setLookAt(target, 30.0F, 30.0F);
            Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
            shark.getNavigation().moveTo(aim.x, aim.y, aim.z, 1.5);
            Vec3 dir = aim.subtract(shark.position()).normalize();
            shark.setDeltaMovement(shark.getDeltaMovement().add(dir.scale(0.05)));
            if (shark.distanceToSqr(target) < 1.6) {
                shark.latchOnto(target);
            }
        }
    }
}
