package com.maxenonyme.createsubmarine.submarine.config;

import net.neoforged.neoforge.common.ModConfigSpec;
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
        public static ModConfigSpec.BooleanValue WELCOME_SCREEN_SEEN;
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

                COMMON_SPEC = common.build();

                ModConfigSpec.Builder server = new ModConfigSpec.Builder();

                server.push("gameplay");
                DISABLE_IMPLOSION = server
                                .comment("Disable all hull implosion damage from pressure.")
                                .define("disableImplosion", false);
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
                                .defineInRange("globalMaxDepthCap", 300, 1, 10000);
                MAX_DEPTH_MULTIPLIER = server
                                .comment("Multiplier on every block's effective maxWaterDepth at runtime.",
                                                "Lower = more fragile hulls, higher = tougher hulls.")
                                .defineInRange("maxDepthMultiplier", 1.0, 0.01, 100.0);
                IMPLOSION_CHANCE_MULTIPLIER = server
                                .comment("Multiplier on every block's implosionChance at runtime.",
                                                "Lower = slower cracking, higher = faster cracking.")
                                .defineInRange("implosionChanceMultiplier", 1.0, 0.0, 10.0);
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
                server.pop();

                server.push("experimental");
                ENABLE_BOAT_WATER_CULLING = server
                                .comment("Experimental: hide the ocean surface seen inside floating boats (sub-levels without an oxygen system).")
                                .define("enableBoatWaterCulling", true);
                server.pop();

                SERVER_SPEC = server.build();

                ModConfigSpec.Builder client = new ModConfigSpec.Builder();

                client.push("client");
                DISABLE_STARTUP_SCREENS = client
                                .comment("Disable all Deep Seas startup UI screens (Welcome screen and Update notifications).",
                                                "Highly recommended to set this to TRUE if you are creating a modpack to avoid annoying your players.")
                                .define("disableStartupScreens", false);
                if (!FMLEnvironment.production) {
                        WELCOME_SCREEN_SEEN = client
                                        .comment("Internal: set to true once the Deep Seas welcome screen has been acknowledged.",
                                                        "Set back to false to show the welcome screen again on the next main menu.")
                                        .define("welcomeScreenSeen", false);
                        IGNORED_UPDATE_VERSION = client
                                        .comment("Internal: stores the version string of the last update notification dismissed by the user.",
                                                        "If the online version matches this, the update screen will not be shown.")
                                        .define("ignoredUpdateVersion", "");
                } else {
                        WELCOME_SCREEN_SEEN = null;
                        IGNORED_UPDATE_VERSION = null;
                }
                client.pop();

                CLIENT_SPEC = client.build();
        }
}
