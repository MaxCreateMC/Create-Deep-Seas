package com.maxenonyme.createsubmarine.submarine.block.entity;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import com.maxenonyme.createsubmarine.submarine.network.CameraShakePayload;
import com.maxenonyme.createsubmarine.submarine.system.MineOwnershipRegistry;
import com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

public class UnderwaterMineBlockEntity extends BlockEntity {
    private static final int MAX_WATER_SCAN = 200;

    private static final Map<UUID, Set<BlockPos>> ACTIVE_MINES = new ConcurrentHashMap<>();
    public static final Set<UnderwaterMineBlockEntity> LOADED_ON_CLIENT = Collections.newSetFromMap(new WeakHashMap<>());
    private boolean isExploded = false;
    public UUID ownerUUID;
    private UUID trackedSubId;
    private boolean exceedsLimitCached = false;
    private long lastLimitCheckTick = -1;

    public UnderwaterMineBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSubmarine.UNDERWATER_MINE_BE.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.ownerUUID != null) {
            tag.putUUID("OwnerUUID", this.ownerUUID);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("OwnerUUID")) {
            this.ownerUUID = tag.getUUID("OwnerUUID");
        }
    }

    public static void clearAll() {
        ACTIVE_MINES.clear();
        MineOwnershipRegistry.clearAll();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide)
            LOADED_ON_CLIENT.add(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED_ON_CLIENT.remove(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED_ON_CLIENT.remove(this);
        if (trackedSubId != null) {
            Set<BlockPos> mines = ACTIVE_MINES.get(trackedSubId);
            if (mines != null) {
                mines.remove(worldPosition);
                if (mines.isEmpty()) {
                    ACTIVE_MINES.remove(trackedSubId);
                }
            }
            trackedSubId = null;
        }
    }

    private static int getMineCount(UUID subId) {
        Set<BlockPos> mines = ACTIVE_MINES.get(subId);
        int count = (mines != null) ? mines.size() : 0;
        return count == 0 ? 1 : count;
    }

    private static boolean exceedsFloatBlockLimit(Level level, SubLevelAccess sub) {
        if (!(sub instanceof SubLevel sl) || sl.getPlot() == null) {
            return false;
        }
        BoundingBox3ic bounds = sl.getPlot().getBoundingBox();
        if (bounds == null) {
            return false;
        }
        int count = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    p.set(x, y, z);
                    if (!level.getBlockState(p).isAir() && ++count > 5) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void serverTick(Level level, BlockPos pos, UnderwaterMineBlockEntity be) {
        if (be.isExploded)
            return;

        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, pos);
        if (sub == null)
            return;

        UUID subId = sub.getUniqueId();
        ACTIVE_MINES.computeIfAbsent(subId, k -> ConcurrentHashMap.newKeySet()).add(pos);
        be.trackedSubId = subId;
        MineOwnershipRegistry.tag(subId, be.ownerUUID);

        Vector3d worldPos = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        sub.logicalPose().transformPosition(worldPos);

        Level parentLevel = SubLevelRegistry.getLevel(sub.getUniqueId());
        if (parentLevel == null && sub instanceof SubLevel sl) {
            parentLevel = sl.getLevel();
        }

        if (parentLevel == null)
            return;

        if (parentLevel instanceof ServerLevel serverParentLevel) {
            AABB triggerBox = new AABB(
                    worldPos.x - 3.0, worldPos.y - 3.0, worldPos.z - 3.0,
                    worldPos.x + 3.0, worldPos.y + 3.0, worldPos.z + 3.0);
            List<Entity> nearbyEntities = serverParentLevel.getEntitiesOfClass(
                    Entity.class, triggerBox,
                    e -> {
                        if (e instanceof Boat) {
                            return true;
                        }
                        if (e instanceof Player || e instanceof LivingEntity) {
                            if (be.ownerUUID != null && e.getUUID().equals(be.ownerUUID)) {
                                return false;
                            }
                            return true;
                        }
                        return false;
                    });

            boolean otherSubNearby = false;
            SubLevelContainer container = SubLevelContainer.getContainer(serverParentLevel);
            if (container != null) {
                for (SubLevel otherSub : container.getAllSubLevels()) {
                    UUID otherSubId = otherSub.getUniqueId();
                    if (sub != null && otherSubId.equals(sub.getUniqueId())) {
                        continue;
                    }
                    if (be.ownerUUID != null && be.ownerUUID.equals(MineOwnershipRegistry.getOwner(otherSubId))) {
                        continue;
                    }
                    Vector3d localInOther = new Vector3d(worldPos.x, worldPos.y, worldPos.z);
                    otherSub.logicalPose().transformPositionInverse(localInOther);

                    if (otherSub.getPlot() != null && otherSub.getPlot().getBoundingBox() != null) {
                        BoundingBox3ic otherBounds = otherSub.getPlot().getBoundingBox();
                        double dx = Math.max(0.0, Math.max(otherBounds.minX() - localInOther.x, localInOther.x - otherBounds.maxX()));
                        double dy = Math.max(0.0, Math.max(otherBounds.minY() - localInOther.y, localInOther.y - otherBounds.maxY()));
                        double dz = Math.max(0.0, Math.max(otherBounds.minZ() - localInOther.z, localInOther.z - otherBounds.maxZ()));
                        double distanceSq = dx * dx + dy * dy + dz * dz;
                        if (distanceSq <= 9.0) {
                            otherSubNearby = true;
                            break;
                        }
                    }
                }
            }

            if (!nearbyEntities.isEmpty() || otherSubNearby) {
                be.explode(level, pos, serverParentLevel, worldPos, sub);
                return;
            }
        }

        if (sub == null)
            return;

        if (level.getGameTime() - be.lastLimitCheckTick >= 20) {
            be.exceedsLimitCached = exceedsFloatBlockLimit(level, sub);
            be.lastLimitCheckTick = level.getGameTime();
        }
        if (be.exceedsLimitCached) {
            return;
        }

        Object handle = SablePhysicsHelper.getHandle(sub);
        Vector3dc currentVel = SablePhysicsHelper.getVelocity(handle);
        double currentVelY = (currentVel != null) ? currentVel.y() : 0;

        BlockPos parentPos = BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
        double localWaterSurfaceY = -999.0;

        FluidState fluidState = CompartmentTracker.realFluidState(parentLevel, parentPos);
        if (fluidState.is(CreateSubmarine.WATER_TAG)) {
            float h = fluidState.getHeight(parentLevel, parentPos);
            localWaterSurfaceY = parentPos.getY() + h + countWaterAbove(parentLevel, parentPos);
        } else {
            BlockPos belowPos = parentPos.below();
            FluidState belowFluid = CompartmentTracker.realFluidState(parentLevel, belowPos);
            if (belowFluid.is(CreateSubmarine.WATER_TAG)) {
                float h = belowFluid.getHeight(parentLevel, belowPos);
                localWaterSurfaceY = belowPos.getY() + h + countWaterAbove(parentLevel, belowPos);
            }
        }

        double depth = localWaterSurfaceY - (worldPos.y - 0.5);
        boolean isUnderWater = (depth > 0.0);

        if (!isUnderWater) {
            return;
        }

        double submergedRatio = Math.max(0.0, Math.min(1.0, depth));
        double distanceToSurface = localWaterSurfaceY - worldPos.y;
        double targetVelY = Math.max(-0.1, Math.min(4.0, distanceToSurface * 3.0));

        double perceivedVelY = Math.max(-0.5, currentVelY);
        double errorY = targetVelY - perceivedVelY;
        double mass = SablePhysicsHelper.readMass(sub);

        double forceMult = SubmarineConfig.BALLAST_FORCE_MULTIPLIER.get();
        int count = getMineCount(subId);
        double forceToApply = ((errorY * mass * 0.8 * forceMult) * submergedRatio) / count;

        double ballastMaxForce = (16000.0 * mass * forceMult) / count;
        forceToApply = Math.max(-ballastMaxForce, Math.min(ballastMaxForce, forceToApply));

        if (Double.isFinite(forceToApply)) {
            applyForce(sub, forceToApply);
        }
    }

    private void explode(Level level, BlockPos pos, ServerLevel parentLevel, Vector3d worldPos, SubLevelAccess sub) {
        if (this.isExploded)
            return;
        this.isExploded = true;

        if (level.getBlockState(pos).is(CreateSubmarine.UNDERWATER_MINE.get())) {
            level.removeBlock(pos, false);
        }

        double worldX = worldPos.x;
        double worldY = worldPos.y;
        double worldZ = worldPos.z;

        parentLevel.playSound(null, worldX, worldY, worldZ, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 10.0f, 0.5f);
        parentLevel.playSound(null, worldX, worldY, worldZ, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 8.0f, 0.8f);
        parentLevel.playSound(null, worldX, worldY, worldZ, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 6.0f, 1.2f);
        parentLevel.playSound(null, worldX, worldY, worldZ, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 8.0f, 0.5f);
        parentLevel.playSound(null, worldX, worldY, worldZ, CreateSubmarine.IMPACT_EXPLOSION_SOUND.get(), SoundSource.BLOCKS, 10.0f, 1.0f);

        parentLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, worldX, worldY, worldZ, 8, 2.0, 2.0, 2.0, 0.1);
        parentLevel.sendParticles(ParticleTypes.LARGE_SMOKE, worldX, worldY, worldZ, 300, 3.0, 3.0, 3.0, 0.0);
        parentLevel.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, worldX, worldY, worldZ, 120, 2.0, 2.0, 2.0, 0.0);
        parentLevel.sendParticles(ParticleTypes.BUBBLE, worldX, worldY, worldZ, 120, 3.5, 3.5, 3.5, 0.2);
        parentLevel.sendParticles(ParticleTypes.SPLASH, worldX, worldY, worldZ, 100, 3.0, 3.0, 3.0, 0.1);
        parentLevel.sendParticles(ParticleTypes.FLASH, worldX, worldY, worldZ, 3, 0.5, 0.5, 0.5, 0.0);

        double maxDist = 40.0;
        boolean mineInWater = CompartmentTracker.realFluidState(parentLevel, BlockPos.containing(worldX, worldY, worldZ))
                .is(CreateSubmarine.WATER_TAG);

        for (ServerPlayer player : parentLevel.players()) {
            double dist = player.distanceToSqr(worldX, worldY, worldZ);

            if (mineInWater && player.isUnderWater() && dist < 160.0 * 160.0) {
                player.connection.send(new ClientboundSoundPacket(
                        BuiltInRegistries.SOUND_EVENT.wrapAsHolder(CreateSubmarine.UNDERWATER_EXPLOSION_SOUND.get()),
                        SoundSource.BLOCKS,
                        worldX, worldY, worldZ,
                        10.0f, 1.0f,
                        parentLevel.getRandom().nextLong()
                ));
            }

            if (dist < maxDist * maxDist) {
                double distance = Math.sqrt(dist);
                float intensity = (float) (2.5 * (1.0 - (distance / maxDist)));
                if (intensity > 0.05f) {
                    int ticks = (int) (60.0 * (1.0 - (distance / maxDist)));
                    if (ticks < 10)
                        ticks = 10;
                    PacketDistributor.sendToPlayer(player,
                            new CameraShakePayload(intensity, ticks));
                }
            }
        }

        AABB damageBox = new AABB(
                worldX - 10.0, worldY - 10.0, worldZ - 10.0,
                worldX + 10.0, worldY + 10.0, worldZ + 10.0);
        List<Entity> entitiesToDamage = parentLevel.getEntitiesOfClass(
                Entity.class, damageBox);
        for (Entity e : entitiesToDamage) {
            double dist = e.distanceToSqr(worldX, worldY, worldZ);
            if (dist < 100.0) {
                double distance = Math.sqrt(dist);
                float damage = (float) (160.0 * (1.0 - (distance / 10.0)));
                if (damage > 1.0f) {
                    e.hurt(parentLevel.damageSources().explosion(null, null), damage);
                }
                if (e instanceof Boat) {
                    e.setRemainingFireTicks(300);
                }
                double pushForce = 2.0 * (1.0 - (distance / 10.0));
                Vec3 dir = new Vec3(e.getX() - worldX, e.getY() - worldY, e.getZ() - worldZ);
                if (dir.lengthSqr() < 0.01) {
                    dir = new Vec3(0, 1, 0);
                } else {
                    dir = dir.normalize();
                }
                e.setDeltaMovement(e.getDeltaMovement().add(dir.x * pushForce, dir.y * pushForce + 0.5, dir.z * pushForce));
                e.hurtMarked = true;
            }
        }

        int launchedCount = 0;
        SubLevelContainer container = SubLevelContainer.getContainer(parentLevel);
        if (container != null) {
            for (SubLevel otherSub : container.getAllSubLevels()) {
                UUID otherSubId = otherSub.getUniqueId();
                Vector3d localInOther = new Vector3d(worldX, worldY, worldZ);
                otherSub.logicalPose().transformPositionInverse(localInOther);

                if (otherSub.getPlot() != null && otherSub.getPlot().getBoundingBox() != null) {
                    BoundingBox3ic otherBounds = otherSub.getPlot().getBoundingBox();
                    Level otherLevel = otherSub.getLevel();
                    if (otherLevel != null) {
                        int minX = Math.max((int) Math.floor(localInOther.x - 8), otherBounds.minX());
                        int maxX = Math.min((int) Math.ceil(localInOther.x + 8), otherBounds.maxX());
                        int minY = Math.max((int) Math.floor(localInOther.y - 8), otherBounds.minY());
                        int maxY = Math.min((int) Math.ceil(localInOther.y + 8), otherBounds.maxY());
                        int minZ = Math.max((int) Math.floor(localInOther.z - 8), otherBounds.minZ());
                        int maxZ = Math.min((int) Math.ceil(localInOther.z + 8), otherBounds.maxZ());

                        BlockPos.MutableBlockPos otherMPos = new BlockPos.MutableBlockPos();
                        for (int lx = minX; lx <= maxX; lx++) {
                            for (int ly = minY; ly <= maxY; ly++) {
                                for (int lz = minZ; lz <= maxZ; lz++) {
                                    double dx = lx + 0.5 - localInOther.x;
                                    double dy = ly + 0.5 - localInOther.y;
                                    double dz = lz + 0.5 - localInOther.z;
                                    if (dx * dx + dy * dy + dz * dz <= 64) {
                                        otherMPos.set(lx, ly, lz);
                                        Vector3d otherWorldBlockPos = new Vector3d(lx + 0.5, ly + 0.5, lz + 0.5);
                                        otherSub.logicalPose().transformPosition(otherWorldBlockPos);
                                        boolean spawned = launchBlock(otherLevel, otherMPos, parentLevel, otherWorldBlockPos, new Vector3d(worldX, worldY, worldZ), launchedCount < 5);
                                        if (spawned) {
                                            launchedCount++;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        BlockPos.MutableBlockPos parentMPos = new BlockPos.MutableBlockPos();
        int px = (int) Math.floor(worldX);
        int py = (int) Math.floor(worldY);
        int pz = (int) Math.floor(worldZ);
        for (int x = -8; x <= 8; x++) {
            for (int y = -8; y <= 8; y++) {
                for (int z = -8; z <= 8; z++) {
                    if (x * x + y * y + z * z <= 64) {
                        parentMPos.set(px + x, py + y, pz + z);
                        Vector3d parentBlockPos = new Vector3d(parentMPos.getX() + 0.5, parentMPos.getY() + 0.5, parentMPos.getZ() + 0.5);
                        boolean spawned = launchBlock(parentLevel, parentMPos, parentLevel, parentBlockPos, new Vector3d(worldX, worldY, worldZ), launchedCount < 5);
                        if (spawned) {
                            launchedCount++;
                        }
                    }
                }
            }
        }
    }

    private static boolean launchBlock(Level blockLevel, BlockPos blockPos, ServerLevel parentLevel, Vector3d worldBlockPos, Vector3d explosionSource, boolean spawnEntity) {
        BlockState state = blockLevel.getBlockState(blockPos);
        if (state.isAir())
            return false;
        if (state.getDestroySpeed(blockLevel, blockPos) < 0)
            return false;

        boolean isWood = state.is(BlockTags.PLANKS)
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.WOODEN_FENCES)
                || state.is(BlockTags.WOODEN_SLABS);

        blockLevel.removeBlock(blockPos, false);

        if (isWood && blockLevel.getFluidState(blockPos).isEmpty()) {
            blockLevel.setBlock(blockPos, Blocks.FIRE.defaultBlockState(), 3);
        }

        if (spawnEntity) {
            FallingBlockEntity fallingBlock = new FallingBlockEntity(
                    parentLevel, worldBlockPos.x, worldBlockPos.y, worldBlockPos.z, state);
            fallingBlock.time = 1;
            fallingBlock.dropItem = false;

            Vector3d direction = new Vector3d(worldBlockPos).sub(explosionSource);
            double dist = direction.length();
            if (dist < 0.1) {
                direction.set(0, 1, 0);
                dist = 1.0;
            } else {
                direction.normalize();
            }

            double force = 1.5 * (1.0 - (dist / 8.0));
            double vx = direction.x * force + (parentLevel.random.nextFloat() - 0.5f) * 0.3;
            double vy = Math.abs(direction.y * force) + 0.6 + parentLevel.random.nextFloat() * 0.4;
            double vz = direction.z * force + (parentLevel.random.nextFloat() - 0.5f) * 0.3;

            fallingBlock.setDeltaMovement(new Vec3(vx, vy, vz));
            fallingBlock.hurtMarked = true;
            parentLevel.addFreshEntity(fallingBlock);
            return true;
        }
        return false;
    }

    private static int countWaterAbove(Level level, BlockPos pos) {
        int depth = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = pos.getY() + 1; y < pos.getY() + 1 + MAX_WATER_SCAN; y++) {
            cursor.set(pos.getX(), y, pos.getZ());
            if (CompartmentTracker.realFluidState(level, cursor).is(CreateSubmarine.WATER_TAG)) {
                depth++;
            } else {
                break;
            }
        }
        return depth;
    }

    private static void applyForce(SubLevelAccess sub, double forceY) {
        Object handle = SablePhysicsHelper.getHandle(sub);
        if (handle == null)
            return;
        SablePhysicsHelper.wakeUp(handle);

        double velY = 0;
        Vector3dc vel = SablePhysicsHelper.getVelocity(handle);
        if (vel != null)
            velY = vel.y();

        double finalForce = (Math.abs(velY) < 0.01 && forceY < 0) ? forceY * 0.1 : forceY;

        Vector3d forceVec = new Vector3d(0, finalForce, 0);
        sub.logicalPose().orientation().conjugate(new Quaterniond()).transform(forceVec);

        SablePhysicsHelper.applyLinearImpulse(handle, forceVec);
    }
}
