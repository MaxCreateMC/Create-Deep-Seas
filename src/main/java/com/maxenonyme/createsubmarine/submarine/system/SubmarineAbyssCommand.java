package com.maxenonyme.createsubmarine.submarine.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.maxenonyme.createsubmarine.worldgen.PlateTectonicsConfig;
import com.maxenonyme.createsubmarine.worldgen.SeafloorGenerator;
import com.maxenonyme.createsubmarine.worldgen.TectonicPlate;

public final class SubmarineAbyssCommand {
    private SubmarineAbyssCommand() {}

    private static final ResourceKey<Level> ABYSS_KEY = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("create_submarine", "abyss"));

    private static final SuggestionProvider<CommandSourceStack> PLATE_SUGGESTER =
        (ctx, builder) -> suggestPlateNames(builder);

    public static void register(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();

        var listCmd = Commands.literal("list")
                .executes(SubmarineAbyssCommand::plateList);

        var locateCmd = Commands.literal("locate")
                .then(Commands.argument("platename", StringArgumentType.greedyString())
                        .suggests(PLATE_SUGGESTER)
                        .executes(SubmarineAbyssCommand::plateLocate));

        var infoCmd = Commands.literal("info")
                .then(Commands.argument("platename", StringArgumentType.greedyString())
                        .suggests(PLATE_SUGGESTER)
                        .executes(SubmarineAbyssCommand::plateInfo));

        var boundaryCmd = Commands.literal("boundary")
                .then(Commands.argument("platename", StringArgumentType.greedyString())
                        .suggests(PLATE_SUGGESTER)
                        .executes(SubmarineAbyssCommand::plateBoundary));

        var plateCmd = Commands.literal("plate")
                .then(listCmd)
                .then(locateCmd)
                .then(infoCmd)
                .then(boundaryCmd);

        var abyssCmd = Commands.literal("abyss")
                .executes(SubmarineAbyssCommand::run)
                .then(Commands.literal("regenerate")
                        .executes(SubmarineAbyssCommand::regenerate));

        dispatcher.register(
            Commands.literal("submarine")
                    .requires(source -> source.hasPermission(2))
                    .then(abyssCmd)
                    .then(plateCmd));
    }

    private static ServerLevel getAbyss(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (server == null) return null;
        return server.getLevel(ABYSS_KEY);
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Player only."));
            return 0;
        }

        ServerLevel abyss = getAbyss(source);
        if (abyss == null) {
            source.sendFailure(Component.literal("Abyss dimension not found."));
            return 0;
        }

        int range = 5000;
        int x = player.getRandom().nextInt(range * 2) - range;
        int z = player.getRandom().nextInt(range * 2) - range;

        int seaFloorY = SeafloorGenerator.getHeightAt(x, z);
        if (seaFloorY < abyss.getMinBuildHeight() || seaFloorY > abyss.getMaxBuildHeight() - 1) {
            source.sendFailure(Component.literal(
                    "Could not find sea floor at this location. Try again or run '/submarine abyss regenerate' first."));
            return 0;
        }

        int fx = x;
        int fz = z;
        int fy = seaFloorY + 1;
        player.teleportTo(abyss, fx + 0.5, fy, fz + 0.5, player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal(
                "Teleported to Abyss sea floor at " + fx + " " + fy + " " + fz), true);
        return 1;
    }

    private static int regenerate(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel abyss = getAbyss(source);
        if (abyss == null) {
            source.sendFailure(Component.literal("Abyss dimension not found."));
            return 0;
        }

        MinecraftServer server = source.getServer();

        // Teleport any players out of the abyss first
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            for (ServerPlayer p : abyss.players()) {
                p.teleportTo(overworld, p.getX(), 64, p.getZ(), p.getYRot(), p.getXRot());
            }
        }

        // Delete region, poi, and entity files on disk
        try {
            Path worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            Path dimPath = worldDir.resolve("dimensions").resolve("create_submarine").resolve("abyss");
            deleteMcaFiles(dimPath.resolve("region"));
            deleteMcaFiles(dimPath.resolve("poi"));
            deleteMcaFiles(dimPath.resolve("entity"));
        } catch (Exception e) {
            source.sendFailure(Component.literal(
                    "Failed to delete dimension files: " + e.getMessage()));
            return 0;
        }

        SeafloorGenerator.clearCache();
        source.sendSuccess(() -> Component.literal(
                "Abyss fully regenerated. Chunk data deleted and cache cleared."), true);
        return 1;
    }

    private static int plateInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String targetName = StringArgumentType.getString(ctx, "platename");

        TectonicPlate target = null;
        for (TectonicPlate p : SeafloorGenerator.getConfig().plates()) {
            if (p.name().equalsIgnoreCase(targetName)) {
                target = p;
                break;
            }
        }
        if (target == null) {
            source.sendFailure(Component.literal("No plate named \"" + targetName + "\"."));
            return 0;
        }

        TectonicPlate ftarget = target;
        double speed = Math.sqrt(ftarget.vx() * ftarget.vx() + ftarget.vz() * ftarget.vz());
        String type = ftarget.oceanic() ? "Oceanic" : "Continental";
        String msg = "§6=== " + ftarget.name() + " Plate ===\n" +
            "§eType: §f" + type + "\n" +
            "§eUUID: §f" + ftarget.uuid() + "\n" +
            "§eVelocity: §f(" + String.format("%.2f", ftarget.vx()) + ", " +
                String.format("%.2f", ftarget.vz()) + ") " +
                String.format("%.2f", speed) + " m/yr\n" +
            "§eMass: §f" + String.format("%.2f", ftarget.mass()) + "\n" +
            "§eWeight: §f" + String.format("%.1f", ftarget.weight()) + "\n" +
            "§eEstimated cell count: §f~" +
                (int)(ftarget.weight() / totalWeight(SeafloorGenerator.getConfig()) * 100) + "% of map";
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int plateBoundary(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String targetName = StringArgumentType.getString(ctx, "platename");

        TectonicPlate target = null;
        for (TectonicPlate p : SeafloorGenerator.getConfig().plates()) {
            if (p.name().equalsIgnoreCase(targetName)) {
                target = p;
                break;
            }
        }
        if (target == null) {
            source.sendFailure(Component.literal("No plate named \"" + targetName + "\"."));
            return 0;
        }

        // Scan Voronoi cells in a radius to find adjacent plates
        // A plate is "adjacent" if cells of the target plate neighbor cells of another plate
        var neighbors = new java.util.LinkedHashMap<String, java.util.List<int[]>>();
        int scanRadius = 3;

        for (int dx = -scanRadius; dx <= scanRadius; dx++) {
            for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                TectonicPlate here = SeafloorGenerator.getPlateAt(
                    dx * SeafloorGenerator.PLATE_CELL_SIZE,
                    dz * SeafloorGenerator.PLATE_CELL_SIZE);
                if (here == null || !here.name().equalsIgnoreCase(targetName)) continue;

                // Check all 8 neighbors of this cell
                for (int ndx = -1; ndx <= 1; ndx++) {
                    for (int ndz = -1; ndz <= 1; ndz++) {
                        if (ndx == 0 && ndz == 0) continue;
                        TectonicPlate neighbor = SeafloorGenerator.getPlateAt(
                            (dx + ndx) * SeafloorGenerator.PLATE_CELL_SIZE,
                            (dz + ndz) * SeafloorGenerator.PLATE_CELL_SIZE);
                        if (neighbor == null || neighbor.name().equalsIgnoreCase(targetName)) continue;

                        // Record a sample boundary point
                        int bx = dx * SeafloorGenerator.PLATE_CELL_SIZE + ndx * SeafloorGenerator.PLATE_CELL_SIZE / 2;
                        int bz = dz * SeafloorGenerator.PLATE_CELL_SIZE + ndz * SeafloorGenerator.PLATE_CELL_SIZE / 2;
                        neighbors.computeIfAbsent(neighbor.name(), k -> new java.util.ArrayList<>())
                            .add(new int[]{bx, bz});
                    }
                }
            }
        }

        if (neighbors.isEmpty()) {
            source.sendFailure(Component.literal("No neighboring plates found for \"" + targetName + "\"."));
            return 0;
        }

        net.minecraft.network.chat.MutableComponent msg = Component.literal("§6=== " + target.name() + " Plate Boundaries ===\n");
        for (var entry : neighbors.entrySet()) {
            String nName = entry.getKey();
            var points = entry.getValue();
            int sampleCount = Math.min(points.size(), 3);
            msg.append(Component.literal("§e" + nName + " §7(" + points.size() + " cells adjacent)\n"));
            for (int i = 0; i < sampleCount; i++) {
                int[] p = points.get(i);
                msg.append(Component.literal("  §7Boundary near: §f" + p[0] + ", " + p[1] + "\n"));
            }
            if (points.size() > sampleCount) {
                msg.append(Component.literal("  §8... and " + (points.size() - sampleCount) + " more\n"));
            }
        }

        source.sendSuccess(() -> msg, false);
        return 1;
    }

    private static double totalWeight(PlateTectonicsConfig config) {
        double sum = 0;
        for (TectonicPlate p : config.plates()) sum += p.weight();
        return sum;
    }

    private static CompletableFuture<Suggestions> suggestPlateNames(SuggestionsBuilder builder) {
        String[] names = SeafloorGenerator.getAllPlateNames();
        if (names != null) {
            String prefix = builder.getRemaining().toLowerCase();
            for (String name : names) {
                if (name.toLowerCase().startsWith(prefix)) {
                    builder.suggest(name);
                }
            }
        }
        return builder.buildFuture();
    }

    private static void deleteMcaFiles(Path dir) throws IOException {
        if (Files.notExists(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".mca"))
                 .forEach(p -> {
                     try { Files.delete(p); } catch (IOException e) {}
                 });
        }
    }

    private static int plateList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String[] names = SeafloorGenerator.getAllPlateNames();
        var list = Component.literal("Tectonic plates (" + names.length + "):");
        for (String n : names) {
            list.append("\n  - ").append(n);
        }
        source.sendSuccess(() -> list, false);
        return 1;
    }

    private static int plateLocate(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Player only."));
            return 0;
        }

        String targetName = StringArgumentType.getString(ctx, "platename");

        // Find the plate by name
        TectonicPlate target = null;
        for (TectonicPlate p : SeafloorGenerator.getConfig().plates()) {
            if (p.name().equalsIgnoreCase(targetName)) {
                target = p;
                break;
            }
        }
        if (target == null) {
            source.sendFailure(Component.literal("No plate named \"" + targetName + "\"."));
            return 0;
        }

        int px = player.blockPosition().getX();
        int pz = player.blockPosition().getZ();
        int cellX = Math.floorDiv(px, SeafloorGenerator.PLATE_CELL_SIZE);
        int cellZ = Math.floorDiv(pz, SeafloorGenerator.PLATE_CELL_SIZE);

        // Spiral search outward for a cell belonging to this plate
        int searchRadius = 20; // max 20 cells in each direction
        int bestX = Integer.MAX_VALUE;
        int bestZ = Integer.MAX_VALUE;
        double bestDist2 = Double.MAX_VALUE;

        for (int r = 0; r <= searchRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                int dz = r - Math.abs(dx);
                if (dz < 0) continue;
                // Check both ±dz
                for (int sign : new int[]{1, -1}) {
                    int cz = cellZ + dz * sign;
                    // But only do the full horizontal line when dz == 0
                    int cx = cellX + dx;
                    if (cx == cellX && cz == cellZ && r > 0) continue; // already checked

                    TectonicPlate here = SeafloorGenerator.getPlateAt(
                        cx * SeafloorGenerator.PLATE_CELL_SIZE,
                        cz * SeafloorGenerator.PLATE_CELL_SIZE);
                    if (here.name().equalsIgnoreCase(targetName)) {
                        double d2 = (cx - cellX) * (cx - cellX) + (cz - cellZ) * (cz - cellZ);
                        if (d2 < bestDist2) {
                            bestDist2 = d2;
                            bestX = cx;
                            bestZ = cz;
                        }
                    }
                }
            }
            if (bestDist2 < Double.MAX_VALUE) break; // found at this radius
        }

        if (bestX == Integer.MAX_VALUE) {
            source.sendFailure(Component.literal("Could not find plate \"" + targetName + "\" nearby."));
            return 0;
        }

        int tx = SeafloorGenerator.getPlateCellCenterX(bestX, bestZ);
        int tz = SeafloorGenerator.getPlateCellCenterZ(bestX, bestZ);
        int ty = SeafloorGenerator.getHeightAt(tx, tz) + 1;

        ServerLevel abyss = source.getServer().getLevel(ABYSS_KEY);
        if (abyss != null) {
            player.teleportTo(abyss, tx + 0.5, ty, tz + 0.5, player.getYRot(), player.getXRot());
            source.sendSuccess(() -> Component.literal(
                "Teleported to plate \"" + targetName + "\" at " + tx + " " + ty + " " + tz), true);
        } else {
            source.sendFailure(Component.literal("Abyss dimension not found."));
            return 0;
        }
        return 1;
    }
}
