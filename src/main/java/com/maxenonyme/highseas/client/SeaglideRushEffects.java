package com.maxenonyme.highseas.client;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class SeaglideRushEffects {
    private SeaglideRushEffects() {
    }

    private static final float SETTLED = 0.004f;

    private static final float SHAKE_PITCH = 0.45f;
    private static final float SHAKE_YAW = 0.30f;
    private static final float SHAKE_ROLL = 0.80f;

    private static final int EDGE_STEPS = 48;
    private static final float EDGE_SPAN = 0.26f;
    private static final int EDGE_ALPHA = 140;
    private static final int EDGE_TINT = 0x0A1E2A;

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float rush = SeaglideClientHandler.rush();
        if (rush < SETTLED) {
            return;
        }
        float t = AnimationTickHolder.getRenderTime();

        float pitch = Mth.sin(t * 0.91f) * 0.6f + Mth.sin(t * 2.33f) * 0.4f;
        float yaw = Mth.sin(t * 1.27f + 1.7f) * 0.6f + Mth.sin(t * 3.11f) * 0.4f;
        float roll = Mth.sin(t * 0.73f + 0.5f) * 0.7f + Mth.sin(t * 1.97f) * 0.3f;

        event.setPitch(event.getPitch() + pitch * SHAKE_PITCH * rush);
        event.setYaw(event.getYaw() + yaw * SHAKE_YAW * rush);
        event.setRoll(event.getRoll() + roll * SHAKE_ROLL * rush);
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        float rush = SeaglideClientHandler.rush();
        if (rush < SETTLED) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        float pulse = 0.85f + 0.15f * Mth.sin(AnimationTickHolder.getRenderTime() * 2.1f);
        int spanX = Math.round(width * EDGE_SPAN);
        int spanY = Math.round(height * EDGE_SPAN);

        for (int i = 0; i < EDGE_STEPS; i++) {
            float depth = (i + 0.5f) / EDGE_STEPS;
            float falloff = (1.0f - depth) * (1.0f - depth) * (1.0f - depth);
            int alpha = Math.round(EDGE_ALPHA * rush * pulse * falloff);
            if (alpha <= 0) {
                continue;
            }
            int color = alpha << 24 | EDGE_TINT;

            int x0 = spanX * i / EDGE_STEPS;
            int x1 = spanX * (i + 1) / EDGE_STEPS;
            int y0 = spanY * i / EDGE_STEPS;
            int y1 = spanY * (i + 1) / EDGE_STEPS;

            if (x1 > x0) {
                graphics.fill(x0, 0, x1, height, color);
                graphics.fill(width - x1, 0, width - x0, height, color);
            }
            if (y1 > y0) {
                graphics.fill(0, y0, width, y1, color);
                graphics.fill(0, height - y1, width, height - y0, color);
            }
        }
    }
}
