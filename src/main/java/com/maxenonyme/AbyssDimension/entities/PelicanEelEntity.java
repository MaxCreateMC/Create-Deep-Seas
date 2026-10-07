package com.maxenonyme.AbyssDimension.entities;

import com.maxenonyme.AbyssDimension.entities.parts.HasSegments;
import com.maxenonyme.AbyssDimension.entities.parts.SegmentDefinition;
import com.maxenonyme.AbyssDimension.entities.parts.SegmentHitbox;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.TryFindWaterGoal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class PelicanEelEntity extends WaterAnimal implements HasSegments {

    private static final List<SegmentDefinition> SEGMENT_DEFS = buildSegmentDefs();

    private static List<SegmentDefinition> buildSegmentDefs() {
        List<SegmentDefinition> defs = new ArrayList<>();
        double px = 1.0 / 16.0;

        defs.add(new SegmentDefinition("head",        24.5 * px, 15.8 * px, 0, 10.8 * px, 6.9 * px, 22 * px));
        defs.add(new SegmentDefinition("neck",        10.75 * px, 14.1 * px, 0, 6.4 * px, 4.8 * px, 8.1 * px));
        defs.add(new SegmentDefinition("body02",      2.4 * px,  14.4 * px, 0, 6.4 * px, 5.2 * px, 9.2 * px));
        defs.add(new SegmentDefinition("body03",      -6.5 * px, 14.35 * px, 0, 5.6 * px, 4.7 * px, 9 * px));
        defs.add(new SegmentDefinition("tail01",      -15.15 * px, 14.15 * px, 0, 4.8 * px, 4.1 * px, 8.7 * px));
        defs.add(new SegmentDefinition("tail02",      -23.25 * px, 13.8 * px, 0, 3.8 * px, 3.6 * px, 8.1 * px));
        defs.add(new SegmentDefinition("tail03",      -30.8 * px, 13.3 * px, 0, 3.0 * px, 3.2 * px, 7.6 * px));
        defs.add(new SegmentDefinition("tail04",      -37.2 * px, 12.45 * px, 0, 2.2 * px, 2.7 * px, 6 * px));
        defs.add(new SegmentDefinition("tail_curve",  -41 * px,   10.9 * px, 0, 1.8 * px, 2.6 * px, 3.6 * px));
        defs.add(new SegmentDefinition("tail_tip",    -43.5 * px, 9.8 * px, 0, 2.2 * px, 2.0 * px, 3 * px));

        return List.copyOf(defs);
    }

    private final List<SegmentHitbox> segments = new ArrayList<>();
    private final double[] incrementalYaw = new double[SEGMENT_DEFS.size()];
    private final double[] incrementalPitch = new double[SEGMENT_DEFS.size()];

    public PelicanEelEntity(EntityType<? extends WaterAnimal> type, Level level) {
        super(type, level);
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 30.0)
            .add(Attributes.MOVEMENT_SPEED, 0.6);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new TryFindWaterGoal(this));
        this.goalSelector.addGoal(1, new RandomSwimmingGoal(this, 0.8, 40));
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (this.level().isClientSide()) {
            for (int i = 0; i < SEGMENT_DEFS.size(); i++) {
                segments.add(null);
            }
        } else {
            recreateSegments();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide() && reason.shouldDestroy()) {
            for (var seg : segments) {
                seg.discard();
            }
        }
        super.remove(reason);
    }

    @Override
    public void tick() {
        super.tick();
        updateSegmentPositions();
        if (!this.level().isClientSide()) {
            boolean anyDead = false;
            for (var seg : segments) {
                if (seg == null || !seg.isAlive()) {
                    anyDead = true;
                    break;
                }
            }
            if (anyDead || segments.size() != SEGMENT_DEFS.size()) {
                recreateSegments();
            }
        }
    }

    private void updateSegmentPositions() {
        if (segments.isEmpty()) return;
        for (var seg : segments) {
            if (seg == null) return;
        }

        Vec3 up = new Vec3(0, 1, 0);
        Vec3 cumulativeDir = this.getLookAngle();
        Vec3 cumulativePos = this.position();
        double prevFwd = 0;

        for (int i = 0; i < segments.size(); i++) {
            SegmentHitbox seg = segments.get(i);
            SegmentDefinition def = SEGMENT_DEFS.get(i);

            double phase = this.tickCount * 0.12 + i * 0.7;
            double amp = 0.15 + i * 0.04;
            double bendYaw = Math.sin(phase) * amp;
            double bendPitch = Math.cos(phase * 0.7 + 1.0) * amp * 0.4;
            incrementalYaw[i] = bendYaw;
            incrementalPitch[i] = bendPitch;

            double cosY = Math.cos(bendYaw);
            double sinY = Math.sin(bendYaw);
            double lx = cumulativeDir.x * cosY - cumulativeDir.z * sinY;
            double lz = cumulativeDir.x * sinY + cumulativeDir.z * cosY;
            double ly = cumulativeDir.y + bendPitch;
            double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
            if (len > 1.0E-7) { lx /= len; ly /= len; lz /= len; }
            cumulativeDir = new Vec3(lx, ly, lz);

            Vec3 right = cumulativeDir.cross(up);
            if (right.lengthSqr() < 1.0E-7) right = new Vec3(1, 0, 0);
            right = right.normalize();

            double segDist = (i == 0) ? def.forwardOffset() : def.forwardOffset() - prevFwd;
            prevFwd = def.forwardOffset();

            double wx = cumulativePos.x + cumulativeDir.x * segDist + right.x * def.lateralOffset();
            double wy = this.getY() + def.verticalOffset() + cumulativeDir.y * segDist;
            double wz = cumulativePos.z + cumulativeDir.z * segDist + right.z * def.lateralOffset();

            double hw = def.width() / 2.0;
            double hh = def.height() / 2.0;
            double hl = def.length() / 2.0;

            seg.setChainPosition(
                new Vec3(wx, wy, wz),
                cumulativeDir,
                new AABB(wx - hw, wy - hh, wz - hl, wx + hw, wy + hh, wz + hl)
            );

            cumulativePos = new Vec3(wx, wy, wz);
        }
    }

    public double getIncrementalYaw(int index) {
        return incrementalYaw[index];
    }

    public double getIncrementalPitch(int index) {
        return incrementalPitch[index];
    }

    @Override
    public List<SegmentHitbox> segments() {
        return segments;
    }

    @Override
    public List<SegmentDefinition> segmentDefinitions() {
        return SEGMENT_DEFS;
    }

    @Override
    public void recreateSegments() {
        for (var seg : segments) {
            if (seg != null) seg.discard();
        }
        segments.clear();
        for (int i = 0; i < SEGMENT_DEFS.size(); i++) {
            SegmentDefinition def = SEGMENT_DEFS.get(i);
            SegmentHitbox seg = new SegmentHitbox(this, i, def, this.level());
            this.level().addFreshEntity(seg);
            segments.add(seg);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.isInWaterOrBubble() || this.isInLava()) {
            Vec3 vel = this.getDeltaMovement();
            this.setDeltaMovement(vel.x, vel.y * 0.9 + 0.002, vel.z);
        }
    }

    @Override
    protected void handleAirSupply(int air) {
        this.setAirSupply(300);
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return EntityDimensions.scalable(0.8F, 0.5F);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, amount);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
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
        return level.getFluidState(pos).is(FluidTags.WATER);
    }
}
