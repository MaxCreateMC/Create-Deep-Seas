package com.maxenonyme.highseas.oar;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.maxenonyme.highseas.CreateHighSeas;
import com.maxenonyme.highseas.config.HighSeasConfig;

public final class OarPropulsionSystem {
    private OarPropulsionSystem() {
    }


    private static final int STROKE_PERIOD = 24;
    private static final int DRIVE_TICKS = 5;
    private static final double CRUISE_SPEED = 0.6;
    private static final double COAST_IMPULSE = 11.0;
    private static final int ROW_TIMEOUT = 3;

    private static final Map<UUID, Long> lastRowTick = new HashMap<>();
    private static final Set<UUID> syncedRowers = new HashSet<>();

    public static void ping(ServerPlayer player) {
        lastRowTick.put(player.getUUID(), player.level().getGameTime());
    }

    public static void clearAll() {
        lastRowTick.clear();
        syncedRowers.clear();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null)
            return;
        if (lastRowTick.isEmpty()) {
            if (!syncedRowers.isEmpty()) {
                syncedRowers.clear();
                broadcast(server);
            }
            return;
        }

        Set<UUID> active = new HashSet<>();
        Iterator<Map.Entry<UUID, Long>> it = lastRowTick.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            long gameTime = player == null ? 0 : player.level().getGameTime();
            if (player == null || gameTime - e.getValue() > ROW_TIMEOUT) {
                it.remove();
                continue;
            }

            int phase = (int) (gameTime % STROKE_PERIOD);
            boolean acted = drive(player, phase < DRIVE_TICKS);
            if (acted) {
                active.add(e.getKey());
                if (phase == 0) {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BOAT_PADDLE_WATER, SoundSource.PLAYERS, 0.7f,
                            0.9f + player.getRandom().nextFloat() * 0.2f);
                }
            }
        }

        if (!active.equals(syncedRowers)) {
            syncedRowers.clear();
            syncedRowers.addAll(active);
            broadcast(server);
        }
    }

    private static void broadcast(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(new OarAnimSyncPayload(new ArrayList<>(syncedRowers)));
    }

    private static boolean drive(ServerPlayer player, boolean power) {
        if (!player.getMainHandItem().is(CreateHighSeas.OAR_OF_BOAT.get())
                && !player.getOffhandItem().is(CreateHighSeas.OAR_OF_BOAT.get()))
            return false;
        Level level = player.level();

        SubLevel sub = resolveBoat(level, player);
        if (sub == null)
            return false;

        if (CompartmentTracker.isInSealedExact(level, player.position())
                || CompartmentTracker.isInSealedExact(level, player.getEyePosition()))
            return false;

        if (!waterNearby(level, player))
            return false;

        Object handle = SablePhysicsHelper.getHandle(sub);
        if (handle == null)
            return false;

        Vec3 look = player.getLookAngle();
        Vector3d forward = new Vector3d(look.x, 0, look.z);
        if (forward.lengthSquared() < 1.0e-6)
            return false;
        forward.normalize();

        Vector3dc vel = SablePhysicsHelper.getVelocity(handle);
        double along = vel == null ? 0 : (vel.x() * forward.x + vel.z() * forward.z);

        double magnitude;
        if (power) {
            if (along >= HighSeasConfig.oarMaxSpeed) {
                return true;
            }
            magnitude = HighSeasConfig.oarImpulse / DRIVE_TICKS;
        } else {
            if (along >= CRUISE_SPEED)
                return true;
            magnitude = COAST_IMPULSE;
        }

        Vector3d impulse = new Vector3d(forward).mul(magnitude);
        sub.logicalPose().orientation().conjugate(new Quaterniond()).transform(impulse);

        Vector3d point = new Vector3d(player.getX(), player.getY(), player.getZ());
        sub.logicalPose().transformPositionInverse(point);

        SablePhysicsHelper.wakeUp(handle);
        if (!SablePhysicsHelper.applyImpulseAtPoint(handle, point, impulse))
            SablePhysicsHelper.applyLinearImpulse(handle, impulse);
        return true;
    }

    private static SubLevel resolveBoat(Level level, ServerPlayer player) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null)
            return null;

        double px = player.getX(), py = player.getY(), pz = player.getZ();
        SubLevel best = null;
        double bestDistSq = Double.MAX_VALUE;
        Vector3d local = new Vector3d();
        for (SubLevel sub : container.getAllSubLevels()) {
            LevelPlot plot = sub.getPlot();
            if (plot == null)
                continue;
            BoundingBox3ic bb = plot.getBoundingBox();
            local.set(px, py, pz);
            sub.logicalPose().transformPositionInverse(local);
            if (local.x < bb.minX() - 0.6 || local.x > bb.maxX() + 1.6
                    || local.z < bb.minZ() - 0.6 || local.z > bb.maxZ() + 1.6
                    || local.y < bb.minY() - 1.0 || local.y > bb.maxY() + 2.6)
                continue;
            double cx = (bb.minX() + bb.maxX() + 1) * 0.5;
            double cz = (bb.minZ() + bb.maxZ() + 1) * 0.5;
            double d = (local.x - cx) * (local.x - cx) + (local.z - cz) * (local.z - cz);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = sub;
            }
        }
        return best;
    }


    private static boolean waterNearby(Level parent, ServerPlayer player) {
        int px = Mth.floor(player.getX());
        int py = Mth.floor(player.getY());
        int pz = Mth.floor(player.getZ());
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy >= -2; dy--) {
                    m.set(px + dx, py + dy, pz + dz);
                    if (CompartmentTracker.realFluidState(parent, m).is(FluidTags.WATER))
                        return true;
                }
            }
        }
        return false;
    }
}
