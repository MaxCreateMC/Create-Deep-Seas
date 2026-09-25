package com.maxenonyme.createsubmarine.submarine.util;

import net.neoforged.fml.ModList;

public class CompatUtil {
    private static Boolean sodiumLoaded = null;
    private static Boolean veilLoaded = null;

    public static boolean isSodiumLoaded() {
        if (sodiumLoaded == null) {
            sodiumLoaded = ModList.get().isLoaded("sodium");
        }
        return sodiumLoaded;
    }

    public static boolean isVeilLoaded() {
        if (veilLoaded == null) {
            veilLoaded = ModList.get().isLoaded("veil");
        }
        return veilLoaded;
    }
}
