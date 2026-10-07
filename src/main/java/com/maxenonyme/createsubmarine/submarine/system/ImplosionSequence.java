package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.network.ImplosionFxPayload;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3d;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class ImplosionSequence {
    private ImplosionSequence() {
    }

    public static final int BUILD_UP = 60;
    private static final double STRESS_REACH = 48.0;
    private static final double BLAST_REACH = 64.0;
    private static final double BLAST_RADIUS = 14.0;
    private static final float BLAST_DAMAGE = 26.0f;
    private static final float CRUSHED = 1000.0f;
    private static final int[] CREAKS = { 0, 14, 26, 36, 44, 50, 54, 57, 59 };

    private static final ResourceKey<DamageType> IMPLOSION = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "implosion"));

    private record Pending(ServerLevel level, Vec3 origin, long start, Runnable burst) {
    }

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    public static void clearAll() {
        PENDING.clear();
    }

    public static boolean isBuilding(UUID id) {
        return PENDING.containsKey(id);
    }

    public static void buildUp(ServerLevel level, UUID id, Vec3 origin, Runnable burst) {
        if (PENDING.putIfAbsent(id, new Pending(level, origin, level.getServer().getTickCount(), burst)) != null)
            return;
        for (ServerPlayer player : level.players()) {
            double distance = Math.sqrt(player.distanceToSqr(origin));
            if (distance <= STRESS_REACH)
                PacketDistributor.sendToPlayer(player, new ImplosionFxPayload(ImplosionFxPayload.STRESS,
                        (float) Math.max(0.2, 1 - distance / STRESS_REACH), BUILD_UP));
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty())
            return;
        long now = event.getServer().getTickCount();
        Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Pending pending = it.next().getValue();
            long age = now - pending.start();
            for (int creak : CREAKS) {
                if (creak == age)
                    creak(pending, age / (float) BUILD_UP);
            }
            if (age >= BUILD_UP) {
                it.remove();
                pending.burst().run();
            }
        }
    }

    private static void creak(Pending pending, float progress) {
        Vec3 at = pending.origin();
        ServerLevel level = pending.level();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.BLOCKS,
                0.6f + progress * 1.6f, 0.45f - progress * 0.3f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS,
                0.15f + progress * 0.5f, 0.5f - progress * 0.2f);
        level.sendParticles(ParticleTypes.BUBBLE, at.x, at.y, at.z, 4 + (int) (progress * 20), 0.4, 0.4, 0.4,
                0.05 + progress * 0.2);
        level.sendParticles(ParticleTypes.DRIPPING_WATER, at.x, at.y, at.z, 3 + (int) (progress * 12), 0.5, 0.5,
                0.5, 0.0);
    }

    public static void rupture(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.4f, 1.5f);
        level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, at.x, at.y, at.z, 40, 0.4, 0.4, 0.4, 0.4);
        level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 30, 0.5, 0.5, 0.5, 0.3);
        for (ServerPlayer player : level.players()) {
            double distance = Math.sqrt(player.distanceToSqr(at));
            if (distance <= STRESS_REACH)
                PacketDistributor.sendToPlayer(player, new ImplosionFxPayload(ImplosionFxPayload.BLAST,
                        (float) (0.45 * (1 - distance / STRESS_REACH)), 0));
        }
    }

    public static void blast(ServerLevel level, Vec3 center, Predicate<ServerPlayer> crushed) {
        DamageSource source = new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(IMPLOSION));
        for (ServerPlayer player : List.copyOf(level.players())) {
            double distance = Math.sqrt(player.distanceToSqr(center));
            if (distance > BLAST_REACH)
                continue;
            float damage = crushed.test(player) ? CRUSHED
                    : distance < BLAST_RADIUS ? BLAST_DAMAGE * (float) Math.pow(1 - distance / BLAST_RADIUS, 2) : 0;
            if (damage > 0.5f && !player.isCreative() && !player.isSpectator())
                player.hurt(source, damage);
            boolean fatal = player.isDeadOrDying();
            float strength = (float) Math.max(0.15, 1 - distance / BLAST_REACH);
            PacketDistributor.sendToPlayer(player,
                    new ImplosionFxPayload(fatal ? ImplosionFxPayload.FATAL : ImplosionFxPayload.BLAST, strength, 0));
        }
    }

    public static Predicate<ServerPlayer> aboard(SubLevelAccess sub, SubLevelRegistry.PlotBounds bounds) {
        return player -> {
            for (BlockPos local : body(sub, player)) {
                if (local.getX() >= bounds.minX() && local.getX() <= bounds.maxX()
                        && local.getY() >= bounds.minY() && local.getY() <= bounds.maxY()
                        && local.getZ() >= bounds.minZ() && local.getZ() <= bounds.maxZ())
                    return true;
            }
            return false;
        };
    }

    public static Predicate<ServerPlayer> inside(SubLevelAccess sub, Set<BlockPos> cells) {
        return player -> {
            for (BlockPos local : body(sub, player)) {
                if (cells.contains(local))
                    return true;
            }
            return false;
        };
    }

    private static BlockPos[] body(SubLevelAccess sub, ServerPlayer player) {
        double[] heights = { 0.1, player.getBbHeight() * 0.5, player.getEyeHeight() };
        BlockPos[] out = new BlockPos[heights.length];
        for (int i = 0; i < heights.length; i++) {
            Vector3d p = new Vector3d(player.getX(), player.getY() + heights[i], player.getZ());
            sub.logicalPose().transformPositionInverse(p);
            out[i] = BlockPos.containing(p.x, p.y, p.z);
        }
        return out;
    }
}
