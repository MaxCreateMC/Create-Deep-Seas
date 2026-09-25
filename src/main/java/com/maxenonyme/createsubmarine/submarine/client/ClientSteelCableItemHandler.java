package com.maxenonyme.createsubmarine.submarine.client;

import dev.ryanhcode.sable.Sable;
import dev.simulated_team.simulated.config.server.blocks.SimBlockConfigs;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
import dev.simulated_team.simulated.index.SimDataComponents;
import dev.simulated_team.simulated.service.SimConfigService;
import dev.simulated_team.simulated.util.SimColors;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import com.maxenonyme.createsubmarine.submarine.util.SteelCableHolderAccessor;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import com.maxenonyme.highseas.block.entity.AnchorBlockEntity;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopePoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.UUID;
import net.minecraft.world.entity.MoverType;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public class ClientSteelCableItemHandler {
    public static void tick() {
        final Player player = Minecraft.getInstance().player;
        final Level level = Minecraft.getInstance().level;

        if (player == null || level == null)
            return;
        if (Minecraft.getInstance().screen != null)
            return;

        for (final InteractionHand hand : InteractionHand.values()) {
            final ItemStack heldItem = player.getItemInHand(hand);

            if (!heldItem.is(CreateSubmarine.STEEL_CABLE.get()))
                continue;

            if (!heldItem.has(SimDataComponents.ROPE_FIRST_CONNECTION))
                continue;

            final BlockPos firstBlock = heldItem.get(SimDataComponents.ROPE_FIRST_CONNECTION);
            final HitResult rayTrace = Minecraft.getInstance().hitResult;

            if (rayTrace instanceof final BlockHitResult hitResult) {
                final BlockPos hitBlock = hitResult.getBlockPos();
                
                final RopeStrandHolderBehavior holderA = RopeItem.getRopeHolder(level, hitBlock);
                final RopeStrandHolderBehavior holderB = RopeItem.getRopeHolder(level, firstBlock);

                Vec3 firstPoint = firstBlock.getCenter();
                if (holderB != null && holderB.blockEntity instanceof AnchorBlockEntity anchorB) {
                    firstPoint = anchorB.getAttachmentPoint(firstBlock, level.getBlockState(firstBlock));
                } else if (holderB != null && holderB.blockEntity instanceof RopeWinchBlockEntity) {
                    firstPoint = firstBlock.getCenter();
                }

                final double maxRopeRange = SubmarineConfig.STEEL_CABLE_MAX_LENGTH
                        .get();

                boolean inRange = Sable.HELPER.distanceSquaredWithSubLevels(level, firstPoint,
                        hitResult.getLocation()) < maxRopeRange * maxRopeRange;
                boolean valid = RopeItem.isValidRopeAttachment(level, hitBlock) && !hitBlock.equals(firstBlock)
                        && inRange;

                if (valid &&
                        holderA != null && holderA.blockEntity instanceof RopeWinchBlockEntity &&
                        holderB != null && holderB.blockEntity instanceof RopeWinchBlockEntity)
                    valid = false;

                Vec3 target = hitResult.getLocation();
                if (valid) {
                    if (holderA != null && holderA.blockEntity instanceof AnchorBlockEntity anchorA) {
                        target = anchorA.getAttachmentPoint(hitBlock, level.getBlockState(hitBlock));
                    } else {
                        target = hitBlock.getCenter();
                    }
                }

                final Color color;
                if (valid) {
                    color = new Color(SimColors.SUCCESS_LIME);
                } else {
                    color = new Color(inRange ? SimColors.PERCHANCE_ORANGE : SimColors.NUH_UH_RED);
                }

                Outliner.getInstance().chaseAABB("FirstRopeAttachmentPoint", new AABB(firstPoint, firstPoint))
                        .colored(color)
                        .lineWidth(1 / 3f)
                        .disableLineNormals();

                final Vec3 globalFirstPoint = Sable.HELPER.projectOutOfSubLevel(level, firstPoint);
                Vec3 globalTarget = Sable.HELPER.projectOutOfSubLevel(level, target);

                if (valid) {
                    Outliner.getInstance().chaseAABB("SecondRopeAttachmentPoint", new AABB(target, target))
                            .colored(color)
                            .lineWidth(1 / 3f)
                            .disableLineNormals();

                    final double points = Math.floor(globalFirstPoint.distanceTo(globalTarget));
                    final Vec3 backwardsDiff = globalFirstPoint.subtract(globalTarget).normalize();
                    for (int i = 0; i < points; i++) {
                        final Vec3 point = globalTarget.add(backwardsDiff.scale(i));

                        Outliner.getInstance().chaseAABB("RopePoint" + i, new AABB(point, point))
                                .colored(color)
                                .lineWidth(1 / 8f)
                                .disableLineNormals();
                    }
                } else if (!inRange) {
                    globalTarget = globalTarget.subtract(globalFirstPoint).normalize().scale(maxRopeRange - 0.5)
                            .add(globalFirstPoint);
                    Outliner.getInstance().chaseAABB("SecondRopeAttachmentPoint", new AABB(globalTarget, globalTarget))
                            .colored(color)
                            .lineWidth(1 / 3f)
                            .disableLineNormals();
                }

                final DustParticleOptions data = new DustParticleOptions(color.asVectorF(), 1);
                final double totalFlyingTicks = 10;
                final int segments = (((int) totalFlyingTicks) / 3) + 1;

                for (int i = 0; i < segments; i++) {
                    final Vec3 vec = globalFirstPoint.lerp(globalTarget, level.random.nextFloat());
                    level.addParticle(data, vec.x, vec.y, vec.z, 0, 0, 0);
                }
            }
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        tick();
        tickPlayerCollision();
    }

    public static void tickPlayerCollision() {
        final Player player = Minecraft.getInstance().player;
        final Level level = Minecraft.getInstance().level;
        if (player == null || level == null)
            return;
        if (player.isSpectator())
            return;

        ClientLevelRopeManager ropeManager = ClientLevelRopeManager
                .getOrCreate(level);
        if (ropeManager != null) {
            Vector3d pPos = new Vector3d(player.getX(), player.getY() + player.getBbHeight() / 2.0,
                    player.getZ());
            collidePlayerWithCables(player, ropeManager, pPos, null);
        }

        UUID subId = SubLevelRegistry.findUUID(player.level());
        SubLevelAccess sub = subId != null
                ? SubLevelRegistry.getAll().get(subId)
                : null;
        if (sub != null) {
            Level parent = SubLevelRegistry.getLevel(subId);
            ClientLevelRopeManager parentManager = parent != null
                    ? ClientLevelRopeManager
                            .getOrCreate(parent)
                    : null;
            if (parentManager != null) {
                Vector3d pPos = new Vector3d(player.getX(),
                        player.getY() + player.getBbHeight() / 2.0, player.getZ());
                sub.logicalPose().transformPosition(pPos);
                collidePlayerWithCables(player, parentManager, pPos, sub);
            }
        }
    }

    private static void collidePlayerWithCables(Player player,
            ClientLevelRopeManager ropeManager,
            Vector3d pPos,
            SubLevelAccess sub) {
        for (ClientRopeStrand strand : ropeManager
                .getAllStrands()) {
            if (!(strand instanceof SteelCableHolderAccessor accessor)
                    || !accessor.createsubmarine$isSteelCable()) {
                continue;
            }

            ObjectArrayList<ClientRopePoint> points = strand
                    .getPoints();
            if (points.size() <= 1)
                continue;

            for (int i = 0; i < points.size() - 1; i++) {
                Vector3d a = points.get(i).position();
                Vector3d b = points.get(i + 1).position();

                Vector3d pFeet = new Vector3d(player.getX(), player.getY() + 0.2, player.getZ());
                Vector3d pMid = new Vector3d(player.getX(),
                        player.getY() + player.getBbHeight() / 2.0, player.getZ());
                Vector3d pHead = new Vector3d(player.getX(),
                        player.getY() + player.getBbHeight() - 0.2, player.getZ());

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
                double horizontalDist = Math
                        .sqrt((worldX - cFeet.x) * (worldX - cFeet.x) + (worldZ - cFeet.z) * (worldZ - cFeet.z));
                double vertDiff = worldFeetY - cFeet.y;

                if (horizontalDist < 0.4 && vertDiff >= -0.15 && vertDiff <= 0.45
                        && player.getDeltaMovement().y <= 0.05) {
                    double targetWorldFeetY = cFeet.y + 0.1;
                    double pushY = Math.max(0.0, targetWorldFeetY - worldFeetY);

                    Vector3d pushVec = new Vector3d(0, pushY, 0);
                    if (sub != null) {
                        sub.logicalPose().orientation().conjugate(new Quaterniond()).transform(pushVec);
                    }

                    player.move(MoverType.SHULKER_BOX, new Vec3(pushVec.x, pushVec.y, pushVec.z));
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
                    if (player.onGround() && push.y < 0) {
                        push.y = 0;
                        if (push.lengthSquared() < 1e-6)
                            continue;
                        push.normalize();
                    }

                    Vector3d pushVec = new Vector3d(push).mul(overlap);
                    if (sub != null) {
                        sub.logicalPose().orientation().conjugate(new Quaterniond()).transform(pushVec);
                    }

                    player.move(MoverType.SHULKER_BOX, new Vec3(pushVec.x, pushVec.y, pushVec.z));

                    Vec3 velocity = player.getDeltaMovement();
                    Vector3d vel = new Vector3d(velocity.x, velocity.y, velocity.z);
                    double dot = vel.dot(push);
                    if (dot < 0) {
                        vel.sub(new Vector3d(push).mul(dot));
                    }
                    vel.add(new Vector3d(push).mul(overlap * 0.8));
                    player.setDeltaMovement(new Vec3(vel.x, vel.y, vel.z));
                    player.hasImpulse = true;

                    pPos.set(player.getX(), player.getY() + player.getBbHeight() / 2.0, player.getZ());
                    if (sub != null) {
                        sub.logicalPose().transformPosition(pPos);
                    }
                }
            }
        }
    }

    private static Vector3d getClosestPointOnSegment(Vector3d a, Vector3d b,
            Vector3d p) {
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
}
