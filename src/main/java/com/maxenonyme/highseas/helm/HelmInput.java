package com.maxenonyme.highseas.helm;

public final class HelmInput {
    private HelmInput() {
    }

    public static final int FORWARD = 1;
    public static final int BACKWARD = 2;
    public static final int LEFT = 4;
    public static final int RIGHT = 8;

    public static boolean has(byte mask, int bit) {
        return (mask & bit) != 0;
    }
}
