package com.maxenonyme.createsubmarine.submarine.mixin.compat.sable;

import com.maxenonyme.createsubmarine.submarine.system.SableSnapshotQueue;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStartTrackingSubLevelPacket;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import foundry.veil.api.network.handler.PacketContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = ClientboundStartTrackingSubLevelPacket.class, remap = false)
public abstract class SableStartTrackingPacketMixin {

    @Inject(method = "handle", at = @At("TAIL"), remap = false, require = 0)
    private void createsubmarine$drainPending(PacketContext ctx, CallbackInfo ci) {
        ClientboundStartTrackingSubLevelPacket self = (ClientboundStartTrackingSubLevelPacket) (Object) this;
        long plot = self.plotCoordinate();

        Level level = ctx.level();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (!(container instanceof ClientSubLevelContainer cc))
            return;

        SubLevel subLevel = cc.getSubLevel(ChunkPos.getX(plot), ChunkPos.getZ(plot));
        if (subLevel instanceof ClientSubLevel csub) {
            SableSnapshotQueue.drain(plot, cc, csub);
        }
    }
}
