package com.maxenonyme.createsubmarine.submarine.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.SharedConstants;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class UpdateChecker {
    private static volatile boolean updateAvailable = false;
    private static volatile String latestVersion = null;
    private static volatile String latestChangelog = null;

    public static void check() {
        if (!FMLEnvironment.dist.isClient()) {
            checkDedicatedServer();
            return;
        }

        String currentVersion = ModList.get()
                .getModContainerById(CreateSubmarine.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("2.1.4");

        String gameVersion = SharedConstants.getCurrentVersion().getName();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.modrinth.com/v2/project/mva5q4qZ/version"))
                .header("User-Agent", "create-deep-seas-update-checker/1.0.0 (maxenonyme/create-deep-seas)")
                .timeout(Duration.ofSeconds(10))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(body -> {
                    try {
                        JsonArray versions = JsonParser.parseString(body).getAsJsonArray();
                        for (JsonElement element : versions) {
                            JsonObject versionObj = element.getAsJsonObject();
                            if (!"listed".equalsIgnoreCase(versionObj.get("status").getAsString())) {
                                continue;
                            }

                            boolean matchesLoader = false;
                            JsonArray loaders = versionObj.getAsJsonArray("loaders");
                            for (JsonElement loader : loaders) {
                                if ("neoforge".equalsIgnoreCase(loader.getAsString())) {
                                    matchesLoader = true;
                                    break;
                                }
                            }

                            if (!matchesLoader) {
                                continue;
                            }

                            boolean matchesGameVersion = false;
                            JsonArray gameVersions = versionObj.getAsJsonArray("game_versions");
                            for (JsonElement gv : gameVersions) {
                                if (gameVersion.equalsIgnoreCase(gv.getAsString())) {
                                    matchesGameVersion = true;
                                    break;
                                }
                            }

                            if (!matchesGameVersion) {
                                continue;
                            }

                            String onlineVersion = versionObj.get("version_number").getAsString();
                            if (compareVersions(currentVersion, onlineVersion) < 0) {
                                latestVersion = onlineVersion;
                                if (versionObj.has("changelog") && !versionObj.get("changelog").isJsonNull()) {
                                    latestChangelog = versionObj.get("changelog").getAsString();
                                } else {
                                    latestChangelog = "";
                                }
                                updateAvailable = true;
                            }
                            break;
                        }
                    } catch (Exception e) {
                        CreateSubmarine.LOGGER.error("[CDS] Failed to parse Modrinth update check response", e);
                    }
                })
                .exceptionally(throwable -> {
                    CreateSubmarine.LOGGER.error("[CDS] Failed to check for updates from Modrinth", throwable);
                    return null;
                });
    }

    public static boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public static String getLatestVersion() {
        return latestVersion;
    }

    public static String getLatestChangelog() {
        return latestChangelog;
    }

    public static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("-", 2);
        String[] parts2 = v2.split("-", 2);

        String[] numbers1 = parts1[0].split("\\.");
        String[] numbers2 = parts2[0].split("\\.");

        int length = Math.max(numbers1.length, numbers2.length);
        for (int i = 0; i < length; i++) {
            int n1 = i < numbers1.length ? parseOrZero(numbers1[i]) : 0;
            int n2 = i < numbers2.length ? parseOrZero(numbers2[i]) : 0;
            if (n1 != n2) {
                return Integer.compare(n1, n2);
            }
        }

        if (parts1.length == 1 && parts2.length == 2) {
            return 1;
        }
        if (parts1.length == 2 && parts2.length == 1) {
            return -1;
        }
        if (parts1.length == 2 && parts2.length == 2) {
            return parts1[1].compareTo(parts2[1]);
        }
        return 0;
    }

    private static int parseOrZero(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void checkDedicatedServer() {
        String currentVersion = ModList.get()
                .getModContainerById(CreateSubmarine.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("2.1.4");
        String gameVersion = SharedConstants.getCurrentVersion().getName();

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.modrinth.com/v2/project/mva5q4qZ/version"))
                .header("User-Agent", "create-deep-seas-update-checker/1.0.0 (maxenonyme/create-deep-seas)")
                .timeout(Duration.ofSeconds(10)).build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(body -> {
                    try {
                        JsonArray versions = JsonParser.parseString(body).getAsJsonArray();
                        for (JsonElement element : versions) {
                            JsonObject versionObj = element.getAsJsonObject();
                            if (!"listed".equalsIgnoreCase(versionObj.get("status").getAsString()))
                                continue;

                            boolean matchesLoader = false;
                            for (JsonElement loader : versionObj.getAsJsonArray("loaders")) {
                                if ("neoforge".equalsIgnoreCase(loader.getAsString())) {
                                    matchesLoader = true;
                                    break;
                                }
                            }
                            if (!matchesLoader)
                                continue;

                            boolean matchesGameVersion = false;
                            for (JsonElement gv : versionObj.getAsJsonArray("game_versions")) {
                                if (gameVersion.equalsIgnoreCase(gv.getAsString())) {
                                    matchesGameVersion = true;
                                    break;
                                }
                            }
                            if (!matchesGameVersion)
                                continue;

                            String onlineVersion = versionObj.get("version_number").getAsString();
                            if (compareVersions(currentVersion, onlineVersion) < 0) {
                            }
                            break;
                        }
                    } catch (Exception e) {
                        CreateSubmarine.LOGGER.error("[CDS] Failed to parse Modrinth update check response on server", e);
                    }
                }).exceptionally(throwable -> {
                    CreateSubmarine.LOGGER.error("[CDS] Failed to check for updates from Modrinth on server", throwable);
                    return null;
                });
    }
}
