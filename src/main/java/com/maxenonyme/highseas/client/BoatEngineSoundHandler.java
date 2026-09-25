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

public final class BoatEngineSoundHandler {
    private BoatEngineSoundHandler() {
    }

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
        BlockPos p = be.getBlockPos();
        Vector3d v = new Vector3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
        if (level != null) {
            SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, p);
            if (sub instanceof ClientSubLevel csl) {
                csl.renderPose().transformPosition(v);
            } else if (sub != null) {
                sub.logicalPose().transformPosition(v);
            }
        }
        return new Vec3(v.x, v.y, v.z);
    }
}
