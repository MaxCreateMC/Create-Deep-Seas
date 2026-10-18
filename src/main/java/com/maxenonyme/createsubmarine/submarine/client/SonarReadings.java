package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.submarine.block.entity.UnderwaterMineBlockEntity;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

import static com.maxenonyme.createsubmarine.submarine.client.SonarView.DOWN;
import static com.maxenonyme.createsubmarine.submarine.client.SonarView.RANGE;
import static com.maxenonyme.createsubmarine.submarine.client.SonarView.UP;

class SonarReadings {

    static void locateContacts(Minecraft mc, ClientSubLevel subLevel, Vector3d sonar, Vector3d center, Matrix4f toScreen,
            SonarScan scan, float front) {
        scan.contacts.clear();
        Plotter plotter = new Plotter(scan, sonar, center, toScreen, front, new Vector4f());
        Level level = subLevel.getLevel();
        plotMines(plotter, level, subLevel);
        plotCreatures(plotter, level, mc.player, sonar);
        plotVessels(plotter, subLevel, sonar);
    }

    private static void plotMines(Plotter plotter, Level level, ClientSubLevel own) {
        Component name = Component.translatable("create_submarine.command_sub.contact.mine");
        for (UnderwaterMineBlockEntity mine : UnderwaterMineBlockEntity.LOADED_ON_CLIENT) {
            if (mine.isRemoved() || mine.getLevel() != level || Sable.HELPER.getContaining(mine) == own)
                continue;
            Vec3 at = Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(mine.getBlockPos()));
            plotter.add(at.x, at.y, at.z, SonarContact.MINE, name);
        }
    }

    private static void plotCreatures(Plotter plotter, Level level, Entity viewer, Vector3d sonar) {
        for (Entity entity : level.getEntities((Entity) null, SonarView.zone(sonar),
                e -> e instanceof LivingEntity && e.isAlive())) {
            if (entity == viewer)
                continue;
            Vec3 at = entity.position();
            int kind = entity instanceof Enemy ? SonarContact.HOSTILE : SonarContact.CREATURE;
            plotter.add(at.x, at.y + entity.getBbHeight() / 2, at.z, kind, nameOf(entity));
        }
    }

    private static void plotVessels(Plotter plotter, ClientSubLevel own, Vector3d sonar) {
        Component name = Component.translatable("create_submarine.command_sub.contact.vessel");
        for (ClientSubLevel other : nearbySubLevels(own, sonar)) {
            BoundingBox3dc bb = other.boundingBox();
            plotter.add((bb.minX() + bb.maxX()) / 2, (bb.minY() + bb.maxY()) / 2, (bb.minZ() + bb.maxZ()) / 2,
                    SonarContact.VESSEL, name);
        }
    }

    private static List<ClientSubLevel> nearbySubLevels(ClientSubLevel own, Vector3d sonar) {
        List<ClientSubLevel> found = new ArrayList<>();
        SubLevelContainer container = SubLevelContainer.getContainer(own.getLevel());
        if (container == null)
            return found;

        for (SubLevel other : container.getAllSubLevels()) {
            if (other == own || !(other instanceof ClientSubLevel vessel) || vessel.isRemoved() || vessel.getPlot() == null)
                continue;
            if (overlapsZone(vessel.boundingBox(), sonar))
                found.add(vessel);
        }
        return found;
    }

    private static boolean overlapsZone(BoundingBox3dc bb, Vector3d sonar) {
        return bb.maxX() >= sonar.x - RANGE && bb.minX() <= sonar.x + RANGE
                && bb.maxZ() >= sonar.z - RANGE && bb.minZ() <= sonar.z + RANGE
                && bb.maxY() >= sonar.y - DOWN && bb.minY() <= sonar.y + UP;
    }

    private static Component nameOf(Entity entity) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (entity instanceof Player || "minecraft".equals(key.getNamespace()))
            return entity.getName();
        return Component.translatable("create_submarine.command_sub.contact.unknown");
    }

    private record Plotter(SonarScan scan, Vector3d sonar, Vector3d center, Matrix4f toScreen, float front,
            Vector4f point) {

        void add(double x, double y, double z, int kind, Component name) {
            double dx = x - sonar.x;
            double dy = y - sonar.y;
            double dz = z - sonar.z;
            if (dx * dx + dz * dz > RANGE * RANGE || dy < -DOWN || dy > UP)
                return;
            if (!scan.swept && Math.sqrt(dx * dx + dy * dy + dz * dz) > front)
                return;

            point.set((float) (x - center.x), (float) (y - center.y), (float) (z - center.z), 1f);
            toScreen.transform(point);
            float u = (point.x + 1) / 2;
            float v = (1 - point.y) / 2;
            if (u >= 0 && u <= 1 && v >= 0 && v <= 1)
                scan.contacts.add(new SonarContact(u, v, kind, name));
        }
    }

    static float floorClearance(ClientSubLevel subLevel, Pose3dc pose) {
        BoundingBox3ic box = subLevel.getPlot().getBoundingBox();
        List<Vector3d> probes = new ArrayList<>();
        double keel = Double.MAX_VALUE;
        Vector3d corner = new Vector3d();
        for (int i = 0; i < 8; i++) {
            corner.set(
                    (i & 1) == 0 ? box.minX() : box.maxX() + 1,
                    (i & 2) == 0 ? box.minY() : box.maxY() + 1,
                    (i & 4) == 0 ? box.minZ() : box.maxZ() + 1);
            pose.transformPosition(corner);
            keel = Math.min(keel, corner.y);
            if ((i & 2) == 0)
                probes.add(new Vector3d(corner));
        }
        probes.add(pose.transformPosition(new Vector3d(
                (box.minX() + box.maxX() + 1) / 2.0,
                box.minY(),
                (box.minZ() + box.maxZ() + 1) / 2.0)));

        float best = Float.NaN;
        for (Vector3d probe : probes) {
            float clearance = clearanceBelow(subLevel.getLevel(), Mth.floor(probe.x), Mth.floor(probe.z), keel);
            if (!Float.isNaN(clearance) && (Float.isNaN(best) || clearance < best))
                best = clearance;
        }
        return best;
    }

    private static float clearanceBelow(Level level, int x, int z, double keel) {
        ChunkAccess chunk = level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
        if (chunk == null)
            return Float.NaN;

        int top = (int) Math.floor(keel);
        int bottom = Math.max(level.getMinBuildHeight(), top - SonarView.FLOOR_REACH);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = top; y >= bottom; y--)
            if (CompartmentTracker.realBlockState(chunk, pos.set(x, y, z)).blocksMotion())
                return (float) Math.max(0, keel - (y + 1));
        return Float.NaN;
    }
}
