package com.maxenonyme.createsubmarine.submarine.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;

public class SubmarineClientState {
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("create_submarine_client_state.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static boolean lithostitchedScreenSeen = false;
    public static boolean safetyScreenSeen = false;
    public static String ignoredUpdateVersion = "";

    public static void load() {
        if (Files.exists(PATH)) {
            try {
                JsonObject json = GSON.fromJson(Files.readString(PATH), JsonObject.class);
                if (json != null) {
                    if (json.has("lithostitchedScreenSeen")) {
                        lithostitchedScreenSeen = json.get("lithostitchedScreenSeen").getAsBoolean();
                    }
                    if (json.has("safetyScreenSeen")) {
                        safetyScreenSeen = json.get("safetyScreenSeen").getAsBoolean();
                    }
                    if (json.has("ignoredUpdateVersion")) {
                        ignoredUpdateVersion = json.get("ignoredUpdateVersion").getAsString();
                    }
                }
            } catch (Exception e) {
                CreateSubmarine.LOGGER.error("[CDS] Failed to load create_submarine_client_state.json", e);
            }
        }
    }

    public static void save() {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("lithostitchedScreenSeen", lithostitchedScreenSeen);
            json.addProperty("safetyScreenSeen", safetyScreenSeen);
            json.addProperty("ignoredUpdateVersion", ignoredUpdateVersion);
            Files.writeString(PATH, GSON.toJson(json));
        } catch (Exception e) {
            CreateSubmarine.LOGGER.error("[CDS] Failed to save create_submarine_client_state.json", e);
        }
    }

    public static boolean hasSeenLithostitchedScreen() {
        return lithostitchedScreenSeen;
    }

    public static void setLithostitchedScreenSeen(boolean seen) {
        lithostitchedScreenSeen = seen;
        save();
    }

    public static boolean hasSeenSafetyScreen() {
        return safetyScreenSeen;
    }

    public static void setSafetyScreenSeen(boolean seen) {
        safetyScreenSeen = seen;
        save();
    }

    public static String getIgnoredUpdateVersion() {
        if (!net.neoforged.fml.loading.FMLEnvironment.production && SubmarineConfig.IGNORED_UPDATE_VERSION != null) {
            return SubmarineConfig.IGNORED_UPDATE_VERSION.get();
        }
        return ignoredUpdateVersion;
    }

    public static void setIgnoredUpdateVersion(String version) {
        if (!net.neoforged.fml.loading.FMLEnvironment.production && SubmarineConfig.IGNORED_UPDATE_VERSION != null) {
            SubmarineConfig.IGNORED_UPDATE_VERSION.set(version);
            SubmarineConfig.IGNORED_UPDATE_VERSION.save();
        } else {
            ignoredUpdateVersion = version;
            save();
        }
    }
}
