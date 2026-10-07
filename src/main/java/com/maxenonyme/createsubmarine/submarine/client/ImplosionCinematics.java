package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.AbyssDimension.client.CameraShake;
import com.maxenonyme.createsubmarine.submarine.network.ImplosionFxPayload;
import net.minecraft.client.Minecraft;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

import java.util.HashSet;
import java.util.Set;

public final class ImplosionCinematics {
    private ImplosionCinematics() {
    }

    private static final int BLACKOUT_HOLD = 60;
    private static final int BLACKOUT_FADE = 20;
    private static final int EPITAPHS = 4;
    private static final float EPITAPH_STEP = 20f;
    private static final float STRESS_PEAK = 10f;
    private static final int DEATH_SHAKE = 12;
    private static final float STRAIN_SWAY = 0.35f;
    private static final float STRAIN_JOLT = 0.9f;
    private static final int JOLT_TICKS = 12;
    private static final int JOLT_ODDS = 6;

    private static int stressTotal;
    private static int stressLeft;
    private static float stressStrength;
    private static boolean lightsOut;
    private static int nextFlicker;
    private static int blackout = -1;
    private static int lead;
    private static int epitaph;
    private static int strainLeft;
    private static float strain;
    private static int jolt;
    private static final Set<SoundInstance> DEATH_SOUNDS = new HashSet<>();

    public static void accept(ImplosionFxPayload payload) {
        switch (payload.kind()) {
            case ImplosionFxPayload.STRESS -> {
                stressTotal = Math.max(1, payload.ticks());
                stressLeft = stressTotal;
                stressStrength = payload.strength();
                nextFlicker = 0;
            }
            case ImplosionFxPayload.STRAIN -> {
                strainLeft = payload.ticks();
                strain = payload.strength();
            }
            case ImplosionFxPayload.FATAL -> {
                endStress();
                die(Minecraft.getInstance());
            }
            default -> {
                endStress();
                CameraShake.shake(4f + 10f * payload.strength(), 30 + (int) (40 * payload.strength()));
            }
        }
    }

    private static void die(Minecraft mc) {
        if (mc.player != null && !mc.player.shouldShowDeathScreen()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(CreateSubmarine.IMPLOSION_SOUND.get(), 1f, 1f));
            return;
        }
        CameraShake.shake(14f, DEATH_SHAKE + 8);
        lead = DEATH_SHAKE;
        epitaph = mc.level == null ? 0 : mc.level.random.nextInt(EPITAPHS);
        mc.getSoundManager().stop();
        DEATH_SOUNDS.clear();
        ring(mc, SimpleSoundInstance.forUI(CreateSubmarine.IMPLOSION_SOUND.get(), 1f, 1f));
        ring(mc, SimpleSoundInstance.forUI(CreateSubmarine.TINNITUS_SOUND.get(), 1f, 0.8f));
    }

    private static void ring(Minecraft mc, SoundInstance sound) {
        DEATH_SOUNDS.add(sound);
        mc.getSoundManager().play(sound);
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if ((blackout >= 0 || lead > 0) && !DEATH_SOUNDS.contains(event.getOriginalSound()))
            event.setSound(null);
    }

    public static boolean lightsOut() {
        return lightsOut;
    }

    private static void endStress() {
        stressLeft = 0;
        strainLeft = 0;
        jolt = 0;
        lightsOut = false;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused())
            return;
        if (stressLeft > 0) {
            stressLeft--;
            float progress = 1 - stressLeft / (float) stressTotal;
            if (--nextFlicker <= 0) {
                lightsOut = !lightsOut;
                int calm = Math.max(1, Math.round(7 * (1 - progress)));
                nextFlicker = lightsOut ? 1 + calm / 2 : calm + (mc.level == null ? 0 : mc.level.random.nextInt(calm + 1));
            }
            if (stressLeft == 0)
                lightsOut = false;
        }
        if (strainLeft > 0) {
            strainLeft--;
            if (jolt > 0)
                jolt--;
            creak(mc);
        }
        if (lead > 0 && --lead == 0)
            blackout = 0;
        if (blackout >= 0) {
            blackout++;
            boolean alive = mc.player != null && !mc.player.isDeadOrDying();
            if (blackout > BLACKOUT_HOLD + BLACKOUT_FADE || (alive && blackout > BLACKOUT_HOLD)) {
                blackout = -1;
                DEATH_SOUNDS.clear();
            }
        }
    }

    private static void creak(Minecraft mc) {
        if (mc.player == null || mc.level == null || stressLeft > 0)
            return;
        RandomSource random = mc.level.random;
        if (random.nextFloat() >= 0.002f + 0.006f * strain)
            return;
        double x = mc.player.getX() + (random.nextDouble() - 0.5) * 8;
        double y = mc.player.getEyeY() + (random.nextDouble() - 0.5) * 3;
        double z = mc.player.getZ() + (random.nextDouble() - 0.5) * 8;
        mc.level.playLocalSound(x, y, z, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.BLOCKS,
                0.25f + 0.45f * strain, 0.12f + random.nextFloat() * 0.18f, false);
        if (random.nextInt(JOLT_ODDS) == 0)
            jolt = JOLT_TICKS;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused())
            return;
        if (stressLeft <= 0) {
            if (strainLeft > 0)
                sway(event, mc);
            return;
        }
        float progress = 1 - (stressLeft - (float) event.getPartialTick()) / stressTotal;
        progress = Mth.clamp(progress, 0f, 1f);
        float amp = (0.25f + STRESS_PEAK * progress * progress * progress) * stressStrength;
        float t = mc.player.tickCount + (float) event.getPartialTick();
        float fast = 9f + 18f * progress;
        event.setPitch(event.getPitch() + (Mth.sin(t * fast) + 0.5f * Mth.sin(t * fast * 2.3f)) * amp * 0.6f);
        event.setYaw(event.getYaw() + (Mth.cos(t * fast * 1.3f) + 0.4f * Mth.sin(t * fast * 3.1f)) * amp * 0.6f);
        event.setRoll(event.getRoll() + Mth.sin(t * fast * 0.8f) * amp);
    }

    private static void sway(ViewportEvent.ComputeCameraAngles event, Minecraft mc) {
        float t = mc.player.tickCount + (float) event.getPartialTick();
        float amp = STRAIN_SWAY * (0.3f + 0.7f * strain);
        float kick = jolt > 0 ? STRAIN_JOLT * strain * (jolt - (float) event.getPartialTick()) / JOLT_TICKS : 0f;
        kick = Math.max(0f, kick);
        event.setPitch(event.getPitch() + Mth.sin(t * 0.21f) * amp * 0.5f + Mth.sin(t * 2.7f) * kick * 0.6f);
        event.setYaw(event.getYaw() + Mth.sin(t * 3.4f) * kick * 0.4f);
        event.setRoll(event.getRoll() + Mth.sin(t * 0.13f) * amp + Mth.cos(t * 3.1f) * kick);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        cover(event.getGuiGraphics(), (float) event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1000);
        cover(graphics, event.getPartialTick());
        graphics.pose().popPose();
    }

    private static void cover(GuiGraphics graphics, float partialTick) {
        if (blackout < 0)
            return;
        float alpha = 1f;
        if (blackout > BLACKOUT_HOLD)
            alpha = 1f - Mth.clamp((blackout - BLACKOUT_HOLD + partialTick) / BLACKOUT_FADE, 0f, 1f);
        if (alpha <= 0f)
            return;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (int) (alpha * 255) << 24);
        float t = blackout + partialTick;
        float shown = Math.min(Mth.clamp(t / EPITAPH_STEP, 0f, 1f), Mth.clamp((EPITAPH_STEP * 3 - t) / EPITAPH_STEP, 0f, 1f));
        if (shown < 0.05f)
            return;
        Font font = Minecraft.getInstance().font;
        Component line = Component.translatable("create_submarine.implosion.death." + epitaph);
        graphics.pose().pushPose();
        graphics.pose().translate(graphics.guiWidth() / 2f, graphics.guiHeight() / 2f, 0);
        graphics.pose().scale(1.3f, 1.3f, 1f);
        graphics.drawCenteredString(font, line, 0, -font.lineHeight / 2, ((int) (shown * 255) << 24) | 0xFFFFFF);
        graphics.pose().popPose();
    }
}
