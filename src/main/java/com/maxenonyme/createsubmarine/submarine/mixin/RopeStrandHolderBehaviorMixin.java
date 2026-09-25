package com.maxenonyme.createsubmarine.submarine.mixin;

import com.maxenonyme.createsubmarine.submarine.util.SteelCableHolderAccessor;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.RopeHolderBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.SteelCableItem;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import com.maxenonyme.createsubmarine.submarine.system.CableElectrificationSystem;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RopeStrandHolderBehavior.class, remap = false)
public class RopeStrandHolderBehaviorMixin implements SteelCableHolderAccessor {

    @Unique
    private boolean createsubmarine$isSteelCable = false;

    @Shadow
    private ClientRopeStrand ownedClientStrand;

    @Shadow
    private ServerRopeStrand ownedServerStrand;

    @Inject(method = "destroyRopeIfAttachmentBroken", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$destroyRopeIfAttachmentBroken(CallbackInfo ci) {
        if (this.ownedServerStrand == null) return;
        Level level = ((com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour) (Object) this).blockEntity.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;

        RopeAttachment endAttachment = this.ownedServerStrand.getAttachment(RopeAttachmentPoint.END);
        if (endAttachment != null) {
            BlockPos blockAttachment = endAttachment.blockAttachment();
            if (serverLevel.isLoaded(blockAttachment)) {
                BlockEntity be = serverLevel.getBlockEntity(blockAttachment);
                if (be == null) {
                    if (serverLevel.getBlockState(blockAttachment).getBlock() instanceof RopeHolderBlock) {
                        ci.cancel();
                    }
                }
            }
        }
    }

    @Override
    public boolean createsubmarine$isSteelCable() {
        return this.createsubmarine$isSteelCable;
    }

    @Override
    public void createsubmarine$setSteelCable(boolean val) {
        this.createsubmarine$isSteelCable = val;
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void createsubmarine$write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        nbt.putBoolean("createsubmarine$IsSteelCable", this.createsubmarine$isSteelCable);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void createsubmarine$read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        this.createsubmarine$isSteelCable = nbt.getBoolean("createsubmarine$IsSteelCable");
        if (this.createsubmarine$isSteelCable) {
            if (this.ownedClientStrand instanceof SteelCableHolderAccessor accessor) {
                accessor.createsubmarine$setSteelCable(true);
            }
            if (this.ownedServerStrand instanceof SteelCableHolderAccessor accessor) {
                accessor.createsubmarine$setSteelCable(true);
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void createsubmarine$tick(CallbackInfo ci) {
        if (this.createsubmarine$isSteelCable) {
            if (this.ownedClientStrand instanceof SteelCableHolderAccessor accessor) {
                accessor.createsubmarine$setSteelCable(true);
            }
            if (this.ownedServerStrand instanceof SteelCableHolderAccessor accessor) {
                accessor.createsubmarine$setSteelCable(true);
            }
        }
    }

    @Inject(method = "receiveClientStrand", at = @At("TAIL"))
    private void createsubmarine$receiveClientStrand(int interpolationTick, List incomingPoints, UUID uuid, BlockPos startAttachmentPos, BlockPos endAttachmentPos, CallbackInfo ci) {
        if (this.createsubmarine$isSteelCable) {
            if (this.ownedClientStrand instanceof SteelCableHolderAccessor accessor) {
                accessor.createsubmarine$setSteelCable(true);
            }
            if (this.ownedServerStrand instanceof SteelCableHolderAccessor accessor) {
                accessor.createsubmarine$setSteelCable(true);
            }
        }
    }

    @ModifyExpressionValue(method = "destroyRope", at = @At(value = "NEW", target = "(Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack createsubmarine$steelCableDrop(ItemStack original) {
        if (this.createsubmarine$isSteelCable)
            return new ItemStack(CreateSubmarine.STEEL_CABLE.get());
        return original;
    }

    @ModifyArg(method = "destroyRope", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"), index = 0)
    private ParticleOptions createsubmarine$steelCableParticles(ParticleOptions options) {
        if (this.createsubmarine$isSteelCable)
            return new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(CreateSubmarine.STEEL_CABLE.get()));
        return options;
    }

    @Inject(method = "destroyRope", at = @At("HEAD"))
    private void createsubmarine$clearFarEndOnDestroy(ServerPlayer player, Vec3 dropPos, boolean dropItem, CallbackInfo ci) {
        createsubmarine$clearFarEnd();
    }

    @Inject(method = "destroyRope", at = @At("TAIL"))
    private void createsubmarine$clearSteelOnDestroy(ServerPlayer player, Vec3 dropPos, boolean dropItem, CallbackInfo ci) {
        this.createsubmarine$isSteelCable = false;
    }

    @Inject(method = "detachRope", at = @At("HEAD"))
    private void createsubmarine$clearFarEndOnDetach(CallbackInfo ci) {
        createsubmarine$clearFarEnd();
    }

    @Inject(method = "detachRope", at = @At("TAIL"))
    private void createsubmarine$clearSteelOnDetach(CallbackInfo ci) {
        this.createsubmarine$isSteelCable = false;
    }

    @Inject(method = "createRope", at = @At("HEAD"), require = 0)
    private void createsubmarine$clearStaleSteel(RopeStrandHolderBehavior other, boolean flag,
            CallbackInfoReturnable<Boolean> cir) {
        if (SteelCableItem.isCreatingSteel())
            return;
        this.createsubmarine$isSteelCable = false;
        createsubmarine$clearIfFree(other);
    }

    @Unique
    private static void createsubmarine$clearIfFree(RopeStrandHolderBehavior holder) {
        if (holder == null || ((RopeStrandHolderBehaviorMixin) (Object) holder).ownedServerStrand != null)
            return;
        ((SteelCableHolderAccessor) (Object) holder).createsubmarine$setSteelCable(false);
    }

    @Unique
    private void createsubmarine$clearFarEnd() {
        if (this.ownedServerStrand == null)
            return;
        Level level = ((com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour) (Object) this).blockEntity.getLevel();
        if (!(level instanceof ServerLevel serverLevel))
            return;
        RopeAttachment end = this.ownedServerStrand.getAttachment(RopeAttachmentPoint.END);
        if (end == null)
            return;
        ServerLevel endLevel = CableElectrificationSystem.getLevelForAttachment(serverLevel, end);
        if (endLevel == null || !endLevel.isLoaded(end.blockAttachment()))
            return;
        if (endLevel.getBlockEntity(end.blockAttachment()) instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity smart)
            createsubmarine$clearIfFree(smart.getBehaviour(RopeStrandHolderBehavior.TYPE));
    }

    @ModifyArg(method = "createRope", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z"), index = 1)
    private double createsubmarine$steelCableReach(double distance) {
        if (this.createsubmarine$isSteelCable)
            return SubmarineConfig.STEEL_CABLE_MAX_LENGTH.get();
        return distance;
    }
}
