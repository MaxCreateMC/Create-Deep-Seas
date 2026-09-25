package com.maxenonyme.createsubmarine.submarine.system;

import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class PhysicsWakeSystem {

    private static int tickCounter = 0;

    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter % 100 != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (!(container instanceof ServerSubLevelContainer serverContainer)) continue;
            for (SubLevel sub : serverContainer.getAllSubLevels()) {
                if (!(sub instanceof ServerSubLevel serverSub)) continue;
                if (serverSub.getPlot() == null) continue;
                boolean hasForces = !serverSub.getPlot().getLiftProviders().isEmpty();
                if (!hasForces) {
                    Object2ObjectMap<ForceGroup, QueuedForceGroup> groups = serverSub.getQueuedForceGroups();
                    hasForces = groups != null && !groups.isEmpty();
                }
                if (hasForces) {
                    serverContainer.physicsSystem().getPipeline().wakeUp(serverSub);
                }
            }
        }
    }
}
