package com.maxenonyme.createsubmarine.submarine.config;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class HullStrengthConfig {
    public record HullProperty(int maxWaterDepth, float implosionChance) {
    }

    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("submarine_hull.json");
    private static final Path LEGACY_DUMP_PATH = FMLPaths.CONFIGDIR.get().resolve("submarine_hull_generated.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int VERSION = 3;
    private static final int DEFAULT_CAP = 400;
    private static final int LEGACY_CAP = 300;
    private static final Map<Block, HullProperty> resolvedCache = new ConcurrentHashMap<>();
    private static volatile Map<String, HullProperty> values = new ConcurrentHashMap<>();
    private static boolean configParseFailed = false;
    private static boolean migrated = false;

    public static void load() {
        Map<String, HullProperty> loaded = new ConcurrentHashMap<>();
        resolvedCache.clear();
        configParseFailed = false;
        migrated = false;

        Map<String, HullProperty> existing = readConfigFile();
        Map<String, HullProperty> staticDefaults = new HashMap<>();
        buildStaticDefaults(staticDefaults);

        Map<String, HullProperty> complete = new TreeMap<>();
        boolean fileMissing = !Files.exists(CONFIG_PATH);
        boolean anyNewBlock = false;

        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            if (state.isAir())
                continue;
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            String key = id.toString();

            HullProperty fresh = staticDefaults.getOrDefault(key, autoCompute(state, id));
            HullProperty prop = existing.get(key);
            if (prop == null) {
                prop = fresh;
                anyNewBlock = true;
            } else if (migrated && prop.equals(legacyDefault(key, state, id))) {
                prop = fresh;
            }
            complete.put(key, prop);
            loaded.put(key, prop);
            resolvedCache.put(block, prop);
        }
        values = loaded;

        boolean shouldWrite = fileMissing || migrated || (anyNewBlock && !configParseFailed);
        if (shouldWrite)
            writeJson(CONFIG_PATH, complete);
        try {
            Files.deleteIfExists(LEGACY_DUMP_PATH);
        } catch (IOException e) {
            CreateSubmarine.LOGGER.warn("Couldn't delete the old {}", LEGACY_DUMP_PATH, e);
        }
    }

    public static Optional<HullProperty> getFor(BlockState state) {
        Block block = state.getBlock();
        HullProperty base = resolvedCache.get(block);
        if (base == null) {
            if (state.isAir())
                return Optional.empty();
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            base = values.getOrDefault(id.toString(), autoCompute(state, id));
            resolvedCache.put(block, base);
        }
        return Optional.of(applyRuntimeMultipliers(base));
    }

    private static HullProperty applyRuntimeMultipliers(HullProperty base) {
        if (!SubmarineConfig.SERVER_SPEC.isLoaded())
            return base;
        double depthMult = SubmarineConfig.MAX_DEPTH_MULTIPLIER.get();
        double chanceMult = SubmarineConfig.IMPLOSION_CHANCE_MULTIPLIER.get();
        int depth = Math.max(1, (int) Math.round(base.maxWaterDepth() * depthMult));
        float chance = (float) Math.max(0.0, Math.min(1.0, base.implosionChance() * chanceMult));
        if (depth == base.maxWaterDepth() && chance == base.implosionChance())
            return base;
        return new HullProperty(depth, chance);
    }

    private static Map<String, HullProperty> readConfigFile() {
        Map<String, HullProperty> map = new LinkedHashMap<>();
        if (!Files.exists(CONFIG_PATH))
            return map;

        String json;
        try {
            json = Files.readString(CONFIG_PATH);
        } catch (IOException e) {
            configParseFailed = true;
            return map;
        }

        JsonObject root;
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setLenient(true);
            root = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            backupBadFile();
            configParseFailed = true;
            return map;
        }

        int version = 1;
        if (root.has("_version")) {
            version = root.get("_version").getAsInt();
        }
        if (version < 2) {
            backupBadFile();
            return map;
        }
        if (version < VERSION) {
            backupBadFile();
            migrated = true;
        }

        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (entry.getKey().startsWith("_"))
                continue;
            JsonElement value = entry.getValue();
            if (!value.isJsonObject())
                continue;
            JsonObject props = value.getAsJsonObject();
            int depth = 0;
            if (props.has("maxWaterDepth")) {
                depth = props.get("maxWaterDepth").getAsInt();
            } else if (props.has("maxDepthY")) {
                depth = convertOldDepthY(props.get("maxDepthY").getAsInt());
            }
            float chance = props.has("implosionChance") ? props.get("implosionChance").getAsFloat() : 0.5f;
            map.put(entry.getKey(), new HullProperty(depth, clamp(chance)));
        }
        return map;
    }

    private static void backupBadFile() {
        try {
            Path backup = CONFIG_PATH
                    .resolveSibling(CONFIG_PATH.getFileName() + ".bak." + Instant.now().getEpochSecond());
            Files.copy(CONFIG_PATH, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            CreateSubmarine.LOGGER.warn("Couldn't back up the broken {}", CONFIG_PATH, e);
        }
    }

    private static int convertOldDepthY(int oldMaxDepthY) {
        return Math.max(10, 64 - oldMaxDepthY);
    }

    private static HullProperty autoCompute(BlockState state, ResourceLocation id) {
        float hardness = 2.0f;
        float resistance = 1.0f;
        SoundType sound = SoundType.STONE;
        try {
            hardness = state.getDestroySpeed(null, null);
            resistance = state.getBlock().getExplosionResistance();
            sound = state.getSoundType();
        } catch (RuntimeException e) {
        }
        double strength = Math.max(0.0, hardness) + Math.log1p(Math.max(0.0, resistance)) * 2.0;

        double depth;
        if (sound == SoundType.GLASS)
            depth = Math.min(30.0, 18.0 + strength * 12.0);
        else if (sound == SoundType.METAL || sound == SoundType.NETHERITE_BLOCK || sound == SoundType.COPPER)
            depth = 30.0 + strength * 8.0;
        else if (sound == SoundType.STONE || sound == SoundType.DEEPSLATE || sound == SoundType.DEEPSLATE_BRICKS
                || sound == SoundType.POLISHED_DEEPSLATE || sound == SoundType.NETHER_BRICKS)
            depth = 25.0 + strength * 8.0;
        else if (sound == SoundType.WOOD || sound == SoundType.BAMBOO_WOOD || sound == SoundType.CHERRY_WOOD
                || sound == SoundType.NETHER_WOOD)
            depth = 20.0 + strength * 5.0;
        else
            depth = 15.0 + strength * 6.0;

        int maxWaterDepth = Math.max(1, (int) Math.round(depth));
        boolean isInternal = id != null && id.getNamespace().equals(CreateSubmarine.MOD_ID);
        if (!isInternal)
            maxWaterDepth = Math.min(maxWaterDepth, globalCap());

        float chance = (float) Math.max(0.05, Math.min(0.85, 0.6 - strength * 0.05));
        return new HullProperty(maxWaterDepth, chance);
    }

    private static HullProperty legacyDefault(String key, BlockState state, ResourceLocation id) {
        HullProperty fixed = switch (key) {
            case "minecraft:obsidian" -> new HullProperty(LEGACY_CAP, 0.08f);
            case "minecraft:reinforced_deepslate" -> new HullProperty(LEGACY_CAP, 0.01f);
            case "minecraft:bedrock" -> new HullProperty(LEGACY_CAP, 0.00f);
            case "create_submarine:creative_oxygenator" -> new HullProperty(250, 0.02f);
            case "create_submarine:ballast_tank" -> new HullProperty(230, 0.04f);
            case "create_submarine:ballast_vent", "create_submarine:water_thruster" -> new HullProperty(220, 0.05f);
            case "create_submarine:iron_pressurizer", "create_submarine:copper_pressurizer",
                    "create_submarine:electrolyzer" -> new HullProperty(200, 0.06f);
            case "create_submarine:oxygene_diffuser" -> new HullProperty(180, 0.07f);
            case "create_submarine:industrial_alarm" -> new HullProperty(160, 0.08f);
            case "create_submarine:barometer" -> new HullProperty(200, 0.05f);
            default -> null;
        };
        if (fixed != null)
            return fixed;

        float hardness = 2.0f;
        float resistance = 1.0f;
        SoundType sound = SoundType.STONE;
        try {
            hardness = state.getDestroySpeed(null, null);
            resistance = state.getBlock().getExplosionResistance();
            sound = state.getSoundType();
        } catch (RuntimeException e) {
        }
        double score = hardness * 11.2 + resistance * 5.6;
        if (sound == SoundType.METAL)
            score *= 1.8;
        else if (sound == SoundType.GLASS)
            score *= 0.3;
        else if (sound == SoundType.WOOD || sound == SoundType.BAMBOO)
            score *= 0.6;
        else if (sound == SoundType.STONE || sound == SoundType.DEEPSLATE)
            score *= 1.1;
        int depth = Math.max(1, (int) score);
        if (id == null || !id.getNamespace().equals(CreateSubmarine.MOD_ID))
            depth = Math.min(depth, LEGACY_CAP);
        float chance = (float) Math.max(0.05, Math.min(0.85, 1.0 - score / 168.0));
        return new HullProperty(depth, chance);
    }

    private static int globalCap() {
        return SubmarineConfig.SERVER_SPEC.isLoaded() ? SubmarineConfig.GLOBAL_MAX_DEPTH_CAP.get() : DEFAULT_CAP;
    }

    private static void buildStaticDefaults(Map<String, HullProperty> map) {
        map.put("minecraft:obsidian", new HullProperty(500, 0.06f));
        map.put("minecraft:crying_obsidian", new HullProperty(500, 0.06f));
        map.put("minecraft:bedrock", new HullProperty(760, 0.00f));

        map.put("create_submarine:creative_oxygenator", new HullProperty(760, 0.02f));
        map.put("create_submarine:ballast_tank", new HullProperty(400, 0.04f));
        map.put("create_submarine:ballast_vent", new HullProperty(400, 0.05f));
        map.put("create_submarine:water_thruster", new HullProperty(400, 0.05f));
        map.put("create_submarine:iron_pressurizer", new HullProperty(700, 0.06f));
        map.put("create_submarine:copper_pressurizer", new HullProperty(700, 0.06f));
        map.put("create_submarine:electrolyzer", new HullProperty(350, 0.06f));
        map.put("create_submarine:oxygene_diffuser", new HullProperty(350, 0.07f));
        map.put("create_submarine:industrial_alarm", new HullProperty(350, 0.08f));
        map.put("create_submarine:barometer", new HullProperty(350, 0.05f));
    }

    private static void writeJson(Path path, Map<String, HullProperty> data) {
        JsonObject root = new JsonObject();
        root.addProperty("_README",
                "Per-block hull strength. Edit maxWaterDepth (int) and implosionChance (0..1). Keys starting with _ are ignored.");
        root.addProperty("_version", VERSION);
        data.forEach((blockId, prop) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("maxWaterDepth", prop.maxWaterDepth());
            entry.addProperty("implosionChance", prop.implosionChance());
            root.add(blockId, entry);
        });
        try {
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            CreateSubmarine.LOGGER.warn("Couldn't write {}", path, e);
        }
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    public static Map<String, HullProperty> getValues() {
        return new TreeMap<>(values);
    }

    public static void applySynced(Map<String, HullProperty> synced) {
        values = new ConcurrentHashMap<>(synced);
        resolvedCache.clear();
    }

    public static void update(String key, int maxWaterDepth, float implosionChance) {
        HullProperty prop = new HullProperty(maxWaterDepth, clamp(implosionChance));
        values.put(key, prop);
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(key));
        if (block != null) {
            resolvedCache.put(block, prop);
        }
    }

    public static void save() {
        Map<String, HullProperty> sorted = new TreeMap<>(values);
        writeJson(CONFIG_PATH, sorted);
    }
}
