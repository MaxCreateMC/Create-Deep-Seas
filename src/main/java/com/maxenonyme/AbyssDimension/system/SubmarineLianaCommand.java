package com.maxenonyme.AbyssDimension.system;

import com.maxenonyme.AbyssDimension.LianaRegistry;
import com.maxenonyme.AbyssDimension.block.entity.SubmarineLianaBlockEntity;
import com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.EnumSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.Random;
import java.util.Comparator;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class SubmarineLianaCommand {
    private static final int SEGMENT_SIZE = 3;

    private static final class PendingSpawn {
        final ServerLevel level;
        final ServerSubLevelContainer container;
        final BlockPos targetPos;
        final int length;

        PendingSpawn(ServerLevel level, ServerSubLevelContainer container, BlockPos targetPos, int length) {
            this.level = level;
            this.container = container;
            this.targetPos = targetPos;
            this.length = length;
        }
    }

    private static final Deque<PendingSpawn> pendingSpawns = new ArrayDeque<>();
    private static final List<ServerSubLevel> frozenSubLevels = new ArrayList<>();
    private static final Deque<ServerSubLevel> wakeUpQueue = new ArrayDeque<>();
    private static int spawnCooldown = 0;
    private static boolean batchActive = false;

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!wakeUpQueue.isEmpty()) {
            ServerSubLevel sub = wakeUpQueue.pollFirst();
            Object handle = SablePhysicsHelper.getHandle(sub);
            SablePhysicsHelper.setAsleep(handle, false);
            return;
        }

        if (pendingSpawns.isEmpty()) {
            if (batchActive) {
                batchActive = false;
                wakeUpQueue.addAll(frozenSubLevels);
                frozenSubLevels.clear();
            }
            return;
        }

        batchActive = true;
        if (spawnCooldown > 0) {
            spawnCooldown--;
            return;
        }
        PendingSpawn spawn = pendingSpawns.pollFirst();
        List<ServerSubLevel> spawned = spawnLianaChain(spawn.level, spawn.container, spawn.targetPos, spawn.length);
        if (batchActive && spawned != null) {
            for (ServerSubLevel sub : spawned) {
                Object handle = SablePhysicsHelper.getHandle(sub);
                SablePhysicsHelper.setAsleep(handle, true);
                frozenSubLevels.add(sub);
            }
        }
        spawnCooldown = 4;
    }

    private SubmarineLianaCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("submarine")
                        .then(Commands.literal("lian")
                                .then(Commands.argument("length", IntegerArgumentType.integer(1, 100))
                                        .executes(SubmarineLianaCommand::run)))
                        .then(Commands.literal("lian_radius")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 50))
                                        .then(Commands.argument("length", IntegerArgumentType.integer(1, 100))
                                                .executes(SubmarineLianaCommand::runRadius)))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("create_abyss.command.player_only"));
            return 0;
        }

        int length = IntegerArgumentType.getInteger(ctx, "length");
        ServerLevel level = source.getLevel();
        ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer.getContainer(level);
        if (container == null) {
            source.sendFailure(Component.translatable("create_abyss.command.no_sublevel_container"));
            return 0;
        }

        BlockPos playerPos = player.blockPosition();
        List<ServerSubLevel> spawned = spawnLianaChain(level, container, playerPos, length);
        if (spawned == null) {
            source.sendFailure(Component.translatable("create_abyss.command.spawn_liana_failed"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("create_abyss.command.spawn_liana_success", length), true);
        return 1;
    }

    private static int runRadius(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("create_abyss.command.player_only"));
            return 0;
        }

        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        int length = IntegerArgumentType.getInteger(ctx, "length");
        ServerLevel level = source.getLevel();
        ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer.getContainer(level);
        if (container == null) {
            source.sendFailure(Component.translatable("create_abyss.command.no_sublevel_container"));
            return 0;
        }

        BlockPos playerPos = player.blockPosition();
        Random random = new Random();
        List<BlockPos> targets = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    if (random.nextFloat() < 0.7f) {
                        BlockPos targetPos = playerPos.offset(dx, 0, dz);
                        targets.add(targetPos);
                    }
                }
            }
        }

        targets.sort((p1, p2) -> {
            double d1x = p1.getX() - playerPos.getX();
            double d1z = p1.getZ() - playerPos.getZ();
            double d2x = p2.getX() - playerPos.getX();
            double d2z = p2.getZ() - playerPos.getZ();
            return Double.compare(d1x * d1x + d1z * d1z, d2x * d2x + d2z * d2z);
        });

        for (BlockPos targetPos : targets) {
            pendingSpawns.add(new PendingSpawn(level, container, targetPos, length));
        }

        int count = targets.size();
        source.sendSuccess(() -> Component.translatable("create_abyss.command.queue_submarine_lianas", count, length), true);
        return 1;
    }

    private static void rollback(ServerLevel level,
            Map<BlockPos, BlockState> originalStates) {
        for (Map.Entry<BlockPos, BlockState> entry : originalStates
                .entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
    }

    private static void removeSubLevels(ServerSubLevelContainer container, List<ServerSubLevel> subLevels) {
        try {
            Class<?> reasonCls = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelRemovalReason");
            Method method = ServerSubLevelContainer.class.getMethod("removeSubLevel",
                    SubLevel.class, reasonCls);
            Object reason = reasonCls.getEnumConstants()[0];
            for (ServerSubLevel sub : subLevels) {
                try {
                    method.invoke(container, sub, reason);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static List<ServerSubLevel> spawnLianaChain(ServerLevel level, ServerSubLevelContainer container,
            BlockPos basePos, int length) {
        if (level.getBlockState(basePos.above(1)).is(LianaRegistry.LIANA_BLOCK.get())) {
            return null;
        }
        int numSegments = (length + SEGMENT_SIZE - 1) / SEGMENT_SIZE;
        ServerSubLevel[] segments = new ServerSubLevel[numSegments];
        UUID[] segmentIds = new UUID[numSegments];
        int[] seedPlotXForSeg = new int[numSegments];
        int[] seedPlotZForSeg = new int[numSegments];
        Vector3d[] seedLocalForSeg = new Vector3d[numSegments];
        Vector3d[] seedOffsetForSeg = new Vector3d[numSegments];
        Arrays.fill(seedPlotXForSeg, -1);
        Arrays.fill(seedPlotZForSeg, -1);
        double currentY = basePos.getY() + 1.5;

        Map<BlockPos, BlockState> originalStates = new HashMap<>();
        List<ServerSubLevel> assembledSubLevels = new ArrayList<>();

        Random random = new Random();
        List<Integer> seedIndices = new ArrayList<>();
        if (length > 1) {
            int maxFruits = Math.min(2, numSegments);
            int fruitsToSpawn = random.nextInt(maxFruits + 1);
            Set<Integer> usedSegments = new HashSet<>();
            while (seedIndices.size() < fruitsToSpawn) {
                int randIdx = random.nextInt(length - 1) + 2;
                if (usedSegments.add((randIdx - 1) / SEGMENT_SIZE)) {
                    seedIndices.add(randIdx);
                }
            }
        }

        List<Integer> directions = new ArrayList<>(List.of(0, 1, 2, 3));
        Collections.shuffle(directions, random);
        int spawnedFruitCount = 0;

        for (int i = 0; i < numSegments; i++) {
            int segmentStart = i * SEGMENT_SIZE;
            int segmentEnd = Math.min(length, (i + 1) * SEGMENT_SIZE);
            int segmentLen = segmentEnd - segmentStart;

            BlockPos plotAnchor = basePos.above(segmentStart + 1);
            List<BlockPos> segmentBlocks = new ArrayList<>();
            for (int j = 0; j < segmentLen; j++) {
                segmentBlocks.add(basePos.above(segmentStart + j + 1));
            }

            for (BlockPos bp : segmentBlocks) {
                originalStates.putIfAbsent(bp, level.getBlockState(bp));
                level.setBlock(bp, LianaRegistry.LIANA_BLOCK.get().defaultBlockState(), 3);
            }

            BoundingBox3i bounds = new BoundingBox3i(
                    basePos.getX(), basePos.getY() + segmentStart + 1, basePos.getZ(),
                    basePos.getX(), basePos.getY() + segmentEnd, basePos.getZ());

            ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, plotAnchor, segmentBlocks, bounds);
            if (subLevel == null) {
                removeSubLevels(container, assembledSubLevels);
                rollback(level, originalStates);
                return null;
            }
            assembledSubLevels.add(subLevel);

            Vector3d finalPos = new Vector3d(basePos.getX() + 0.5, currentY, basePos.getZ() + 0.5);
            subLevel.logicalPose().position().set(finalPos);
            subLevel.updateLastPose();
            container.physicsSystem().getPipeline().teleport(subLevel, finalPos, new Quaterniond());

            segments[i] = subLevel;
            segmentIds[i] = subLevel.getUniqueId();

            for (int j = 0; j < segmentLen; j++) {
                int blockIndex = segmentStart + j + 1;
                if (seedIndices.contains(blockIndex)) {
                    int dir = directions.get(spawnedFruitCount);
                    spawnedFruitCount++;

                    BlockPos seedPos;
                    Vector3d seedFinalPos;
                    Vector3d localAnchorCurrent;
                    Vector3d localAnchorOffset;

                    BlockPos segmentPlotAnchor = subLevel.getPlot().getCenterBlock();

                    if (dir == 0) {
                        seedPos = basePos.above(blockIndex).east(1);
                        seedFinalPos = new Vector3d(finalPos.x + 0.4, finalPos.y + j - 0.4, finalPos.z);
                        localAnchorCurrent = new Vector3d(segmentPlotAnchor.getX() + 0.9,
                                segmentPlotAnchor.getY() + j + 0.5, segmentPlotAnchor.getZ() + 0.5);
                        localAnchorOffset = new Vector3d(0.9, j + 0.5, 0.5);
                    } else if (dir == 1) {
                        seedPos = basePos.above(blockIndex).west(1);
                        seedFinalPos = new Vector3d(finalPos.x - 0.4, finalPos.y + j - 0.4, finalPos.z);
                        localAnchorCurrent = new Vector3d(segmentPlotAnchor.getX() + 0.1,
                                segmentPlotAnchor.getY() + j + 0.5, segmentPlotAnchor.getZ() + 0.5);
                        localAnchorOffset = new Vector3d(0.1, j + 0.5, 0.5);
                    } else if (dir == 2) {
                        seedPos = basePos.above(blockIndex).south(1);
                        seedFinalPos = new Vector3d(finalPos.x, finalPos.y + j - 0.4, finalPos.z + 0.4);
                        localAnchorCurrent = new Vector3d(segmentPlotAnchor.getX() + 0.5,
                                segmentPlotAnchor.getY() + j + 0.5, segmentPlotAnchor.getZ() + 0.9);
                        localAnchorOffset = new Vector3d(0.5, j + 0.5, 0.9);
                    } else {
                        seedPos = basePos.above(blockIndex).north(1);
                        seedFinalPos = new Vector3d(finalPos.x, finalPos.y + j - 0.4, finalPos.z - 0.4);
                        localAnchorCurrent = new Vector3d(segmentPlotAnchor.getX() + 0.5,
                                segmentPlotAnchor.getY() + j + 0.5, segmentPlotAnchor.getZ() + 0.1);
                        localAnchorOffset = new Vector3d(0.5, j + 0.5, 0.1);
                    }

                    originalStates.putIfAbsent(seedPos, level.getBlockState(seedPos));
                    level.setBlock(seedPos, LianaRegistry.CREEPVINE_SEED.get().defaultBlockState(), 3);
                    BoundingBox3i seedBounds = new BoundingBox3i(seedPos.getX(), seedPos.getY(), seedPos.getZ(),
                            seedPos.getX(), seedPos.getY(), seedPos.getZ());
                    ServerSubLevel seedSubLevel = SubLevelAssemblyHelper.assembleBlocks(level, seedPos,
                            List.of(seedPos), seedBounds);
                    if (seedSubLevel == null) {
                        removeSubLevels(container, assembledSubLevels);
                        rollback(level, originalStates);
                        return null;
                    }
                    assembledSubLevels.add(seedSubLevel);
                    seedPlotXForSeg[i] = seedSubLevel.getPlot().plotPos.x;
                    seedPlotZForSeg[i] = seedSubLevel.getPlot().plotPos.z;
                    seedLocalForSeg[i] = localAnchorOffset;
                    seedOffsetForSeg[i] = new Vector3d(seedFinalPos).sub(finalPos);

                    seedSubLevel.logicalPose().position().set(seedFinalPos);
                    seedSubLevel.updateLastPose();
                    container.physicsSystem().getPipeline().teleport(seedSubLevel, seedFinalPos, new Quaterniond());

                    BlockPos seedPlotAnchor = seedSubLevel.getPlot().getCenterBlock();
                    Vector3d localAnchorNext = new Vector3d(seedPlotAnchor.getX() + 0.5, seedPlotAnchor.getY() + 0.9,
                            seedPlotAnchor.getZ() + 0.5);

                    PhysicsConstraintHandle seedJoint = container.physicsSystem().getPipeline().addConstraint(
                            subLevel,
                            seedSubLevel,
                            new GenericConstraintConfiguration(
                                    localAnchorCurrent,
                                    localAnchorNext,
                                    new Quaterniond(),
                                    new Quaterniond(),
                                    EnumSet.of(ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.LINEAR_Y,
                                            ConstraintJointAxis.LINEAR_Z)));
                    if (seedJoint != null) {
                        seedJoint.setContactsEnabled(false);
                    }

                    BlockEntity rawBe = subLevel.getPlot()
                            .getEmbeddedLevelAccessor().getBlockEntity(segmentPlotAnchor);
                    if (rawBe instanceof SubmarineLianaBlockEntity lianaBe) {
                        lianaBe.setSeed(seedSubLevel.getUniqueId(), localAnchorOffset);
                        lianaBe.setSeedJoint(seedJoint);
                        lianaBe.setSeedPlot(seedSubLevel.getPlot().plotPos.x, seedSubLevel.getPlot().plotPos.z);
                    }
                }
            }

            currentY += segmentLen - 0.1;
        }

        for (int i = 0; i < numSegments; i++) {
            ServerSubLevel current = segments[i];
            int segmentLen = Math.min(length, (i + 1) * SEGMENT_SIZE) - (i * SEGMENT_SIZE);
            boolean isController = (i == 0);
            Vector3d ground = isController
                    ? new Vector3d(basePos.getX() + 0.5, basePos.getY() + 1.0, basePos.getZ() + 0.5)
                    : null;
            int parentPlotX = (i > 0) ? segments[i - 1].getPlot().plotPos.x : -1;
            int parentPlotZ = (i > 0) ? segments[i - 1].getPlot().plotPos.z : -1;
            int childPlotX = (i < numSegments - 1) ? segments[i + 1].getPlot().plotPos.x : -1;
            int childPlotZ = (i < numSegments - 1) ? segments[i + 1].getPlot().plotPos.z : -1;

            Vector3d seedLocal = seedLocalForSeg[i];
            Vector3d seedOffset = seedOffsetForSeg[i];
            PlantPhysicsRegistry.get(level).putSegment(new PlantPhysicsRegistry.SegmentData(
                    current.getPlot().plotPos.x,
                    current.getPlot().plotPos.z,
                    isController,
                    ground != null,
                    ground != null ? ground.x : 0.0,
                    ground != null ? ground.y : 0.0,
                    ground != null ? ground.z : 0.0,
                    parentPlotX, parentPlotZ,
                    childPlotX, childPlotZ,
                    seedPlotXForSeg[i], seedPlotZForSeg[i],
                    seedLocal != null ? seedLocal.x : 0.0,
                    seedLocal != null ? seedLocal.y : 0.0,
                    seedLocal != null ? seedLocal.z : 0.0,
                    seedOffset != null ? seedOffset.x : 0.0,
                    seedOffset != null ? seedOffset.y : 0.0,
                    seedOffset != null ? seedOffset.z : 0.0,
                    segmentLen));

            BlockPos plotAnchor = current.getPlot().getCenterBlock();
            BlockEntity rawBe = current.getPlot().getEmbeddedLevelAccessor()
                    .getBlockEntity(plotAnchor);
            if (rawBe instanceof SubmarineLianaBlockEntity be) {
                be.setController(isController);
                be.setSegmentLen(segmentLen);
                if (ground != null) {
                    be.setGroundAnchor(ground);
                }
                if (i > 0) {
                    be.setParent(segmentIds[i - 1]);
                    be.setParentPlot(parentPlotX, parentPlotZ);
                }
                if (i < numSegments - 1) {
                    be.setChildPlot(childPlotX, childPlotZ);
                }
                if (seedPlotXForSeg[i] != -1) {
                    be.setSeedPlot(seedPlotXForSeg[i], seedPlotZForSeg[i]);
                }
            }
        }

        if (numSegments > 0) {
            BlockPos plotAnchor0 = segments[0].getPlot().getCenterBlock();
            Vector3d localAnchor0 = new Vector3d(plotAnchor0.getX() + 0.5, plotAnchor0.getY(),
                    plotAnchor0.getZ() + 0.5);
            Vector3d worldAnchor = new Vector3d(basePos.getX() + 0.5, basePos.getY() + 1.0, basePos.getZ() + 0.5);
            PhysicsConstraintHandle groundJoint = container.physicsSystem().getPipeline().addConstraint(
                    segments[0],
                    null,
                    new GenericConstraintConfiguration(
                            localAnchor0,
                            worldAnchor,
                            new Quaterniond(),
                            new Quaterniond(),
                            EnumSet.of(ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.LINEAR_Y,
                                    ConstraintJointAxis.LINEAR_Z, ConstraintJointAxis.ANGULAR_Y)));
            if (groundJoint != null) {
                groundJoint.setContactsEnabled(false);
            }
            BlockEntity rawBe0 = segments[0].getPlot().getEmbeddedLevelAccessor()
                    .getBlockEntity(plotAnchor0);
            if (rawBe0 instanceof SubmarineLianaBlockEntity be0) {
                be0.setGroundJoint(groundJoint);
            }
        }

        for (int i = 0; i < numSegments - 1; i++) {
            ServerSubLevel current = segments[i];
            ServerSubLevel next = segments[i + 1];
            BlockPos currentPlotAnchor = current.getPlot().getCenterBlock();
            BlockPos nextPlotAnchor = next.getPlot().getCenterBlock();
            int currentLen = Math.min(length, (i + 1) * SEGMENT_SIZE) - (i * SEGMENT_SIZE);
            Vector3d localAnchorCurrent = new Vector3d(currentPlotAnchor.getX() + 0.5,
                    currentPlotAnchor.getY() + currentLen - 0.05, currentPlotAnchor.getZ() + 0.5);
            Vector3d localAnchorNext = new Vector3d(nextPlotAnchor.getX() + 0.5, nextPlotAnchor.getY() + 0.05,
                    nextPlotAnchor.getZ() + 0.5);
            PhysicsConstraintHandle joint = container.physicsSystem().getPipeline().addConstraint(
                    current,
                    next,
                    new GenericConstraintConfiguration(
                            localAnchorCurrent,
                            localAnchorNext,
                            new Quaterniond(),
                            new Quaterniond(),
                            EnumSet.of(ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.LINEAR_Y,
                                    ConstraintJointAxis.LINEAR_Z, ConstraintJointAxis.ANGULAR_Y)));
            if (joint != null) {
                joint.setContactsEnabled(false);
            }
            BlockEntity rawBeNext = next.getPlot().getEmbeddedLevelAccessor()
                    .getBlockEntity(nextPlotAnchor);
            if (rawBeNext instanceof SubmarineLianaBlockEntity beNext) {
                beNext.setParentJoint(joint);
            }
        }

        return List.of(segments);
    }
}
