package com.maxenonyme.createsubmarine.submarine.client;

import net.minecraft.network.chat.Component;

import java.util.List;

public record SonarContact(float u, float v, int kind, Component name) {
    public static final int MINE = 0;
    public static final int HOSTILE = 1;
    public static final int CREATURE = 2;
    public static final int VESSEL = 3;

    private static final int[] COLORS = { 0xFFA8433C, 0xFFA8433C, 0xFF3F86E0, 0xFFD9822B };

    public int color() {
        return COLORS[kind];
    }

    public static SonarContact nearest(List<SonarContact> contacts, float u, float v, float reachU, float reachV) {
        SonarContact best = null;
        float bestDistance = Float.MAX_VALUE;
        for (SonarContact contact : contacts) {
            float du = (contact.u() - u) / reachU;
            float dv = (contact.v() - v) / reachV;
            float distance = du * du + dv * dv;
            if (distance <= 1f && distance < bestDistance) {
                bestDistance = distance;
                best = contact;
            }
        }
        return best;
    }
}
