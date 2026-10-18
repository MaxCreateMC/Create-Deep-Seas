package com.maxenonyme.createsubmarine.submarine.client;

import com.google.gson.JsonSyntaxException;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.io.IOException;

public class PressureVision {
    private static final ResourceLocation CHAIN = ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID,
            "shaders/post/pressure_vision.json");
    private static final float CALM_BPM = 72f;
    private static final float PANIC_BPM = 190f;
    public static final float HEART_THRESHOLD = 0.08f;
    private static final int HEART_LAYERS = 4;

    private static PostChain chain;
    private static boolean broken;
    private static int width = -1;
    private static int height = -1;
    private static float danger;
    private static float previous;
    private static float beatIn;
    private static float grey;
    private static float previousGrey;
    private static float squeeze;
    private static float previousSqueeze;
    private static float duck = 1f;
    private static float pushedDuck = 1f;
    private static float muffle;
    private static PanicDrone drone;
    private static float gasp;
    private static float previousGasp;

    public static boolean enabled() {
        return !SubmarineConfig.CLIENT_SPEC.isLoaded() || SubmarineConfig.PRESSURE_EFFECTS.get();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused())
            return;

        float target = mc.player == null || mc.level == null || !enabled() ? 0f : ImplosionCinematics.dread();
        previous = danger;
        danger += (target - danger) * (target > danger ? 0.04f : 0.03f);
        if (danger < 0.005f)
            danger = 0f;
        float greyTarget = mc.player == null || mc.level == null || !enabled() ? 0f : ImplosionCinematics.greyness();
        previousGrey = grey;
        grey += (greyTarget - grey) * (greyTarget > grey ? 0.0125f : 0.04f);
        if (grey < 0.005f)
            grey = 0f;
        float squeezeTarget = mc.player == null || mc.level == null || !enabled() ? 0f : ImplosionCinematics.squeeze();
        previousSqueeze = squeeze;
        if (squeezeTarget > squeeze)
            squeeze = Math.min(squeezeTarget, squeeze + 1f / 60f);
        else
            squeeze += (squeezeTarget - squeeze) * 0.03f;
        if (squeeze < 0.005f)
            squeeze = 0f;
        previousGasp = gasp;
        gasp += Mth.TWO_PI * Mth.lerp(danger, 0.25f, 1.1f) / 20f;

        if (gasp > Mth.TWO_PI * 64f) {
            gasp -= Mth.TWO_PI * 64f;
            previousGasp -= Mth.TWO_PI * 64f;
        }

        heartbeat(mc);
        duck(mc);
        EarPressure.apply(mc, muffle);
        hum(mc);
    }

    public static float droneVolume() {
        Minecraft mc = Minecraft.getInstance();
        if (ImplosionCinematics.silent() || mc.player == null || mc.player.isDeadOrDying())
            return 0f;

        return fear() * fear();
    }

    private static float fear() {
        float fear = Mth.clamp((danger - HEART_THRESHOLD) / (1f - HEART_THRESHOLD), 0f, 1f);
        return fear * fear * (3f - 2f * fear);
    }

    private static void hum(Minecraft mc) {
        if (droneVolume() <= 0.01f || (drone != null && !drone.isStopped()))
            return;

        drone = new PanicDrone();
        mc.getSoundManager().play(drone);
    }

    public static void dropSqueeze() {
        squeeze = 0f;
        previousSqueeze = 0f;
    }

    public static float panic() {
        return grey;
    }

    public static float duckVolume() {
        return duck;
    }

    private static void duck(Minecraft mc) {
        boolean silent = ImplosionCinematics.silent();
        float duckTarget = silent ? 0.03f : 1f - 0.45f * fear();
        duck += (duckTarget - duck) * (silent ? 0.45f : 0.1f);
        if (Math.abs(duck - duckTarget) < 0.005f)
            duck = duckTarget;
        float muffleTarget = silent ? 1f : Math.max(fear(), squeeze);
        muffle += (muffleTarget - muffle) * (silent ? 0.2f : 0.1f);
        if (Math.abs(duck - pushedDuck) < 0.01f && (duck < 1f || pushedDuck == 1f))
            return;

        pushedDuck = duck;
        mc.getSoundManager().updateSourceVolume(SoundSource.BLOCKS, mc.options.getSoundSourceVolume(SoundSource.BLOCKS));
    }

    private static void heartbeat(Minecraft mc) {
        if (mc.player == null || mc.player.isDeadOrDying()) {
            if (beatIn != 0f)
                mc.getSoundManager().stop(CreateSubmarine.HEARTBEAT_SOUND.get().getLocation(), SoundSource.MASTER);
            beatIn = 0f;
            return;
        }

        if (danger < HEART_THRESHOLD || ImplosionCinematics.silent()) {
            beatIn = 0f;
            return;
        }

        beatIn -= 1f;
        if (beatIn > 0f)
            return;

        beatIn += 1200f / Mth.lerp(danger, CALM_BPM, PANIC_BPM);
        float pitch = 0.95f + 0.15f * danger;
        float volume = Math.min(1f, 0.6f + 0.6f * danger);

        for (int layer = 0; layer < HEART_LAYERS; layer++)
            mc.getSoundManager().play(SimpleSoundInstance.forUI(CreateSubmarine.HEARTBEAT_SOUND.get(), pitch, volume));
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.player == null || mc.player.isDeadOrDying()
                || danger < HEART_THRESHOLD && previous < HEART_THRESHOLD)
            return;
        float partialTick = (float) event.getPartialTick();
        float fear = Mth.clamp((Mth.lerp(partialTick, previous, danger) - HEART_THRESHOLD) / (1f - HEART_THRESHOLD), 0f, 1f);
        float amp = fear * fear * (3f - 2f * fear);
        float p = Mth.lerp(partialTick, previousGasp, gasp);
        float sway = Mth.sin(p) + 0.35f * Mth.sin(p * 0.53f + 1.7f) + 0.15f * Mth.sin(p * 1.37f + 0.4f);
        float drift = Mth.sin(p * 0.5f + 0.9f) + 0.4f * Mth.sin(p * 0.21f + 2.3f);
        float breath = 0.5f - 0.5f * Mth.cos(p * 2f);
        event.setRoll(event.getRoll() + sway * amp * 3.2f);
        event.setYaw(event.getYaw() + drift * amp * 1.1f);
        event.setPitch(event.getPitch() + (breath - 0.5f) * amp * 1.4f);
    }

    public static void onRenderGui(RenderGuiEvent.Pre event) {
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float tunnel = Mth.lerp(partialTick, previousSqueeze, squeeze);
        float strength = Math.max(Mth.lerp(partialTick, previous, danger), tunnel);
        if (strength < 0.01f)
            return;

        Minecraft mc = Minecraft.getInstance();
        PostChain effect = chain(mc);
        if (effect == null)
            return;

        float fade = Mth.lerp(partialTick, previousGrey, grey);
        effect.setUniform("Strength", strength);
        effect.setUniform("Grey", fade);
        effect.setUniform("Tunnel", tunnel);
        effect.process(partialTick);
        mc.getMainRenderTarget().bindWrite(false);
    }

    private static PostChain chain(Minecraft mc) {
        if (broken)
            return null;

        RenderTarget main = mc.getMainRenderTarget();

        if (chain == null) {
            try {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, CHAIN);
            } catch (IOException | JsonSyntaxException e) {
                CreateSubmarine.LOGGER.warn("Could not load the pressure vision effect", e);
                broken = true;
                return null;
            }

            width = -1;
        }

        if (main.width != width || main.height != height) {
            width = main.width;
            height = main.height;
            chain.resize(width, height);
        }

        return chain;
    }

    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            if (chain != null)
                chain.close();
            chain = null;
            broken = false;
        });
    }
}
