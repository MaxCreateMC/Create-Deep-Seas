package com.maxenonyme.createsubmarine.submarine.mixin.compat.sable;

import com.maxenonyme.createsubmarine.submarine.system.SableSnapshotQueue;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotDualPacket;
import dev.ryanhcode.sable.network.packets.PacketReceiveMode;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(value = ClientboundSableSnapshotDualPacket.class, remap = false)
public abstract class SableSnapshotPacketMixin {

    @Shadow
    @Final
    private List<ClientboundSableSnapshotDualPacket.Entry> entries;
    @Shadow
    @Final
    private int interpolationTick;

    @Inject(method = "handleClient(Lnet/minecraft/world/level/Level;Ldev/ryanhcode/sable/network/packets/PacketReceiveMode;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void createsubmarine$queueOrApply(Level level, PacketReceiveMode mode, CallbackInfo ci) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (!(container instanceof ClientSubLevelContainer cc))
            return;

        for (ClientboundSableSnapshotDualPacket.Entry e : this.entries) {
            SubLevel subLevel = cc.getSubLevel(ChunkPos.getX(e.plotCoordinate()), ChunkPos.getZ(e.plotCoordinate()));
            if (subLevel instanceof ClientSubLevel csub) {
                cc.getInterpolation().receiveSnapshot(csub, this.interpolationTick, e.pose(), mode);
            } else {
                SableSnapshotQueue.enqueue(e.plotCoordinate(), this.interpolationTick, e.pose(), mode);
            }
        }
        ci.cancel();
    }
}
