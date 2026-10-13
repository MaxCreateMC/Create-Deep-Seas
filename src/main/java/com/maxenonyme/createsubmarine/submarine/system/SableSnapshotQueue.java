package com.maxenonyme.createsubmarine.submarine.system;

import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.network.packets.PacketReceiveMode;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SableSnapshotQueue {

    private static final long TTL_NS = 2_000_000_000L;

    private record Snap(int tick, Pose3d pose, PacketReceiveMode mode, long ts) {}

    private static final Map<Long, Deque<Snap>> PENDING = new ConcurrentHashMap<>();

    private static final long SWEEP_INTERVAL_NS = 10_000_000_000L;
    private static volatile long lastSweepNanos = 0;

    public static void enqueue(long plot, int tick, Pose3d pose, PacketReceiveMode mode) {
        while (true) {
            Deque<Snap> q = PENDING.computeIfAbsent(plot, k -> new ArrayDeque<>());
            synchronized (q) {
                if (PENDING.get(plot) == q) {
                    if (q.size() >= 16)
                        q.pollFirst();
                    q.offerLast(new Snap(tick, new Pose3d(pose), mode, System.nanoTime()));
                    break;
                }
            }
        }
        sweepStale();
    }

    private static void sweepStale() {
        long now = System.nanoTime();
        if (now - lastSweepNanos < SWEEP_INTERVAL_NS)
            return;
        lastSweepNanos = now;
        for (Map.Entry<Long, Deque<Snap>> e : PENDING.entrySet()) {
            Deque<Snap> q = e.getValue();
            synchronized (q) {
                Snap last = q.peekLast();
                if (last == null || now - last.ts > TTL_NS) {
                    PENDING.remove(e.getKey(), q);
                }
            }
        }
    }

    public static void drain(long plot, ClientSubLevelContainer container, ClientSubLevel sub) {
        Deque<Snap> q = PENDING.get(plot);
        if (q == null)
            return;
        long now = System.nanoTime();
        synchronized (q) {
            Snap s;
            while ((s = q.pollFirst()) != null) {
                if (now - s.ts > TTL_NS)
                    continue;
                container.getInterpolation().receiveSnapshot(sub, s.tick, s.pose, s.mode);
            }
        }
    }
}
