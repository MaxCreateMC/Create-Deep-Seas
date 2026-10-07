package com.maxenonyme.createsubmarine.submarine.config;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;
import net.neoforged.fml.loading.FMLEnvironment;

public class SubmarineConfig {
        public static final ModConfigSpec COMMON_SPEC;
        public static final ModConfigSpec SERVER_SPEC;
        public static final ModConfigSpec CLIENT_SPEC;
        public static final ModConfigSpec.BooleanValue DISABLE_IMPLOSION;
        public static final ModConfigSpec.IntValue OXYGEN_MAX_FILL_BLOCKS;
        public static final ModConfigSpec.IntValue GLOBAL_MAX_DEPTH_CAP;
        public static final ModConfigSpec.DoubleValue IMPLOSION_CHANCE_MULTIPLIER;
        public static final ModConfigSpec.DoubleValue MAX_DEPTH_MULTIPLIER;
        public static final ModConfigSpec.DoubleValue BALLAST_FORCE_MULTIPLIER;
        public static final ModConfigSpec.DoubleValue BALLAST_LIFT_PER_TANK;
        public static final ModConfigSpec.DoubleValue FLOATER_LIFT;
        public static final ModConfigSpec.DoubleValue BALLAST_VERTICAL_SPEED;
        public static final ModConfigSpec.DoubleValue BALLAST_TRANSFER_RATE_MULTIPLIER;
        public static final ModConfigSpec.DoubleValue WATER_THRUSTER_POWER_MULTIPLIER;
        public static final ModConfigSpec.DoubleValue SUBMARINE_PROPELLER_POWER_MULTIPLIER;
        public static final ModConfigSpec.DoubleValue PULLEY_MAX_SLIDE_SPEED;
        public static final ModConfigSpec.IntValue STEEL_CABLE_MAX_LENGTH;
        public static final ModConfigSpec.BooleanValue ENABLE_BOAT_WATER_CULLING;
        public static final ModConfigSpec.BooleanValue ENABLE_DEEPER_OCEANS;
        public static final ModConfigSpec.IntValue DEEPER_OCEANS_DEPTH;
        public static final ModConfigSpec.BooleanValue DISABLE_STARTUP_SCREENS;
        public static final ModConfigSpec.BooleanValue VEIL_LIGHTS;
        public static final ModConfigSpec.BooleanValue PHOTOSENSITIVE_MODE;
        public static final ModConfigSpec.BooleanValue ALARM_STEADY_LIGHT;
        public static final ModConfigSpec.BooleanValue PROGRESSIVE_FLOODING;
        public static final ModConfigSpec.IntValue IMPLOSION_DEPTH;
        public static final ModConfigSpec.EnumValue<PressureModel> PRESSURE_MODEL;
        public static final ModConfigSpec.IntValue SHAPE_REFERENCE_SPAN;
        public static final ModConfigSpec.DoubleValue SHAPE_FACTOR_MIN;
        public static final ModConfigSpec.DoubleValue SHAPE_FACTOR_MAX;
        public static final ModConfigSpec.IntValue SHAPE_MAX_BLOCKS;
        public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> LIQUID_DENSITIES;
        public static final ModConfigSpec.BooleanValue LAVA_BURNS_HULL;
        public static final ModConfigSpec.BooleanValue EXPERIMENTAL_ABYSS_WORLDGEN;

        public enum PressureModel implements TranslatableEnum {
                CLASSIC(ChatFormatting.GREEN), HYBRID(ChatFormatting.AQUA);

                private final ChatFormatting color;

                PressureModel(ChatFormatting color) {
                        this.color = color;
                }

                @Override
                public Component getTranslatedName() {
                        return Component.translatable("create_submarine.configuration.pressureModel." + name().toLowerCase())
                                        .withStyle(color);
                }
        }
        public static ModConfigSpec.ConfigValue<String> IGNORED_UPDATE_VERSION;

        static {
                ModConfigSpec.Builder common = new ModConfigSpec.Builder();

                common.push("gameplay");
                ENABLE_DEEPER_OCEANS = common
                                .comment("Deepen the ocean floor below vanilla.",
                                                "Off = vanilla ocean depth. Set the amount with deeperOceansDepth.",
                                                "Requires the 'Lithostitched' mod to be installed.")
                                .define("enableDeeperOceans", false);
                DEEPER_OCEANS_DEPTH = common
                                .comment("How many blocks deeper to push the ocean floor when enableDeeperOceans is on.",
                                                "WARNING: large values generate and render far more terrain below the sea floor",
                                                "and can badly hurt world-generation and rendering performance. Raise it carefully.")
                                .defineInRange("deeperOceansDepth", 10, 1, 256);
                common.pop();

                common.push("experimental");
                EXPERIMENTAL_ABYSS_WORLDGEN = common
                                .comment("Experimental: replace the Abyss terrain with the tectonic seafloor (plates, trenches, ridges, three biomes).",
                                                "Only new Abyss chunks change. The Abyss height range changes too, so test it on a new world.")
                                .define("experimentalAbyssWorldgen", false);
                common.pop();

                COMMON_SPEC = common.build();

                ModConfigSpec.Builder server = new ModConfigSpec.Builder();

                server.push("gameplay");
                DISABLE_IMPLOSION = server
                                .comment("Disable all hull implosion damage from pressure.")
                                .define("disableImplosion", false);
                IMPLOSION_DEPTH = server
                                .comment("Depth below the surface, in blocks, past which a hull block giving way to the pressure implodes the whole submarine.",
                                                "Above it, the block only bursts and the room floods through the hole.")
                                .defineInRange("implosionDepth", 120, 1, 2048);
                OXYGEN_MAX_FILL_BLOCKS = server
                                .comment("Maximum size, in blocks, of a creation that oxygen diffusers can scan and fill with breathable air.",
                                                "Raise this if air pockets stop working on very large ships.",
                                                "WARNING: large values make the air scan use more memory and take longer to finish.")
                                .defineInRange("oxygenMaxFillBlocks", 500_000, 1_000, 10_000_000);
                server.pop();

                server.push("hullStrength");
                GLOBAL_MAX_DEPTH_CAP = server
                                .comment("Cap applied to maxWaterDepth of all non-create_submarine blocks.",
                                                "Per-block values are stored in config/submarine_hull.json.")
                                .defineInRange("globalMaxDepthCap", 400, 1, 10000);
                MAX_DEPTH_MULTIPLIER = server
                                .comment("Multiplier on every block's effective maxWaterDepth at runtime.",
                                                "Lower = more fragile hulls, higher = tougher hulls.")
                                .defineInRange("maxDepthMultiplier", 1.0, 0.01, 100.0);
                IMPLOSION_CHANCE_MULTIPLIER = server
                                .comment("Multiplier on every block's implosionChance at runtime.",
                                                "Lower = slower cracking, higher = faster cracking.")
                                .defineInRange("implosionChanceMultiplier", 1.0, 0.0, 10.0);
                PRESSURE_MODEL = server
                                .comment("CLASSIC: each block holds down to its own maxWaterDepth, whatever the shape of the hull.",
                                                "HYBRID: that depth is scaled by the shape of the hull. Wide flat walls hold less,",
                                                "small panels, thick walls, ribs and rounded hulls hold more.")
                                .defineEnum("pressureModel", PressureModel.HYBRID);
                SHAPE_REFERENCE_SPAN = server
                                .comment("HYBRID only: free width, in blocks, of a single-thickness wall that keeps exactly its block's maxWaterDepth.",
                                                "Wider panels hold less, narrower or thicker ones hold more.")
                                .defineInRange("shapeReferenceSpan", 7, 2, 64);
                SHAPE_FACTOR_MIN = server
                                .comment("HYBRID only: lowest multiplier the shape of the hull can put on a block's maxWaterDepth.")
                                .defineInRange("shapeFactorMin", 0.5, 0.1, 1.0);
                SHAPE_FACTOR_MAX = server
                                .comment("HYBRID only: highest multiplier the shape of the hull can put on a block's maxWaterDepth.")
                                .defineInRange("shapeFactorMax", 2.0, 1.0, 5.0);
                SHAPE_MAX_BLOCKS = server
                                .comment("HYBRID only: ships with more solid blocks than this skip the shape analysis and use CLASSIC depths.",
                                                "WARNING: the analysis of very large ships takes more memory and time.")
                                .defineInRange("shapeMaxBlocks", 200_000, 1_000, 10_000_000);
                LIQUID_DENSITIES = server
                                .comment("Density of each liquid compared to water, as \"fluid=density\" or \"#tag=density\".",
                                                "A denser liquid presses harder: at the same depth, lava (3.0) cracks a hull three times sooner than water.",
                                                "Liquids not listed count as water (1.0).")
                                .defineListAllowEmpty("liquidDensities", java.util.List.of("#minecraft:lava=3.0"),
                                                () -> "#minecraft:lava=3.0", o -> o instanceof String str && str.contains("="));
                LAVA_BURNS_HULL = server
                                .comment("Flammable hull blocks (wood, wool...) touching lava catch fire.")
                                .define("lavaBurnsHull", true);
                server.pop();

                server.push("mechanics");
                BALLAST_FORCE_MULTIPLIER = server
                                .comment("Multiplier on the vertical force ballast tanks apply.",
                                                "Lower = slower dive/ascend, higher = snappier.")
                                .defineInRange("ballastForceMultiplier", 1.0, 0.1, 10.0);
                BALLAST_LIFT_PER_TANK = server
                                .comment("Submarine mass each ballast tank can drive up or down (Sable mass units, roughly 1 per block).",
                                                "Total lift scales with the number of tanks: a heavy submarine needs more of them.")
                                .defineInRange("ballastLiftPerTank", 75.0, 1.0, 100000.0);
                FLOATER_LIFT = server
                                .comment("Mass each floater can push toward the surface (Sable mass units, roughly 1 per block).",
                                                "Total lift scales with the number of floaters.")
                                .defineInRange("floaterLift", 25.0, 1.0, 100000.0);
                BALLAST_VERTICAL_SPEED = server
                                .comment("Maximum vertical speed (blocks per tick) ballast tanks drive the submarine at when diving or ascending.",
                                                "Higher = faster dive and climb. The old fixed value was 2.0.")
                                .defineInRange("ballastVerticalSpeed", 2.0, 0.1, 20.0);
                BALLAST_TRANSFER_RATE_MULTIPLIER = server
                                .comment("Multiplier on the ballast vent fill/drain transfer rate.",
                                                "Lower = slower filling/emptying, higher = faster.")
                                .defineInRange("ballastTransferRateMultiplier", 2.0, 0.1, 20.0);
                WATER_THRUSTER_POWER_MULTIPLIER = server
                                .comment("Multiplier on water thruster thrust output.",
                                                "Lower = weaker propulsion, higher = stronger.")
                                .defineInRange("waterThrusterPowerMultiplier", 6.0, 0.1, 50.0);
                SUBMARINE_PROPELLER_POWER_MULTIPLIER = server
                                .comment("Multiplier on Submarine Propeller thrust and airflow output.",
                                                "Lower = weaker propulsion, higher = stronger.")
                                .defineInRange("submarinePropellerPowerMultiplier", 3.0, 0.1, 50.0);
                PULLEY_MAX_SLIDE_SPEED = server
                                .comment("Maximum sliding speed of a pulley along a steel cable (blocks/s).",
                                                "Above this speed the pulley starts overheating.")
                                .defineInRange("pulleyMaxSlideSpeed", 24.0, 1.0, 200.0);
                STEEL_CABLE_MAX_LENGTH = server
                                .comment("Maximum length of a steel cable in blocks (distance between its two attachment points).",
                                                "Very long cables can cause server lag; lower this on servers.")
                                .defineInRange("steelCableMaxLength", 1000, 1, 1000000);
                ALARM_STEADY_LIGHT = server
                                .comment("Industrial Alarms keep their lamp and light on while they sound instead of flashing.",
                                                "Turn this on if a photosensitive or epileptic player is on the server. Applies to every player.")
                                .define("alarmSteadyLight", false);
                server.pop();

                server.push("experimental");
                ENABLE_BOAT_WATER_CULLING = server
                                .comment("Experimental: hide the ocean surface seen inside floating boats (sub-levels without an oxygen system).")
                                .define("enableBoatWaterCulling", true);
                PROGRESSIVE_FLOODING = server
                                .comment("Experimental: a breach fills the ship block by block with real water that weighs it down.",
                                                "When off, a breach below the waterline lets the sea into the whole room at once.")
                                .define("progressiveFlooding", false);
                server.pop();

                SERVER_SPEC = server.build();

                ModConfigSpec.Builder client = new ModConfigSpec.Builder();

                client.push("client");
                DISABLE_STARTUP_SCREENS = client
                                .comment("Disable all Deep Seas startup UI screens (Welcome screen and Update notifications).",
                                                "Highly recommended to set this to TRUE if you are creating a modpack to avoid annoying your players.")
                                .define("disableStartupScreens", false);
                VEIL_LIGHTS = client
                                .comment("Coloured moving lights on the Sonar, the Industrial Alarm and the Submarine Staff (Veil).",
                                                "Turn off to light every block and item with plain Minecraft light only.",
                                                "Always off when Iris is installed: Veil cannot draw them next to Iris, even with shaders turned off.")
                                .define("veilLights", true);
                PHOTOSENSITIVE_MODE = client
                                .comment("For photosensitive or epileptic players: the Industrial Alarm no longer throws its coloured flashing Veil light,",
                                                "only normal Minecraft light. Other Veil lights are not affected.")
                                .define("photosensitiveMode", false);
                if (!FMLEnvironment.production) {
                        IGNORED_UPDATE_VERSION = client
                                        .comment("Internal: stores the version string of the last update notification dismissed by the user.",
                                                        "If the online version matches this, the update screen will not be shown.")
                                        .define("ignoredUpdateVersion", "");
                } else {
                        IGNORED_UPDATE_VERSION = null;
                }
                client.pop();

                CLIENT_SPEC = client.build();
        }

        public static boolean progressiveFlooding() {
                return SERVER_SPEC.isLoaded() && PROGRESSIVE_FLOODING.get();
        }

        public static boolean hybridPressure() {
                return SERVER_SPEC.isLoaded() && PRESSURE_MODEL.get() == PressureModel.HYBRID;
        }
}
