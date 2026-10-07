package com.maxenonyme.highseas.config;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class HighSeasConfig {
    private HighSeasConfig() {
    }

    public static double baseWind = 1.0;
    public static double gustAmplitude = 0.25;
    public static double rainBoost = 0.8;
    public static double thunderBoost = 1.6;
    public static double windOcclusion = 0.5;

    public static double sailHullSpeed = 4.0;
    public static double sailMaxSpeed = 14.0;
    public static double sailUpwindEfficiency = 0.1;
    public static double sailCanvasNeeded = 0.2;
    public static double sailResponseTime = 5.0;
    public static int sailScanInterval = 20;

    public static double engineThrust = 36.0;
    public static double engineReverse = 0.50;

    public static double oarImpulse = 130.0;
    public static double oarMaxSpeed = 1.1;

    public static double seaglideThrust = 0.045;
    public static double seaglideMaxSwim = 0.45;
    public static double seaglideFlightSpeed = 0.425;

    public static double buoyRise = 9.0;
    public static double buoySink = 6.0;

    public static double anchorMass = 2000.0;

    public static double boatSelfRighting = 4.0;

    public static int sailSubdivisions = 4;
    public static boolean seaglideScreenEffects = true;

    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    private static final ModConfigSpec.DoubleValue BASE_WIND;
    private static final ModConfigSpec.DoubleValue GUST_AMPLITUDE;
    private static final ModConfigSpec.DoubleValue RAIN_BOOST;
    private static final ModConfigSpec.DoubleValue THUNDER_BOOST;
    private static final ModConfigSpec.DoubleValue WIND_OCCLUSION;

    private static final ModConfigSpec.DoubleValue SAIL_HULL_SPEED;
    private static final ModConfigSpec.DoubleValue SAIL_MAX_SPEED;
    private static final ModConfigSpec.DoubleValue SAIL_UPWIND_EFFICIENCY;
    private static final ModConfigSpec.DoubleValue SAIL_CANVAS_NEEDED;
    private static final ModConfigSpec.DoubleValue SAIL_RESPONSE_TIME;
    private static final ModConfigSpec.IntValue SAIL_SCAN_INTERVAL;

    private static final ModConfigSpec.DoubleValue ENGINE_THRUST;
    private static final ModConfigSpec.DoubleValue ENGINE_REVERSE;

    private static final ModConfigSpec.DoubleValue OAR_IMPULSE;
    private static final ModConfigSpec.DoubleValue OAR_MAX_SPEED;

    private static final ModConfigSpec.DoubleValue SEAGLIDE_THRUST;
    private static final ModConfigSpec.DoubleValue SEAGLIDE_MAX_SWIM;
    private static final ModConfigSpec.DoubleValue SEAGLIDE_FLIGHT_SPEED;

    private static final ModConfigSpec.DoubleValue BUOY_RISE;
    private static final ModConfigSpec.DoubleValue BUOY_SINK;

    private static final ModConfigSpec.DoubleValue ANCHOR_MASS;
    private static final ModConfigSpec.DoubleValue BOAT_SELF_RIGHTING;

    private static final ModConfigSpec.IntValue SAIL_SUBDIVISIONS;
    private static final ModConfigSpec.BooleanValue SEAGLIDE_SCREEN_EFFECTS;

    static {
        ModConfigSpec.Builder server = new ModConfigSpec.Builder();

        server.push("wind");
        BASE_WIND = server
                .comment("Wind strength with no weather and no gust, before terrain and altitude.",
                        "This is the baseline every other wind figure is measured against.")
                .defineInRange("baseWind", 1.0, 0.0, 10.0);
        GUST_AMPLITUDE = server
                .comment("How far gusts swing above and below the baseline, as a fraction of it.",
                        "0 = perfectly steady wind, 1 = gusts from nothing to double strength.")
                .defineInRange("gustAmplitude", 0.25, 0.0, 2.0);
        RAIN_BOOST = server
                .comment("Extra wind while it rains, as a fraction of the baseline.")
                .defineInRange("rainBoost", 0.8, 0.0, 5.0);
        THUNDER_BOOST = server
                .comment("Extra wind during a thunderstorm, as a fraction of the baseline.")
                .defineInRange("thunderBoost", 1.6, 0.0, 5.0);
        WIND_OCCLUSION = server
                .comment("How much terrain upwind can steal from the wind.",
                        "0 = terrain never blocks wind, 1 = a cliff can cut it entirely.")
                .defineInRange("terrainShelter", 0.5, 0.0, 1.0);
        server.pop();

        server.push("sails");
        SAIL_HULL_SPEED = server
                .comment("Hull speed coefficient. A fully rigged ship running before the wind",
                        "cruises at this times the square root of its length, in m/s.",
                        "4.0 gives about 8 m/s for a 4-block raft and 14 m/s (the cap) for a 13-block ship.")
                .defineInRange("sailHullSpeed", 4.0, 0.1, 10.0);
        SAIL_MAX_SPEED = server
                .comment("Speed no sailing ship can go past, however big it is, in m/s.")
                .defineInRange("sailMaxSpeed", 14.0, 0.5, 30.0);
        SAIL_UPWIND_EFFICIENCY = server
                .comment("Share of its speed a ship keeps on a heading none of its sails can use,",
                        "such as square sails beating upwind or any sail straight into the wind.",
                        "0 = dead in the water, 1 = heading and rig no longer matter.")
                .defineInRange("sailUpwindEfficiency", 0.1, 0.0, 1.0);
        SAIL_CANVAS_NEEDED = server
                .comment("How much canvas a ship needs for its size to reach full speed.",
                        "Higher means big ships need more sails before they get going.")
                .defineInRange("sailCanvasNeeded", 0.2, 0.05, 5.0);
        SAIL_RESPONSE_TIME = server
                .comment("Seconds a small ship takes to pick up speed. Big ships take up to twice as long.")
                .defineInRange("sailResponseTime", 5.0, 0.5, 30.0);
        SAIL_SCAN_INTERVAL = server
                .comment("Ticks between two sweeps looking for sails on a ship.",
                        "Higher is cheaper but slower to notice a sail being added or removed.")
                .defineInRange("sailRescanInterval", 20, 1, 200);
        server.pop();

        server.push("hull");
        BOAT_SELF_RIGHTING = server
                .comment("How hard a floating boat swings back upright, like a weighted keel, in rad/s² at a full side tilt.",
                        "Keeps tall masts from tipping narrow hulls over. 0 leaves only the buoyancy of the hold.")
                .defineInRange("selfRighting", 4.0, 0.0, 20.0);
        server.pop();

        server.push("boatEngine");
        ENGINE_THRUST = server
                .comment("Boat engine thrust at full throttle.")
                .defineInRange("enginePower", 36.0, 0.0, 200.0);
        ENGINE_REVERSE = server
                .comment("Share of that power available in reverse.")
                .defineInRange("engineReversePower", 0.50, 0.0, 1.0);
        server.pop();

        server.push("oars");
        OAR_IMPULSE = server
                .comment("Push delivered by one oar stroke.")
                .defineInRange("oarStrokePower", 130.0, 0.0, 1000.0);
        OAR_MAX_SPEED = server
                .comment("Speed past which rowing stops helping, in blocks per tick.")
                .defineInRange("oarMaxSpeed", 1.1, 0.0, 10.0);
        server.pop();

        server.push("seaglide");
        SEAGLIDE_THRUST = server
                .comment("Push the seaglide adds each tick while running.")
                .defineInRange("seaglidePower", 0.045, 0.0, 1.0);
        SEAGLIDE_MAX_SWIM = server
                .comment("Top speed under the seaglide, in blocks per tick.",
                        "0.45 is about 9 blocks per second.")
                .defineInRange("seaglideMaxSpeed", 0.45, 0.0, 5.0);
        SEAGLIDE_FLIGHT_SPEED = server
                .comment("Speed the seaglide pulls you to while gliding with an elytra, in blocks per tick.",
                        "0.425 is about 8.5 blocks per second, a quarter of the speed of a firework rocket (about 1.7).")
                .defineInRange("seaglideFlightSpeed", 0.425, 0.0, 5.0);
        server.pop();

        server.push("buoy");
        BUOY_RISE = server
                .comment("Fastest a buoy climbs back to the surface, in blocks per second.")
                .defineInRange("buoyMaxRiseSpeed", 9.0, 0.0, 100.0);
        BUOY_SINK = server
                .comment("Fastest a buoy is allowed to be dragged under, in blocks per second.")
                .defineInRange("buoyMaxSinkSpeed", 6.0, 0.0, 100.0);
        server.pop();

        server.push("anchor");
        ANCHOR_MASS = server
                .comment("Mass a dropped anchor pins the ship with once it bites the sea floor.",
                        "Raise it if big ships still drift at anchor.")
                .defineInRange("anchorHoldingMass", 2000.0, 0.0, 100000.0);
        server.pop();

        SERVER_SPEC = server.build();

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();

        client.push("visual");
        SAIL_SUBDIVISIONS = client
                .comment("Sail mesh subdivision level (GPU tessellation).",
                        "Lower = blockier, more vanilla-Minecraft look. Higher = smoother, cloth-like billowing.",
                        "Purely aesthetic: GPU tessellation runs on the graphics card with negligible performance cost.",
                        "1 = flat faces (no subdivision), 8 = very smooth. Applies when you close the config screen.")
                .defineInRange("sailSubdivisions", 4, 1, 8);
        SEAGLIDE_SCREEN_EFFECTS = client
                .comment("Camera shake and wind speed-lines at full seaglide speed.",
                        "Turn off if the motion bothers you; speed itself is unchanged.")
                .define("seaglideScreenEffects", true);
        client.pop();

        CLIENT_SPEC = client.build();
    }

    public static void onConfig(ModConfigEvent event) {
        if (event instanceof ModConfigEvent.Unloading)
            return;
        if (event.getConfig().getSpec() == SERVER_SPEC) {
            pullServer();
        } else if (event.getConfig().getSpec() == CLIENT_SPEC) {
            pullClient();
        }
    }

    private static void pullServer() {
        baseWind = BASE_WIND.get();
        gustAmplitude = GUST_AMPLITUDE.get();
        rainBoost = RAIN_BOOST.get();
        thunderBoost = THUNDER_BOOST.get();
        windOcclusion = WIND_OCCLUSION.get();

        sailHullSpeed = SAIL_HULL_SPEED.get();
        sailMaxSpeed = SAIL_MAX_SPEED.get();
        sailUpwindEfficiency = SAIL_UPWIND_EFFICIENCY.get();
        sailCanvasNeeded = SAIL_CANVAS_NEEDED.get();
        sailResponseTime = SAIL_RESPONSE_TIME.get();
        sailScanInterval = SAIL_SCAN_INTERVAL.get();

        engineThrust = ENGINE_THRUST.get();
        engineReverse = ENGINE_REVERSE.get();

        oarImpulse = OAR_IMPULSE.get();
        oarMaxSpeed = OAR_MAX_SPEED.get();

        seaglideThrust = SEAGLIDE_THRUST.get();
        seaglideMaxSwim = SEAGLIDE_MAX_SWIM.get();
        seaglideFlightSpeed = SEAGLIDE_FLIGHT_SPEED.get();

        buoyRise = BUOY_RISE.get();
        buoySink = BUOY_SINK.get();

        anchorMass = ANCHOR_MASS.get();

        boatSelfRighting = BOAT_SELF_RIGHTING.get();
    }

    private static void pullClient() {
        sailSubdivisions = SAIL_SUBDIVISIONS.get();
        seaglideScreenEffects = SEAGLIDE_SCREEN_EFFECTS.get();
    }
}
