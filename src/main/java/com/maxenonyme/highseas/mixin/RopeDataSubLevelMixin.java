package com.maxenonyme.highseas.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.network.packets.rope.ClientboundRopeDataPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ClientboundRopeDataPacket.class, remap = false)
public class RopeDataSubLevelMixin {

    @WrapOperation(method = "handle", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity createhighseas$reachIntoPlot(Level level, BlockPos pos, Operation<BlockEntity> original) {
        BlockEntity direct = original.call(level, pos);
        if (direct != null) {
            return direct;
        }
        SubLevelAccess access = SableCompanion.INSTANCE.getContaining(level, pos);
        if (!(access instanceof SubLevel sub) || sub.getPlot() == null) {
            return null;
        }
        return sub.getPlot().getEmbeddedLevelAccessor().getBlockEntity(pos);
    }
}
