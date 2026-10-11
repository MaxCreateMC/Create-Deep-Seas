package com.maxenonyme.createsubmarine.submarine.client.renderer;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class SubLevelRenderPoseCapture {
    private static final Map<UUID, Pose3d> POSES = new ConcurrentHashMap<>();

    public static void capture(UUID subId, Pose3dc pose) {
        POSES.computeIfAbsent(subId, k -> new Pose3d()).set(pose);
    }

    public static Pose3dc get(UUID subId) {
        return POSES.get(subId);
    }

    public static void clearAll() {
        POSES.clear();
    }
}
