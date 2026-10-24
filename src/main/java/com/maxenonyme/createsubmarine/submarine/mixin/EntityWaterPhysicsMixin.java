package com.maxenonyme.createsubmarine.submarine.mixin;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import org.joml.Vector3d;

@Mixin(Entity.class)
public abstract class EntityWaterPhysicsMixin {

    @Inject(method = "isInWater", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$isInWater(CallbackInfoReturnable<Boolean> cir) {
        if (createsubmarine$isInsideAirtightSub())
            cir.setReturnValue(false);
    }

    @Inject(method = "getFluidHeight(Lnet/minecraft/tags/TagKey;)D", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$getFluidHeight(TagKey<?> fluidTag, CallbackInfoReturnable<Double> cir) {
        if (createsubmarine$isInsideAirtightSub())
            cir.setReturnValue(0.0D);
    }

    @org.spongepowered.asm.mixin.Shadow
    protected boolean wasTouchingWater;
    @org.spongepowered.asm.mixin.Shadow
    protected it.unimi.dsi.fastutil.objects.Object2DoubleMap<TagKey<Fluid>> fluidHeight;
    @org.spongepowered.asm.mixin.Shadow(remap = false)
    @org.spongepowered.asm.mixin.Final
    private it.unimi.dsi.fastutil.objects.Object2DoubleMap<FluidType> forgeFluidTypeHeight;

    @Inject(method = "updateInWaterStateAndDoFluidPushing", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$cancelWaterPushing(CallbackInfoReturnable<Boolean> cir) {
        if (createsubmarine$isInsideAirtightSub()) {
            wasTouchingWater = false;
            fluidHeight.clear();
            forgeFluidTypeHeight.clear();
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isEyeInFluid", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$isEyeInFluid(TagKey<?> fluidTag, CallbackInfoReturnable<Boolean> cir) {
        if (createsubmarine$isInsideAirtightSub())
            cir.setReturnValue(false);
    }

    @Inject(method = "doWaterSplashEffect", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$noSplashWhileSubmerged(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        BlockPos eye = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
        FluidState fs = entity.level().getFluidState(eye);
        if (fs.is(CreateSubmarine.WATER_TAG)
                && entity.getEyeY() < eye.getY() + fs.getHeight(entity.level(), eye)) {
            ci.cancel();
        }
    }

    @Inject(method = {"onInsideBubbleColumn", "onAboveBubbleCol"}, at = @At("HEAD"), cancellable = true)
    private void createsubmarine$ignoreBubbleColumn(boolean downwards, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (createsubmarine$isInsideAirtightSub())
            ci.cancel();
    }

    @Inject(method = {"isInWaterOrBubble", "isInWaterRainOrBubble"}, at = @At("HEAD"), cancellable = true)
    private void createsubmarine$dryInsideSub(CallbackInfoReturnable<Boolean> cir) {
        if (createsubmarine$isInsideAirtightSub())
            cir.setReturnValue(false);
    }

    @Inject(method = "isUnderWater", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$isUnderWater(CallbackInfoReturnable<Boolean> cir) {
        if (createsubmarine$isInsideAirtightSub())
            cir.setReturnValue(false);
    }

    @Inject(method = "updateFluidOnEyes", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$updateFluidOnEyes(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (createsubmarine$isInsideAirtightSub()) {
            ci.cancel();
        }
    }

    @Inject(method = "getEyeInFluidType", at = @At("HEAD"), cancellable = true, remap = false)
    private void createsubmarine$getEyeInFluidType(CallbackInfoReturnable<FluidType> cir) {
        if (createsubmarine$isInsideAirtightSub()) {
            cir.setReturnValue(NeoForgeMod.EMPTY_TYPE.value());
        }
    }

    @Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$updateSwimming(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (createsubmarine$isInsideAirtightSub()) {
            Entity entity = (Entity) (Object) this;
            entity.setSwimming(false);
            ci.cancel();
        }
    }

    @Inject(method = "isSwimming", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$isSwimming(CallbackInfoReturnable<Boolean> cir) {
        if (createsubmarine$isInsideAirtightSub())
            cir.setReturnValue(false);
    }

    @Unique
    private long createsubmarine$cacheTick = -1L;
    @Unique
    private double createsubmarine$cacheX, createsubmarine$cacheY, createsubmarine$cacheZ, createsubmarine$cacheEyeY;
    @Unique
    private boolean createsubmarine$cachedInside = false;

    @Unique
    private boolean createsubmarine$isInsideAirtightSub() {
        Entity entity = (Entity) (Object) this;
        Level level = entity.level();
        if (level == null)
            return false;

        long tick = level.getGameTime();
        double x = entity.getX(), y = entity.getY(), z = entity.getZ(), eyeY = entity.getEyeY();
        if (tick == createsubmarine$cacheTick
                && x == createsubmarine$cacheX && y == createsubmarine$cacheY
                && z == createsubmarine$cacheZ && eyeY == createsubmarine$cacheEyeY) {
            return createsubmarine$cachedInside;
        }

        Vec3 eyePos = new Vec3(x, eyeY, z);
        boolean inside = CompartmentTracker.isInSealedExact(level, eyePos);
        if (!inside) {
            inside = CompartmentTracker.isInSealedExact(level, entity.position());
        }

        if (inside) {
            SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(level, eyePos);
            if (ship != null) {
                Vector3d localPos = new Vector3d(x, eyeY, z);
                ship.logicalPose().transformPositionInverse(localPos);
                BlockPos localBlockPos = BlockPos.containing(localPos.x, localPos.y, localPos.z);
                
                Level subLevel = SubLevelRegistry.getLevel(ship.getUniqueId());
                if (subLevel == null && ship instanceof SubLevel sl) {
                    subLevel = sl.getLevel();
                }
                
                if (subLevel != null) {
                    FluidState fs = subLevel.getFluidState(localBlockPos);
                    if (fs.is(CreateSubmarine.WATER_TAG)) {
                        inside = false;
                    } else {
                        double[] probes = { y + 0.15, y + entity.getBbHeight() * 0.5 };
                        for (double probe : probes) {
                            Vector3d local = new Vector3d(x, probe, z);
                            ship.logicalPose().transformPositionInverse(local);
                            BlockPos cell = BlockPos.containing(local.x, local.y, local.z);
                            if (subLevel.getFluidState(cell).is(CreateSubmarine.WATER_TAG)) {
                                inside = false;
                                break;
                            }
                        }
                    }
                }
            }
        }

        createsubmarine$cacheTick = tick;
        createsubmarine$cacheX = x;
        createsubmarine$cacheY = y;
        createsubmarine$cacheZ = z;
        createsubmarine$cacheEyeY = eyeY;
        createsubmarine$cachedInside = inside;
        return inside;
    }

}
