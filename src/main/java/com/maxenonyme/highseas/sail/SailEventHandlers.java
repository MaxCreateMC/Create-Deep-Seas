package com.maxenonyme.highseas.sail;

import dev.eriksonn.aeronautics.content.particle.AirPoofParticleData;
import dev.eriksonn.aeronautics.content.particle.GustParticleData;
import dev.eriksonn.aeronautics.index.AeroSoundEvents;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import com.maxenonyme.highseas.block.BoatSailBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.List;

@EventBusSubscriber(modid = "create_submarine")
public class SailEventHandlers {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerSubLevel ship)) return;
        if (!event.getState().is(BoatSailBlock.SAILS)) return;
        
        ServerLevel parentLevel = (ServerLevel) ship.getLevel();
        if (parentLevel == null) return;

        long time = parentLevel.getGameTime();
        List<SailGroup> sails = SailWindRegistry.getSails(ship, time);
        BlockPos bp = event.getPos();

        for (SailGroup group : sails) {
            if (bp.getX() >= group.min().getX() && bp.getX() <= group.max().getX() &&
                bp.getY() >= group.min().getY() && bp.getY() <= group.max().getY() &&
                bp.getZ() >= group.min().getZ() && bp.getZ() <= group.max().getZ()) {
                
                Vector3d localPos = new Vector3d(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
                ship.logicalPose().transformPosition(localPos);
                
                parentLevel.playSound(null, localPos.x, localPos.y, localPos.z, AeroSoundEvents.GUST.event(), SoundSource.BLOCKS, 0.65f, 0.35f);
                
                Quaternionf particleOrientation = new Quaternionf();
                parentLevel.sendParticles(new GustParticleData(particleOrientation), localPos.x, localPos.y, localPos.z, 1, 0, 0, 0, 0);
                parentLevel.sendParticles(new AirPoofParticleData(), localPos.x, localPos.y, localPos.z, 4, 0.5, 0.5, 0.5, 0);
                
                SailWindRegistry.addDecayingSail(ship.getUniqueId(), group, time);
                break;
            }
        }
    }
}
