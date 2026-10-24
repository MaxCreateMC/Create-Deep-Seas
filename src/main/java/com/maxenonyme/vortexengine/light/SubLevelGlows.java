package com.maxenonyme.vortexengine.light;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compartment.PlotSnapshot;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SubLevelGlows {

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Vortex sub-level glows");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private static final Map<UUID, Tracked> TRACKED = new HashMap<>();

    private static class Tracked {
        List<Light> local = List.of();
        CompletableFuture<List<Light>> pending;
        boolean dirty = true;
    }

    static void markDirty(UUID id) {
        Tracked tracked = TRACKED.get(id);
        if (tracked != null)
            tracked.dirty = true;
    }

    static void clear() {
        TRACKED.clear();
    }

    static void collect(ClientLevel level, Vec3 camera, int distance, float partialTick, List<Light> out) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            TRACKED.clear();
            return;
        }

        Set<UUID> seen = new HashSet<>();
        Vector3d point = new Vector3d();

        for (SubLevel sub : container.getAllSubLevels()) {
            if (!(sub instanceof ClientSubLevel subLevel) || subLevel.isRemoved() || subLevel.getPlot() == null)
                continue;

            BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
            if (bounds.minX() == bounds.maxX() && bounds.minY() == bounds.maxY() && bounds.minZ() == bounds.maxZ())
                continue;

            UUID id = subLevel.getUniqueId();
            seen.add(id);
            Tracked tracked = TRACKED.computeIfAbsent(id, key -> new Tracked());
            refresh(subLevel, tracked);
            if (tracked.local.isEmpty())
                continue;

            Pose3dc pose = subLevel.renderPose(partialTick);
            double reach = distance * distance;

            for (Light light : tracked.local) {
                pose.transformPosition(point.set(light.x(), light.y(), light.z()));
                Light placed = light.movedTo(point.x, point.y, point.z);
                if (placed.distanceSqr(camera) <= reach)
                    out.add(placed);
            }
        }

        TRACKED.keySet().retainAll(seen);
    }

    private static void refresh(ClientSubLevel subLevel, Tracked tracked) {
        if (tracked.pending != null && tracked.pending.isDone()) {
            List<Light> found = tracked.pending.join();
            tracked.pending = null;
            if (found != null)
                tracked.local = found;
        }

        if (!tracked.dirty || tracked.pending != null)
            return;

        Map<BlockState, Glow> glows = GlowDefinitions.all();
        tracked.dirty = false;

        if (glows.isEmpty()) {
            tracked.local = List.of();
            return;
        }

        PlotSnapshot plot = new PlotSnapshot(subLevel.getPlot());
        boolean[] lit = { false };
        plot.palette(state -> lit[0] |= glows.containsKey(state));

        if (!lit[0]) {
            tracked.local = List.of();
            return;
        }

        UUID id = subLevel.getUniqueId();
        tracked.pending = CompletableFuture.supplyAsync(() -> {
            try {
                return scan(plot, glows);
            } catch (RuntimeException e) {
                CreateSubmarine.LOGGER.warn("Couldn't find the glowing blocks of sub-level {}", id, e);
                return null;
            }
        }, WORKER);
    }

    private static List<Light> scan(PlotSnapshot plot, Map<BlockState, Glow> glows) {
        List<Light> found = new ArrayList<>();

        for (int x = plot.minX; x <= plot.maxX; x++) {
            for (int y = plot.minY; y <= plot.maxY; y++) {
                for (int z = plot.minZ; z <= plot.maxZ; z++) {
                    BlockState state = plot.get(x, y, z);
                    Glow glow = state == null ? null : glows.get(state);
                    if (glow != null)
                        found.add(glow.at(x + 0.5, y + 0.5, z + 0.5));
                }
            }
        }

        return found;
    }
}
