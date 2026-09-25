package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.mojang.brigadier.context.CommandContext;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import net.minecraft.world.level.EmptyBlockGetter;

public final class SubmarineInfoCommand {
    private SubmarineInfoCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("submarine")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("info").executes(SubmarineInfoCommand::run))
                        .then(Commands.literal("findhole").executes(SubmarineInfoCommand::findHoles)));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Player only."));
            return 0;
        }
        Level level = player.level();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            source.sendFailure(Component.literal("No sublevels in this dimension."));
            return 0;
        }

        Vector3d ppos = new Vector3d(player.getX(), player.getY(), player.getZ());
        SubLevel found = null;
        for (SubLevel sub : container.getAllSubLevels()) {
            if (sub.getPlot() == null) continue;
            BoundingBox3ic b = sub.getPlot().getBoundingBox();
            if (b == null) continue;
            Vector3d local = new Vector3d(ppos);
            sub.logicalPose().transformPositionInverse(local);
            if (local.x >= b.minX() - 2 && local.x <= b.maxX() + 2
                    && local.y >= b.minY() - 2 && local.y <= b.maxY() + 2
                    && local.z >= b.minZ() - 2 && local.z <= b.maxZ() + 2) {
                found = sub;
                break;
            }
        }

        if (found == null) {
            source.sendSuccess(() -> Component.literal("You are not in or on a sublevel.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        UUID id = found.getUniqueId();
        BoundingBox3ic b = found.getPlot().getBoundingBox();
        Level subLevel = found.getLevel();

        int controllers = 0, diffusers = 0, floaters = 0;
        long volume = (long) (b.maxX() - b.minX() + 1) * (b.maxY() - b.minY() + 1) * (b.maxZ() - b.minZ() + 1);
        boolean scanned = subLevel != null && volume <= 50_000;
        if (scanned) {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int y = b.minY(); y <= b.maxY(); y++) {
                    for (int z = b.minZ(); z <= b.maxZ(); z++) {
                        m.set(x, y, z);
                        BlockState s = subLevel.getBlockState(m);
                        if (s.is(CreateSubmarine.CREATIVE_OXYGENATOR.get())) controllers++;
                        else if (s.is(CreateSubmarine.OXYGENE_DIFFUSER.get())) diffusers++;
                        else if (s.is(CreateSubmarine.FLOATER.get())) floaters++;
                    }
                }
            }
        }

        boolean hermetic = CompartmentTracker.hasAnySealed(id);
        boolean breached = SubmarinePressureSystem.isBreached(id);
        int cracks = SubmarinePressureSystem.getCrackCount(id);
        int depth = SubmarinePressureSystem.getCachedDepth(id);
        boolean underPressure = hermetic && depth > 0;
        int hullLimit = subLevel != null ? SubmarinePressureSystem.getWeakestHullDepth(id, subLevel) : -1;

        final int fc = controllers, fd = diffusers, ff = floaters;
        final boolean fScanned = scanned;
        source.sendSuccess(() -> Component.literal("=== Submarine Info ===").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> line("Sublevel", id.toString().substring(0, 8)), false);
        if (fScanned) {
            source.sendSuccess(() -> line("Hull controllers", String.valueOf(fc)), false);
            source.sendSuccess(() -> line("Oxygen diffusers", String.valueOf(fd)), false);
            source.sendSuccess(() -> line("Floaters", String.valueOf(ff)), false);
        } else {
            source.sendSuccess(() -> Component.literal("(too large to scan modules)").withStyle(ChatFormatting.DARK_GRAY), false);
        }
        source.sendSuccess(() -> bool("Hermetic (sealed)", hermetic), false);
        source.sendSuccess(() -> bool("Breached", breached), false);
        source.sendSuccess(() -> line("Cracked blocks", String.valueOf(cracks)), false);
        source.sendSuccess(() -> line("Water depth", String.valueOf(depth)), false);
        source.sendSuccess(() -> bool("Under pressure", underPressure), false);
        if (hullLimit > 0 && hullLimit < Integer.MAX_VALUE)
            source.sendSuccess(() -> line("Hull depth limit", String.valueOf(hullLimit)), false);
        return 1;
    }

    private static int findHoles(CommandContext<CommandSourceStack> ctx) {
        return findHoles(ctx.getSource());
    }

    public static int findHoles(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Player only."));
            return 0;
        }
        Level level = player.level();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            source.sendFailure(Component.literal("No sublevels in this dimension."));
            return 0;
        }

        Vector3d ppos = new Vector3d(player.getX(), player.getY(), player.getZ());
        SubLevel found = null;
        for (SubLevel sub : container.getAllSubLevels()) {
            if (sub.getPlot() == null) continue;
            BoundingBox3ic b = sub.getPlot().getBoundingBox();
            if (b == null) continue;
            Vector3d local = new Vector3d(ppos);
            sub.logicalPose().transformPositionInverse(local);
            if (local.x >= b.minX() - 2 && local.x <= b.maxX() + 2
                    && local.y >= b.minY() - 2 && local.y <= b.maxY() + 2
                    && local.z >= b.minZ() - 2 && local.z <= b.maxZ() + 2) {
                found = sub;
                break;
            }
        }

        if (found == null) {
            source.sendSuccess(() -> Component.translatable("create_submarine.command.findhole.not_in_submarine").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        BoundingBox3ic b = found.getPlot().getBoundingBox();
        Level subLevel = found.getLevel();
        if (subLevel == null) {
            source.sendFailure(Component.translatable("create_submarine.command.findhole.world_not_loaded"));
            return 0;
        }

        Vector3d localPlayerVec = new Vector3d(ppos);
        found.logicalPose().transformPositionInverse(localPlayerVec);
        BlockPos playerLocalPos = BlockPos.containing(localPlayerVec.x, localPlayerVec.y, localPlayerVec.z);

        int minX = b.minX(), maxX = b.maxX();
        int minY = b.minY(), maxY = b.maxY();
        int minZ = b.minZ(), maxZ = b.maxZ();

        if (playerLocalPos.getX() < minX || playerLocalPos.getX() > maxX
                || playerLocalPos.getY() < minY || playerLocalPos.getY() > maxY
                || playerLocalPos.getZ() < minZ || playerLocalPos.getZ() > maxZ) {
            source.sendFailure(Component.translatable("create_submarine.command.findhole.must_be_inside"));
            return 0;
        }

        int dx = maxX - minX + 1;
        int dy = maxY - minY + 1;
        int dz = maxZ - minZ + 1;
        int totalVolume = dx * dy * dz;

        if (totalVolume <= 0 || totalVolume > 500000) {
            source.sendFailure(Component.translatable("create_submarine.command.findhole.too_large"));
            return 0;
        }

        boolean[] visited = new boolean[totalVolume];
        int[] bfsQueue = new int[totalVolume];
        int qHead = 0, qTail = 0;

        int playerIdx = (playerLocalPos.getX() - minX) * (dy * dz) + (playerLocalPos.getY() - minY) * dz + (playerLocalPos.getZ() - minZ);
        bfsQueue[qTail++] = playerIdx;
        visited[playerIdx] = true;

        int[] reachableList = new int[totalVolume];
        int reachableCount = 0;

        boolean reachedBoundary = false;

        while (qHead < qTail) {
            int currIdx = bfsQueue[qHead++];
            reachableList[reachableCount++] = currIdx;

            int rem = currIdx;
            int cx = minX + rem / (dy * dz);
            rem %= (dy * dz);
            int cy = minY + rem / dz;
            int cz = minZ + rem % dz;

            if (cx == minX || cx == maxX || cy == minY || cy == maxY || cz == minZ || cz == maxZ) {
                reachedBoundary = true;
            }

            for (Direction dir : Direction.values()) {
                int nx = cx + dir.getStepX();
                int ny = cy + dir.getStepY();
                int nz = cz + dir.getStepZ();

                if (nx < minX || nx > maxX || ny < minY || ny > maxY || nz < minZ || nz > maxZ) {
                    reachedBoundary = true;
                    continue;
                }

                int nIdx = (nx - minX) * (dy * dz) + (ny - minY) * dz + (nz - minZ);
                if (!visited[nIdx]) {
                    BlockState ns = subLevel.getBlockState(new BlockPos(nx, ny, nz));
                    if (isBlockPermeable(ns)) {
                        visited[nIdx] = true;
                        bfsQueue[qTail++] = nIdx;
                    }
                }
            }
        }

        if (!reachedBoundary) {
            source.sendSuccess(() -> Component.translatable("create_submarine.command.findhole.sealed").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }

        int[] globalToCompressed = new int[totalVolume];
        Arrays.fill(globalToCompressed, -1);
        for (int i = 0; i < reachableCount; i++) {
            globalToCompressed[reachableList[i]] = i;
        }

        boolean[] isBoundary = new boolean[reachableCount];
        for (int i = 0; i < reachableCount; i++) {
            int idx = reachableList[i];
            int rem = idx;
            int cx = minX + rem / (dy * dz);
            rem %= (dy * dz);
            int cy = minY + rem / dz;
            int cz = minZ + rem % dz;
            if (cx == minX || cx == maxX || cy == minY || cy == maxY || cz == minZ || cz == maxZ) {
                isBoundary[i] = true;
            }
        }

        int vertexCount = 2 * reachableCount + 1;
        int maxEdges = 16 * reachableCount + 200;
        int[] head = new int[vertexCount + 1];
        Arrays.fill(head, -1);
        int[] to = new int[maxEdges];
        int[] next = new int[maxEdges];
        int[] cap = new int[maxEdges];
        int[] flow = new int[maxEdges];

        int edgeCount = 0;
        int T = 2 * reachableCount;
        int S = 2 * globalToCompressed[playerIdx] + 1;

        for (int i = 0; i < reachableCount; i++) {
            int uIn = 2 * i;
            int uOut = 2 * i + 1;
            int c = (i == globalToCompressed[playerIdx] || isBoundary[i]) ? 1000000 : 1;

            to[edgeCount] = uOut;
            cap[edgeCount] = c;
            flow[edgeCount] = 0;
            next[edgeCount] = head[uIn];
            head[uIn] = edgeCount++;

            to[edgeCount] = uIn;
            cap[edgeCount] = 0;
            flow[edgeCount] = 0;
            next[edgeCount] = head[uOut];
            head[uOut] = edgeCount++;

            if (isBoundary[i]) {
                to[edgeCount] = T;
                cap[edgeCount] = 1000000;
                flow[edgeCount] = 0;
                next[edgeCount] = head[uOut];
                head[uOut] = edgeCount++;

                to[edgeCount] = uOut;
                cap[edgeCount] = 0;
                flow[edgeCount] = 0;
                next[edgeCount] = head[T];
                head[T] = edgeCount++;
            }
        }

        for (int i = 0; i < reachableCount; i++) {
            int uOut = 2 * i + 1;
            int idx = reachableList[i];
            int rem = idx;
            int cx = minX + rem / (dy * dz);
            rem %= (dy * dz);
            int cy = minY + rem / dz;
            int cz = minZ + rem % dz;

            for (Direction dir : Direction.values()) {
                int nx = cx + dir.getStepX();
                int ny = cy + dir.getStepY();
                int nz = cz + dir.getStepZ();

                if (nx >= minX && nx <= maxX && ny >= minY && ny <= maxY && nz >= minZ && nz <= maxZ) {
                    int nIdx = (nx - minX) * (dy * dz) + (ny - minY) * dz + (nz - minZ);
                    int j = globalToCompressed[nIdx];
                    if (j != -1) {
                        int vIn = 2 * j;

                        to[edgeCount] = vIn;
                        cap[edgeCount] = 1000000;
                        flow[edgeCount] = 0;
                        next[edgeCount] = head[uOut];
                        head[uOut] = edgeCount++;

                        to[edgeCount] = uOut;
                        cap[edgeCount] = 0;
                        flow[edgeCount] = 0;
                        next[edgeCount] = head[vIn];
                        head[vIn] = edgeCount++;
                    }
                }
            }
        }

        int maxFlow = 0;
        int[] parentEdge = new int[vertexCount + 1];
        int[] flowQueue = new int[vertexCount + 1];

        while (true) {
            Arrays.fill(parentEdge, -1);
            int fHead = 0, fTail = 0;
            flowQueue[fTail++] = S;
            parentEdge[S] = -2;

            while (fHead < fTail) {
                int curr = flowQueue[fHead++];
                for (int e = head[curr]; e != -1; e = next[e]) {
                    int neighbor = to[e];
                    if (parentEdge[neighbor] == -1 && (cap[e] - flow[e] > 0)) {
                        parentEdge[neighbor] = e;
                        flowQueue[fTail++] = neighbor;
                    }
                }
            }

            if (parentEdge[T] == -1) {
                break;
            }

            int pathFlow = Integer.MAX_VALUE;
            for (int curr = T; curr != S; ) {
                int e = parentEdge[curr];
                pathFlow = Math.min(pathFlow, cap[e] - flow[e]);
                curr = to[e ^ 1];
            }

            for (int curr = T; curr != S; ) {
                int e = parentEdge[curr];
                flow[e] += pathFlow;
                flow[e ^ 1] -= pathFlow;
                curr = to[e ^ 1];
            }

            maxFlow += pathFlow;
            if (maxFlow > 50) {
                break;
            }
        }

        if (maxFlow >= 500000) {
            source.sendFailure(Component.translatable("create_submarine.command.findhole.outside"));
            return 0;
        }

        boolean[] residualReachable = new boolean[vertexCount + 1];
        int[] residualQueue = new int[vertexCount + 1];
        int rHead = 0, rTail = 0;

        residualQueue[rTail++] = S;
        residualReachable[S] = true;

        while (rHead < rTail) {
            int curr = residualQueue[rHead++];
            for (int e = head[curr]; e != -1; e = next[e]) {
                int neighbor = to[e];
                if (!residualReachable[neighbor] && (cap[e] - flow[e] > 0)) {
                    residualReachable[neighbor] = true;
                    residualQueue[rTail++] = neighbor;
                }
            }
        }

        List<BlockPos> cutBlocks = new ArrayList<>();
        for (int i = 0; i < reachableCount; i++) {
            if (residualReachable[2 * i] && !residualReachable[2 * i + 1]) {
                int idx = reachableList[i];
                int rem = idx;
                int cx = minX + rem / (dy * dz);
                rem %= (dy * dz);
                int cy = minY + rem / dz;
                int cz = minZ + rem % dz;
                cutBlocks.add(new BlockPos(cx, cy, cz));
            }
        }

        if (cutBlocks.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("create_submarine.command.findhole.sealed").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }

        int cutCount = cutBlocks.size();
        source.sendSuccess(() -> Component.translatable("create_submarine.command.findhole.detected_count", cutCount).withStyle(ChatFormatting.RED), false);
        int displayLimit = Math.min(cutCount, 10);
        for (int i = 0; i < displayLimit; i++) {
            BlockPos localPos = cutBlocks.get(i);
            Vector3d worldVec = new Vector3d(localPos.getX() + 0.5, localPos.getY() + 0.5, localPos.getZ() + 0.5);
            found.logicalPose().transformPosition(worldVec);
            BlockPos worldPos = BlockPos.containing(worldVec.x, worldVec.y, worldVec.z);
            source.sendSuccess(() -> Component.translatable("create_submarine.command.findhole.entry",
                    localPos.getX(), localPos.getY(), localPos.getZ(),
                    worldPos.getX(), worldPos.getY(), worldPos.getZ()).withStyle(ChatFormatting.YELLOW), false);
        }
        if (cutCount > 10) {
            int remaining = cutCount - 10;
            source.sendSuccess(() -> Component.translatable("create_submarine.command.findhole.more", remaining).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static boolean isBlockPermeable(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        return state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    }

    private static Component line(String label, String value) {
        return Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    private static Component bool(String label, boolean value) {
        return Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value ? "yes" : "no").withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED));
    }
}
