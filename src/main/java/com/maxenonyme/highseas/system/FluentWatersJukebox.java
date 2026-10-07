package com.maxenonyme.highseas.system;

import com.maxenonyme.highseas.CreateHighSeas;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class FluentWatersJukebox {
    private FluentWatersJukebox() {
    }

    private static final int INTERVAL = 20;
    private static final double RADIUS = 8.0;
    private static final int DURATION = 100;
    private static final double SHIP_REACH = 128.0;

    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % INTERVAL != 0)
            return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.players().isEmpty())
                continue;
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator())
                    continue;
                if (hears(level, container, player.position()))
                    player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, DURATION, 0, true, true));
            }
        }
    }

    private static boolean hears(ServerLevel level, SubLevelContainer container, Vec3 at) {
        if (playing(level, null, at))
            return true;
        if (container == null)
            return false;
        for (SubLevel ship : container.getAllSubLevels()) {
            if (ship.isRemoved() || ship.getPlot() == null)
                continue;
            Vector3dc c = ship.logicalPose().position();
            if (at.distanceToSqr(c.x(), c.y(), c.z()) > SHIP_REACH * SHIP_REACH)
                continue;
            Vector3d local = ship.logicalPose().transformPositionInverse(new Vector3d(at.x, at.y, at.z));
            if (playing(level, ship.getPlot(), new Vec3(local.x, local.y, local.z)))
                return true;
        }
        return false;
    }

    private static boolean playing(ServerLevel level, LevelPlot plot, Vec3 at) {
        int x0 = Mth.floor(at.x - RADIUS) >> 4, x1 = Mth.floor(at.x + RADIUS) >> 4;
        int z0 = Mth.floor(at.z - RADIUS) >> 4, z1 = Mth.floor(at.z + RADIUS) >> 4;
        if (plot != null) {
            BoundingBox3ic b = plot.getBoundingBox();
            x0 = Math.max(x0, b.minX() >> 4);
            x1 = Math.min(x1, b.maxX() >> 4);
            z0 = Math.max(z0, b.minZ() >> 4);
            z1 = Math.min(z1, b.maxZ() >> 4);
        }
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                LevelChunk chunk = plot == null ? level.getChunkSource().getChunkNow(cx, cz)
                        : plot.getChunk(plot.toLocal(new ChunkPos(cx, cz)));
                if (chunk == null)
                    continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof JukeboxBlockEntity jukebox) || !jukebox.getSongPlayer().isPlaying()
                            || !jukebox.getTheItem().is(CreateHighSeas.MUSIC_DISC_HIDDEN_BETWEEN_FLUENT_WATERS.get()))
                        continue;
                    if (be.getBlockPos().getCenter().distanceToSqr(at) <= RADIUS * RADIUS)
                        return true;
                }
            }
        }
        return false;
    }
}
