package com.maxenonyme.createsubmarine.submarine.stress;

import com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MaterialProperties {
    private static final Map<ResourceLocation, double[]> PROPERTIES = new HashMap<>();
    private static final Map<ResourceLocation, double[]> DIRECTIONAL_MODIFIERS = new HashMap<>();
    private static final Map<Block, double[]> RESOLVED = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> BRITTLE = new HashSet<>();
    private static final ResourceLocation REINFORCED_GLASS = ResourceLocation.fromNamespaceAndPath("simulated",
            "reinforced_glass");

    private static final double[] METAL = { 2.00e11, 2.50e8, 0.29, 101 };
    private static final double[] STONE = { 5.00e10, 1.50e8, 0.25, 80 };
    private static final double[] WOOD = { 1.10e10, 4.00e7, 0.30, 44 };
    private static final double[] GLASS = { 7.00e10, 4.00e7, 0.25, 30 };
    private static final double[] SOFT = { 5.00e9, 2.50e7, 0.30, 40 };

    static {
        for (String wood : new String[]{"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
                "crimson", "warped"}) {
            for (String suffix : new String[]{"log", "wood", "stripped_log", "stripped_wood"}) {
                DIRECTIONAL_MODIFIERS.put(ResourceLocation.withDefaultNamespace(wood + "_" + suffix), new double[]{1.0, 0.05});
            }
        }
        DIRECTIONAL_MODIFIERS.put(ResourceLocation.withDefaultNamespace("bamboo_block"), new double[]{1.0, 0.05});
        DIRECTIONAL_MODIFIERS.put(ResourceLocation.withDefaultNamespace("stripped_bamboo_block"), new double[]{1.0, 0.05});
        DIRECTIONAL_MODIFIERS.put(ResourceLocation.withDefaultNamespace("bone_block"), new double[]{1.0, 0.3});
        DIRECTIONAL_MODIFIERS.put(ResourceLocation.withDefaultNamespace("hay_block"), new double[]{1.0, 0.3});
        DIRECTIONAL_MODIFIERS.put(ResourceLocation.withDefaultNamespace("basalt"), new double[]{1.0, 0.5});
        DIRECTIONAL_MODIFIERS.put(ResourceLocation.withDefaultNamespace("polished_basalt"), new double[]{1.0, 0.5});
        DIRECTIONAL_MODIFIERS.put(ResourceLocation.withDefaultNamespace("smooth_basalt"), new double[]{1.0, 0.5});
        DIRECTIONAL_MODIFIERS.put(ResourceLocation.withDefaultNamespace("mushroom_stem"), new double[]{1.0, 0.5});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("iron_block"), new double[]{2.00e11, 2.50e8, 0.29});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("raw_iron_block"), new double[]{2.00e11, 2.00e8, 0.29});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("gold_block"), new double[]{7.90e10, 1.00e8, 0.35});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("raw_gold_block"), new double[]{7.90e10, 0.80e8, 0.35});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("diamond_block"), new double[]{1.22e12, 5.00e9, 0.25});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("netherite_block"), new double[]{5.50e11, 8.00e8, 0.28});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("ancient_debris"), new double[]{4.00e11, 6.00e8, 0.26});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("copper_block"), new double[]{1.10e11, 7.00e7, 0.35});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("raw_copper_block"), new double[]{1.10e11, 5.00e7, 0.35});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("waxed_copper_block"), new double[]{1.10e11, 7.00e7, 0.35});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("exposed_copper"), new double[]{1.05e11, 6.50e7, 0.34});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("weathered_copper"), new double[]{1.00e11, 6.00e7, 0.34});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("oxidized_copper"), new double[]{0.90e11, 5.00e7, 0.33});

        for (String variant : new String[]{"cut_copper", "exposed_cut_copper", "weathered_cut_copper", "oxidized_cut_copper",
                "waxed_cut_copper", "waxed_exposed_cut_copper", "waxed_weathered_cut_copper", "waxed_oxidized_cut_copper"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(variant), new double[]{1.10e11, 7.00e7, 0.35});
        }
        for (String variant : new String[]{"cut_copper_slab", "exposed_cut_copper_slab", "weathered_cut_copper_slab", "oxidized_cut_copper_slab",
                "waxed_cut_copper_slab", "waxed_exposed_cut_copper_slab", "waxed_weathered_cut_copper_slab", "waxed_oxidized_cut_copper_slab"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(variant), new double[]{1.10e11, 7.00e7, 0.35});
        }
        for (String variant : new String[]{"cut_copper_stairs", "exposed_cut_copper_stairs", "weathered_cut_copper_stairs", "oxidized_cut_copper_stairs",
                "waxed_cut_copper_stairs", "waxed_exposed_cut_copper_stairs", "waxed_weathered_cut_copper_stairs", "waxed_oxidized_cut_copper_stairs"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(variant), new double[]{1.10e11, 7.00e7, 0.35});
        }

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("emerald_block"), new double[]{3.00e11, 3.00e8, 0.25});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("lapis_block"), new double[]{6.00e10, 1.00e8, 0.24});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("redstone_block"), new double[]{3.00e10, 5.00e7, 0.25});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("quartz_block"), new double[]{7.00e10, 1.50e8, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("smooth_quartz"), new double[]{7.00e10, 1.50e8, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("quartz_bricks"), new double[]{7.00e10, 1.60e8, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("quartz_pillar"), new double[]{7.00e10, 1.50e8, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("chiseled_quartz_block"), new double[]{7.00e10, 1.40e8, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("smooth_quartz_slab"), new double[]{7.00e10, 1.50e8, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("smooth_quartz_stairs"), new double[]{7.00e10, 1.50e8, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("quartz_slab"), new double[]{7.00e10, 1.50e8, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("quartz_stairs"), new double[]{7.00e10, 1.50e8, 0.22});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("amethyst_block"), new double[]{7.00e10, 4.00e7, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("budding_amethyst"), new double[]{7.00e10, 4.00e7, 0.25});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("bone_block"), new double[]{2.00e10, 1.50e8, 0.30});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("slime_block"), new double[]{1.00e6, 5.00e4, 0.49});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("honey_block"), new double[]{5.00e5, 1.00e4, 0.49});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("anvil"), new double[]{1.00e11, 7.00e8, 0.28});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("chipped_anvil"), new double[]{1.00e11, 5.00e8, 0.28});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("damaged_anvil"), new double[]{1.00e11, 3.00e8, 0.28});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("heavy_core"), new double[]{2.00e11, 5.00e8, 0.29});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("granite"), new double[]{6.00e10, 1.50e8, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("polished_granite"), new double[]{6.00e10, 1.50e8, 0.25});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("diorite"), new double[]{5.00e10, 1.20e8, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("polished_diorite"), new double[]{5.00e10, 1.20e8, 0.25});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("andesite"), new double[]{4.50e10, 1.20e8, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("polished_andesite"), new double[]{4.50e10, 1.20e8, 0.25});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("stone"), new double[]{5.00e10, 1.50e8, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("smooth_stone"), new double[]{5.00e10, 1.50e8, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("stone_slab"), new double[]{5.00e10, 1.50e8, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("smooth_stone_slab"), new double[]{5.00e10, 1.50e8, 0.25});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("cobblestone"), new double[]{4.00e10, 1.00e8, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("mossy_cobblestone"), new double[]{3.00e10, 8.00e7, 0.22});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("deepslate"), new double[]{7.00e10, 1.80e8, 0.24});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("cobbled_deepslate"), new double[]{5.00e10, 1.20e8, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("polished_deepslate"), new double[]{7.00e10, 1.80e8, 0.24});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("deepslate_bricks"), new double[]{7.00e10, 1.80e8, 0.24});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("deepslate_tiles"), new double[]{7.00e10, 1.80e8, 0.24});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("cracked_deepslate_bricks"), new double[]{6.00e10, 1.40e8, 0.23});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("cracked_deepslate_tiles"), new double[]{6.00e10, 1.40e8, 0.23});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("chiseled_deepslate"), new double[]{7.00e10, 1.80e8, 0.24});
        for (String v : new String[]{"deepslate_brick_slab", "deepslate_brick_stairs", "deepslate_brick_wall",
                "deepslate_tile_slab", "deepslate_tile_stairs", "deepslate_tile_wall",
                "cobbled_deepslate_slab", "cobbled_deepslate_stairs", "cobbled_deepslate_wall",
                "polished_deepslate_slab", "polished_deepslate_stairs", "polished_deepslate_wall"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(v), new double[]{7.00e10, 1.80e8, 0.24});
        }

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("tuff"), new double[]{3.00e10, 8.00e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("polished_tuff"), new double[]{3.00e10, 8.00e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("tuff_bricks"), new double[]{3.00e10, 8.00e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("chiseled_tuff"), new double[]{3.00e10, 8.00e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("tuff_slab"), new double[]{3.00e10, 8.00e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("tuff_stairs"), new double[]{3.00e10, 8.00e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("tuff_wall"), new double[]{3.00e10, 8.00e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("polished_tuff_slab"), new double[]{3.00e10, 8.00e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("polished_tuff_stairs"), new double[]{3.00e10, 8.00e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("polished_tuff_wall"), new double[]{3.00e10, 8.00e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("tuff_brick_slab"), new double[]{3.00e10, 8.00e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("tuff_brick_stairs"), new double[]{3.00e10, 8.00e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("tuff_brick_wall"), new double[]{3.00e10, 8.00e7, 0.20});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("calcite"), new double[]{2.50e10, 6.00e7, 0.22});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("basalt"), new double[]{6.00e10, 1.60e8, 0.24});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("polished_basalt"), new double[]{6.00e10, 1.60e8, 0.24});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("smooth_basalt"), new double[]{5.50e10, 1.60e8, 0.24});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("obsidian"), new double[]{8.70e10, 2.00e8, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("crying_obsidian"), new double[]{8.70e10, 2.20e8, 0.22});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("blackstone"), new double[]{5.50e10, 1.40e8, 0.24});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("gilded_blackstone"), new double[]{4.50e10, 1.20e8, 0.24});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("polished_blackstone"), new double[]{5.50e10, 1.40e8, 0.24});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("polished_blackstone_bricks"), new double[]{5.50e10, 1.50e8, 0.24});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("cracked_polished_blackstone_bricks"), new double[]{4.50e10, 1.00e8, 0.23});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("chiseled_polished_blackstone"), new double[]{5.50e10, 1.40e8, 0.24});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("netherrack"), new double[]{1.00e10, 3.00e7, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("soul_sand"), new double[]{1.00e6, 1.00e4, 0.35});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("soul_soil"), new double[]{1.00e6, 1.00e4, 0.35});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("crimson_nylium"), new double[]{5.00e9, 2.00e7, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("warped_nylium"), new double[]{5.00e9, 2.00e7, 0.25});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("end_stone"), new double[]{1.00e11, 2.50e8, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("end_stone_bricks"), new double[]{1.00e11, 2.50e8, 0.22});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("purpur_block"), new double[]{3.00e10, 1.00e8, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("purpur_pillar"), new double[]{3.00e10, 1.00e8, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("purpur_slab"), new double[]{3.00e10, 1.00e8, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("purpur_stairs"), new double[]{3.00e10, 1.00e8, 0.20});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("bricks"), new double[]{2.00e10, 5.00e7, 0.18});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("brick_slab"), new double[]{2.00e10, 5.00e7, 0.18});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("brick_stairs"), new double[]{2.00e10, 5.00e7, 0.18});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("brick_wall"), new double[]{2.00e10, 5.00e7, 0.18});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("stone_bricks"), new double[]{3.00e10, 8.00e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("mossy_stone_bricks"), new double[]{2.50e10, 7.00e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("cracked_stone_bricks"), new double[]{2.50e10, 6.00e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("chiseled_stone_bricks"), new double[]{3.00e10, 8.00e7, 0.20});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("nether_bricks"), new double[]{3.00e10, 1.20e8, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("red_nether_bricks"), new double[]{3.00e10, 1.20e8, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("cracked_nether_bricks"), new double[]{2.50e10, 9.00e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("chiseled_nether_bricks"), new double[]{3.00e10, 1.20e8, 0.20});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("white_concrete"), new double[]{3.00e10, 2.50e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("orange_concrete"), new double[]{3.00e10, 2.50e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("magenta_concrete"), new double[]{3.00e10, 2.50e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("light_blue_concrete"), new double[]{3.00e10, 2.50e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("yellow_concrete"), new double[]{3.00e10, 2.50e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("lime_concrete"), new double[]{3.00e10, 2.50e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("pink_concrete"), new double[]{3.00e10, 2.50e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("gray_concrete"), new double[]{3.00e10, 2.50e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("light_gray_concrete"), new double[]{3.00e10, 2.50e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("cyan_concrete"), new double[]{3.00e10, 2.50e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("purple_concrete"), new double[]{3.00e10, 2.50e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("blue_concrete"), new double[]{3.00e10, 2.50e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("brown_concrete"), new double[]{3.00e10, 2.50e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("green_concrete"), new double[]{3.00e10, 2.50e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("red_concrete"), new double[]{3.00e10, 2.50e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("black_concrete"), new double[]{3.00e10, 2.50e7, 0.20});
        for (String c : new String[]{"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"}) {
            for (String suffix : new String[]{"concrete_powder"}) {
                PROPERTIES.put(ResourceLocation.withDefaultNamespace(c + "_" + suffix), new double[]{1.00e6, 5.00e3, 0.35});
            }
        }

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("prismarine"), new double[]{4.00e10, 1.20e8, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("prismarine_bricks"), new double[]{4.00e10, 1.40e8, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("dark_prismarine"), new double[]{4.00e10, 1.40e8, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("prismarine_slab"), new double[]{4.00e10, 1.20e8, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("prismarine_stairs"), new double[]{4.00e10, 1.20e8, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("prismarine_wall"), new double[]{4.00e10, 1.20e8, 0.22});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("sandstone"), new double[]{2.00e10, 5.00e7, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("chiseled_sandstone"), new double[]{2.00e10, 5.00e7, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("cut_sandstone"), new double[]{2.00e10, 5.00e7, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("smooth_sandstone"), new double[]{2.00e10, 5.00e7, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("red_sandstone"), new double[]{1.80e10, 4.00e7, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("chiseled_red_sandstone"), new double[]{1.80e10, 4.00e7, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("cut_red_sandstone"), new double[]{1.80e10, 4.00e7, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("smooth_red_sandstone"), new double[]{1.80e10, 4.00e7, 0.22});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("glass"), new double[]{7.00e10, 4.00e7, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("tinted_glass"), new double[]{7.00e10, 4.00e7, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("white_stained_glass"), new double[]{7.00e10, 4.00e7, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("orange_stained_glass"), new double[]{7.00e10, 4.00e7, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("magenta_stained_glass"), new double[]{7.00e10, 4.00e7, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("light_blue_stained_glass"), new double[]{7.00e10, 4.00e7, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("yellow_stained_glass"), new double[]{7.00e10, 4.00e7, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("lime_stained_glass"), new double[]{7.00e10, 4.00e7, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("pink_stained_glass"), new double[]{7.00e10, 4.00e7, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("gray_stained_glass"), new double[]{7.00e10, 4.00e7, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("light_gray_stained_glass"), new double[]{7.00e10, 4.00e7, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("cyan_stained_glass"), new double[]{7.00e10, 4.00e7, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("purple_stained_glass"), new double[]{7.00e10, 4.00e7, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("blue_stained_glass"), new double[]{7.00e10, 4.00e7, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("brown_stained_glass"), new double[]{7.00e10, 4.00e7, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("green_stained_glass"), new double[]{7.00e10, 4.00e7, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("red_stained_glass"), new double[]{7.00e10, 4.00e7, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("black_stained_glass"), new double[]{7.00e10, 4.00e7, 0.25});
        for (String c : new String[]{"glass_pane", "white_stained_glass_pane", "orange_stained_glass_pane",
                "magenta_stained_glass_pane", "light_blue_stained_glass_pane", "yellow_stained_glass_pane",
                "lime_stained_glass_pane", "pink_stained_glass_pane", "gray_stained_glass_pane",
                "light_gray_stained_glass_pane", "cyan_stained_glass_pane", "purple_stained_glass_pane",
                "blue_stained_glass_pane", "brown_stained_glass_pane", "green_stained_glass_pane",
                "red_stained_glass_pane", "black_stained_glass_pane"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(c), new double[]{7.00e10, 4.00e7, 0.25});
        }

        for (String wood : new String[]{"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
                "crimson", "warped", "bamboo"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(wood + "_planks"), new double[]{1.00e10, 4.00e7, 0.30});
        }
        for (String wood : new String[]{"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
                "crimson", "warped"}) {
            for (String suffix : new String[]{"log", "wood", "stripped_log", "stripped_wood"}) {
                PROPERTIES.put(ResourceLocation.withDefaultNamespace(wood + "_" + suffix), new double[]{1.20e10, 5.00e7, 0.30});
            }
        }
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("bamboo_block"), new double[]{1.50e10, 5.00e7, 0.30});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("stripped_bamboo_block"), new double[]{1.50e10, 5.00e7, 0.30});
        for (String wood : new String[]{"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
                "crimson", "warped", "bamboo"}) {
            for (String suffix : new String[]{"door", "trapdoor", "fence", "fence_gate", "slab", "stairs",
                    "pressure_plate", "button", "sign", "hanging_sign"}) {
                PROPERTIES.put(ResourceLocation.withDefaultNamespace(wood + "_" + suffix), new double[]{1.00e10, 4.00e7, 0.30});
            }
        }

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("dirt"), new double[]{1.00e7, 5.00e4, 0.30});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("coarse_dirt"), new double[]{1.00e7, 5.00e4, 0.30});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("rooted_dirt"), new double[]{2.00e7, 8.00e4, 0.30});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("grass_block"), new double[]{1.00e7, 4.00e4, 0.30});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("mycelium"), new double[]{5.00e6, 3.00e4, 0.30});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("podzol"), new double[]{1.00e7, 5.00e4, 0.30});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("mud"), new double[]{1.00e6, 1.00e4, 0.35});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("muddy_mangrove_roots"), new double[]{5.00e6, 2.00e4, 0.33});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("packed_mud"), new double[]{5.00e6, 3.00e4, 0.30});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("sand"), new double[]{5.00e6, 1.00e4, 0.35});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("red_sand"), new double[]{5.00e6, 1.00e4, 0.35});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("gravel"), new double[]{5.00e6, 1.00e4, 0.30});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("clay"), new double[]{3.00e6, 2.00e4, 0.35});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("farmland"), new double[]{5.00e6, 3.00e4, 0.30});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("ice"), new double[]{8.00e9, 2.00e7, 0.32});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("frosted_ice"), new double[]{4.00e9, 1.00e7, 0.32});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("packed_ice"), new double[]{1.00e10, 3.00e7, 0.30});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("blue_ice"), new double[]{1.50e10, 5.00e7, 0.30});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("snow_block"), new double[]{5.00e6, 1.00e5, 0.35});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("snow"), new double[]{1.00e6, 5.00e4, 0.35});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("bedrock"), new double[]{5.00e10, 1.00e10, 0.22});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("sponge"), new double[]{1.00e6, 1.00e4, 0.40});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("wet_sponge"), new double[]{1.00e6, 1.00e4, 0.40});

        for (String c : new String[]{"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(c + "_wool"), new double[]{1.00e6, 1.00e4, 0.45});
        }

        for (String leaf : new String[]{"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
                "azalea", "flowering_azalea"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(leaf + "_leaves"), new double[]{1.00e6, 5.00e3, 0.40});
        }

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("terracotta"), new double[]{1.50e10, 3.00e7, 0.22});
        for (String c : new String[]{"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(c + "_terracotta"), new double[]{1.50e10, 3.00e7, 0.22});
        }

        for (String c : new String[]{"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(c + "_glazed_terracotta"), new double[]{2.00e10, 4.00e7, 0.20});
        }

        for (String coral : new String[]{"tube", "brain", "bubble", "fire", "horn",
                "dead_tube", "dead_brain", "dead_bubble", "dead_fire", "dead_horn"}) {
            for (String suffix : new String[]{"coral", "coral_block", "coral_fan"}) {
                PROPERTIES.put(ResourceLocation.withDefaultNamespace(coral + "_" + suffix), new double[]{2.00e10, 3.00e7, 0.22});
            }
        }

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("brown_mushroom_block"), new double[]{5.00e5, 1.00e4, 0.40});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("red_mushroom_block"), new double[]{5.00e5, 1.00e4, 0.40});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("mushroom_stem"), new double[]{5.00e5, 1.00e4, 0.40});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("warped_wart_block"), new double[]{1.00e6, 5.00e3, 0.35});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("nether_wart_block"), new double[]{1.00e6, 5.00e3, 0.35});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("shroomlight"), new double[]{1.00e6, 5.00e3, 0.35});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("glowstone"), new double[]{5.00e9, 1.00e7, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("sea_lantern"), new double[]{5.00e9, 1.00e7, 0.22});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("magma_block"), new double[]{2.00e10, 5.00e7, 0.24});

        for (String casing : new String[]{"andesite_casing", "brass_casing", "copper_casing", "railway_casing",
                "shadow_steel_casing", "refined_radiance_casing", "fluid_tank", "item_vault"}) {
            PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create", casing), new double[]{1.50e11, 2.00e8, 0.28});
        }
        for (String part : new String[]{"shaft", "cogwheel", "large_cogwheel", "gearbox"}) {
            PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create", part), new double[]{2.00e11, 3.00e8, 0.29});
        }
        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create", "mechanical_bearing"), new double[]{2.00e11, 3.00e8, 0.29});
        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create", "windmill_bearing"), new double[]{2.00e11, 3.00e8, 0.29});
        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create", "mechanical_piston"), new double[]{2.00e11, 3.00e8, 0.29});
        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create", "piston_extension_pole"), new double[]{2.00e11, 3.00e8, 0.29});
        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create", "gantry_carriage"), new double[]{2.00e11, 3.00e8, 0.29});
        for (String kin : new String[]{"mechanical_press", "mechanical_mixer", "mechanical_drill", "mechanical_saw",
                "mechanical_harvester", "mechanical_plough", "deployer", "portable_storage_interface",
                "mechanical_roller", "millstone", "crushing_wheel", "mechanical_crafter"}) {
            PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create", kin), new double[]{1.50e11, 2.00e8, 0.28});
        }
        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create", "track"), new double[]{2.00e11, 4.00e8, 0.29});
        for (String train : new String[]{"train_track", "train_track_slab", "train_track_stairs"}) {
            PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create", train), new double[]{2.00e11, 4.00e8, 0.29});
        }
        for (String glassBlock : new String[]{"framed_glass_door", "framed_glass_trapdoor"}) {
            PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create", glassBlock), new double[]{7.00e10, 4.00e7, 0.25});
        }
        for (String bars : new String[]{"andesite_bars", "brass_bars", "copper_bars"}) {
            PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create", bars), new double[]{2.00e11, 3.00e8, 0.29});
        }
        for (String copycat : new String[]{"copycat_bars", "copycat_panel", "copycat_step", "copycat_slab"}) {
            PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create", copycat), new double[]{2.00e11, 3.00e8, 0.29});
        }
        for (String cc : new String[]{"copycat_half_panel", "copycat_vertical_step", "copycat_vertical_sliding_step",
                "copycat_vertical_sliding_step_slim", "copycat_vertical_sliding_step_slim_connected",
                "copycat_vertical_sliding_step_connected", "copycat_vertical_stair",
                "copycat_vertical_stair_slim", "copycat_vertical_stair_slim_connected",
                "copycat_vertical_stair_connected", "copycat_vertical_raised_step",
                "copycat_vertical_raised_step_connected", "copycat_vertical_raised_step_slim",
                "copycat_vertical_raised_step_slim_connected", "copycat_vertical_gate",
                "copycat_vertical_gate_connected", "copycat_vertical_gate_slim",
                "copycat_vertical_gate_slim_connected", "copycat_vertical_slab",
                "copycat_vertical_slab_slim", "copycat_vertical_slab_connected",
                "copycat_vertical_slab_slim_connected", "copycat_post_box", "copycat_post_box_slim",
                "copycat_post_box_slim_connected", "copycat_post_box_connected", "copycat_post",
                "copycat_post_connected", "copycat_post_slim", "copycat_post_slim_connected",
                "copycat_board", "copycat_board_connected", "copycat_board_slim", "copycat_board_slim_connected",
                "copycat_wall", "copycat_wall_connected", "copycat_wall_slim", "copycat_wall_slim_connected",
                "copycat_fence", "copycat_fence_connected", "copycat_fence_slim", "copycat_fence_slim_connected",
                "copycat_beam", "copycat_beam_connected", "copycat_beam_slim", "copycat_beam_slim_connected",
                "copycat_beam_straight", "copycat_beam_straight_connected", "copycat_beam_straight_slim",
                "copycat_beam_straight_slim_connected"}) {
            PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("copycats", cc), new double[]{2.00e11, 3.00e8, 0.29});
        }
        for (String struct : new String[]{"metal_ladder", "metal_scaffolding", "metal_girder", "metal_girder_encased_shaft"}) {
            PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create", struct), new double[]{1.50e11, 2.00e8, 0.28});
        }
        for (String trainBlock : new String[]{"station", "signal", "track_observer", "train_controls",
                "controls", "bogey", "bogey_head", "bogey_wheel", "track_station", "flap_display",
                "display_board", "train_door", "train_trapdoor"}) {
            PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create", trainBlock), new double[]{2.00e11, 4.00e8, 0.29});
        }
        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create", "belt"), new double[]{5.00e8, 1.00e7, 0.45});
        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create", "chain_conveyor"), new double[]{2.00e11, 4.00e8, 0.29});
        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create", "schematicannon"), new double[]{1.00e10, 4.00e7, 0.30});
        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create", "schematic_table"), new double[]{1.00e10, 4.00e7, 0.30});
        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create", "elevator_contact"), new double[]{1.50e11, 2.00e8, 0.28});
        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create", "elevator_pulley"), new double[]{2.00e11, 3.00e8, 0.29});

        for (String sm : new String[]{"creative_oxygenator", "ballast_tank", "ballast_vent",
                "oxygene_diffuser", "electrolyzer", "industrial_alarm", "water_thruster",
                "glass_pressurizer", "floater"}) {
            PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("create_submarine", sm), new double[]{1.50e11, 2.00e8, 0.28});
        }

        for (String aero : new String[]{"levitite", "levitite_block", "levitite_tiles",
                "levitite_pillar", "chiseled_levitite"}) {
            PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("aeronautics", aero), new double[]{5.00e10, 1.00e8, 0.25});
        }
        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("aeronautics", "propeller_bearing"), new double[]{2.00e11, 4.00e8, 0.29});
        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("aeronautics", "gyro_bearing"), new double[]{2.00e11, 4.00e8, 0.29});
        for (String bal : new String[]{"hot_air_burner", "envelope_block", "basket_block"}) {
            PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("aeronautics", bal), new double[]{5.00e9, 3.00e7, 0.30});
        }

        for (String sim : new String[]{"steel_block", "aluminum_block", "reinforced_glass", "composite_panel"}) {
            PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("simulated", sim), new double[]{2.00e11, 3.00e8, 0.28});
        }
        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("simulated", "rope"), new double[]{2.00e11, 8.00e8, 0.30});
        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("simulated", "rope_winch"), new double[]{2.00e11, 5.00e8, 0.29});
        for (String sensor : new String[]{"gimbal_sensor", "velocity_sensor", "altitude_sensor", "angle_sensor"}) {
            PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("simulated", sensor), new double[]{7.00e10, 5.00e7, 0.25});
        }

        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("offroad", "suspension_block"), new double[]{2.00e11, 3.00e8, 0.29});
        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("offroad", "wheel_block"), new double[]{1.00e9, 2.00e7, 0.40});
        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("offroad", "shock_absorber"), new double[]{2.00e11, 3.00e8, 0.28});

        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("sable", "assembler"), new double[]{1.50e11, 2.00e8, 0.28});
        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("sable", "disassembler"), new double[]{1.50e11, 2.00e8, 0.28});
        PROPERTIES.put(ResourceLocation.fromNamespaceAndPath("sable", "anchor_block"), new double[]{1.00e12, 5.00e9, 0.22});

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("piston"), new double[]{1.50e11, 2.00e8, 0.28});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("sticky_piston"), new double[]{1.50e11, 2.00e8, 0.28});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("piston_head"), new double[]{1.50e11, 2.00e8, 0.28});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("furnace"), new double[]{3.00e10, 1.20e8, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("blast_furnace"), new double[]{3.00e10, 1.50e8, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("smoker"), new double[]{3.00e10, 1.20e8, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("crafting_table"), new double[]{1.00e10, 4.00e7, 0.30});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("fletching_table"), new double[]{1.00e10, 4.00e7, 0.30});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("smithing_table"), new double[]{1.00e10, 4.00e7, 0.30});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("bookshelf"), new double[]{5.00e9, 2.00e7, 0.30});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("chiseled_bookshelf"), new double[]{5.00e9, 2.00e7, 0.30});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("jukebox"), new double[]{1.00e10, 3.00e7, 0.30});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("note_block"), new double[]{1.00e10, 4.00e7, 0.30});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("enchanting_table"), new double[]{5.00e10, 2.50e8, 0.24});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("ender_chest"), new double[]{8.70e10, 2.00e8, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("beacon"), new double[]{1.00e11, 1.00e8, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("conduit"), new double[]{8.00e10, 8.00e7, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("spawner"), new double[]{5.00e11, 1.00e9, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("vault"), new double[]{2.00e11, 5.00e8, 0.29});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("ender_eye"), new double[]{2.00e10, 2.00e7, 0.30});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("respawn_anchor"), new double[]{8.70e10, 2.00e8, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("lodestone"), new double[]{5.00e10, 2.00e8, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("target"), new double[]{1.00e7, 5.00e4, 0.35});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("hay_block"), new double[]{5.00e6, 5.00e4, 0.40});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("dried_kelp_block"), new double[]{1.00e6, 1.00e4, 0.40});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("honeycomb_block"), new double[]{5.00e7, 5.00e5, 0.45});
        for (String ct : new String[]{"tube_coral_block", "brain_coral_block", "bubble_coral_block",
                "fire_coral_block", "horn_coral_block"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(ct), new double[]{2.00e10, 3.00e7, 0.22});
        }
        for (String ct : new String[]{"dead_tube_coral_block", "dead_brain_coral_block", "dead_bubble_coral_block",
                "dead_fire_coral_block", "dead_horn_coral_block"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(ct), new double[]{2.00e10, 2.00e7, 0.22});
        }
        for (String ore : new String[]{"iron_ore", "deepslate_iron_ore", "gold_ore", "deepslate_gold_ore",
                "copper_ore", "deepslate_copper_ore", "diamond_ore", "deepslate_diamond_ore",
                "emerald_ore", "deepslate_emerald_ore", "lapis_ore", "deepslate_lapis_ore",
                "redstone_ore", "deepslate_redstone_ore", "coal_ore", "deepslate_coal_ore"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(ore), new double[]{5.00e10, 1.50e8, 0.24});
        }
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("nether_gold_ore"), new double[]{1.50e10, 3.00e7, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("nether_quartz_ore"), new double[]{1.50e10, 3.00e7, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("glow_lichen"), new double[]{1.00e6, 5.00e3, 0.40});
        for (String am : new String[]{"amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud", "small_amethyst_bud"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(am), new double[]{7.00e10, 2.00e7, 0.25});
        }
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("pointed_dripstone"), new double[]{3.00e10, 5.00e7, 0.22});
        for (String sk : new String[]{"sculk", "sculk_catalyst", "sculk_sensor", "sculk_shrieker"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(sk), new double[]{1.00e9, 1.00e7, 0.30});
        }
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("mangrove_roots"), new double[]{5.00e6, 2.00e4, 0.33});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("big_dripleaf"), new double[]{1.00e6, 5.00e3, 0.40});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("spore_blossom"), new double[]{1.00e6, 5.00e3, 0.40});
        for (String candle : new String[]{"candle", "white_candle", "orange_candle", "magenta_candle",
                "light_blue_candle", "yellow_candle", "lime_candle", "pink_candle", "gray_candle",
                "light_gray_candle", "cyan_candle", "purple_candle", "blue_candle", "brown_candle",
                "green_candle", "red_candle", "black_candle"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(candle), new double[]{1.00e6, 1.00e4, 0.45});
        }
        for (String cc : new String[]{"candle_cake", "white_candle_cake", "orange_candle_cake", "magenta_candle_cake",
                "light_blue_candle_cake", "yellow_candle_cake", "lime_candle_cake", "pink_candle_cake", "gray_candle_cake",
                "light_gray_candle_cake", "cyan_candle_cake", "purple_candle_cake", "blue_candle_cake", "brown_candle_cake",
                "green_candle_cake", "red_candle_cake", "black_candle_cake"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(cc), new double[]{5.00e6, 1.00e4, 0.40});
        }
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("stone_stairs"), new double[]{5.00e10, 1.50e8, 0.25});
        for (String v : new String[]{"stone_brick_slab", "stone_brick_stairs", "stone_brick_wall",
                "mossy_stone_brick_slab", "mossy_stone_brick_stairs", "mossy_stone_brick_wall",
                "cobblestone_stairs", "cobblestone_slab", "cobblestone_wall",
                "mossy_cobblestone_stairs", "mossy_cobblestone_slab", "mossy_cobblestone_wall",
                "stone_pressure_plate", "stone_button",
                "granite_slab", "granite_stairs", "granite_wall",
                "polished_granite_slab", "polished_granite_stairs",
                "diorite_slab", "diorite_stairs", "diorite_wall",
                "polished_diorite_slab", "polished_diorite_stairs",
                "andesite_slab", "andesite_stairs", "andesite_wall",
                "polished_andesite_slab", "polished_andesite_stairs"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(v), new double[]{5.00e10, 1.50e8, 0.25});
        }

        for (String v : new String[]{"sandstone_slab", "sandstone_stairs", "sandstone_wall",
                "smooth_sandstone_slab", "smooth_sandstone_stairs",
                "cut_sandstone_slab"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(v), new double[]{2.00e10, 5.00e7, 0.22});
        }
        for (String v : new String[]{"red_sandstone_slab", "red_sandstone_stairs", "red_sandstone_wall",
                "smooth_red_sandstone_slab", "smooth_red_sandstone_stairs",
                "cut_red_sandstone_slab"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(v), new double[]{1.80e10, 4.00e7, 0.22});
        }

        for (String v : new String[]{"blackstone_slab", "blackstone_stairs", "blackstone_wall",
                "polished_blackstone_slab", "polished_blackstone_stairs", "polished_blackstone_wall",
                "polished_blackstone_brick_slab", "polished_blackstone_brick_stairs", "polished_blackstone_brick_wall",
                "polished_blackstone_pressure_plate", "polished_blackstone_button"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(v), new double[]{5.50e10, 1.40e8, 0.24});
        }

        for (String v : new String[]{"nether_brick_slab", "nether_brick_stairs", "nether_brick_wall", "nether_brick_fence",
                "red_nether_brick_slab", "red_nether_brick_stairs", "red_nether_brick_wall"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(v), new double[]{3.00e10, 1.20e8, 0.20});
        }

        for (String v : new String[]{"repeater", "comparator", "observer", "redstone_lamp",
                "redstone_wire", "lever", "tripwire_hook", "daylight_detector", "dispenser", "dropper", "hopper", "crafter"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(v), new double[]{3.00e10, 5.00e7, 0.25});
        }

        for (String v : new String[]{"chest", "trapped_chest", "barrel"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(v), new double[]{1.00e10, 4.00e7, 0.30});
        }
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("brewing_stand"), new double[]{5.00e10, 5.00e7, 0.24});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("cauldron"), new double[]{2.00e11, 1.50e8, 0.29});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("grindstone"), new double[]{5.00e10, 1.00e8, 0.25});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("stonecutter"), new double[]{1.00e11, 1.50e8, 0.26});
        for (String v : new String[]{"cartography_table", "loom", "lectern"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(v), new double[]{1.00e10, 4.00e7, 0.30});
        }
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("composter"), new double[]{1.00e10, 3.00e7, 0.30});

        for (String v : new String[]{"rail", "powered_rail", "detector_rail", "activator_rail"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(v), new double[]{2.00e11, 2.50e8, 0.29});
        }

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("iron_bars"), new double[]{2.00e11, 2.00e8, 0.29});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("iron_door"), new double[]{2.00e11, 2.50e8, 0.29});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("iron_trapdoor"), new double[]{2.00e11, 2.50e8, 0.29});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("heavy_weighted_pressure_plate"), new double[]{2.00e11, 2.50e8, 0.29});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("light_weighted_pressure_plate"), new double[]{7.90e10, 1.00e8, 0.35});

        for (String v : new String[]{"end_stone_brick_slab", "end_stone_brick_stairs", "end_stone_brick_wall"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(v), new double[]{1.00e11, 2.50e8, 0.22});
        }

        PROPERTIES.put(ResourceLocation.withDefaultNamespace("moss_block"), new double[]{1.00e6, 5.00e3, 0.40});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("dripstone_block"), new double[]{2.50e10, 6.00e7, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("trial_spawner"), new double[]{1.00e11, 5.00e8, 0.22});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("decorated_pot"), new double[]{2.00e10, 2.00e7, 0.20});
        PROPERTIES.put(ResourceLocation.withDefaultNamespace("cobweb"), new double[]{1.00e5, 1.00e4, 0.45});
        for (String v : new String[]{"pumpkin", "carved_pumpkin", "jack_o_lantern", "melon"}) {
            PROPERTIES.put(ResourceLocation.withDefaultNamespace(v), new double[]{5.00e6, 1.00e4, 0.40});
        }

        for (String v : new String[]{"glass", "tinted_glass", "glass_pane", "obsidian", "crying_obsidian",
                "amethyst_block", "budding_amethyst", "calcite", "terracotta"}) {
            BRITTLE.add(ResourceLocation.withDefaultNamespace(v));
        }
        for (String c : new String[]{"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"}) {
            for (String suffix : new String[]{"_stained_glass", "_stained_glass_pane", "_terracotta",
                    "_glazed_terracotta"})
                BRITTLE.add(ResourceLocation.withDefaultNamespace(c + suffix));
        }
        BRITTLE.add(ResourceLocation.fromNamespaceAndPath("create", "framed_glass_door"));
        BRITTLE.add(ResourceLocation.fromNamespaceAndPath("create", "framed_glass_trapdoor"));
        BRITTLE.add(REINFORCED_GLASS);
    }


    public static double[] of(BlockState state) {
        return RESOLVED.computeIfAbsent(state.getBlock(), b -> resolve(state));
    }

    private static double[] resolve(BlockState state) {
        double[] known = PROPERTIES.get(state.getBlock().builtInRegistryHolder().key().location());
        if (known != null)
            return known;
        double[] family = family(state);
        Optional<HullStrengthConfig.HullProperty> prop = HullStrengthConfig.getFor(state);
        double multiplier = SubmarineConfig.SERVER_SPEC.isLoaded() ? SubmarineConfig.MAX_DEPTH_MULTIPLIER.get() : 1.0;
        double scale = prop.map(p -> Math.max(0.5, Math.min(2.5, p.maxWaterDepth() / multiplier / family[3]))).orElse(1.0);
        return new double[] { family[0], family[1] * scale, family[2] };
    }

    private static double[] family(BlockState state) {
        SoundType sound;
        try {
            sound = state.getSoundType();
        } catch (RuntimeException e) {
            return SOFT;
        }
        if (sound == SoundType.METAL || sound == SoundType.NETHERITE_BLOCK || sound == SoundType.COPPER
                || sound == SoundType.ANVIL || sound == SoundType.CHAIN || sound == SoundType.LANTERN)
            return METAL;
        if (sound == SoundType.STONE || sound == SoundType.DEEPSLATE || sound == SoundType.DEEPSLATE_BRICKS
                || sound == SoundType.POLISHED_DEEPSLATE || sound == SoundType.NETHER_BRICKS || sound == SoundType.TUFF
                || sound == SoundType.CALCITE || sound == SoundType.BASALT)
            return STONE;
        if (sound == SoundType.WOOD || sound == SoundType.BAMBOO_WOOD || sound == SoundType.CHERRY_WOOD
                || sound == SoundType.NETHER_WOOD || sound == SoundType.BAMBOO)
            return WOOD;
        if (sound == SoundType.GLASS)
            return GLASS;
        return SOFT;
    }

    public static boolean brittle(BlockState state) {
        return BRITTLE.contains(state.getBlock().builtInRegistryHolder().key().location());
    }

    public static double tensile(BlockState state) {
        if (state.getBlock().builtInRegistryHolder().key().location().equals(REINFORCED_GLASS))
            return 1.20e8;
        return of(state)[1];
    }

    public static double[] grain(BlockState state) {
        return DIRECTIONAL_MODIFIERS.get(state.getBlock().builtInRegistryHolder().key().location());
    }

    public static double directionalFactor(BlockState state, int dx, int dy, int dz) {
        if (!state.hasProperty(BlockStateProperties.AXIS))
            return 1.0;
        double[] mods = DIRECTIONAL_MODIFIERS.get(state.getBlock().builtInRegistryHolder().key().location());
        if (mods == null)
            return 1.0;
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
        double along = switch (axis) {
            case X -> Math.abs(dx);
            case Y -> Math.abs(dy);
            case Z -> Math.abs(dz);
        };
        double weight = along / Math.sqrt(dx * dx + dy * dy + dz * dz);
        return mods[0] * weight + mods[1] * (1.0 - weight);
    }

    public static void clearCache() {
        RESOLVED.clear();
    }
}
