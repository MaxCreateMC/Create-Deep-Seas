package com.maxenonyme.AbyssDimension.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.TryFindWaterGoal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class AbyssalCuttlefishEntity extends WaterAnimal {
    public final AnimationState swimAnimationState = new AnimationState();
    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState biteAnimationState = new AnimationState();
    public final AnimationState jetUseAnimationState = new AnimationState();

    private static final byte BITE_EVENT_ID = 67;

    public float camouflageAlpha = 0.85F;
    private float targetAlpha = 0.85F;
    private int biteCooldown = 0;

    public AbyssalCuttlefishEntity(EntityType<? extends WaterAnimal> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new TryFindWaterGoal(this));
        this.goalSelector.addGoal(1, new RandomSwimmingGoal(this, 0.5, 40));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.15);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            tickClient();
        } else {
            tickServer();
        }
    }

    private void tickClient() {
        updateCamouflageAlpha();
        updateAnimations();
        if (this.hurtTime > 0) {
            spawnInkCloud();
        }
    }

    private void tickServer() {
        if (biteCooldown > 0) biteCooldown--;

        if (biteCooldown == 0) {
            List<Mob> nearbyMobs = this.level().getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(5.0),
                m -> m != this && m.isAlive());
            for (Mob target : nearbyMobs) {
                if (this.distanceToSqr(target) < 16.0) {
                    target.hurt(this.damageSources().mobAttack(this), 6.0F);
                    biteCooldown = 30;
                    this.level().broadcastEntityEvent(this, BITE_EVENT_ID);
                    break;
                }
            }
        }
    }

    private void updateCamouflageAlpha() {
        Player player = this.level().getNearestPlayer(this, 64.0);
        if (player == null) {
            this.targetAlpha = 1.0F;
        } else {
            Vec3 toCuttlefish = this.position().subtract(player.position());
            double dist = toCuttlefish.length();
            if (dist < 0.01) {
                this.targetAlpha = 1.0F;
            } else {
                Vec3 dir = toCuttlefish.normalize();
                boolean hasBlockBehind = false;
                for (double d = 0.5; d <= 4.0; d += 0.5) {
                    Vec3 check = this.position().add(dir.scale(d));
                    BlockPos pos = BlockPos.containing(check);
                    if (this.level().getBlockState(pos).isSolid()) {
                        hasBlockBehind = true;
                        break;
                    }
                }

                boolean hasBlockBetween = false;
                if (dist > 1.0) {
                    Vec3 step = dir.scale(-1.0);
                    for (double d = 1.0; d < dist - 0.5; d += 1.0) {
                        Vec3 check = this.position().add(step.scale(d));
                        BlockPos pos = BlockPos.containing(check);
                        if (this.level().getBlockState(pos).isSolid()) {
                            hasBlockBetween = true;
                            break;
                        }
                    }
                }

                float blockAlpha;
                if (hasBlockBehind || hasBlockBetween) {
                    blockAlpha = 0.5F + this.random.nextFloat() * 0.2F;
                } else {
                    blockAlpha = 0.85F + this.random.nextFloat() * 0.1F;
                }

                float distFactor = (float) Mth.clamp(1.0 - (dist / 50.0), 0.6, 1.0);
                this.targetAlpha = blockAlpha * distFactor;
            }
        }

        this.camouflageAlpha += (this.targetAlpha - this.camouflageAlpha) * 0.08F;
    }

    private void updateAnimations() {
        AnimationState active = getActiveAnimation();
        boolean isMoving = this.zza != 0.0F || this.getDeltaMovement().horizontalDistanceSqr() > 0.005;
        boolean isMovingFast = this.getDeltaMovement().horizontalDistanceSqr() > 0.1;

        if (active == biteAnimationState) {
            swimAnimationState.stop();
            idleAnimationState.stop();
        } else if (isMovingFast && active != jetUseAnimationState) {
            swimAnimationState.stop();
            idleAnimationState.stop();
            jetUseAnimationState.startIfStopped(this.tickCount);
        } else if (active == jetUseAnimationState && jetUseAnimationState.getAccumulatedTime() > 600) {
            jetUseAnimationState.stop();
        } else if (isMoving) {
            swimAnimationState.startIfStopped(this.tickCount);
            idleAnimationState.stop();
        } else {
            idleAnimationState.startIfStopped(this.tickCount);
            swimAnimationState.stop();
        }
    }

    private AnimationState getActiveAnimation() {
        if (biteAnimationState.isStarted()) return biteAnimationState;
        if (jetUseAnimationState.isStarted()) return jetUseAnimationState;
        if (swimAnimationState.isStarted()) return swimAnimationState;
        if (idleAnimationState.isStarted()) return idleAnimationState;
        return null;
    }

    @Override
    public boolean isInvisibleTo(Player player) {
        return false;
    }

    private void spawnInkCloud() {
        RandomSource random = this.getRandom();
        Vec3 forward = Vec3.directionFromRotation(0, this.getYRot());
        double bx = this.getX() + forward.x * 4.0;
        double by = this.getY() + 0.5;
        double bz = this.getZ() + forward.z * 4.0;

        for (int i = 0; i < 20; i++) {
            double speed = 0.05 + random.nextDouble() * 0.4;
            this.level().addParticle(ParticleTypes.SQUID_INK,
                bx + (random.nextDouble() - 0.5) * 1.5,
                by + (random.nextDouble() - 0.5) * 1.0,
                bz + (random.nextDouble() - 0.5) * 1.5,
                forward.x * speed + (random.nextDouble() - 0.5) * 0.3,
                (random.nextDouble() - 0.5) * 0.3,
                forward.z * speed + (random.nextDouble() - 0.5) * 0.3);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == BITE_EVENT_ID && this.level().isClientSide()) {
            this.biteAnimationState.start(this.tickCount);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SQUID_AMBIENT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SQUID_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.SQUID_HURT;
    }

    public static boolean checkSpawnRules(EntityType<? extends WaterAnimal> type, LevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    public void triggerBite() {
        if (this.level().isClientSide()) {
            this.biteAnimationState.start(this.tickCount);
        }
    }
}
