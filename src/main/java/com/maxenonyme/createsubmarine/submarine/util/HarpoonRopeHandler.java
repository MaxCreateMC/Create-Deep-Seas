package com.maxenonyme.createsubmarine.submarine.util;

import com.maxenonyme.createsubmarine.submarine.item.harpoon_gun.HarpoonEntity;

import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HarpoonRopeHandler {

    private static final Map<ServerLevel, HarpoonRopeHandler> INSTANCES = new HashMap<>();

    private final Map<UUID, UUID> harpoonToStrand = new HashMap<>();
    private final Map<UUID, HarpoonEntity> activeHarpoons = new HashMap<>();

    public static HarpoonRopeHandler getOrCreate(ServerLevel level) {
        return INSTANCES.computeIfAbsent(level, k -> new HarpoonRopeHandler());
    }

    public void createRopeIfNeeded(HarpoonEntity harpoon, ServerLevel level) {
        if (harpoonToStrand.containsKey(harpoon.getUUID())) return;

        try {
            Player owner = harpoon.getOwner() instanceof Player p ? p : null;
            if (owner == null) return;

            Vec3 start = owner.position().add(0, owner.getEyeHeight() * 0.8, 0);
            Vec3 end = harpoon.position();

            List<Vector3d> points = new ArrayList<>();
            points.add(new Vector3d(start.x, start.y, start.z));
            points.add(new Vector3d(end.x, end.y, end.z));

            ServerRopeStrand strand = new ServerRopeStrand(UUID.randomUUID(), points);

            strand.addAttachment(level, RopeAttachmentPoint.START,
                new RopeAttachment(RopeAttachmentPoint.START, null, BlockPos.ZERO));
            strand.addAttachment(level, RopeAttachmentPoint.END,
                new RopeAttachment(RopeAttachmentPoint.END, null, BlockPos.ZERO));

            ServerLevelRopeManager manager = ServerLevelRopeManager.getOrCreate(level);
            manager.addStrand(strand);

            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container instanceof ServerSubLevelContainer sslc) {
                sslc.physicsSystem().addObject(strand);
                strand.setAttachment(RopeHandle.AttachmentPoint.START, new Vector3d(start.x, start.y, start.z), null);
                strand.setAttachment(RopeHandle.AttachmentPoint.END, new Vector3d(end.x, end.y, end.z), null);
            }

            harpoonToStrand.put(harpoon.getUUID(), strand.getUUID());
            activeHarpoons.put(harpoon.getUUID(), harpoon);
        } catch (Exception e) {
            harpoonToStrand.remove(harpoon.getUUID());
            activeHarpoons.remove(harpoon.getUUID());
        }
    }

    public void updateRope(HarpoonEntity harpoon, ServerLevel level) {
        UUID strandId = harpoonToStrand.get(harpoon.getUUID());
        if (strandId == null) return;

        ServerLevelRopeManager manager = ServerLevelRopeManager.getOrCreate(level);
        ServerRopeStrand strand = manager.getStrand(strandId);
        if (strand == null || !strand.isActive()) {
            harpoonToStrand.remove(harpoon.getUUID());
            return;
        }

        Player owner = harpoon.getOwner() instanceof Player p ? p : null;
        if (owner == null) return;

        try {
            Vec3 start = owner.position().add(0, owner.getEyeHeight() * 0.8, 0);
            Vec3 end = harpoon.position();

            List<Vector3d> points = strand.getPoints();
            if (!points.isEmpty()) {
                Vector3d first = points.getFirst();
                Vector3d last = points.get(points.size() - 1);

                boolean startMoved = first.distanceSquared(new Vector3d(start.x, start.y, start.z)) > 0.01;
                boolean endMoved = last.distanceSquared(new Vector3d(end.x, end.y, end.z)) > 0.01;

                if (startMoved) {
                    strand.removeFirstPoint();
                    strand.setAttachment(RopeHandle.AttachmentPoint.START, new Vector3d(start.x, start.y, start.z), null);
                }
                if (endMoved) {
                    strand.addPoint(new Vector3d(end.x, end.y, end.z));
                    strand.setAttachment(RopeHandle.AttachmentPoint.END, new Vector3d(end.x, end.y, end.z), null);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void removeRope(HarpoonEntity harpoon) {
        UUID strandId = harpoonToStrand.remove(harpoon.getUUID());
        activeHarpoons.remove(harpoon.getUUID());
        if (strandId == null) return;

        if (harpoon.level() instanceof ServerLevel serverLevel) {
            try {
                ServerLevelRopeManager manager = ServerLevelRopeManager.getOrCreate(serverLevel);
                ServerRopeStrand strand = manager.getStrand(strandId);
                if (strand != null) {
                    SubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
                    if (container instanceof ServerSubLevelContainer sslc) {
                        sslc.physicsSystem().removeObject(strand);
                    }
                    manager.removeStrand(strandId);
                }
            } catch (Exception ignored) {
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            HarpoonRopeHandler handler = INSTANCES.get(level);
            if (handler == null) continue;

            Iterator<Map.Entry<UUID, HarpoonEntity>> it = handler.activeHarpoons.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, HarpoonEntity> entry = it.next();
                try {
                    HarpoonEntity harpoon = entry.getValue();
                    if (!harpoon.isAlive() || harpoon.isRemoved()) {
                        handler.harpoonToStrand.remove(entry.getKey());
                        it.remove();
                        continue;
                    }
                    handler.updateRope(harpoon, level);
                } catch (Exception ignored) {
                    handler.harpoonToStrand.remove(entry.getKey());
                    it.remove();
                }
            }
        }
    }

    public static void clearForLevel(ServerLevel level) {
        HarpoonRopeHandler handler = INSTANCES.remove(level);
        if (handler != null) {
            handler.harpoonToStrand.clear();
            handler.activeHarpoons.clear();
        }
    }
}
