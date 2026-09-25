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

public class HullStrengthConfig {
    public record HullProperty(int maxWaterDepth, float implosionChance) {
    }

    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("submarine_hull.json");
    private static final Path LEGACY_DUMP_PATH = FMLPaths.CONFIGDIR.get().resolve("submarine_hull_generated.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Block, HullProperty> resolvedCache = new HashMap<>();
    private static Map<String, HullProperty> values = new HashMap<>();
    private static boolean configParseFailed = false;

    public static void load() {
        values = new HashMap<>();
        resolvedCache.clear();
        configParseFailed = false;

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

            HullProperty prop = existing.get(key);
            if (prop == null) {
                prop = staticDefaults.getOrDefault(key, autoCompute(state, id));
                anyNewBlock = true;
            }
            complete.put(key, prop);
            values.put(key, prop);
            resolvedCache.put(block, prop);
        }

        boolean shouldWrite = fileMissing || (anyNewBlock && !configParseFailed);
        if (shouldWrite)
            writeJson(CONFIG_PATH, complete);
        try {
            Files.deleteIfExists(LEGACY_DUMP_PATH);
        } catch (IOException ignored) {
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
        }
    }

    private static int convertOldDepthY(int oldMaxDepthY) {
        return Math.max(10, 64 - oldMaxDepthY);
    }

    private static HullProperty autoCompute(BlockState state, ResourceLocation id) {
        Block block = state.getBlock();
        float hardness = 2.0f, resistance = 1.0f;
        SoundType sound = SoundType.STONE;
        try {
            hardness = state.getDestroySpeed(null, null);
            resistance = block.getExplosionResistance();
            sound = state.getSoundType();
        } catch (Throwable ignored) {
        }
        double score = (hardness * 11.2) + (resistance * 5.6);
        double multiplier = 1.0;
        if (sound == SoundType.METAL)
            multiplier = 1.8;
        else if (sound == SoundType.GLASS)
            multiplier = 0.3;
        else if (sound == SoundType.WOOD || sound == SoundType.BAMBOO)
            multiplier = 0.6;
        else if (sound == SoundType.STONE || sound == SoundType.DEEPSLATE)
            multiplier = 1.1;
        score *= multiplier;

        int globalCap = globalCap();
        int maxWaterDepth = Math.max(1, (int) score);
        boolean isInternal = id != null && id.getNamespace().equals(CreateSubmarine.MOD_ID);
        if (!isInternal && maxWaterDepth > globalCap)
            maxWaterDepth = globalCap;

        float chance = (float) Math.max(0.05, Math.min(0.85, 1.0 - (score / 168.0)));
        return new HullProperty(maxWaterDepth, chance);
    }

    private static int globalCap() {
        return SubmarineConfig.SERVER_SPEC.isLoaded() ? SubmarineConfig.GLOBAL_MAX_DEPTH_CAP.get() : 300;
    }

    private static void buildStaticDefaults(Map<String, HullProperty> map) {
        int cap = globalCap();
        map.put("minecraft:obsidian", new HullProperty(cap, 0.08f));
        map.put("minecraft:reinforced_deepslate", new HullProperty(cap, 0.01f));
        map.put("minecraft:bedrock", new HullProperty(cap, 0.00f));

        map.put("create_submarine:creative_oxygenator", new HullProperty(250, 0.02f));
        map.put("create_submarine:ballast_tank", new HullProperty(230, 0.04f));
        map.put("create_submarine:ballast_vent", new HullProperty(220, 0.05f));
        map.put("create_submarine:water_thruster", new HullProperty(220, 0.05f));
        map.put("create_submarine:iron_pressurizer", new HullProperty(200, 0.06f));
        map.put("create_submarine:copper_pressurizer", new HullProperty(200, 0.06f));
        map.put("create_submarine:electrolyzer", new HullProperty(200, 0.06f));
        map.put("create_submarine:oxygene_diffuser", new HullProperty(180, 0.07f));
        map.put("create_submarine:industrial_alarm", new HullProperty(160, 0.08f));
        map.put("create_submarine:barometer", new HullProperty(200, 0.05f));
    }

    private static void writeJson(Path path, Map<String, HullProperty> data) {
        JsonObject root = new JsonObject();
        root.addProperty("_README",
                "Per-block hull strength. Edit maxWaterDepth (int) and implosionChance (0..1). Keys starting with _ are ignored.");
        root.addProperty("_version", 2);
        data.forEach((blockId, prop) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("maxWaterDepth", prop.maxWaterDepth());
            entry.addProperty("implosionChance", prop.implosionChance());
            root.add(blockId, entry);
        });
        try {
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
        }
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    public static Map<String, HullProperty> getValues() {
        return new TreeMap<>(values);
    }

    public static void applySynced(Map<String, HullProperty> synced) {
        values = new HashMap<>(synced);
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
