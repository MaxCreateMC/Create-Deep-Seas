package com.maxenonyme.highseas.sail;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import com.maxenonyme.highseas.config.HighSeasConfig;
import java.util.ArrayList;

public final class SailWindRegistry {
    private SailWindRegistry() {
    }

    private static final Map<UUID, List<SailGroup>> SAILS = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> RUDDERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SCAN = new ConcurrentHashMap<>();
    private static final Map<UUID, List<DecayingSail>> DECAYING_SAILS = new ConcurrentHashMap<>();

    public static List<SailGroup> getSails(SubLevel ship, long gameTime) {
        UUID id = ship.getUniqueId();
        Long last = LAST_SCAN.get(id);
        List<SailGroup> cached = SAILS.get(id);
        if (cached != null && last != null && gameTime - last < HighSeasConfig.sailScanInterval) {
            return cached;
        }

        List<SailGroup> result = List.of();
        Vec3 rudder = null;
        Level blocks = ship.getLevel();
        if (blocks != null && ship.getPlot() != null) {
            List<SailGroup> detected = SailDetector.detect(blocks, ship.getPlot().getBoundingBox());
            
            if (cached != null) {
                List<SailGroup> newResult = new ArrayList<>();
                for (SailGroup g : detected) {
                    long st = gameTime;
                    for (SailGroup c : cached) {
                        if (c.axis() == g.axis() && c.area() == g.area() && c.min().equals(g.min()) && c.max().equals(g.max())) {
                            st = c.startTick();
                            break;
                        }
                    }
                    newResult.add(new SailGroup(g.axis(), g.localCenter(), g.area(), g.min(), g.max(), g.supportSign(), st));
                }
                result = newResult;
            } else {
                List<SailGroup> newResult = new ArrayList<>();
                for (SailGroup g : detected) {
                    newResult.add(new SailGroup(g.axis(), g.localCenter(), g.area(), g.min(), g.max(), g.supportSign(), gameTime));
                }
                result = newResult;
            }
            
            rudder = RudderDetector.centroid(blocks, ship.getPlot().getBoundingBox());

        }
        SAILS.put(id, result);
        if (rudder != null) {
            RUDDERS.put(id, rudder);
        } else {
            RUDDERS.remove(id);
        }
        LAST_SCAN.put(id, gameTime);
        return result;
    }

    public static Vec3 getRudder(UUID id) {
        return RUDDERS.get(id);
    }

    public static void addDecayingSail(UUID id, SailGroup sail, long currentTick) {
        DECAYING_SAILS.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>()).add(new DecayingSail(sail, currentTick));
    }

    public static List<DecayingSail> getDecayingSails(UUID id, long currentTick) {
        List<DecayingSail> list = DECAYING_SAILS.get(id);
        if (list == null) return List.of();
        list.removeIf(d -> currentTick - d.startTick() > 60);
        return list;
    }

    public static void forget(UUID id) {
        SAILS.remove(id);
        RUDDERS.remove(id);
        LAST_SCAN.remove(id);
        DECAYING_SAILS.remove(id);
    }

    public static void clearAll() {
        SAILS.clear();
        RUDDERS.clear();
        LAST_SCAN.clear();
        DECAYING_SAILS.clear();
    }
}
