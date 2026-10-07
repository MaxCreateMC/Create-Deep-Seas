package com.maxenonyme.AbyssDimension.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

public class MagmaticSnailEntity extends WaterAnimal {
    public final AnimationState crawlAnimationState = new AnimationState();
    public final AnimationState hideAnimationState = new AnimationState();
    public final AnimationState hiddenAnimationState = new AnimationState();

    private int hideTimer = -1;

    public MagmaticSnailEntity(EntityType<? extends WaterAnimal> type, Level level) {
        super(type, level);
        this.moveControl = new MoveControl(this);
        this.lookControl = new LookControl(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
        .add(Attributes.MAX_HEALTH, 12.0)
        .add(Attributes.MOVEMENT_SPEED, 0.15)
        .add(Attributes.ARMOR, 4.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new PanicGoal(this, 1.5));
        this.goalSelector.addGoal(2, new RandomStrollGoal(this, 0.6, 20));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new GroundPathNavigation(this, level);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            updateAnimations();
        }
    }

    @Override
    protected void handleAirSupply(int air) {
        if (this.isInWaterOrBubble() || this.isInLava()) {
            this.setAirSupply(300);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide() && this.isInLava()) {
            this.heal(1.0F);
        }
    }

    @Override
    public void travel(net.minecraft.world.phys.Vec3 travelVector) {
        if (this.isEffectiveAi() && !this.isInWater() && !this.isInLava()) {
            travelVector = travelVector.scale(0.3);
        }
        super.travel(travelVector);
    }

    private void updateAnimations() {
        if (this.hurtTime > 0) {
            this.crawlAnimationState.stop();
            if (this.hurtTime > 5) {
                this.hideAnimationState.startIfStopped(this.tickCount);
                this.hiddenAnimationState.stop();
            } else {
                this.hideAnimationState.stop();
                this.hiddenAnimationState.startIfStopped(this.tickCount);
            }
        } else {
            this.hideAnimationState.stop();
            this.hiddenAnimationState.stop();
            this.crawlAnimationState.startIfStopped(this.tickCount);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.FIRE_EXTINGUISH;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.FIRE_EXTINGUISH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.STONE_BREAK;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide() && source.getDirectEntity() != null) {
            double attackerY = source.getDirectEntity().getY();
            double shellBottom = this.getY() + this.getBbHeight() * 0.35F;
            if (attackerY > shellBottom && amount > 0) {
                amount = Math.max(amount / 10.0F, 0.01F);
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected float getSoundVolume() {
        return 0.3F;
    }

    public static boolean checkSpawnRules(EntityType<? extends WaterAnimal> type, LevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return (level.getFluidState(pos).is(FluidTags.WATER) || level.getFluidState(pos).is(FluidTags.LAVA)) && pos.getY() < 32;
    }
}
