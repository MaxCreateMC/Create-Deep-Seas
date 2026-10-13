package com.maxenonyme.createsubmarine.submarine.config;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;
import net.neoforged.fml.loading.FMLEnvironment;
import java.util.List;

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
        public static final ModConfigSpec.BooleanValue ENABLE_PERMANENT_WATER_CULLING_TEST;
        public static final ModConfigSpec.BooleanValue ENABLE_DEEPER_OCEANS;
        public static final ModConfigSpec.IntValue DEEPER_OCEANS_DEPTH;
        public static final ModConfigSpec.BooleanValue DISABLE_STARTUP_SCREENS;
        public static ModConfigSpec.BooleanValue WELCOME_SCREEN_SEEN;
        public static final ModConfigSpec.BooleanValue VEIL_LIGHTS;
        public static final ModConfigSpec.BooleanValue PHOTOSENSITIVE_MODE;
        public static final ModConfigSpec.BooleanValue PRESSURE_EFFECTS;
        public static final ModConfigSpec.BooleanValue ALARM_STEADY_LIGHT;
        public static final ModConfigSpec.BooleanValue PROGRESSIVE_FLOODING;
        public static final ModConfigSpec.IntValue IMPLOSION_DEPTH;
        public static final ModConfigSpec.EnumValue<PressureModel> PRESSURE_MODEL;
        public static final ModConfigSpec.IntValue SHAPE_REFERENCE_SPAN;
        public static final ModConfigSpec.DoubleValue SHAPE_FACTOR_MIN;
        public static final ModConfigSpec.DoubleValue SHAPE_FACTOR_MAX;
        public static final ModConfigSpec.IntValue SHAPE_MAX_BLOCKS;
        public static final ModConfigSpec.DoubleValue REALISTIC_WALL_THICKNESS;
        public static final ModConfigSpec.IntValue REALISTIC_MAX_BLOCKS;
        public static final ModConfigSpec.ConfigValue<List<? extends String>> LIQUID_DENSITIES;
        public static final ModConfigSpec.BooleanValue LAVA_BURNS_HULL;
        public static final ModConfigSpec.BooleanValue EXPERIMENTAL_ABYSS_WORLDGEN;
        // Cogfly lattice solver keys (kept through the experimental merge).
        public static final ModConfigSpec.DoubleValue COHERENCE_THRESHOLD_ANALYTICAL;
        public static final ModConfigSpec.DoubleValue COHERENCE_THRESHOLD_CORRECTED;
        public static final ModConfigSpec.DoubleValue KERNEL_RADIUS;
        public static final ModConfigSpec.BooleanValue COPYCAT_INHERIT_MATERIAL;
        public static final ModConfigSpec.BooleanValue USE_ORIENTATION_PRESSURE;
        public static final ModConfigSpec.DoubleValue MOON_POOL_PRESSURE_FACTOR;
        public static final ModConfigSpec.DoubleValue ROUGHNESS_PENALTY;
        public static final ModConfigSpec.IntValue MIN_APPROXIMATION_RADIUS;
        public static final ModConfigSpec.DoubleValue GAUSSIAN_MAX_ERROR;
        public static final ModConfigSpec.DoubleValue WATER_DENSITY_GRAVITY;
        public static final ModConfigSpec.DoubleValue POISSON_RATIO;
        public static final ModConfigSpec.DoubleValue TIKHONOV_ALPHA_FRACTION;
        public static final ModConfigSpec.DoubleValue LATTICE_MODULUS_SCALE;
        public static final ModConfigSpec.DoubleValue LATTICE_SHEAR_SCALE;
        public static final ModConfigSpec.DoubleValue LATTICE_BEND_SCALE;
        public static final ModConfigSpec.DoubleValue SOLVER_TIME_BUDGET_MS;
        public static final ModConfigSpec.DoubleValue SOLVER_INITIAL_BUDGET_MS;
        public static final ModConfigSpec.DoubleValue STRESS_FORCE_MAX;

        public enum PressureModel implements TranslatableEnum {
                CLASSIC(ChatFormatting.GREEN), HYBRID(ChatFormatting.AQUA), REALISTIC(ChatFormatting.LIGHT_PURPLE);

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
                                                "Above it, the block only bursts and the room floods through the hole.",
                                                "Past it, a submarine more than 30 blocks beyond its hull limit also seizes its pump controllers and shakes its crew.")
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
                                                "small panels, thick walls, ribs and rounded hulls hold more.",
                                                "REALISTIC: a structural solver computes how much every block of the hull really bends under the water,",
                                                "from its material (stiffness, strength, wood grain) and the shape of the whole ship.")
                                .defineEnum("pressureModel", PressureModel.REALISTIC);
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
                REALISTIC_WALL_THICKNESS = server
                                .comment("REALISTIC only: how thick, in metres, the steel or wood plate a single block stands for.",
                                                "Depths grow with the square of it: 1.0 is raw physics for walls one metre thick,",
                                                "0.46 suits an Abyss around 700 blocks deep.")
                                .defineInRange("realisticWallThickness", 0.46, 0.05, 3.0);
                REALISTIC_MAX_BLOCKS = server
                                .comment("REALISTIC only: ships with more solid blocks than this fall back to CLASSIC depths.",
                                                "The solver runs on its own threads; bigger ships take longer to refresh after an edit.")
                                .defineInRange("realisticMaxBlocks", 30_000, 500, 1_000_000);
                LIQUID_DENSITIES = server
                                .comment("Density of each liquid compared to water, as \"fluid=density\" or \"#tag=density\".",
                                                "A denser liquid presses harder: at the same depth, lava (3.0) cracks a hull three times sooner than water.",
                                                "Liquids not listed count as water (1.0).")
                                .defineListAllowEmpty("liquidDensities", List.of("#minecraft:lava=3.0"),
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

                server.push("latticeSolver");
                COHERENCE_THRESHOLD_ANALYTICAL = server
                                .comment("Coherence score above which a surface is treated as smooth analytical.",
                                                "Higher = only very smooth surfaces get analytical treatment.",
                                                "Range: 0.0-1.0, Default: 0.85")
                                .defineInRange("coherenceThresholdAnalytical", 0.85, 0.0, 1.0);
                COHERENCE_THRESHOLD_CORRECTED = server
                                .comment("Coherence score above which a surface gets corrected smoothed normals.",
                                                "Below this, full lattice solver is used without normal smoothing.",
                                                "Range: 0.0-1.0, Default: 0.60")
                                .defineInRange("coherenceThresholdCorrected", 0.60, 0.0, 1.0);
                KERNEL_RADIUS = server
                                .comment("Radius (in blocks) of the kernel used for normal smoothing.",
                                                "Larger = more smoothing, but blurs fine details.",
                                                "Range: 0.5-3.0, Default: 1.5")
                                .defineInRange("kernelRadius", 1.5, 0.5, 3.0);
                ROUGHNESS_PENALTY = server
                                .comment("Additional stress penalty applied to corrected surfaces.",
                                                "Multiplied by (0.85 - coherence) / (0.85 - 0.60).",
                                                "Range: 0.0-1.0, Default: 0.05")
                                .defineInRange("roughnessPenalty", 0.05, 0.0, 1.0);
                MIN_APPROXIMATION_RADIUS = server
                                .comment("Minimum curve radius (in blocks) for analytical approximation.",
                                                "Below this, full lattice solver is always used regardless of coherence.",
                                                "Range: 1-50, Default: 2")
                                .defineInRange("minApproximationRadius", 2, 1, 50);
                GAUSSIAN_MAX_ERROR = server
                                .comment("Maximum coherence error allowed for Gaussian (kernel-smoothed) face counts.",
                                                "When the kernel-smoothed normal error exceeds this threshold,",
                                                "raw (non-smoothed) face counts are used instead.",
                                                "Range: 0.01-0.50, Default: 0.05")
                                .defineInRange("gaussianMaxError", 0.05, 0.01, 0.50);
                COPYCAT_INHERIT_MATERIAL = server
                                .comment("Copycat blocks inherit the Young's modulus and yield strength",
                                                "of the block they are mimicking (e.g., a copycat slab copying iron",
                                                "gets iron's E and yield, with slab geometry).",
                                                "Requires Copycats+ mod to be installed; silently falls back to defaults if not.")
                                .define("copycatInheritMaterial", true);
                USE_ORIENTATION_PRESSURE = server
                                .comment("Account for ship orientation when computing hydrostatic pressure gradient.",
                                                "When enabled, a tilted submarine gets correct pressure distribution",
                                                "with the deeper end experiencing higher pressure.",
                                                "Disable for legacy behavior (bounding-box Y gradient only).")
                                .define("useOrientationPressure", true);
                MOON_POOL_PRESSURE_FACTOR = server
                                .comment("Fraction of external hydrostatic pressure applied to moon pool walls",
                                                "from internal water column. 0 = no moon pool effect, 1 = full effect.",
                                                "Range: 0.0-1.0, Default: 0.8")
                                .defineInRange("moonPoolPressureFactor", 0.8, 0.0, 1.0);
                WATER_DENSITY_GRAVITY = server
                                .comment("Product of water density and gravitational acceleration.",
                                                "Controls how quickly hydrostatic pressure builds with depth.",
                                                "Default: 10000.0 (1000 kg/m3 x 10 m/s2)")
                                .defineInRange("waterDensityGravity", 10000.0, 100.0, 100000.0);
                POISSON_RATIO = server
                                .comment("Poisson's ratio for hull materials.",
                                                "Affects the relationship between Young's modulus and shear modulus.",
                                                "Default 0.3 matches the lattice's intrinsic Poisson ratio (~0.297).",
                                                "Range: 0.0-0.5, Default: 0.3")
                                .defineInRange("poissonRatio", 0.3, 0.0, 0.49);
                LATTICE_MODULUS_SCALE = server
                                .comment("Axial stiffness scale of the block lattice.",
                                                "The raw lattice is ~2.4x too stiff relative to the modulus used",
                                                "for stress recovery. The default 0.419 is calibrated so a",
                                                "uniform hydrostatic load reproduces sigma = -p.",
                                                "Range: 0.05-1.0, Default: 0.419")
                                .defineInRange("latticeModulusScale", 0.419, 0.05, 1.0);
                LATTICE_SHEAR_SCALE = server
                                .comment("Transverse spring scale of the block lattice.",
                                                "A 26-link central-force lattice already gets its shear stiffness from",
                                                "the 45-degree links; the default 0.1 avoids double-counting it.",
                                                "Range: 0.01-2.0, Default: 0.1")
                                .defineInRange("latticeShearScale", 0.1, 0.01, 2.0);
                LATTICE_BEND_SCALE = server
                                .comment("Rotational spring scale of the block lattice.",
                                                "The rotational springs carry the dimensionally-correct k*L^2/12",
                                                "bending stiffness; the lattice reproduces the Euler-Bernoulli",
                                                "height scaling but is ~3x stiffer in absolute bending.",
                                                "Range: 0.1-10.0, Default: 1.0")
                                .defineInRange("latticeBendScale", 1.0, 0.1, 10.0);
                SOLVER_TIME_BUDGET_MS = server
                                .comment("Wall-clock budget in milliseconds for one incremental stress",
                                                "re-solve (the path that runs at 20 Hz).",
                                                "Range: 1-50, Default: 8")
                                .defineInRange("solverTimeBudgetMs", 8.0, 1.0, 50.0);
                SOLVER_INITIAL_BUDGET_MS = server
                                .comment("Wall-clock budget in milliseconds for the first solve of a newly",
                                                "built structure. Cold solves converge more slowly than warm",
                                                "re-solves, so this one may spend longer.",
                                                "Range: 5-500, Default: 150")
                                .defineInRange("solverInitialBudgetMs", 150.0, 5.0, 500.0);
                TIKHONOV_ALPHA_FRACTION = server
                                .comment("Tikhonov regularization fraction relative to Young's modulus.",
                                                "Added to stiffness matrix diagonal to suppress rigid-body modes.",
                                                "Range: 1e-6 to 0.1, Default: 1e-6 (0.0001% of E)")
                                .defineInRange("tikhonovAlphaFraction", 1e-6, 1e-6, 0.1);
                STRESS_FORCE_MAX = server
                                .comment("Maximum force applied per exposed hull face",
                                                "at full stress fraction (waterDepth >> crushDepth).",
                                                "Range: 1.0-100000.0, Default: 2000.0")
                                .defineInRange("stressForceMax", 2000.0, 1.0, 100000.0);
                server.pop();

                server.push("experimental");
                ENABLE_BOAT_WATER_CULLING = server
                                .comment("Experimental: hide the ocean surface seen inside floating boats (sub-levels without an oxygen system).")
                                .define("enableBoatWaterCulling", true);
                ENABLE_PERMANENT_WATER_CULLING_TEST = server
                                .comment("Enable the experimental Permanent Water Culling test for submarines and boats")
                                .define("enablePermanentWaterCullingTest", false);
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
                PRESSURE_EFFECTS = client
                                .comment("Tunnel vision and a racing heartbeat while the hull is under too much pressure,",
                                                "growing until the implosion.")
                                .define("pressureEffects", true);
                if (!FMLEnvironment.production) {
                        IGNORED_UPDATE_VERSION = client
                                        .comment("Internal: stores the version string of the last update notification dismissed by the user.",
                                                        "If the online version matches this, the update screen will not be shown.")
                                        .define("ignoredUpdateVersion", "");
                        WELCOME_SCREEN_SEEN = client
                                        .comment("Internal: set to true once the Deep Seas welcome screen has been acknowledged.",
                                                        "Set back to false to show the welcome screen again on the next main menu.")
                                        .define("welcomeScreenSeen", false);
                } else {
                        IGNORED_UPDATE_VERSION = null;
                        WELCOME_SCREEN_SEEN = null;
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

        public static boolean realisticPressure() {
                return SERVER_SPEC.isLoaded() && PRESSURE_MODEL.get() == PressureModel.REALISTIC;
        }
}
