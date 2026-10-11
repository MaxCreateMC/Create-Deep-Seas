package com.maxenonyme.createsubmarine.submarine.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class SubmarineConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue DISABLE_IMPLOSION;
    public static final ModConfigSpec.IntValue OXYGEN_MAX_FILL_BLOCKS;
    public static final ModConfigSpec.IntValue GLOBAL_MAX_DEPTH_CAP;
    public static final ModConfigSpec.DoubleValue IMPLOSION_CHANCE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MAX_DEPTH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue BALLAST_FORCE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue BALLAST_TRANSFER_RATE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WATER_THRUSTER_POWER_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SUBMARINE_PROPELLER_POWER_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue PULLEY_MAX_SLIDE_SPEED;
    public static final ModConfigSpec.IntValue STEEL_CABLE_MAX_LENGTH;
    public static final ModConfigSpec.BooleanValue ENABLE_PERMANENT_WATER_CULLING_TEST;
    public static final ModConfigSpec.BooleanValue ENABLE_ABYSS_GENERATION;
    public static final ModConfigSpec.BooleanValue ENABLE_BOAT_WATER_CULLING;
    public static final ModConfigSpec.BooleanValue ENABLE_DEEPER_OCEANS;
    public static final ModConfigSpec.IntValue DEEPER_OCEANS_DEPTH;
    public static ModConfigSpec.BooleanValue WELCOME_SCREEN_SEEN;
    public static final ModConfigSpec.BooleanValue DISABLE_STARTUP_SCREENS;
    public static ModConfigSpec.ConfigValue<String> IGNORED_UPDATE_VERSION;

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

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("gameplay");
        DISABLE_IMPLOSION = builder
                .comment("Disable all hull implosion damage from pressure.")
                .define("disableImplosion", false);
        OXYGEN_MAX_FILL_BLOCKS = builder
                .comment("Maximum size, in blocks, of a creation that oxygen diffusers can scan and fill with breathable air.",
                        "Raise this if air pockets stop working on very large ships.",
                        "WARNING: large values make the air scan use more memory and take longer to finish.")
                .defineInRange("oxygenMaxFillBlocks", 500_000, 1_000, 10_000_000);
        builder.pop();

        builder.push("shapeClassifier");
        COHERENCE_THRESHOLD_ANALYTICAL = builder
                .comment("Coherence score above which a surface is treated as smooth analytical.",
                        "Higher = only very smooth surfaces get analytical treatment.",
                        "Range: 0.0-1.0, Default: 0.85")
                .defineInRange("coherenceThresholdAnalytical", 0.85, 0.0, 1.0);
        COHERENCE_THRESHOLD_CORRECTED = builder
                .comment("Coherence score above which a surface gets corrected smoothed normals.",
                        "Below this, full lattice solver is used without normal smoothing.",
                        "Range: 0.0-1.0, Default: 0.60")
                .defineInRange("coherenceThresholdCorrected", 0.60, 0.0, 1.0);
        KERNEL_RADIUS = builder
                .comment("Radius (in blocks) of the kernel used for normal smoothing.",
                        "Larger = more smoothing, but blurs fine details.",
                        "Range: 0.5-3.0, Default: 1.5")
                .defineInRange("kernelRadius", 1.5, 0.5, 3.0);
        ROUGHNESS_PENALTY = builder
                .comment("Additional stress penalty applied to corrected surfaces.",
                        "Multiplied by (0.85 - coherence) / (0.85 - 0.60).",
                        "Range: 0.0-1.0, Default: 0.05")
                .defineInRange("roughnessPenalty", 0.05, 0.0, 1.0);
        MIN_APPROXIMATION_RADIUS = builder
                .comment("Minimum curve radius (in blocks) for analytical approximation.",
                        "Below this, full lattice solver is always used regardless of coherence.",
                        "Range: 1-50, Default: 2")
                .defineInRange("minApproximationRadius", 2, 1, 50);
        GAUSSIAN_MAX_ERROR = builder
                .comment("Maximum coherence error allowed for Gaussian (kernel-smoothed) face counts.",
                        "When the kernel-smoothed normal error exceeds this threshold,",
                        "raw (non-smoothed) face counts are used instead.",
                        "Range: 0.01-0.50, Default: 0.05")
                .defineInRange("gaussianMaxError", 0.05, 0.01, 0.50);
        builder.pop();

        builder.push("hullStrength");
        GLOBAL_MAX_DEPTH_CAP = builder
                .comment("Cap applied to maxWaterDepth of all non-create_submarine blocks.",
                        "Per-block values are stored in config/submarine_hull.json.")
                .defineInRange("globalMaxDepthCap", 100, 1, 10000);
        MAX_DEPTH_MULTIPLIER = builder
                .comment("Multiplier on every block's effective maxWaterDepth at runtime.",
                        "Lower = more fragile hulls, higher = tougher hulls.")
                .defineInRange("maxDepthMultiplier", 1.0, 0.01, 100.0);
        IMPLOSION_CHANCE_MULTIPLIER = builder
                .comment("Multiplier on every block's implosionChance at runtime.",
                        "Lower = slower cracking, higher = faster cracking.")
                .defineInRange("implosionChanceMultiplier", 1.0, 0.0, 10.0);
        builder.pop();

        builder.push("copycats");
        COPYCAT_INHERIT_MATERIAL = builder
                .comment("Copycat blocks inherit the Young's modulus and yield strength",
                        "of the block they are mimicking (e.g., a copycat slab copying iron",
                        "gets iron's E and yield, with slab geometry).",
                        "Requires Copycats+ mod to be installed; silently falls back to defaults if not.")
                .define("copycatInheritMaterial", true);
        builder.pop();

        builder.push("physics");
        USE_ORIENTATION_PRESSURE = builder
                .comment("Account for ship orientation when computing hydrostatic pressure gradient.",
                        "When enabled, a tilted submarine gets correct pressure distribution",
                        "with the deeper end experiencing higher pressure.",
                        "Disable for legacy behavior (bounding-box Y gradient only).")
                .define("useOrientationPressure", true);
        MOON_POOL_PRESSURE_FACTOR = builder
                .comment("Fraction of external hydrostatic pressure applied to moon pool walls",
                        "from internal water column. 0 = no moon pool effect, 1 = full effect.",
                        "Range: 0.0-1.0, Default: 0.8")
                .defineInRange("moonPoolPressureFactor", 0.8, 0.0, 1.0);
        WATER_DENSITY_GRAVITY = builder
                .comment("Product of water density and gravitational acceleration (ρ·g).",
                        "Controls how quickly hydrostatic pressure builds with depth.",
                        "Default: 10000.0 (1000 kg/m³ × 10 m/s²)")
                .defineInRange("waterDensityGravity", 10000.0, 100.0, 100000.0);
        POISSON_RATIO = builder
                .comment("Poisson's ratio for hull materials.",
                        "Affects the relationship between Young's modulus and shear modulus.",
                        "Default 0.3 matches the lattice's intrinsic Poisson ratio (~0.297).",
                        "Range: 0.0-0.5, Default: 0.3")
                .defineInRange("poissonRatio", 0.3, 0.0, 0.49);
        LATTICE_MODULUS_SCALE = builder
                .comment("Axial stiffness scale of the block lattice.",
                        "The raw lattice is ~2.4x too stiff relative to the modulus used",
                        "for stress recovery, so every stress was ~2.4x too low and every",
                        "crush depth ~2.4x too deep. The default 0.419 is calibrated so a",
                        "uniform hydrostatic load reproduces sigma = -p.",
                        "Range: 0.05-1.0, Default: 0.419")
                .defineInRange("latticeModulusScale", 0.419, 0.05, 1.0);
        LATTICE_SHEAR_SCALE = builder
                .comment("Transverse spring scale of the block lattice.",
                        "A 26-link central-force lattice already gets its shear stiffness from",
                        "the 45-degree links; the default 0.1 avoids double-counting it and the",
                        "spurious transverse stress that comes with it.",
                        "Range: 0.01-2.0, Default: 0.1")
                .defineInRange("latticeShearScale", 0.1, 0.01, 2.0);
        LATTICE_BEND_SCALE = builder
                .comment("Rotational spring scale of the block lattice.",
                        "The rotational springs carry the dimensionally-correct k*L^2/12",
                        "bending stiffness; the lattice reproduces the Euler-Bernoulli",
                        "height scaling but is ~3x stiffer in absolute bending.",
                        "Range: 0.1-10.0, Default: 1.0")
                .defineInRange("latticeBendScale", 1.0, 0.1, 10.0);
        SOLVER_TIME_BUDGET_MS = builder
                .comment("Wall-clock budget in milliseconds for one incremental stress",
                        "re-solve (the path that runs at 20 Hz).",
                        "",
                        "Measured cost of ONE CG iteration (26-link 6-DOF operator,",
                        "16 cores): 5 ms at 64k blocks, 7 ms at 115k, 15 ms at 216k,",
                        "35 ms at 512k. A solve cannot stop mid-iteration, so this budget",
                        "effectively buys one iteration up to ~115k blocks, which is the",
                        "size at which the solver keeps a true 20 Hz cadence:",
                        "  <= ~115k blocks : solve ~1 iter / 4 ticks + ~10 ms/tick recording",
                        "  ~216k blocks    : one solve costs a whole tick (a visible hitch",
                        "                     on 1 tick in 4, but never unbounded)",
                        "  >= ~512k blocks : one operator pass already exceeds a tick",
                        "Range: 1-50, Default: 8")
                .defineInRange("solverTimeBudgetMs", 8.0, 1.0, 50.0);
        SOLVER_INITIAL_BUDGET_MS = builder
                .comment("Wall-clock budget in milliseconds for the first solve of a newly",
                        "built structure. Cold solves converge more slowly than warm",
                        "re-solves, so this one may spend longer.",
                        "Range: 5-500, Default: 150")
                .defineInRange("solverInitialBudgetMs", 150.0, 5.0, 500.0);
        TIKHONOV_ALPHA_FRACTION = builder
                .comment("Tikhonov regularization fraction relative to Young's modulus.",
                        "Added to stiffness matrix diagonal to suppress rigid-body modes.",
                        "Higher = more regularization (stiffer, fewer CG iterations),",
                        "Lower = more accurate but slower convergence.",
                        "Range: 1e-6 to 0.1, Default: 1e-6 (0.0001% of E)")
                .defineInRange("tikhonovAlphaFraction", 1e-6, 1e-6, 0.1);
        STRESS_FORCE_MAX = builder
                .comment("Maximum force (in arbitrary units) applied per exposed hull face",
                        "at full stress fraction (waterDepth >> crushDepth).",
                        "This is the force pushed into Sable's QueuedForceGroup for force arrows.",
                        "Range: 1.0-100000.0, Default: 2000.0")
                .defineInRange("stressForceMax", 2000.0, 1.0, 100000.0);
        builder.pop();

        builder.push("propulsion");
        BALLAST_FORCE_MULTIPLIER = builder
                .comment("Multiplier on the vertical force ballast tanks apply.",
                        "Lower = slower dive/ascend, higher = snappier.")
                .defineInRange("ballastForceMultiplier", 1.0, 0.1, 10.0);
        BALLAST_TRANSFER_RATE_MULTIPLIER = builder
                .comment("Multiplier on the ballast vent fill/drain transfer rate.",
                        "Lower = slower filling/emptying, higher = faster.")
                .defineInRange("ballastTransferRateMultiplier", 2.0, 0.1, 20.0);
        WATER_THRUSTER_POWER_MULTIPLIER = builder
                .comment("Multiplier on water thruster thrust output.",
                        "Lower = weaker propulsion, higher = stronger.")
                .defineInRange("waterThrusterPowerMultiplier", 6.0, 0.1, 50.0);
        SUBMARINE_PROPELLER_POWER_MULTIPLIER = builder
                .comment("Multiplier on Submarine Propeller thrust and airflow output.",
                        "Lower = weaker propulsion, higher = stronger.")
                .defineInRange("submarinePropellerPowerMultiplier", 3.0, 0.1, 50.0);
        PULLEY_MAX_SLIDE_SPEED = builder
                .comment("Maximum sliding speed of a pulley along a steel cable (blocks/s).",
                        "Above this speed the pulley starts overheating.")
                .defineInRange("pulleyMaxSlideSpeed", 24.0, 1.0, 200.0);
        STEEL_CABLE_MAX_LENGTH = builder
                .comment("Maximum length of a steel cable in blocks (distance between its two attachment points).",
                        "Very long cables can cause server lag; lower this on servers.")
                .defineInRange("steelCableMaxLength", 1000, 1, 1000000);
        builder.pop();

        builder.push("experimental");
        ENABLE_PERMANENT_WATER_CULLING_TEST = builder
                .comment("Enable the experimental Permanent Water Culling test for submarines and boats")
                .define("enablePermanentWaterCullingTest", false);
        ENABLE_ABYSS_GENERATION = builder
                .comment("Generate Abyss biome pockets and the deeper ocean trenches that go with them.",
                        "Off = vanilla ocean depth, no Abyss biome.")
                .define("enableAbyssGeneration", true);
        ENABLE_BOAT_WATER_CULLING = builder
                .comment("Experimental: hide the ocean surface seen inside floating boats (sub-levels without an oxygen system).")
                .define("enableBoatWaterCulling", false);
        ENABLE_DEEPER_OCEANS = builder
                .comment("Deepen the ocean floor below vanilla.",
                        "Off = vanilla ocean depth. Set the amount with deeperOceansDepth.")
                .define("enableDeeperOceans", false);
        DEEPER_OCEANS_DEPTH = builder
                .comment("How many blocks deeper to push the ocean floor when enableDeeperOceans is on.",
                        "WARNING: large values generate and render far more terrain below the sea floor",
                        "and can badly hurt world-generation and rendering performance. Raise it carefully.")
                .defineInRange("deeperOceansDepth", 10, 1, 256);
        builder.pop();

        builder.push("client");
        DISABLE_STARTUP_SCREENS = builder
                .comment("Disable all Deep Seas startup UI screens (Welcome screen and Update notifications).",
                        "Highly recommended to set this to TRUE if you are creating a modpack to avoid annoying your players.")
                .define("disableStartupScreens", false);
        if (!net.neoforged.fml.loading.FMLEnvironment.production) {
                WELCOME_SCREEN_SEEN = builder
                                .comment("Internal: set to true once the Deep Seas welcome screen has been acknowledged.",
                                                "Set back to false to show the welcome screen again on the next main menu.")
                                .define("welcomeScreenSeen", false);
                IGNORED_UPDATE_VERSION = builder
                                .comment("Internal: stores the version string of the last update notification dismissed by the user.",
                                                "If the online version matches this, the update screen will not be shown.")
                                .define("ignoredUpdateVersion", "");
        } else {
                WELCOME_SCREEN_SEEN = null;
                IGNORED_UPDATE_VERSION = null;
        }
        builder.pop();

        SPEC = builder.build();
    }
}
