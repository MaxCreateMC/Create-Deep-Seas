package com.maxenonyme.highseas;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.maxenonyme.highseas.config.HighSeasConfig;

public final class BuoyFloatSystem {
    private BuoyFloatSystem() {
    }


    private static final Map<UUID, Double> FLOOR = new ConcurrentHashMap<>();

    public static void hold(UUID id, double poseFloorY) {
        FLOOR.put(id, poseFloorY);
    }

    public static void release(UUID id) {
        FLOOR.remove(id);
    }

    public static void onPostPhysicsTick(ForgeSablePostPhysicsTickEvent event) {
        if (FLOOR.isEmpty()) {
            return;
        }
        ServerLevel level = event.getPhysicsSystem().getLevel();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        for (SubLevel raw : container.getAllSubLevels()) {
            if (!(raw instanceof ServerSubLevel sub)) {
                continue;
            }
            if (!FLOOR.containsKey(sub.getUniqueId())) {
                continue;
            }
            RigidBodyHandle handle = event.getPhysicsSystem().getPhysicsHandle(sub);
            if (handle == null || !handle.isValid()) {
                continue;
            }
            Vector3dc velocity = handle.getLinearVelocity();
            if (velocity == null) {
                continue;
            }
            double excess = 0.0;
            if (velocity.y() > HighSeasConfig.buoyRise) {
                excess = HighSeasConfig.buoyRise - velocity.y();
            } else if (velocity.y() < -HighSeasConfig.buoySink) {
                excess = -HighSeasConfig.buoySink - velocity.y();
            }
            if (excess != 0.0) {
                handle.addLinearAndAngularVelocity(new Vector3d(0.0, excess, 0.0), new Vector3d());
            }
        }
    }

    public static void clearAll() {
        FLOOR.clear();
    }
}
