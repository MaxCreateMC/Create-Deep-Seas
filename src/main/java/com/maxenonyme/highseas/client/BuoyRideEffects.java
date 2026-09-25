package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.block.entity.BuoySeatEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class BuoyRideEffects {
    private BuoyRideEffects() {
    }

    private static final double JOLT_SCALE = 3.4;
    private static final float JOLT_DECAY = 0.86f;
    private static final float JOLT_TRIGGER = 0.18f;
    private static final float SHAKE_PITCH = 6.5f;
    private static final float SHAKE_YAW = 5.0f;
    private static final float SHAKE_ROLL = 9.0f;
    private static final float SHAKE_FREQ = 3.1f;
    private static final float SETTLED = 0.01f;
    private static final float SWAY_GAIN = 9.0f;
    private static final float SWAY_SMOOTH = 0.18f;

    private static final float WIND_START = 3.0f;
    private static final float WIND_FULL = 9.0f;
    private static final float WIND_SMOOTH = 0.12f;
    private static final int STREAKS = 34;
    private static final float WIND_FADE = 0.5f;
    private static final float RING_INNER = 0.62f;
    private static final float RING_OUTER = 1.30f;

    private static double lastY = Double.NaN;
    private static double lastX;
    private static double lastZ;
    private static double lastDrop;
    private static float jolt;
    private static float joltDirX;
    private static float joltDirY;
    private static float joltStart;
    private static float wind;
    private static float sway;
    private static boolean headless;

    public static float jolt() {
        return jolt;
    }

    public static float sway() {
        return sway;
    }

    public static float wind() {
        return wind;
    }

    public static boolean hidingHead() {
        return headless;
    }

    public static boolean riding(LocalPlayer player) {
        return player != null && player.getVehicle() instanceof BuoySeatEntity;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!riding(player)) {
            lastY = Double.NaN;
            jolt = 0.0f;
            sway = 0.0f;
            wind = Mth.lerp(WIND_SMOOTH, wind, 0.0f);
            return;
        }

        float carried = 0.0f;
        if (!Double.isNaN(lastY)) {
            double drop = player.getY() - lastY;
            double kick = Math.abs(drop - lastDrop);
            float struck = (float) Math.min(1.0, kick * JOLT_SCALE);
            float faded = jolt * JOLT_DECAY;
            if (struck > faded && struck > JOLT_TRIGGER) {
                int corner = player.getRandom().nextInt(4);
                float spread = (player.getRandom().nextFloat() - 0.5f) * 0.7f;
                float angle = (float) (Math.PI / 4.0 + corner * Math.PI / 2.0) + spread;
                joltDirX = Mth.cos(angle);
                joltDirY = Mth.sin(angle);
                joltStart = AnimationTickHolder.getRenderTime();
            }
            jolt = Math.max(faded, struck);
            lastDrop = drop;

            double dx = player.getX() - lastX;
            double dz = player.getZ() - lastZ;
            carried = (float) Math.sqrt(dx * dx + dz * dz) * 20.0f;
            sway = Mth.lerp(SWAY_SMOOTH, sway, Mth.clamp((float) dx * SWAY_GAIN, -1.0f, 1.0f));
        }
        lastY = player.getY();
        lastX = player.getX();
        lastZ = player.getZ();

        float target = Mth.clamp((carried - WIND_START) / (WIND_FULL - WIND_START), 0.0f, 1.0f);
        wind = Mth.lerp(WIND_SMOOTH, wind, target);
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (jolt < SETTLED) {
            return;
        }
        float age = AnimationTickHolder.getRenderTime() - joltStart;
        float swing = Mth.cos(age * SHAKE_FREQ);
        float punch = jolt * swing;

        event.setPitch(event.getPitch() + joltDirY * SHAKE_PITCH * punch);
        event.setYaw(event.getYaw() + joltDirX * SHAKE_YAW * punch);
        event.setRoll(event.getRoll() + joltDirX * SHAKE_ROLL * punch);
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (wind < SETTLED) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float reach = Math.max(width, height);
        float t = AnimationTickHolder.getRenderTime();

        PoseStack ms = graphics.pose();
        ms.pushPose();
        ms.translate(width / 2.0f, height / 2.0f, 0.0f);

        for (int i = 0; i < STREAKS; i++) {
            float seed = i * 137.5f;
            float sweep = (t * (0.9f + (i % 5) * 0.11f) + seed) % 1.0f;
            float phase = ((t * 0.35f + i * 0.61f) % 1.0f);
            float span = RING_INNER + phase * (RING_OUTER - RING_INNER);
            int alpha = Math.round(255.0f * wind * (1.0f - phase * (1.0f - WIND_FADE)));
            if (alpha <= 2) {
                continue;
            }
            ms.pushPose();
            ms.mulPose(Axis.ZP.rotationDegrees(seed + sweep * 6.0f));
            int near = Math.round(reach * span * 0.5f);
            int len = Math.round(reach * (0.05f + (i % 4) * 0.02f) * (0.4f + wind));
            graphics.fill(near, -2, near + len, 2, alpha << 24 | 0xFFFFFF);
            ms.popPose();
        }
        ms.popPose();
    }

    public static void onRenderHand(RenderHandEvent event) {
        if (riding(Minecraft.getInstance().player)) {
            event.setCanceled(true);
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!riding(player) || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double x = Mth.lerp(pt, player.xo, player.getX()) - cam.x;
        double y = Mth.lerp(pt, player.yo, player.getY()) - cam.y;
        double z = Mth.lerp(pt, player.zo, player.getZ()) - cam.z;
        float yaw = Mth.rotLerp(pt, player.yRotO, player.getYRot());

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack ms = event.getPoseStack();
        ms.pushPose();
        headless = true;
        dispatcher.setRenderShadow(false);
        dispatcher.render(player, x, y, z, yaw, pt, ms, buffers, dispatcher.getPackedLightCoords(player, pt));
        dispatcher.setRenderShadow(true);
        headless = false;
        ms.popPose();
        buffers.endBatch();
    }
}
