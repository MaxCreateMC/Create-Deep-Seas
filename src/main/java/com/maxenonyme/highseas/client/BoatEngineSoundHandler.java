package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.CreateHighSeas;
import com.maxenonyme.highseas.block.BoatEngineBlock;
import com.maxenonyme.highseas.block.entity.BoatEngineBlockEntity;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;

public class BoatEngineSoundHandler {

    private static final Map<BlockPos, BoatEngineSoundInstance[]> active = new HashMap<>();

    public static void tick(BoatEngineBlockEntity be) {
        BlockPos pos = be.getBlockPos().immutable();
        boolean powered = be.getBlockState().getValue(BoatEngineBlock.POWERED);
        BoatEngineSoundInstance[] pair = active.get(pos);
        if (powered) {
            if (pair == null || pair[0].isStopped() || pair[1].isStopped()) {
                BoatEngineSoundInstance idle = new BoatEngineSoundInstance(be, CreateHighSeas.BOAT_ENGINE_IDLE_SOUND.get(), false);
                BoatEngineSoundInstance running = new BoatEngineSoundInstance(be, CreateHighSeas.BOAT_ENGINE_RUNNING_SOUND.get(), true);
                Minecraft.getInstance().getSoundManager().play(idle);
                Minecraft.getInstance().getSoundManager().play(running);
                active.put(pos, new BoatEngineSoundInstance[] { idle, running });
            }
        } else if (pair != null) {
            active.remove(pos);
        }
    }

    public static void clear() {
        active.clear();
    }

    public static Vec3 worldPos(BoatEngineBlockEntity be) {
        Level level = be.getLevel();
        BlockPos blockPos = be.getBlockPos();
        Vector3d v = new Vector3d(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
        if (level != null) {
            SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(level, blockPos);
            if (ship instanceof ClientSubLevel subLevel) {
                subLevel.renderPose().transformPosition(v);
            } else if (ship != null) {
                ship.logicalPose().transformPosition(v);
            }
        }
        return new Vec3(v.x, v.y, v.z);
    }
}
