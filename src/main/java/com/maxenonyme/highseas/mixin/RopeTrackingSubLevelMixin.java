package com.maxenonyme.highseas.mixin;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = RopeStrandHolderBehavior.class, remap = false)
public class RopeTrackingSubLevelMixin {

    @Inject(method = "getStrandTrackingPlayers", at = @At("RETURN"), cancellable = true)
    private void createhighseas$reachOutOfPlot(CallbackInfoReturnable<List<ServerPlayer>> info) {
        List<ServerPlayer> vanilla = info.getReturnValue();
        SmartBlockEntity holder = ((BlockEntityBehaviour) (Object) this).blockEntity;
        if (holder == null) {
            return;
        }
        Level level = holder.getLevel();
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        BlockPos pos = holder.getBlockPos();
        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, pos);

        List<ServerPlayer> projected = null;
        if (sub != null) {
            Vector3d where = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            sub.logicalPose().transformPosition(where);
            projected = server.getChunkSource().chunkMap
                    .getPlayers(new ChunkPos(BlockPos.containing(where.x, where.y, where.z)), false);
        }

        if (projected != null && !projected.isEmpty() && (vanilla == null || vanilla.isEmpty())) {
            info.setReturnValue(projected);
        }
    }
}
