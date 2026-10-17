package com.maxenonyme.vortexengine;

import net.neoforged.neoforge.common.ModConfigSpec;

public class VortexConfig {

    public enum LightStyle {
        VOLUMES, FLOOD, VANILLA, HYBRID, PIXEL
    }

    public enum HaloStyle {
        CORE, GLOW_FOG, NONE
    }

    public enum WaterStyle {
        NONE, SUN_RAYS, ALL_RAYS
    }

    public enum MistStyle {
        PUFFS, VOXEL
    }

    public enum MotesStyle {
        NONE, PLANKTON, SNOW
    }

    public enum WakeStyle {
        NONE, GLOW
    }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue LIGHTS;
    public static final ModConfigSpec.IntValue LIGHT_DISTANCE;
    public static final ModConfigSpec.IntValue MAX_LIGHTS;
    public static final ModConfigSpec.BooleanValue PIXEL_LIGHTING;
    public static final ModConfigSpec.BooleanValue SHADOWS;
    public static final ModConfigSpec.BooleanValue HAZE;
    public static final ModConfigSpec.BooleanValue AUTO_GLOW;
    public static final ModConfigSpec.BooleanValue BLOOM;
    public static final ModConfigSpec.BooleanValue MIST;
    public static final ModConfigSpec.IntValue MAX_PUFFS;
    public static final ModConfigSpec.EnumValue<LightStyle> LIGHT_STYLE;
    public static final ModConfigSpec.EnumValue<HaloStyle> HALO_STYLE;
    public static final ModConfigSpec.EnumValue<WaterStyle> WATER_STYLE;
    public static final ModConfigSpec.EnumValue<MistStyle> MIST_STYLE;
    public static final ModConfigSpec.EnumValue<MotesStyle> MOTES_STYLE;
    public static final ModConfigSpec.EnumValue<WakeStyle> WAKE_STYLE;


// ya plusieurs mode pour le rendu, je ne sais pas encore lequel choisir


    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("vortex");
        ENABLED = builder
                .comment("Master switch of the Vortex Engine.",
                        "Turning it off disables everything the engine draws or computes.")
                .define("enabled", true);
        builder.pop();

        builder.push("lights");
        LIGHTS = builder
                .comment("Coloured light around glowing blocks, and the moving lights of the Sonar,",
                        "the Industrial Alarm and the Submarine Staff. Off while an Iris shader pack is in use.")
                .define("lights", true);

        LIGHT_DISTANCE = builder
                .comment("How far from the camera, in blocks, coloured lights are drawn.")
                .defineInRange("lightDistance", 64, 16, 256);

        MAX_LIGHTS = builder
                .comment("Most coloured lights drawn at once; the closest ones are kept.",
                        "Lower it on weak graphics cards.")
                .defineInRange("maxLights", 256, 16, 8192);

        PIXEL_LIGHTING = builder
                .comment("Light fades in steps of one texture pixel, like Minecraft's own textures.",
                        "Turn off for a smooth gradient.")
                .define("pixelLighting", true);

        SHADOWS = builder
                .comment("Solid blocks stop coloured light and cast shadows, within 64 blocks of the camera.",
                        "Costs more on the graphics card.")
                .define("shadows", true);

        HAZE = builder
                .comment("Coloured lights glow through the water around them, and the Sonar's beams show in it.")
                .define("haze", true);

        AUTO_GLOW = builder
                .comment("Every block that gives off light, or has glowing parts, glows in a colour taken",
                        "from its own texture. Definitions in assets/<namespace>/vortex/glow/*.json override it.")
                .define("autoGlow", true);

        BLOOM = builder
                .comment("Lit areas spill a soft, pixelated glow around them, out of the water too.")
                .define("bloom", true);
        builder.pop();

        builder.push("mist");
        MIST = builder
                .comment("Drifting mist and smoke volumes, lit by the world and by coloured lights.",
                        "Off while an Iris shader pack is in use.")
                .define("mist", true);

        MAX_PUFFS = builder
                .comment("Most smoke puffs alive at once, all mist volumes together.")
                .defineInRange("maxPuffs", 1500, 64, 8000);
        builder.pop();

        builder.push("styles");
        LIGHT_STYLE = builder
                .comment("How coloured light reaches the blocks around it. Switch in game with /vortex style.",
                        "VOLUMES: one sphere of light per source, with sharp shadows (the first version).",
                        "FLOOD: light spreads block by block and blends its colours, wrapping around corners",
                        "and never through walls, like Complementary or Rethinking Voxels.",
                        "VANILLA: the same spreading, but falling off one step per block like Minecraft's light.",
                        "HYBRID: FLOOD for the soft bounce, plus sharp shadows from the lights close to you,",
                        "like Rethinking Voxels.",
                        "PIXEL: every glowing block is its own light, falling off one level per block like",
                        "Minecraft's light, in texture pixels, with block shadows and a halo of glowing cubes.")
                .defineEnum("lightStyle", LightStyle.FLOOD);

        HALO_STYLE = builder
                .comment("How light shows in the water around its source.",
                        "CORE: a small halo around each source (the first version).",
                        "GLOW_FOG: the water itself glows where the light reaches, stopped by walls.",
                        "Needs the FLOOD or VANILLA light style, otherwise CORE is used.",
                        "NONE: no halo.")
                .defineEnum("haloStyle", HaloStyle.GLOW_FOG);

        WATER_STYLE = builder
                .comment("Sunlight under the water.",
                        "SUN_RAYS: shafts of sunlight and moving caustics on the sea floor, in the daytime.",
                        "ALL_RAYS: the same, and the brightest lights near you throw beams through the water,",
                        "cut by anything standing in front of them. Works with every light style.")
                .defineEnum("waterStyle", WaterStyle.ALL_RAYS);

        MIST_STYLE = builder
                .comment("How mist from /vortex mist is drawn.",
                        "PUFFS: large smoke puffs that hug the ground (the first version).",
                        "VOXEL: a volume of small cubes of fog, lit by every coloured light around.",
                        "Needs the FLOOD or VANILLA light style, otherwise PUFFS is used.")
                .defineEnum("mistStyle", MistStyle.VOXEL);

        MOTES_STYLE = builder
                .comment("Tiny specks floating in the water, one pixel wide.",
                        "PLANKTON: glowing plankton that twinkles in the colour of the light around it.",
                        "SNOW: marine snow slowly sinking, caught by sunlight and coloured lights.",
                        "Needs the FLOOD or VANILLA light style.")
                .defineEnum("motesStyle", MotesStyle.PLANKTON);

        WAKE_STYLE = builder
                .comment("Bioluminescent wakes.",
                        "GLOW: the water lights up blue behind anything swimming through it, other players,",
                        "mobs and fish, and the plankton around them glows.",
                        "Your own wake stays dark so it never hides the scene.",
                        "Needs the FLOOD, VANILLA or HYBRID light style.")
                .defineEnum("wakeStyle", WakeStyle.GLOW);
        builder.pop();

        SPEC = builder.build();
    }
}
