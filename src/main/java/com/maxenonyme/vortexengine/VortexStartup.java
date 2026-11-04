package com.maxenonyme.vortexengine;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class VortexStartup {

    public static final String FILE = "create_submarine-vortex.toml";

    private static Boolean enabled;

    public static boolean enabled() {
        if (enabled == null)
            enabled = read(FMLPaths.CONFIGDIR.get().resolve(FILE));
        return enabled;
    }

    private static boolean read(Path file) {
        if (!Files.isRegularFile(file))
            return true;

        try {
            String section = "";

            for (String line : Files.readAllLines(file)) {
                String text = line.strip();
                if (text.startsWith("[")) {
                    section = text;
                    continue;
                }

                if (section.equals("[vortex]") && text.matches("enabled\\s*=.*"))
                    return !text.substring(text.indexOf('=') + 1).strip().startsWith("false");
            }
        } catch (IOException e) {
            return true;
        }

        return true;
    }
}
