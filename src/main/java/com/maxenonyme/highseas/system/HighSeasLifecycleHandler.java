package com.maxenonyme.highseas.system;

import com.maxenonyme.highseas.sail.BoatClassifier;
import com.maxenonyme.highseas.sail.SailCollisionSystem;
import com.maxenonyme.highseas.sail.SailWindRegistry;
import com.maxenonyme.highseas.wind.WindManager;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import com.maxenonyme.highseas.BoatBuoyancySystem;
import com.maxenonyme.highseas.BoatManager;
import com.maxenonyme.highseas.helm.HelmServer;

public final class HighSeasLifecycleHandler {
    private HighSeasLifecycleHandler() {
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        SailWindRegistry.clearAll();
        WindManager.clearAll();
        BoatClassifier.clearAll();
        SailCollisionSystem.clearAll();
        BoatManager.clearAll();
        HelmServer.clearAll();
        BoatBuoyancySystem.clearAll();
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            WindManager.clearForDimension(serverLevel.dimension());
        }
    }
}
