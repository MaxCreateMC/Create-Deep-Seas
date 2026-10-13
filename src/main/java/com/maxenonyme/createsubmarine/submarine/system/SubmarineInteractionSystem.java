package com.maxenonyme.createsubmarine.submarine.system;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import com.maxenonyme.createsubmarine.submarine.math.OrientedBoundingBox3d;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
public class SubmarineInteractionSystem {
    private static final ConcurrentHashMap<UUID, Vector3d> LAST_POSITIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Double> SUB_VELOCITIES = new ConcurrentHashMap<>();
    private static int tickCounter = 0;

    public static void clearAll() {
        LAST_POSITIONS.clear();
        SUB_VELOCITIES.clear();
        tickCounter = 0;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        Map<UUID, OrientedBoundingBox3d> hulls = SubmarineHullManager.getActiveHulls();
        if (hulls.isEmpty()) {
            LAST_POSITIONS.clear();
            SUB_VELOCITIES.clear();
            return;
        }
        for (Map.Entry<UUID, OrientedBoundingBox3d> entry : hulls.entrySet()) {
            UUID id = entry.getKey();
            OrientedBoundingBox3d obb = entry.getValue();
            Level level = SubLevelRegistry.getLevel(id);
            if (level instanceof ServerLevel serverLevel) {
                Vector3d currentPos = new Vector3d(obb.getPosition());
                Vector3d lastPos = LAST_POSITIONS.get(id);
                double velocity = 0;
                if (lastPos != null) {
                    velocity = currentPos.distance(lastPos);
                }
                LAST_POSITIONS.put(id, currentPos);
                SUB_VELOCITIES.put(id, velocity);
                blockEntities(serverLevel, obb, velocity);
                if (velocity > 0.01 && tickCounter % 5 == 0) {
                    clearVegetation(serverLevel, obb);
                }
            }
        }
    }
    private static void blockEntities(ServerLevel level, OrientedBoundingBox3d obb, double velocity) {
        Vector3d pos = obb.getPosition();
        Vector3d dim = obb.getDimensions();
        boolean isHighSpeed = velocity > 1.0;
        double maxDim = Math.max(dim.x, Math.max(dim.y, dim.z)) * 0.8;
        AABB searchBox = new AABB(
            pos.x - maxDim, pos.y - maxDim, pos.z - maxDim,
            pos.x + maxDim, pos.y + maxDim, pos.z + maxDim
        );
        for (Entity entity : level.getEntitiesOfClass(Entity.class, searchBox)) {
            if (entity instanceof WaterAnimal || entity.getType().is(EntityTypeTags.AQUATIC)) {
                if (SubmarineHullManager.contains(obb, entity.getX(), entity.getY(), entity.getZ())) {
                    Vec3 center = new Vec3(pos.x, pos.y, pos.z);
                    Vec3 pushDir = entity.position().subtract(center).normalize();
                    if (pushDir.lengthSqr() < 0.01)
                        pushDir = new Vec3(0, 1, 0);
                    if (isHighSpeed) {
                        entity.setDeltaMovement(entity.getDeltaMovement().add(pushDir.scale(velocity * 1.5)));
                        entity.hurt(level.damageSources().generic(), (float)(velocity * 10.0));
                    } else {
                        entity.setDeltaMovement(entity.getDeltaMovement().add(pushDir.scale(0.2)));
                    }
                    entity.hasImpulse = true;
                }
            }
        }
    }
    private static void clearVegetation(ServerLevel level, OrientedBoundingBox3d obb) {
        Vector3d pos = obb.getPosition();
        Vector3d dim = obb.getDimensions();
        Vector3d ax = obb.getOrientation().transform(new Vector3d(dim.x * 0.5, 0, 0));
        Vector3d ay = obb.getOrientation().transform(new Vector3d(0, dim.y * 0.5, 0));
        Vector3d az = obb.getOrientation().transform(new Vector3d(0, 0, dim.z * 0.5));
        double hx = Math.abs(ax.x) + Math.abs(ay.x) + Math.abs(az.x);
        double hy = Math.abs(ax.y) + Math.abs(ay.y) + Math.abs(az.y);
        double hz = Math.abs(ax.z) + Math.abs(ay.z) + Math.abs(az.z);
        int minX = (int) Math.floor(pos.x - hx);
        int maxX = (int) Math.ceil(pos.x + hx);
        int minY = Math.max((int) Math.floor(pos.y - hy), level.getMinBuildHeight());
        int maxY = Math.min((int) Math.ceil(pos.y + hy), level.getMaxBuildHeight() - 1);
        int minZ = (int) Math.floor(pos.z - hz);
        int maxZ = (int) Math.ceil(pos.z + hz);
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null)
                    continue;
                int x0 = Math.max(minX, cx << 4), x1 = Math.min(maxX, (cx << 4) + 15);
                int z0 = Math.max(minZ, cz << 4), z1 = Math.min(maxZ, (cz << 4) + 15);
                LevelChunkSection[] sections = chunk.getSections();
                for (int sy = chunk.getSectionIndex(minY); sy <= chunk.getSectionIndex(maxY); sy++) {
                    if (sy < 0 || sy >= sections.length)
                        continue;
                    LevelChunkSection section = sections[sy];
                    if (section == null || section.hasOnlyAir() || !section.maybeHas(SubmarineInteractionSystem::isVegetation))
                        continue;
                    int yBase = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sy));
                    int y0 = Math.max(minY, yBase), y1 = Math.min(maxY, yBase + 15);
                    for (int y = y0; y <= y1; y++) {
                        for (int x = x0; x <= x1; x++) {
                            for (int z = z0; z <= z1; z++) {
                                BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                                if (!isVegetation(state))
                                    continue;
                                if (!SubmarineHullManager.contains(obb, x + 0.5, y + 0.5, z + 0.5))
                                    continue;
                                mutablePos.set(x, y, z);
                                clearVegetationColumn(level, mutablePos);
                                if (RAND.nextFloat() < 0.1f) {
                                    level.sendParticles(ParticleTypes.BUBBLE, x + 0.5, y + 0.5, z + 0.5, 2, 0.2, 0.2, 0.2, 0.01);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    private static void clearVegetationColumn(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        while (isVegetation(level.getBlockState(cursor))) {
            level.setBlock(cursor, Blocks.WATER.defaultBlockState(), 3);
            cursor.move(Direction.UP);
            if (cursor.getY() > level.getMaxBuildHeight())
                break;
        }
    }
    private static final Random RAND = new Random();
    private static boolean isVegetation(BlockState state) {
        return state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT) ||
               state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS);
    }
}
