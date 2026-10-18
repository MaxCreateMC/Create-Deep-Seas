package com.maxenonyme.createsubmarine.submarine.system;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MineOwnershipRegistry {
    private static final Map<UUID, UUID> OWNERS = new ConcurrentHashMap<>();

    public static void tag(UUID subLevelId, UUID owner) {
        if (subLevelId != null && owner != null) {
            OWNERS.put(subLevelId, owner);
        }
    }

    public static UUID getOwner(UUID subLevelId) {
        return OWNERS.get(subLevelId);
    }

    public static void onSplit(UUID newSubLevelId, UUID parentSubLevelId) {
        if (newSubLevelId == null || parentSubLevelId == null) {
            return;
        }
        UUID owner = OWNERS.get(parentSubLevelId);
        if (owner != null) {
            OWNERS.put(newSubLevelId, owner);
        }
    }

    public static void clearAll() {
        OWNERS.clear();
    }
}
