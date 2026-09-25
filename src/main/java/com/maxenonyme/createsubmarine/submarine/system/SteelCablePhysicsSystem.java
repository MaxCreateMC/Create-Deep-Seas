package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import com.maxenonyme.createsubmarine.submarine.util.SteelCableHolderAccessor;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

import java.util.List;
import java.util.UUID;

public class SteelCablePhysicsSystem {

    public static void onServerTick(ServerTickEvent.Post event) {
        for (Player player : event.getServer().getPlayerList().getPlayers()) {
            if (player.isSpectator())
                continue;

            if (player.level() instanceof ServerLevel sl) {
                Vector3d pPos = new Vector3d(player.getX(), player.getY() + player.getBbHeight() / 2.0, player.getZ());
                collidePlayerWithCables(player, sl, pPos, null);
            }

            UUID subId = SubLevelRegistry.findUUID(player.level());
            SubLevelAccess sub = subId != null ? SubLevelRegistry.getAll().get(subId) : null;
            if (sub != null && SubLevelRegistry.getLevel(subId) instanceof ServerLevel parent) {
                Vector3d pPos = new Vector3d(player.getX(), player.getY() + player.getBbHeight() / 2.0, player.getZ());
                sub.logicalPose().transformPosition(pPos);
                collidePlayerWithCables(player, parent, pPos, sub);
            }
        }

        for (ServerLevel serverLevel : event.getServer().getAllLevels()) {
            ServerLevelRopeManager ropeManager = ServerLevelRopeManager.getOrCreate(serverLevel);
            if (ropeManager == null)
                continue;

            for (ServerRopeStrand strand : ropeManager.getAllStrands()) {
                if (!strand.isActive())
                    continue;
                if (!isSteelCable(strand, serverLevel))
                    continue;

                List<Vector3d> points = strand.getPoints();
                for (int i = 0; i < points.size() - 1; i++) {
                    Vector3d a = points.get(i);
                    Vector3d b = points.get(i + 1);

                    AABB segmentBox = new AABB(
                            Math.min(a.x, b.x) - 1.0, Math.min(a.y, b.y) - 1.0, Math.min(a.z, b.z) - 1.0,
                            Math.max(a.x, b.x) + 1.0, Math.max(a.y, b.y) + 1.0, Math.max(a.z, b.z) + 1.0);

                    for (Entity entity : serverLevel.getEntities((Entity) null, segmentBox,
                            e -> !(e instanceof Player) && e.isAlive() && !e.isPassenger())) {
                        collideEntityWithCableSegment(entity, a, b);
                    }
                }
            }
        }
    }

    private static void collidePlayerWithCables(Player player, ServerLevel level, Vector3d pPos, SubLevelAccess sub) {
        ServerLevelRopeManager ropeManager = ServerLevelRopeManager.getOrCreate(level);
        if (ropeManager == null)
            return;

        for (ServerRopeStrand strand : ropeManager.getAllStrands()) {
            if (!strand.isActive())
                continue;
            if (!isSteelCable(strand, level))
                continue;

            List<Vector3d> points = strand.getPoints();
            for (int i = 0; i < points.size() - 1; i++) {
                Vector3d a = points.get(i);
                Vector3d b = points.get(i + 1);

                Vector3d pFeet = new Vector3d(player.getX(), player.getY() + 0.2, player.getZ());
                Vector3d pMid = new Vector3d(player.getX(), player.getY() + player.getBbHeight() / 2.0, player.getZ());
                Vector3d pHead = new Vector3d(player.getX(), player.getY() + player.getBbHeight() - 0.2, player.getZ());

                Vector3d cFeet = getClosestPointOnSegment(a, b, pFeet);
                Vector3d cMid = getClosestPointOnSegment(a, b, pMid);
                Vector3d cHead = getClosestPointOnSegment(a, b, pHead);

                double dFeet = pFeet.distance(cFeet);
                double dMid = pMid.distance(cMid);
                double dHead = pHead.distance(cHead);

                Vector3d bestP = pMid;
                Vector3d bestC = cMid;
                double bestD = dMid;

                if (dFeet < bestD) {
                    bestD = dFeet;
                    bestP = pFeet;
                    bestC = cFeet;
                }
                if (dHead < bestD) {
                    bestD = dHead;
                    bestP = pHead;
                    bestC = cHead;
                }

                double worldX = player.getX();
                double worldZ = player.getZ();
                double worldFeetY = player.getY();
                double horizontalDist = Math.sqrt((worldX - cFeet.x) * (worldX - cFeet.x) + (worldZ - cFeet.z) * (worldZ - cFeet.z));
                double vertDiff = worldFeetY - cFeet.y;

                if (horizontalDist < 0.4 && vertDiff >= -0.15 && vertDiff <= 0.45
                        && player.getDeltaMovement().y <= 0.05) {
                    double targetWorldFeetY = cFeet.y + 0.1;
                    double pushY = targetWorldFeetY - worldFeetY;

                    Vector3d pushVec = new Vector3d(0, pushY, 0);
                    if (sub != null) {
                        sub.logicalPose().orientation().conjugate(new Quaterniond()).transform(pushVec);
                    }

                    player.setPos(player.getX() + pushVec.x, player.getY() + pushVec.y, player.getZ() + pushVec.z);
                    player.setDeltaMovement(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
                    player.setOnGround(true);
                    player.fallDistance = 0.0f;

                    pPos.set(player.getX(), player.getY() + player.getBbHeight() / 2.0, player.getZ());
                    if (sub != null) {
                        sub.logicalPose().transformPosition(pPos);
                    }
                    continue;
                }

                double collisionLimit = 0.4;
                if (bestD < collisionLimit) {
                    Vector3d push = new Vector3d(bestP).sub(bestC);
                    double dist = push.length();
                    if (dist < 1e-6) {
                        push.set(0, 1, 0);
                        dist = 1.0;
                    }
                    double overlap = collisionLimit - dist;
                    push.normalize();

                    Vector3d pushVec = new Vector3d(push).mul(overlap);
                    if (sub != null) {
                        sub.logicalPose().orientation().conjugate(new Quaterniond()).transform(pushVec);
                    }

                    player.setPos(player.getX() + pushVec.x, player.getY() + pushVec.y, player.getZ() + pushVec.z);

                    net.minecraft.world.phys.Vec3 velocity = player.getDeltaMovement();
                    Vector3d vel = new Vector3d(velocity.x, velocity.y, velocity.z);
                    double dot = vel.dot(push);
                    if (dot < 0) {
                        vel.sub(new Vector3d(push).mul(dot));
                    }
                    vel.add(new Vector3d(push).mul(overlap * 0.8));
                    player.setDeltaMovement(new net.minecraft.world.phys.Vec3(vel.x, vel.y, vel.z));
                    player.hasImpulse = true;

                    pPos.set(player.getX(), player.getY() + player.getBbHeight() / 2.0, player.getZ());
                    if (sub != null) {
                        sub.logicalPose().transformPosition(pPos);
                    }
                }
            }
        }
    }

    private static void collideEntityWithCableSegment(Entity entity, Vector3d a, Vector3d b) {
        double h = entity.getBbHeight();
        double offset = Math.min(0.2, h / 3.0);
        
        Vector3d pFeet = new Vector3d(entity.getX(), entity.getY() + offset, entity.getZ());
        Vector3d pMid = new Vector3d(entity.getX(), entity.getY() + h / 2.0, entity.getZ());
        Vector3d pHead = new Vector3d(entity.getX(), entity.getY() + h - offset, entity.getZ());

        Vector3d cFeet = getClosestPointOnSegment(a, b, pFeet);
        Vector3d cMid = getClosestPointOnSegment(a, b, pMid);
        Vector3d cHead = getClosestPointOnSegment(a, b, pHead);

        double dFeet = pFeet.distance(cFeet);
        double dMid = pMid.distance(cMid);
        double dHead = pHead.distance(cHead);

        Vector3d bestP = pMid;
        Vector3d bestC = cMid;
        double bestD = dMid;

        if (dFeet < bestD) {
            bestD = dFeet;
            bestP = pFeet;
            bestC = cFeet;
        }
        if (dHead < bestD) {
            bestD = dHead;
            bestP = pHead;
            bestC = cHead;
        }

        double horizontalDist = Math.sqrt((entity.getX() - cFeet.x) * (entity.getX() - cFeet.x) + (entity.getZ() - cFeet.z) * (entity.getZ() - cFeet.z));
        double vertDiff = entity.getY() - cFeet.y;

        if (horizontalDist < 0.4 && vertDiff >= -0.15 && vertDiff <= 0.45 && entity.getDeltaMovement().y <= 0.05) {
            entity.setPos(entity.getX(), cFeet.y + 0.1, entity.getZ());
            entity.setDeltaMovement(entity.getDeltaMovement().x, 0.0, entity.getDeltaMovement().z);
            entity.setOnGround(true);
            entity.fallDistance = 0.0f;
            return;
        }

        double collisionLimit = 0.4;
        if (bestD < collisionLimit) {
            Vector3d push = new Vector3d(bestP).sub(bestC);
            double dist = push.length();
            if (dist < 1e-6) {
                push.set(0, 1, 0);
                dist = 1.0;
            }
            double overlap = collisionLimit - dist;
            push.normalize();

            entity.setPos(entity.getX() + push.x * overlap, entity.getY() + push.y * overlap,
                    entity.getZ() + push.z * overlap);

            net.minecraft.world.phys.Vec3 velocity = entity.getDeltaMovement();
            Vector3d vel = new Vector3d(velocity.x, velocity.y, velocity.z);
            double dot = vel.dot(push);
            if (dot < 0)
                vel.sub(new Vector3d(push).mul(dot));
            vel.add(new Vector3d(push).mul(overlap * 0.8));
            entity.setDeltaMovement(new net.minecraft.world.phys.Vec3(vel.x, vel.y, vel.z));
            entity.hasImpulse = true;
        }
    }


    public static Vector3d getClosestPointOnSegment(Vector3d a, Vector3d b, Vector3d p) {
        Vector3d ab = new Vector3d(b).sub(a);
        Vector3d ap = new Vector3d(p).sub(a);
        double abLenSq = ab.lengthSquared();
        if (abLenSq < 1e-6) {
            return new Vector3d(a);
        }
        double t = ap.dot(ab) / abLenSq;
        t = Math.clamp(t, 0.0, 1.0);
        return new Vector3d(a).add(ab.mul(t));
    }

    public static boolean isSteelCable(ServerRopeStrand strand, ServerLevel level) {
        if (strand instanceof SteelCableHolderAccessor accessor && accessor.createsubmarine$isSteelCable()) {
            return true;
        }
        RopeAttachment startAttachment = strand.getAttachment(RopeAttachmentPoint.START);
        if (startAttachment != null) {
            ServerLevel startLevel = CableElectrificationSystem.getLevelForAttachment(level, startAttachment);
            BlockEntity be = startLevel.getBlockEntity(startAttachment.blockAttachment());
            if (be instanceof SmartBlockEntity smartBe) {
                RopeStrandHolderBehavior behavior = smartBe.getBehaviour(RopeStrandHolderBehavior.TYPE);
                if (behavior instanceof SteelCableHolderAccessor accessor && accessor.createsubmarine$isSteelCable()) {
                    return true;
                }
            }
        }
        RopeAttachment endAttachment = strand.getAttachment(RopeAttachmentPoint.END);
        if (endAttachment != null) {
            ServerLevel endLevel = CableElectrificationSystem.getLevelForAttachment(level, endAttachment);
            BlockEntity be = endLevel.getBlockEntity(endAttachment.blockAttachment());
            if (be instanceof SmartBlockEntity smartBe) {
                RopeStrandHolderBehavior behavior = smartBe.getBehaviour(RopeStrandHolderBehavior.TYPE);
                if (behavior instanceof SteelCableHolderAccessor accessor && accessor.createsubmarine$isSteelCable()) {
                    return true;
                }
            }
        }
        return false;
    }
}
