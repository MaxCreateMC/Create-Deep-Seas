package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.submarine.util.SteelCableHolderAccessor;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.ArrayList;
import java.util.UUID;

public class CableElectrificationSystem {

    public static final Map<BlockEntity, ElectrifiedEnergyStorage> WINCH_ENERGY = Collections
            .synchronizedMap(new WeakHashMap<>());

    private static final int FE_CAPACITY = 1000000;
    private static final int FE_TRANSFER_RATE = 75000;
    private static final int FE_DRAIN_PER_TICK = 50;
    private static final float DAMAGE_AMOUNT = 2.0f;
    private static final double DAMAGE_RADIUS = 1.0;
    private static final int DAMAGE_INTERVAL_TICKS = 20;

    private static int tickCounter = 0;

    public static ElectrifiedEnergyStorage getOrCreateStorage(BlockEntity be) {
        return WINCH_ENERGY.computeIfAbsent(be, k -> new ElectrifiedEnergyStorage(FE_CAPACITY));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;

        for (ServerLevel serverLevel : event.getServer().getAllLevels()) {
            ServerLevelRopeManager ropeManager = ServerLevelRopeManager.getOrCreate(serverLevel);
            if (ropeManager == null)
                continue;

            for (ServerRopeStrand strand : ropeManager.getAllStrands()) {
                if (!strand.isActive())
                    continue;
                if (!SteelCablePhysicsSystem.isSteelCable(strand, serverLevel))
                    continue;

                RopeAttachment startAttachment = strand.getAttachment(RopeAttachmentPoint.START);
                RopeAttachment endAttachment = strand.getAttachment(RopeAttachmentPoint.END);
                if (startAttachment != null && endAttachment != null) {
                    ServerLevel startLevel = getLevelForAttachment(serverLevel, startAttachment);
                    ServerLevel endLevel = getLevelForAttachment(serverLevel, endAttachment);
                    if (startLevel != null && endLevel != null) {
                        BlockEntity startBE = startLevel.getBlockEntity(startAttachment.blockAttachment());
                        BlockEntity endBE = endLevel.getBlockEntity(endAttachment.blockAttachment());
                        if ((startBE instanceof RopeWinchBlockEntity || startBE instanceof RopeConnectorBlockEntity) &&
                                (endBE instanceof RopeWinchBlockEntity || endBE instanceof RopeConnectorBlockEntity)) {
                            ElectrifiedEnergyStorage storageA = getOrCreateStorage(startBE);
                            ElectrifiedEnergyStorage storageB = getOrCreateStorage(endBE);
                            if (storageA != null && storageB != null) {
                                int energyA = storageA.getEnergyStored();
                                int energyB = storageB.getEnergyStored();
                                boolean changed = false;
                                if (energyA > energyB) {
                                    int toTransfer = Math.min(FE_TRANSFER_RATE, (energyA - energyB) / 2);
                                    if (toTransfer > 0) {
                                        int extracted = storageA.extractEnergy(toTransfer, false);
                                        int received = storageB.receiveEnergy(extracted, false);
                                        if (extracted > received) {
                                            storageA.receiveEnergy(extracted - received, false);
                                        }
                                        changed = true;
                                    }
                                } else if (energyB > energyA) {
                                    int toTransfer = Math.min(FE_TRANSFER_RATE, (energyB - energyA) / 2);
                                    if (toTransfer > 0) {
                                        int extracted = storageB.extractEnergy(toTransfer, false);
                                        int received = storageA.receiveEnergy(extracted, false);
                                        if (extracted > received) {
                                            storageB.receiveEnergy(extracted - received, false);
                                        }
                                        changed = true;
                                    }
                                }
                                if (changed) {
                                    startBE.setChanged();
                                    startLevel.sendBlockUpdated(startBE.getBlockPos(), startBE.getBlockState(),
                                            startBE.getBlockState(), 2);
                                    endBE.setChanged();
                                    endLevel.sendBlockUpdated(endBE.getBlockPos(), endBE.getBlockState(),
                                            endBE.getBlockState(), 2);
                                }
                            }
                        }
                    }
                }

                BlockEntity electrifiedNode = findElectrifiedNode(strand, serverLevel);
                if (electrifiedNode == null)
                    continue;

                ElectrifiedEnergyStorage storage = WINCH_ENERGY.get(electrifiedNode);
                if (storage == null || storage.getEnergyStored() <= 0)
                    continue;

                int oldEnergy = storage.getEnergyStored();
                storage.extractEnergy(FE_DRAIN_PER_TICK, false);
                if (storage.getEnergyStored() != oldEnergy && tickCounter % 10 == 0) {
                    electrifiedNode.setChanged();
                    serverLevel.sendBlockUpdated(electrifiedNode.getBlockPos(), electrifiedNode.getBlockState(),
                            electrifiedNode.getBlockState(), 2);
                }

                List<Vector3d> points = strand.getPoints();
                spawnSparksAlongCable(serverLevel, points);

                if (tickCounter % DAMAGE_INTERVAL_TICKS == 0) {
                    damageEntitiesAlongCable(serverLevel, points, event.getServer());
                }
            }
        }

        List<BlockEntity> electrifiedBlocks;
        synchronized (WINCH_ENERGY) {
            electrifiedBlocks = new ArrayList<>(WINCH_ENERGY.keySet());
        }
        for (BlockEntity be : electrifiedBlocks) {
            if (be.isRemoved())
                continue;
            Level level = be.getLevel();
            if (level != null && !level.isClientSide()) {
                ElectrifiedEnergyStorage myStorage = WINCH_ENERGY.get(be);
                if (myStorage != null) {
                    tickAdjacentEnergyTransfer(be, myStorage, level);
                }
            }
        }
    }

    private static void tickAdjacentEnergyTransfer(BlockEntity be, ElectrifiedEnergyStorage myStorage, Level level) {
        BlockPos pos = be.getBlockPos();
        for (Direction dir : Direction.values()) {
            BlockPos adjPos = pos.relative(dir);
            if (!level.isLoaded(adjPos))
                continue;
            BlockEntity adjBE = level.getBlockEntity(adjPos);
            if (adjBE == null)
                continue;

            IEnergyStorage adjStorage = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK,
                    adjPos,
                    level.getBlockState(adjPos),
                    adjBE,
                    dir.getOpposite());
            if (adjStorage == null)
                continue;

            int myEnergy = myStorage.getEnergyStored();
            int adjEnergy = adjStorage.getEnergyStored();

            if (myEnergy > adjEnergy) {
                int maxPush = Math.min(FE_TRANSFER_RATE, (myEnergy - adjEnergy) / 2);
                if (maxPush > 0) {
                    int extracted = myStorage.extractEnergy(maxPush, true);
                    int accepted = adjStorage.receiveEnergy(extracted, true);
                    if (accepted > 0) {
                        int actualExtracted = myStorage.extractEnergy(accepted, false);
                        adjStorage.receiveEnergy(actualExtracted, false);
                        if (actualExtracted > 0) {
                            be.setChanged();
                            boolean sync = (tickCounter + pos.hashCode()) % 10 == 0;
                            if (sync && level instanceof ServerLevel sl) {
                                sl.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 2);
                            }
                            if (adjBE instanceof RopeWinchBlockEntity || adjBE instanceof RopeConnectorBlockEntity) {
                                adjBE.setChanged();
                                if (sync && level instanceof ServerLevel sl) {
                                    sl.sendBlockUpdated(adjPos, adjBE.getBlockState(), adjBE.getBlockState(), 2);
                                }
                            }
                        }
                    }
                }
            } else if (adjEnergy > myEnergy) {
                int maxPull = Math.min(FE_TRANSFER_RATE, (adjEnergy - myEnergy) / 2);
                if (maxPull > 0) {
                    int extracted = adjStorage.extractEnergy(maxPull, true);
                    int accepted = myStorage.receiveEnergy(extracted, true);
                    if (accepted > 0) {
                        int actualExtracted = adjStorage.extractEnergy(accepted, false);
                        myStorage.receiveEnergy(actualExtracted, false);
                        if (actualExtracted > 0) {
                            be.setChanged();
                            boolean sync = (tickCounter + pos.hashCode()) % 10 == 0;
                            if (sync && level instanceof ServerLevel sl) {
                                sl.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 2);
                            }
                            if (adjBE instanceof RopeWinchBlockEntity || adjBE instanceof RopeConnectorBlockEntity) {
                                adjBE.setChanged();
                                if (sync && level instanceof ServerLevel sl) {
                                    sl.sendBlockUpdated(adjPos, adjBE.getBlockState(), adjBE.getBlockState(), 2);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static ServerLevel getLevelForAttachment(ServerLevel defaultLevel, RopeAttachment attachment) {
        if (attachment.subLevelID() != null) {
            Level level = SubLevelRegistry.getLevel(attachment.subLevelID());
            if (level instanceof ServerLevel serverLevel) {
                return serverLevel;
            }
        }
        return defaultLevel;
    }

    private static BlockEntity findElectrifiedNode(ServerRopeStrand strand, ServerLevel level) {
        for (RopeAttachmentPoint point : new RopeAttachmentPoint[] { RopeAttachmentPoint.START,
                RopeAttachmentPoint.END }) {
            RopeAttachment attachment = strand.getAttachment(point);
            if (attachment == null)
                continue;
            BlockEntity be = level.getBlockEntity(attachment.blockAttachment());
            if (be instanceof RopeWinchBlockEntity || be instanceof RopeConnectorBlockEntity) {
                EnergyStorage storage = WINCH_ENERGY.get(be);
                if (storage != null && storage.getEnergyStored() > 0)
                    return be;
            }
            if (be instanceof SmartBlockEntity smartBe) {
                RopeStrandHolderBehavior behavior = smartBe.getBehaviour(RopeStrandHolderBehavior.TYPE);
                if (behavior instanceof SteelCableHolderAccessor accessor && accessor.createsubmarine$isSteelCable()) {
                    EnergyStorage storage = WINCH_ENERGY.get(be);
                    if (storage != null && storage.getEnergyStored() > 0)
                        return be;
                }
            }
        }
        return null;
    }

    private static void spawnSparksAlongCable(ServerLevel level, List<Vector3d> points) {
        if (points.size() < 2)
            return;
        int sparkCount = Math.min(points.size() - 1, 3);
        for (int k = 0; k < sparkCount; k++) {
            int i = (tickCounter + k * 7) % (points.size() - 1);
            Vector3d a = points.get(i);
            Vector3d b = points.get(i + 1);
            double t = (Math.random() * 0.6 + 0.2);
            double x = a.x + (b.x - a.x) * t;
            double y = a.y + (b.y - a.y) * t;
            double z = a.z + (b.z - a.z) * t;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 3, 0.05, 0.05, 0.05, 0.1);
        }
    }

    private static void damageEntitiesAlongCable(ServerLevel level, List<Vector3d> points,
            MinecraftServer server) {
        if (points.size() < 2)
            return;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Vector3d p : points) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
            maxZ = Math.max(maxZ, p.z);
        }
        AABB bounds = new AABB(minX - DAMAGE_RADIUS, minY - DAMAGE_RADIUS, minZ - DAMAGE_RADIUS,
                maxX + DAMAGE_RADIUS, maxY + DAMAGE_RADIUS, maxZ + DAMAGE_RADIUS);

        for (Entity entity : level.getEntities((Entity) null, bounds,
                e -> e instanceof LivingEntity && e.isAlive() && !(e instanceof Player))) {
            tryDamage(level, (LivingEntity) entity,
                    new Vector3d(entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ()), points);
        }

        for (Player player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !player.isAlive())
                continue;

            if (player.level() == level) {
                Vector3d worldPos = new Vector3d(player.getX(), player.getY() + player.getBbHeight() / 2.0,
                        player.getZ());
                tryDamage(level, player, worldPos, points);
                continue;
            }

            UUID subId = SubLevelRegistry.findUUID(player.level());
            SubLevelAccess subLevel = subId != null ? SubLevelRegistry.getAll().get(subId) : null;
            if (subLevel != null && SubLevelRegistry.getLevel(subId) == level) {
                Vector3d worldPos = new Vector3d(player.getX(), player.getY() + player.getBbHeight() / 2.0,
                        player.getZ());
                subLevel.logicalPose().transformPosition(worldPos);
                tryDamage(level, player, worldPos, points);
            }
        }
    }

    private static void tryDamage(ServerLevel level, LivingEntity entity, Vector3d worldPos, List<Vector3d> points) {
        double closestDist = Double.MAX_VALUE;
        for (int i = 0; i < points.size() - 1; i++) {
            Vector3d vec = getClosestPoint(points.get(i), points.get(i + 1), worldPos);
            closestDist = Math.min(closestDist, worldPos.distance(vec));
        }
        if (closestDist <= DAMAGE_RADIUS) {
            entity.hurt(level.damageSources().lightningBolt(), DAMAGE_AMOUNT);
        }
    }

    private static Vector3d getClosestPoint(Vector3d a, Vector3d b, Vector3d p) {
        Vector3d ab = new Vector3d(b).sub(a);
        double t = new Vector3d(p).sub(a).dot(ab) / ab.lengthSquared();
        t = Math.clamp(t, 0.0, 1.0);
        return new Vector3d(a).add(new Vector3d(ab).mul(t));
    }

    public static class ElectrifiedEnergyStorage extends EnergyStorage {
        public ElectrifiedEnergyStorage(int capacity) {
            super(capacity);
        }

        public void setEnergy(int energy) {
            this.energy = energy;
        }
    }
}
